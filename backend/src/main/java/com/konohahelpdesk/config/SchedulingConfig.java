package com.konohahelpdesk.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the @Scheduled sweeps (SLA breaches, contract visits and renewals, warranty reminders,
 * token cleanup, audit pruning).
 *
 * Switchable because those jobs must run ONCE, not once per container. With a single backend
 * container leave it on (the default). If you ever scale the backend out, either set
 * SCHEDULING_ENABLED=false on every replica but one, or add ShedLock with its Redis lock provider
 * so the replicas take turns — the job code doesn't change in either case.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
