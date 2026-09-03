package io.vaullet.template.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated configuration for everything this service owns.
 *
 * <p>Why a record and not {@code @Value} injection scattered across beans:
 *
 * <ul>
 *   <li><b>Fail fast.</b> {@code @Validated} runs Bean Validation at binding time, so a bad value
 *       stops the context from starting instead of blowing up on the first request that hits it.
 *   <li><b>Immutable.</b> Records give constructor binding for free — no setters, no half-built
 *       configuration objects, safe to share across threads.
 *   <li><b>Discoverable.</b> {@code spring-boot-configuration-processor} (wired up in the POM)
 *       turns this file into IDE auto-completion and inline docs for {@code application.yaml}.
 * </ul>
 *
 * <p>Rule of thumb: one prefix per service ({@code app}), nested records per concern. Never bind
 * secrets here as plain defaults — take them from the environment.
 *
 * <p>Note the Javadoc style: record components are documented with {@code @param} on the record
 * itself, not with a comment above each component. A comment there is attached to nothing, which
 * {@code -Xlint} will tell you about.
 *
 * @param environment free-form environment label, surfaced on {@code /actuator/info} and in the
 *     OpenAPI document
 * @param api inbound API behaviour
 * @param notifications outbound notification integration
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record ApplicationProperties(
        @NotBlank String environment, @Valid @NotNull Api api, @Valid @NotNull Notifications notifications) {

    /**
     * Inbound API behaviour.
     *
     * @param maxPageSize upper bound on {@code ?size=}, so a client cannot ask for the whole table
     * @param allowedOrigins browser origins allowed to call this API; empty disables CORS entirely
     */
    public record Api(@Min(1) @Max(200) int maxPageSize, @NotNull List<String> allowedOrigins) {}

    /**
     * Outbound notification integration. See {@code io.vaullet.template.notification}.
     *
     * @param enabled kill switch, letting every environment without the downstream run the real
     *     code path
     * @param maxRetries extra attempts after the first failure before {@code @Retryable} gives up
     * @param retryDelay delay before the first retry; later delays grow with the backoff multiplier
     */
    public record Notifications(
            boolean enabled, @Min(0) @Max(10) int maxRetries, @NotNull Duration retryDelay) {}
}
