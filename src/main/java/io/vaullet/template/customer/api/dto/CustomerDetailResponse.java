package io.vaullet.template.customer.api.dto;

import io.vaullet.template.customer.CustomerStatus;
import io.vaullet.template.customer.service.Customer;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Version 1.1 of the customer representation: adds audit timestamps.
 *
 * <p>A second record rather than an optional field on the first one. Additive changes to a shared
 * DTO look cheap and then quietly become a contract every existing client must tolerate; a new
 * version keeps the old shape frozen and lets it be retired on a schedule.
 */
public record CustomerDetailResponse(
        UUID id,
        String email,
        String displayName,
        CustomerStatus status,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt) {

    public static CustomerDetailResponse from(Customer customer) {
        return new CustomerDetailResponse(
                customer.id(),
                customer.email(),
                customer.displayName(),
                customer.status(),
                customer.createdAt(),
                customer.updatedAt());
    }
}
