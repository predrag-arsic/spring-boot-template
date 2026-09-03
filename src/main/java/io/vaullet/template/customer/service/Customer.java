package io.vaullet.template.customer.service;

import io.vaullet.template.customer.CustomerStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The customer as the rest of the application talks about it: an immutable snapshot, free of JPA
 * and free of HTTP.
 *
 * <p>Having a model between the entity and the response DTO is what lets the API evolve
 * independently of the schema (see the versioned endpoints in the API layer) and lets the service
 * be called from a scheduler or a message listener without dragging a detached-entity problem
 * along. For a pure CRUD service with one consumer it is fair to collapse this into the response
 * DTO — just make that a decision rather than an accident.
 */
public record Customer(
        UUID id,
        String email,
        String displayName,
        CustomerStatus status,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt) {}
