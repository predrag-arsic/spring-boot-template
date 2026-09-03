package io.vaullet.template.customer.dao;

import io.vaullet.template.common.persistence.AuditableEntity;
import io.vaullet.template.customer.CustomerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * JPA mapping for the {@code customers} table.
 *
 * <p>This type is an implementation detail of the DAO layer: it never crosses the service boundary
 * and never appears in an HTTP response. Serialising entities directly is what produces lazy-loading
 * exceptions in the message converter, accidental exposure of internal columns, and an API contract
 * that changes whenever someone renames a field.
 *
 * <p>Mapping choices, and why:
 *
 * <ul>
 *   <li><b>Application-assigned {@link UUID} id.</b> The id exists before the row does, so the
 *       service can log it, publish it and build a {@code Location} header without a database
 *       round-trip. Combined with the nullable {@code @Version} in {@link AuditableEntity},
 *       {@code save()} still performs a plain insert.
 *   <li><b>{@code @Enumerated(STRING)}.</b> The default is {@code ORDINAL}, which stores an array
 *       index — reordering the enum then silently rewrites the meaning of every existing row.
 *   <li><b>No {@code @Data}/Lombok, no public setters for identity.</b> The entity exposes intent
 *       ({@code rename}, {@code changeStatus}) instead of anaemic setters.
 *   <li><b>{@code equals}/{@code hashCode} on the assigned id only.</b> Hibernate requires a stable
 *       hash code across the entity lifecycle; deriving it from mutable fields breaks
 *       {@code Set} membership the moment an entity is modified.
 * </ul>
 */
@Entity
@Table(name = "customers")
public class CustomerEntity extends AuditableEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    /**
     * Required by JPA — Hibernate instantiates through this constructor and then populates the
     * fields. Kept {@code protected} so application code cannot build a half-initialised entity.
     */
    protected CustomerEntity() {}

    public CustomerEntity(UUID id, String email, String displayName, CustomerStatus status) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.status = status;
    }

    public static CustomerEntity newCustomer(String email, String displayName) {
        return new CustomerEntity(UUID.randomUUID(), email, displayName, CustomerStatus.PENDING);
    }

    public void rename(String newDisplayName) {
        this.displayName = newDisplayName;
    }

    public void changeEmail(String newEmail) {
        this.email = newEmail;
    }

    public void changeStatus(CustomerStatus newStatus) {
        this.status = newStatus;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof CustomerEntity entity && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CustomerEntity[id=%s, status=%s]".formatted(id, status);
    }
}
