package com.konohahelpdesk.service.ticket;

import com.konohahelpdesk.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the SLA breach sweep on a timer.
 *
 * Why a sweep rather than a timer per ticket: deadlines move (a priority change re-stamps them),
 * tickets get resolved between checks, and the app can be restarted at any moment — a periodic
 * "what is late right now" pass survives all three, where scheduled per-ticket callbacks don't.
 *
 * The work itself lives in {@link SlaBreachRecorder} so its @Transactional boundary is real.
 * This class only decides when to run and makes sure a bad pass can't take the scheduler down.
 */
@Component
public class SlaMonitor {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitor.class);

    private final SlaBreachRecorder recorder;
    private final AppProperties.SlaMonitoring cfg;

    public SlaMonitor(SlaBreachRecorder recorder, AppProperties props) {
        this.recorder = recorder;
        this.cfg = props.getSla();
    }

    @Scheduled(fixedDelayString = "${app.sla.check-interval-ms:300000}",
               initialDelayString = "${app.sla.check-interval-ms:300000}")
    public void sweep() {
        if (!cfg.isMonitorEnabled()) return;
        try {
            int breached = recorder.runOnce();
            if (breached > 0) log.info("SLA sweep recorded {} new breach(es)", breached);
        } catch (RuntimeException e) {
            // One bad pass (a malformed ticket, a mail failure) must not stop every later sweep.
            log.error("SLA sweep failed: {}", e.getMessage(), e);
        }
    }
}
