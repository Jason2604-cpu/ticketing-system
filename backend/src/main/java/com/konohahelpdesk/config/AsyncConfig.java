package com.konohahelpdesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async (email sending). Scheduling lives in {@link SchedulingConfig}. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
