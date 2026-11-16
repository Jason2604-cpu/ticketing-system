package com.konohahelpdesk.service.audit;

import com.konohahelpdesk.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Trims the audit table to its retention window. The table only ever grows — every sign-in adds a
 * row — so something has to prune it, and it may as well be the app that writes it.
 *
 * The delete itself is {@code @Transactional} on {@link AuditQueryService}; this class only
 * decides when to run, for the same proxy reason as SlaMonitor and ContractMonitor.
 */
@Component
public class AuditRetentionMonitor {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionMonitor.class);

    private final AuditQueryService audit;
    private final AppProperties.Audit cfg;

    public AuditRetentionMonitor(AuditQueryService audit, AppProperties props) {
        this.audit = audit;
        this.cfg = props.getAudit();
    }

    @Scheduled(fixedDelayString = "${app.audit.prune-interval-ms:86400000}",
               initialDelayString = "${app.audit.prune-initial-delay-ms:300000}")
    public void prune() {
        if (cfg.getRetentionDays() <= 0) return;
        try {
            int removed = audit.prune();
            if (removed > 0) log.info("Pruned {} audit event(s) older than {} days", removed, cfg.getRetentionDays());
        } catch (RuntimeException e) {
            log.error("Audit retention prune failed: {}", e.getMessage(), e);
        }
    }
}
