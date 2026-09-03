package io.vaullet.template.customer.service;

import io.vaullet.template.common.error.ResourceConflictException;
import io.vaullet.template.common.error.ResourceNotFoundException;
import io.vaullet.template.customer.dao.CustomerEntity;
import io.vaullet.template.customer.dao.CustomerRepository;
import io.vaullet.template.customer.CustomerStatus;
import io.vaullet.template.notification.NotificationService;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Business rules and transaction boundaries for customers.
 *
 * <p>This is the layer that owns behaviour. The controller translates HTTP, the repository
 * translates SQL; everything that a second caller (a job, a consumer, another service) would also
 * need lives here.
 *
 * <p>Practices demonstrated:
 *
 * <ul>
 *   <li><b>Constructor injection, final fields.</b> No {@code @Autowired} on fields: dependencies
 *       are explicit, the object cannot exist half-built, and the class is trivially unit-testable
 *       with {@code new}. Field injection also hides the moment a class has grown too many
 *       collaborators.
 *   <li><b>{@code @Transactional(readOnly = true)} at class level</b>, overridden on writers.
 *       Read-only transactions let Hibernate skip dirty-checking and let the driver route to a
 *       replica. This is one annotation for a measurable win.
 *   <li><b>The transaction ends at this class.</b> Never at the controller (you would hold a
 *       connection while serialising a response) and never at the repository (you could not
 *       compose two writes atomically).
 *   <li><b>Side effects after commit.</b> The notification fires from an
 *       {@code afterCommit} callback, so a rolled-back transaction cannot emit an event claiming a
 *       customer exists. Doing remote I/O inside the transaction also pins a database connection
 *       for the duration of a network call.
 *   <li><b>Authorisation next to the rule it protects.</b> {@code @PreAuthorize} on the method
 *       keeps the check in force no matter which entry point calls it.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository repository;
    private final NotificationService notifications;

    public CustomerService(CustomerRepository repository, NotificationService notifications) {
        this.repository = repository;
        this.notifications = notifications;
    }

    public Page<Customer> findAll(@Nullable CustomerStatus status, @Nullable String search, Pageable pageable) {
        Page<CustomerEntity> page;
        if (search != null && !search.isBlank()) {
            page = repository.search(search.strip(), pageable);
        } else if (status != null) {
            page = repository.findByStatus(status, pageable);
        } else {
            page = repository.findAll(pageable);
        }
        return page.map(CustomerMapper::toModel);
    }

    public Customer findById(UUID id) {
        return repository.findById(id).map(CustomerMapper::toModel).orElseThrow(() -> notFound(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_customers:write')")
    public Customer create(String email, String displayName) {
        var normalisedEmail = email.strip().toLowerCase();
        if (repository.existsByEmailIgnoreCase(normalisedEmail)) {
            throw new ResourceConflictException("A customer with email '%s' already exists".formatted(normalisedEmail));
        }

        var saved = repository.save(CustomerEntity.newCustomer(normalisedEmail, displayName.strip()));
        log.info("Created customer {}", saved.getId());

        // Registered here, executed only if the surrounding transaction commits.
        afterCommit(() -> notifications.customerCreated(saved.getId(), saved.getEmail()));
        return CustomerMapper.toModel(saved);
    }

    /**
     * Partial update. Absent ({@code null}) arguments leave the corresponding field untouched.
     *
     * <p>No explicit {@code save()} call: inside a read-write transaction the entity is managed, so
     * Hibernate's dirty checking flushes the change at commit. Calling {@code save()} anyway is
     * harmless but signals a misunderstanding of the persistence context.
     */
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_customers:write')")
    public Customer update(UUID id, @Nullable String displayName, @Nullable CustomerStatus status) {
        var entity = repository.findById(id).orElseThrow(() -> notFound(id));

        if (displayName != null) {
            entity.rename(displayName.strip());
        }
        if (status != null) {
            entity.changeStatus(status);
        }
        return CustomerMapper.toModel(entity);
    }

    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_customers:write')")
    public void delete(UUID id) {
        var entity = repository.findById(id).orElseThrow(() -> notFound(id));
        repository.delete(entity);
        log.info("Deleted customer {}", id);
    }

    private static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Customer", id);
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
