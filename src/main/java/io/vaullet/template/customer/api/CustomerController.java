package io.vaullet.template.customer.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.vaullet.template.common.web.PageResponse;
import io.vaullet.template.customer.api.dto.CreateCustomerRequest;
import io.vaullet.template.customer.api.dto.CustomerDetailResponse;
import io.vaullet.template.customer.api.dto.CustomerResponse;
import io.vaullet.template.customer.api.dto.UpdateCustomerRequest;
import io.vaullet.template.customer.CustomerStatus;
import io.vaullet.template.customer.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * HTTP entry point for customers.
 *
 * <p>The controller's whole job is translation: bind and validate the request, call one service
 * method, shape the response. There is no business logic, no transaction, no try/catch — errors
 * propagate to {@code ApiExceptionHandler}, which is the only place that knows about status codes.
 * A controller that stays this thin is why the same rules can later be exposed over GraphQL or a
 * message listener without a rewrite.
 *
 * <p>Conventions on display:
 *
 * <ul>
 *   <li><b>Plural, noun-based resource paths.</b> {@code /api/customers/{id}} — never
 *       {@code /api/getCustomer}. The verb is the HTTP method.
 *   <li><b>201 with a {@code Location} header</b> on create, 204 with no body on delete. Returning
 *       200-with-a-body for everything throws away information the protocol already carries.
 *   <li><b>{@code PATCH} for partial updates</b>, with a nullable-field request type, rather than
 *       {@code PUT} semantics that silently blank omitted fields.
 *   <li><b>Paged list endpoints, always.</b> An unbounded collection endpoint is an outage waiting
 *       for the table to grow.
 *   <li><b>Versioning via {@code @GetMapping(version = ...)}</b> — new in Spring Framework 7. The
 *       resolver (header, query parameter, path segment or media-type parameter) is chosen in
 *       {@code application.yaml} under {@code spring.mvc.apiversion.*}, so the controller declares
 *       only which version a handler serves. This replaces {@code /api/v1/...} path duplication and
 *       the custom filters teams used to write.
 * </ul>
 */
@Validated
@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers", description = "Create and manage customer records")
class CustomerController {

    private final CustomerService service;

    CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List customers", description = "Paged, optionally filtered by status or free-text search.")
    PageResponse<CustomerResponse> list(
            @RequestParam(required = false) @Nullable CustomerStatus status,
            @RequestParam(required = false) @Size(min = 2, max = 100) @Nullable String search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {

        return PageResponse.of(service.findAll(status, search, pageable), CustomerResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a customer")
    @ApiResponse(responseCode = "404", description = "No customer with that id")
    CustomerResponse getById(@PathVariable UUID id) {
        return CustomerResponse.from(service.findById(id));
    }

    /**
     * Same resource, richer representation, served to callers that ask for API version 1.1 or
     * later. Version 1.0 clients keep receiving {@link CustomerResponse} from the handler above
     * with no change on their side.
     */
    @GetMapping(path = "/{id}", version = "1.1+")
    @Operation(summary = "Fetch a customer (1.1+)", description = "Adds audit timestamps to the 1.0 representation.")
    CustomerDetailResponse getByIdV11(@PathVariable UUID id) {
        return CustomerDetailResponse.from(service.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create a customer")
    @ApiResponse(responseCode = "409", description = "Email already in use")
    ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CreateCustomerRequest request, UriComponentsBuilder uriBuilder) {

        var created = service.create(request.email(), request.displayName());
        URI location = uriBuilder.path("/api/customers/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(CustomerResponse.from(created));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a customer", description = "Omitted fields are left unchanged.")
    CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        return CustomerResponse.from(service.update(id, request.displayName(), request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a customer")
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
