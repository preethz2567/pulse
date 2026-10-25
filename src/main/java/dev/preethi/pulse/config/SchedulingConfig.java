package dev.preethi.pulse.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "pulse.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
