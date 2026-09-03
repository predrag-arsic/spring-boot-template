package io.vaullet.template.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web-layer wiring that has no property equivalent.
 *
 * <p>Only CORS lives here. Notably <em>absent</em> is API-version configuration: Boot 4
 * auto-configures {@code ApiVersionStrategy} from {@code spring.mvc.apiversion.*}, so the strategy,
 * the supported versions and the deprecation handling are all data in {@code application.yaml}
 * rather than code. Prefer the properties; reach for {@code WebMvcConfigurer#configureApiVersioning}
 * only when you need a custom resolver or parser.
 *
 * <p>Implementing {@link WebMvcConfigurer} <em>adds</em> to Boot's auto-configuration. Putting
 * {@code @EnableWebMvc} on it would replace that auto-configuration wholesale and quietly cost you
 * content negotiation, message converters and error handling — a classic Spring Boot own-goal.
 */
@Configuration(proxyBeanMethods = false)
// @ConfigurationPropertiesScan on the application class covers the running application, but a
// @WebMvcTest slice does not scan. Declaring the binding the class actually needs keeps the slice
// self-sufficient — a small habit that avoids "works in prod, fails in the slice test" wiring bugs.
@EnableConfigurationProperties(ApplicationProperties.class)
class WebMvcConfig implements WebMvcConfigurer {

    private final ApplicationProperties properties;

    WebMvcConfig(ApplicationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var origins = properties.api().allowedOrigins();
        if (origins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("*")
                .exposedHeaders("Location", "X-API-Version")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
