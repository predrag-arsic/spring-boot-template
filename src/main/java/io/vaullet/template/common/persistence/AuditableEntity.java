package io.vaullet.template.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Audit columns and the optimistic-locking version, shared by every persistent entity.
 *
 * <p>Two things are doing real work here:
 *
 * <ul>
 *   <li><b>{@code @Version}.</b> Optimistic locking turns a lost update into an
 *       {@code OptimisticLockingFailureException} (surfaced as HTTP 409) instead of silently
 *       overwriting a concurrent change. It is the cheapest correctness win in any CRUD service.
 *   <li><b>A boxed {@code Long} version.</b> Spring Data JPA uses "version is null" to decide an
 *       entity is new. That lets the application assign UUID primary keys up front while
 *       {@code save()} still issues an {@code INSERT} rather than a {@code SELECT}-then-{@code
 *       merge}. Declaring it as a primitive {@code long} would quietly re-introduce that extra
 *       select on every insert.
 * </ul>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @Version
    @Column(nullable = false)
    private @Nullable Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private @Nullable String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private @Nullable String updatedBy;

    public @Nullable Long getVersion() {
        return version;
    }

    public @Nullable Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getUpdatedAt() {
        return updatedAt;
    }

    public @Nullable String getCreatedBy() {
        return createdBy;
    }

    public @Nullable String getUpdatedBy() {
        return updatedBy;
    }
}
