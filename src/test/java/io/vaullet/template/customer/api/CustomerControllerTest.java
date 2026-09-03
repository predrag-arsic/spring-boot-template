package io.vaullet.template.customer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import io.vaullet.template.common.error.ResourceNotFoundException;
import io.vaullet.template.config.MethodSecurityConfig;
import io.vaullet.template.config.SecurityConfig;
import io.vaullet.template.customer.CustomerStatus;
import io.vaullet.template.customer.service.Customer;
import io.vaullet.template.customer.service.CustomerService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Web-layer slice test: real routing, real validation, real security filters, real error handling —
 * with the service mocked out.
 *
 * <p>{@code @WebMvcTest} starts a context containing the MVC infrastructure and nothing else, so
 * these tests are fast enough to run on every save, and a failure points at the web layer rather
 * than at the database. That is the whole argument for slices over one big {@code @SpringBootTest}:
 * a suite made only of full-context tests is slow and, when it breaks, tells you nothing about
 * where.
 *
 * <p>{@code SecurityConfig} is imported rather than disabled. Testing a controller with
 * {@code addFilters = false} verifies an application that will never be deployed; the interesting
 * assertions here are the 401 and the 403.
 *
 * <p>{@code @MockitoBean} replaced {@code @MockBean}, which was deprecated in Boot 3.4 and removed
 * in Boot 4.
 */
@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class})
class CustomerControllerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CustomerService service;

    /** Required to assemble the production filter chain; never invoked — see jwt() below. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("rejects an unauthenticated request before it reaches the controller")
    void requiresAuthentication() {
        assertThat(mvc.get().uri("/api/customers/{id}", CUSTOMER_ID)).hasStatus(401);
    }

    @Test
    @DisplayName("returns the 1.0 representation when no version header is sent")
    void returnsCustomer() {
        when(service.findById(CUSTOMER_ID)).thenReturn(sampleCustomer());

        assertThat(mvc.get().uri("/api/customers/{id}", CUSTOMER_ID).with(jwt()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo("ada@example.com");
    }

    @Test
    @DisplayName("serves the richer representation to clients asking for API version 1.1")
    void returnsVersionedCustomer() {
        when(service.findById(CUSTOMER_ID)).thenReturn(sampleCustomer());

        assertThat(mvc.get()
                        .uri("/api/customers/{id}", CUSTOMER_ID)
                        .header("X-API-Version", "1.1")
                        .with(jwt()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.createdAt")
                .isNotNull();
    }

    @Test
    @DisplayName("answers a missing customer with an RFC 9457 problem document")
    void returnsProblemDetailForMissingCustomer() {
        when(service.findById(CUSTOMER_ID)).thenThrow(new ResourceNotFoundException("Customer", CUSTOMER_ID));

        var result = assertThat(mvc.get().uri("/api/customers/{id}", CUSTOMER_ID).with(jwt()))
                .hasStatus(404)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        result.bodyJson().extractingPath("$.type").isEqualTo("https://errors.vaullet.io/resource-not-found");
        result.bodyJson().extractingPath("$.title").isEqualTo("Resource not found");
    }

    @Test
    @DisplayName("rejects an invalid body with field-level errors, without calling the service")
    void validatesRequestBody() {
        var result = assertThat(mvc.post()
                        .uri("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"displayName\":\"A\"}")
                        .with(jwt().authorities(() -> "SCOPE_customers:write")))
                .hasStatus(400);

        result.bodyJson().extractingPath("$.errors[*].field").asArray().contains("email", "displayName");
    }

    @Test
    @DisplayName("returns 201 with a Location header pointing at the new resource")
    void createsCustomer() {
        when(service.create(eq("ada@example.com"), eq("Ada Lovelace"))).thenReturn(sampleCustomer());

        assertThat(mvc.post()
                        .uri("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@example.com\",\"displayName\":\"Ada Lovelace\"}")
                        .with(jwt().authorities(() -> "SCOPE_customers:write")))
                .hasStatus(201)
                .headers()
                .hasHeaderSatisfying(
                        "Location", values -> assertThat(values.getFirst()).endsWith("/api/customers/" + CUSTOMER_ID));
    }

    @Test
    @DisplayName("pages the list endpoint by default rather than returning everything")
    void listsCustomersWithDefaultPaging() {
        Page<Customer> page = new PageImpl<>(List.of(sampleCustomer()), Pageable.ofSize(20), 1);
        when(service.findAll(any(), any(), any())).thenReturn(page);

        assertThat(mvc.get().uri("/api/customers").with(jwt()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.size")
                .isEqualTo(20);
    }

    private static Customer sampleCustomer() {
        return new Customer(
                CUSTOMER_ID,
                "ada@example.com",
                "Ada Lovelace",
                CustomerStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));
    }
}
