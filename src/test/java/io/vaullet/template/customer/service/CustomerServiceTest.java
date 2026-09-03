package io.vaullet.template.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vaullet.template.common.error.ResourceConflictException;
import io.vaullet.template.common.error.ResourceNotFoundException;
import io.vaullet.template.customer.CustomerStatus;
import io.vaullet.template.customer.dao.CustomerEntity;
import io.vaullet.template.customer.dao.CustomerRepository;
import io.vaullet.template.notification.NotificationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the business rules — no Spring context, no database, no HTTP.
 *
 * <p>These run in milliseconds because {@code CustomerService} takes its collaborators through the
 * constructor: the test simply passes mocks. That is the practical payoff of constructor injection
 * that arguments about style tend to skip over.
 *
 * <p>What is asserted here is <em>behaviour</em> — the conflict rule, the not-found rule, the fact
 * that a notification is not sent when creation fails. Whether the row lands in PostgreSQL is the
 * job of {@code CustomerRepositoryIT}; whether the status code is 409 is the job of the controller
 * test. Each layer is tested where it is cheapest to test.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository repository;

    @Mock
    private NotificationService notifications;

    @InjectMocks
    private CustomerService service;

    @Test
    @DisplayName("normalises the email and persists a new customer as PENDING")
    void createsCustomer() {
        when(repository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(repository.save(any(CustomerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create("  Ada@Example.COM ", "  Ada Lovelace  ");

        assertThat(created.email()).isEqualTo("ada@example.com");
        assertThat(created.displayName()).isEqualTo("Ada Lovelace");
        assertThat(created.status()).isEqualTo(CustomerStatus.PENDING);

        var saved = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
    }

    @Test
    @DisplayName("rejects a duplicate email without touching the database or the downstream")
    void rejectsDuplicateEmail() {
        when(repository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create("ada@example.com", "Ada Lovelace"))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("already exists");

        verify(repository, never()).save(any());
        verify(notifications, never()).customerCreated(any(), anyString());
    }

    @Test
    @DisplayName("reports a missing customer as not found rather than returning null")
    void failsFastOnMissingCustomer() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    @DisplayName("leaves omitted fields untouched on a partial update")
    void appliesPartialUpdate() {
        var entity = CustomerEntity.newCustomer("ada@example.com", "Ada Lovelace");
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));

        var updated = service.update(entity.getId(), null, CustomerStatus.ACTIVE);

        assertThat(updated.displayName()).isEqualTo("Ada Lovelace");
        assertThat(updated.status()).isEqualTo(CustomerStatus.ACTIVE);
    }
}
