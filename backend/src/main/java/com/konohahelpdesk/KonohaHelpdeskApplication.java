package com.konohahelpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
// @Async (MailDispatcher) is opt-in — without it mail would send on the request thread.
// @Scheduled jobs are switched on by SchedulingConfig (app.scheduling.enabled).
@EnableAsync
public class KonohaHelpdeskApplication {

	public static void main(String[] args) {
		SpringApplication.run(KonohaHelpdeskApplication.class, args);
	}

}
