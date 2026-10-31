package dev.preethi.pulse.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.info.InfoContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.Show;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthEndpointGroup;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActuatorSecurityTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withInitializer(new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(
                    EndpointAutoConfiguration.class,
                    WebEndpointAutoConfiguration.class,
                    HealthEndpointAutoConfiguration.class,
                    HealthContributorAutoConfiguration.class,
                    InfoEndpointAutoConfiguration.class,
                    InfoContributorAutoConfiguration.class
            ));

    @Test
    void healthEndpoint_configuredWithShowDetailsNever_hidesAllComponentsAndDetails() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("healthEndpoint"));
            HealthEndpoint healthEndpoint = context.getBean(HealthEndpoint.class);
            HealthEndpointGroups groups = context.getBean(HealthEndpointGroups.class);
            HealthEndpointGroup primaryGroup = groups.getPrimary();

            assertNotNull(primaryGroup);
            // Verify that for anonymous access (SecurityContext.NONE), details and components are never shown
            assertFalse(primaryGroup.showDetails(SecurityContext.NONE));
            assertFalse(primaryGroup.showComponents(SecurityContext.NONE));

            HealthEndpointProperties properties = context.getBean(HealthEndpointProperties.class);
            assertEquals(Show.NEVER, properties.getShowDetails());

            // Health check still functions for probes returning UP
            HealthComponent health = healthEndpoint.health();
            assertNotNull(health);
            assertEquals(Status.UP, health.getStatus());
        });
    }

    @Test
    void infoEndpoint_doesNotExposeEnvironmentOrInternalDetails() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("infoEndpoint"));
            InfoEndpoint infoEndpoint = context.getBean(InfoEndpoint.class);
            Map<String, Object> info = infoEndpoint.info();

            assertNotNull(info);
            assertFalse(info.containsKey("env"), "Info endpoint must not expose env");
            assertFalse(info.containsKey("config"), "Info endpoint must not expose config");
            assertFalse(info.containsKey("git"), "Info endpoint must not expose git without config");
        });
    }
}
