package com.konohahelpdesk.service.audit;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.dto.audit.AuditEventResponse;
import com.konohahelpdesk.dto.audit.AuditPageResponse;
import com.konohahelpdesk.model.audit.AuditEvent;
import com.konohahelpdesk.model.audit.AuditLevel;
import com.konohahelpdesk.repository.audit.AuditEventRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the audit trail for the admin viewer. Read-only by construction — nothing in this class
 * writes, and the controller above it exposes no write endpoint, so "view the audit log" can be
 * delegated through {@code VIEW_AUDIT_LOG} without handing over anything else.
 *
 * Filters are composed as a {@link Specification} rather than a JPQL query with nullable
 * parameters: {@code (:x is null or col = :x)} sends Postgres an untyped null and fails. The same
 * spec backs both the page and the CSV export, so a download can never be wider than the view.
 */
@Service
public class AuditQueryService {

    private final AuditEventRepository repository;
    private final AppProperties.Audit cfg;

    public AuditQueryService(AuditEventRepository repository, AppProperties props) {
        this.repository = repository;
        this.cfg = props.getAudit();
    }

    /** All filters are optional; any combination narrows the result. */
    public record Filters(String action, Long actorId, AuditLevel level, LocalDate from, LocalDate to, String q) {}

    @Transactional(readOnly = true)
    public AuditPageResponse page(Filters f, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), cfg.getMaxPageSize());
        Page<AuditEvent> result = repository.findAll(
            spec(f),
            PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));

        return new AuditPageResponse(
            result.getContent().stream().map(AuditEventResponse::from).toList(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.getNumber(),
            result.getSize(),
            repository.findDistinctActions());
    }

    /** The same filters, flattened for CSV. Capped so a three-year range can't stream the table. */
    @Transactional(readOnly = true)
    public List<AuditEventResponse> forExport(Filters f) {
        return repository.findAll(
                spec(f),
                PageRequest.of(0, cfg.getMaxExportRows(), Sort.by(Sort.Direction.DESC, "createdAt", "id")))
            .getContent().stream().map(AuditEventResponse::from).toList();
    }

    private Specification<AuditEvent> spec(Filters f) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();

            if (f.action() != null && !f.action().isBlank()) {
                and.add(cb.equal(root.get("action"), f.action().trim()));
            }
            if (f.actorId() != null) {
                and.add(cb.equal(root.get("actorId"), f.actorId()));
            }
            if (f.level() != null) {
                and.add(cb.equal(root.get("level"), f.level()));
            }
            if (f.from() != null) {
                and.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay()));
            }
            if (f.to() != null) {
                // Inclusive of the whole end day — a user picking "to 10 Sep" means through the 10th.
                and.add(cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                and.add(cb.or(
                    cb.like(cb.lower(root.get("details")), like),
                    cb.like(cb.lower(root.get("actorName")), like),
                    cb.like(cb.lower(root.get("actorEmail")), like),
                    cb.like(cb.lower(root.get("action")), like),
                    cb.like(cb.lower(root.get("ipAddress")), like)));
            }
            return and.isEmpty() ? cb.conjunction() : cb.and(and.toArray(new Predicate[0]));
        };
    }

    /** Deletes rows past the retention window. Returns how many went. */
    @Transactional
    public int prune() {
        if (cfg.getRetentionDays() <= 0) return 0;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(cfg.getRetentionDays());
        return repository.deleteOlderThan(cutoff);
    }
}
