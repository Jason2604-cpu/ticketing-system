package com.konohahelpdesk.service.audit;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.dto.audit.AuditEventResponse;
import com.konohahelpdesk.dto.audit.AuditPageResponse;
import com.konohahelpdesk.model.audit.AuditEvent;
import com.konohahelpdesk.model.audit.AuditLevel;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.audit.AuditEventRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * User ids embedded in older details strings: {@code userId=2}, {@code assignee=7},
     * {@code by=1}. Details written since AuditLogger.describe() name people directly, so this only
     * ever matches historical rows — but those rows are the ones you most want to be able to read.
     */
    private static final Pattern USER_ID_REF = Pattern.compile("\\b(userId|assignee|by)=(\\d+)\\b");

    private final AuditEventRepository repository;
    private final UserRepository users;
    private final AppProperties.Audit cfg;

    public AuditQueryService(AuditEventRepository repository, UserRepository users, AppProperties props) {
        this.repository = repository;
        this.users = users;
        this.cfg = props.getAudit();
    }

    /**
     * Turns raw user ids in stored details into names, FOR DISPLAY ONLY.
     *
     * Audit rows are immutable — rewriting history to make it prettier is the one thing an audit
     * log must never do — so the stored text is left exactly as written and the substitution
     * happens on the way out. That also means it keeps working for rows written by an older
     * version of the app, which is the whole point.
     *
     * One query for the whole page, not one per row. Ids that no longer resolve (a purged account)
     * are left as they were: showing {@code userId=42} is honest, inventing a name is not.
     */
    private List<AuditEventResponse> enrich(List<AuditEvent> events) {
        Set<Long> ids = new HashSet<>();
        for (AuditEvent e : events) {
            if (e.getDetails() == null) continue;
            Matcher m = USER_ID_REF.matcher(e.getDetails());
            while (m.find()) ids.add(Long.valueOf(m.group(2)));
        }
        if (ids.isEmpty()) return events.stream().map(AuditEventResponse::from).toList();

        Map<Long, String> names = new HashMap<>();
        // findAllById, not a status-filtered query: a deactivated or soft-deleted account is
        // exactly the kind the log is being read about, and its name still belongs in the row.
        for (User u : users.findAllById(ids)) names.put(u.getId(), AuditLogger.describe(u));

        return events.stream().map(e -> AuditEventResponse.from(e, substitute(e.getDetails(), names))).toList();
    }

    private String substitute(String details, Map<Long, String> names) {
        if (details == null || details.isBlank()) return details;
        Matcher m = USER_ID_REF.matcher(details);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = names.get(Long.valueOf(m.group(2)));
            String replacement;
            if (name == null) {
                replacement = m.group(0);                       // unresolved: leave it honest
            } else {
                // Keep a label on the ones that aren't the subject, or two names end up sitting
                // side by side with nothing saying which is which.
                replacement = switch (m.group(1)) {
                    case "assignee" -> "assignee: " + name;
                    case "by" -> "by " + name;
                    default -> name;                            // userId= — the row is about them
                };
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
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
            enrich(result.getContent()),
            result.getTotalElements(),
            result.getTotalPages(),
            result.getNumber(),
            result.getSize(),
            repository.findDistinctActions());
    }

    /** The same filters, flattened for CSV. Capped so a three-year range can't stream the table. */
    @Transactional(readOnly = true)
    public List<AuditEventResponse> forExport(Filters f) {
        // Same enrichment as the page, so an export never reads worse than the screen it came from.
        return enrich(repository.findAll(
                spec(f),
                PageRequest.of(0, cfg.getMaxExportRows(), Sort.by(Sort.Direction.DESC, "createdAt", "id")))
            .getContent());
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
