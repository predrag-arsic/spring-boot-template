package io.vaullet.template.customer.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vaullet.template.config.PersistenceConfig;
import io.vaullet.template.customer.CustomerStatus;
import io.vaullet.template.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Persistence slice against a real PostgreSQL.
 *
 * <p>{@code @DataJpaTest} loads Hibernate, the repositories and Flyway — not the web layer — and
 * rolls each test back at the end, so tests cannot leak state into one another.
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} is the line people forget: without it the
 * slice swaps in an embedded database and the container is ignored, quietly turning a PostgreSQL
 * test into an H2 test.
 *
 * <p>{@code PersistenceConfig} has to be imported explicitly: a slice loads only the
 * auto-configuration it declares, so {@code @EnableJpaAuditing} — and therefore every
 * {@code created_at} value — is absent unless asked for. Importing the real configuration rather
 * than re-declaring auditing in the test is what makes the audit columns test meaningful.
 *
 * <p>The most valuable assertion in this class is the one about the unique index. The service does
 * check for a duplicate email first, but two concurrent requests can both pass that check — only
 * the database constraint actually prevents the duplicate, and only a test against the real engine
 * proves the constraint exists and is case-insensitive.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, PersistenceConfig.class})
class CustomerRepositoryIT {

    @Autowired
    private CustomerRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("applies the Flyway migrations and validates the Hibernate mapping against them")
    void schemaMatchesTheMapping() {
        // Reaching this point at all means ddl-auto=validate accepted the migrated schema and the
        // seed row from V2 is present — a mapping/migration mismatch fails the context, not an
        // assertion.
        assertThat(repository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("finds a customer by email regardless of case")
    void findsByEmailIgnoringCase() {
        repository.save(CustomerEntity.newCustomer("ada@example.com", "Ada Lovelace"));
        entityManager.flush();

        assertThat(repository.findByEmailIgnoreCase("ADA@EXAMPLE.COM")).isPresent();
    }

    @Test
    @DisplayName("lets the database reject a duplicate email that slipped past the service check")
    void enforcesUniqueEmailInTheDatabase() {
        repository.saveAndFlush(CustomerEntity.newCustomer("ada@example.com", "Ada Lovelace"));

        // saveAndFlush, not entityManager.flush: the call goes through the repository proxy, so
        // Spring's persistence exception translation converts Hibernate's ConstraintViolationException
        // into the DataIntegrityViolationException the rest of the application is written against.
        // Flushing the EntityManager directly bypasses that translation and leaks the provider's
        // exception type into the test.
        assertThatThrownBy(() -> repository.saveAndFlush(CustomerEntity.newCustomer("ADA@example.com", "Impostor")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("populates the audit columns and the optimistic-locking version on insert")
    void populatesAuditColumns() {
        var saved = repository.save(CustomerEntity.newCustomer("grace@example.com", "Grace Hopper"));
        entityManager.flush();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo(PersistenceConfig.SYSTEM_PRINCIPAL);
    }

    @Test
    @DisplayName("searches across email and display name")
    void searchesAcrossColumns() {
        repository.save(CustomerEntity.newCustomer("ada@example.com", "Ada Lovelace"));
        repository.save(CustomerEntity.newCustomer("grace@example.com", "Grace Hopper"));
        entityManager.flush();

        var byName = repository.search("lovelace", PageRequest.of(0, 10));
        var byEmail = repository.search("grace@", PageRequest.of(0, 10));

        assertThat(byName.getContent()).singleElement().returns("Ada Lovelace", CustomerEntity::getDisplayName);
        assertThat(byEmail.getContent()).singleElement().returns("Grace Hopper", CustomerEntity::getDisplayName);
    }

    @Test
    @DisplayName("filters by status")
    void filtersByStatus() {
        repository.save(CustomerEntity.newCustomer("pending@example.com", "Pending Person"));
        entityManager.flush();

        var pending = repository.findByStatus(CustomerStatus.PENDING, PageRequest.of(0, 10));

        assertThat(pending.getContent()).extracting(CustomerEntity::getEmail).contains("pending@example.com");
    }
}
