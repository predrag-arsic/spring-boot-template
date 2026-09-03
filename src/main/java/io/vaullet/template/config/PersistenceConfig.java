package io.vaullet.template.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Enables JPA auditing so {@code created_at} / {@code created_by} are filled in by the framework
 * rather than by whoever remembers to.
 *
 * <p>The auditor is derived from the security context, so the columns record the authenticated
 * principal from the JWT — not an application-supplied value a caller could spoof.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
public class PersistenceConfig {

    /** Written into {@code created_by} / {@code updated_by} for jobs and migrations. */
    public static final String SYSTEM_PRINCIPAL = "system";

    @Bean
    AuditorAware<String> securityAuditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(authentication -> authentication.isAuthenticated())
                .map(authentication -> authentication.getName())
                .or(() -> Optional.of(SYSTEM_PRINCIPAL));
    }
}
