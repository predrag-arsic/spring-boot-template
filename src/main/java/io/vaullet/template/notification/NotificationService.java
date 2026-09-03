package io.vaullet.template.notification;

import io.vaullet.template.config.ApplicationProperties;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Outbound integration boundary for customer notifications.
 *
 * <p>The retry and concurrency policy lives in {@link NotificationSender}; this class owns the
 * decision of what a failure <em>means</em>.
 *
 * <p>Note what it does not do: propagate. Notifying is not part of the customer-creation contract,
 * so an exhausted retry is logged and swallowed rather than turned into a 500 for a request whose
 * real work already committed. When the side effect genuinely is critical, the answer is not a
 * longer retry — it is the transactional outbox pattern (write the intent to a table in the same
 * transaction, deliver it from a separate poller).
 *
 * <p>The {@code enabled} flag lets every environment without a downstream — CI, a laptop, a review
 * app — run the real code path without pointing at a stub that has to be maintained.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationSender sender;
    private final ApplicationProperties properties;

    NotificationService(NotificationSender sender, ApplicationProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    public void customerCreated(UUID customerId, String email) {
        if (!properties.notifications().enabled()) {
            log.debug("Notifications disabled; skipping customer.created for {}", customerId);
            return;
        }
        try {
            var request =
                    new NotificationRequest("customer.created", email, Map.of("customerId", customerId.toString()));
            var ack = sender.send(request);
            log.debug("Notification {} accepted with status {}", ack.id(), ack.status());
        } catch (RestClientException ex) {
            log.warn("Notification for customer {} could not be delivered: {}", customerId, ex.getMessage());
        }
    }
}
