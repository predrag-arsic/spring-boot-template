package io.vaullet.template.customer.api.dto;

import io.vaullet.template.customer.CustomerStatus;
import io.vaullet.template.customer.service.Customer;
import java.util.UUID;

/** Version 1.0 of the customer representation. */
public record CustomerResponse(UUID id, String email, String displayName, CustomerStatus status) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.id(), customer.email(), customer.displayName(), customer.status());
    }
}
