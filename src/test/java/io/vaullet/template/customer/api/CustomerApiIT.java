package io.vaullet.template.customer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import io.vaullet.template.customer.dao.CustomerRepository;
import io.vaullet.template.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * End-to-end through every layer: HTTP in, PostgreSQL out.
 *
 * <p>These are the tests that catch what unit and slice tests structurally cannot — a wrong column
 * name, a transaction boundary in the wrong place, a security rule that only fails once the real
 * chain is assembled, a Jackson configuration that serialises the wrong shape.
 *
 * <p>They are also the expensive ones, so the suite stays small and covers journeys rather than
 * permutations: create, read back, reject a duplicate, enforce authorisation. The naming convention
 * ({@code *IT}) binds them to Failsafe and {@code mvn verify}, keeping {@code mvn test} fast enough
 * to run constantly.
 */
@IntegrationTest
class CustomerApiIT {

    private static final String WRITE_SCOPE = "SCOPE_customers:write";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private CustomerRepository repository;

    @BeforeEach
    void resetState() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("creates a customer and reads it back through the API")
    void createsAndReadsBack() {
        var created = assertThat(mvc.post()
                        .uri("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@example.com\",\"displayName\":\"Ada Lovelace\"}")
                        .with(jwt().authorities(() -> WRITE_SCOPE)))
                .hasStatus(201);

        var id = created.bodyJson().extractingPath("$.id").asString().actual();

        assertThat(mvc.get().uri("/api/customers/{id}", id).with(jwt()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("PENDING");

        assertThat(repository.findByEmailIgnoreCase("ada@example.com")).isPresent();
    }

    @Test
    @DisplayName("rejects a duplicate email with 409 and a problem document")
    void rejectsDuplicateEmail() {
        createAda();

        var conflict = assertThat(mvc.post()
                        .uri("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ADA@example.com\",\"displayName\":\"Impostor\"}")
                        .with(jwt().authorities(() -> WRITE_SCOPE)))
                .hasStatus(409);

        conflict.bodyJson().extractingPath("$.type").isEqualTo("https://errors.vaullet.io/resource-conflict");
    }

    @Test
    @DisplayName("denies a write to a token that only carries read scope")
    void enforcesWriteScope() {
        assertThat(mvc.post()
                        .uri("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mallory@example.com\",\"displayName\":\"Mallory\"}")
                        .with(jwt().authorities(() -> "SCOPE_customers:read")))
                .hasStatus(403);

        assertThat(repository.findByEmailIgnoreCase("mallory@example.com")).isEmpty();
    }

    @Test
    @DisplayName("keeps the health endpoint open to unauthenticated probes")
    void exposesHealthWithoutAuthentication() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    @DisplayName("publishes an OpenAPI document that includes the customer endpoints")
    void publishesOpenApiDocument() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.paths['/api/customers']")
                .isNotNull();
    }

    private void createAda() {
        assertThat(mvc.post()
                        .uri("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@example.com\",\"displayName\":\"Ada Lovelace\"}")
                        .with(jwt().authorities(() -> WRITE_SCOPE)))
                .hasStatus(201);
    }
}
