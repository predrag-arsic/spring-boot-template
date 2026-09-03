package io.vaullet.template;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Application entry point.
 *
 * <p>Deliberately thin: it declares <em>capabilities</em> only, never beans. Everything else lives
 * in {@code config} or in a feature slice.
 *
 * <ul>
 *   <li>{@link ConfigurationPropertiesScan} — binds {@code @ConfigurationProperties} types without
 *       having to list each one in {@code @EnableConfigurationProperties}.
 *   <li>{@link EnableResilientMethods} — Spring Framework 7's built-in {@code @Retryable} /
 *       {@code @ConcurrencyLimit} support, which removes the need for Spring Retry or
 *       Resilience4j for the common cases.
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableResilientMethods
public class TemplateApplication {

    public static void main(String[] args) {
        SpringApplication.run(TemplateApplication.class, args);
    }
}
