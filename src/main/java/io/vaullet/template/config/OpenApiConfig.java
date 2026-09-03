package io.vaullet.template.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.1 description, generated from the controllers rather than hand-maintained.
 *
 * <p>springdoc reads the actual request mappings, Bean Validation constraints and Javadoc-free
 * record components, so the published contract cannot drift from the code the way a checked-in
 * {@code openapi.yaml} does. The document is served at {@code /v3/api-docs} and rendered at
 * {@code /swagger-ui.html}.
 *
 * <p>Declaring the bearer scheme here means "Authorize" in Swagger UI actually works, which is the
 * difference between docs people use and docs people skim.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    OpenAPI apiDefinition(
            ApplicationProperties properties, @Value("${spring.application.name}") String applicationName) {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API")
                        .version("1.1")
                        .description("Reference REST API for @vaullet-io services. Environment: "
                                + properties.environment())
                        .contact(new Contact().name("Platform team").email("platform@vaullet.io"))
                        .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("OAuth2 access token issued by the platform identity provider.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
