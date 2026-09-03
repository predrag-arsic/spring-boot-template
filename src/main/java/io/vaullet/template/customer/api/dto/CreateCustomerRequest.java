package io.vaullet.template.customer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a customer.
 *
 * <p>A dedicated input type — rather than the entity or the response DTO — is what prevents
 * mass-assignment: there is simply no {@code id}, {@code status} or {@code createdAt} field for a
 * caller to set. Constraints live on the record components so the rejection happens before any
 * business code runs, and springdoc turns them into the published schema automatically.
 */
public record CreateCustomerRequest(
        @NotBlank @Email @Size(max = 320) @Schema(example = "ada@example.com") String email,
        @NotBlank @Size(min = 2, max = 120) @Schema(example = "Ada Lovelace") String displayName) {}
