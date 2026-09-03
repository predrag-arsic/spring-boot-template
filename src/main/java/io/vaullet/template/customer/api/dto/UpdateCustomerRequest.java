package io.vaullet.template.customer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.vaullet.template.customer.CustomerStatus;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Request body for a partial update ({@code PATCH}).
 *
 * <p>Every component is nullable and null means "leave unchanged". That is the honest encoding of
 * PATCH semantics; reusing the create request here would force clients to resend fields they are
 * not changing and would turn every omission into a silent overwrite.
 */
public record UpdateCustomerRequest(
        @Nullable @Size(min = 2, max = 120) String displayName,
        @Nullable @Schema(example = "ACTIVE") CustomerStatus status) {}
