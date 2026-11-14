package fi.agentgate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled jobs (the approval expiry job). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
