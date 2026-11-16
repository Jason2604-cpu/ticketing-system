package com.konohahelpdesk.service.contract;

import com.konohahelpdesk.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the contract sweep on a timer — daily by default, because contract visits are dated, not
 * timed, and nothing about them changes between one hour and the next.
 *
 * Like {@code SlaMonitor}, this class only decides when to run and stops a bad pass from taking
 * the scheduler down; the work and its transaction boundary live in {@link ContractSweeper}.
 */
@Component
public class ContractMonitor {

    private static final Logger log = LoggerFactory.getLogger(ContractMonitor.class);

    private final ContractSweeper sweeper;
    private final AppProperties.Contracts cfg;

    public ContractMonitor(ContractSweeper sweeper, AppProperties props) {
        this.sweeper = sweeper;
        this.cfg = props.getContracts();
    }

    @Scheduled(fixedDelayString = "${app.contracts.check-interval-ms:86400000}",
               initialDelayString = "${app.contracts.initial-delay-ms:120000}")
    public void sweep() {
        if (!cfg.isMonitorEnabled()) return;
        try {
            int raised = sweeper.runOnce();
            if (raised > 0) log.info("Contract sweep raised {} maintenance ticket(s)", raised);
        } catch (RuntimeException e) {
            log.error("Contract sweep failed: {}", e.getMessage(), e);
        }
    }
}
