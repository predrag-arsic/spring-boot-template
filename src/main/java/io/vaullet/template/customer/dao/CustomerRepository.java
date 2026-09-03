package io.vaullet.template.customer.dao;

import io.vaullet.template.customer.CustomerStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The DAO for customers.
 *
 * <p>Spring Data generates the implementation from the method names, so this <em>is</em> the data
 * access layer — a hand-written {@code CustomerDaoImpl} wrapping an {@code EntityManager} adds a
 * layer of indirection over code that no longer needs to be written. The interface still gives you
 * the seam you would have wanted from a DAO: the service depends on this abstraction and can be
 * unit-tested against a mock of it.
 *
 * <p>Guidance for growing this file:
 *
 * <ul>
 *   <li>Derived queries ({@code findByEmailIgnoreCase}) for anything expressible in a short name.
 *   <li>{@code @Query} once the derived name would exceed roughly four conditions — a method called
 *       {@code findByStatusAndCreatedAtBeforeAndDisplayNameContainingIgnoreCase} is write-only code.
 *   <li>{@code Specification} or a jOOQ/QueryDSL layer for genuinely dynamic filtering.
 *   <li>Projections (interface or record) when you need three columns out of a wide table; loading
 *       whole entities to read one field is the most common source of N+1 and heap pressure.
 * </ul>
 */
@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {

    Optional<CustomerEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Page<CustomerEntity> findByStatus(CustomerStatus status, Pageable pageable);

    /**
     * Case-insensitive search across the two human-readable columns.
     *
     * <p>Written as JPQL because the derived-name equivalent would be unreadable, and named
     * parameters keep it obvious which value goes where.
     */
    @Query(
            """
            select c from CustomerEntity c
            where lower(c.email) like lower(concat('%', :term, '%'))
               or lower(c.displayName) like lower(concat('%', :term, '%'))
            """)
    Page<CustomerEntity> search(@Param("term") String term, Pageable pageable);

    /**
     * Bulk state transition, e.g. from a scheduled job.
     *
     * <p>{@code @Modifying} bypasses the persistence context, which is exactly why it is fast and
     * exactly why {@code clearAutomatically} matters: without it, entities already loaded in the
     * current transaction would keep their stale status.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CustomerEntity c set c.status = :status where c.status = :currentStatus and c.updatedAt < :before")
    int bulkChangeStatus(
            @Param("currentStatus") CustomerStatus currentStatus,
            @Param("status") CustomerStatus status,
            @Param("before") Instant before);
}
