package io.vaullet.template.notification;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * The retried, concurrency-limited call itself.
 *
 * <p>It is a separate bean from {@link NotificationService} for a reason that bites every Spring
 * codebase eventually: {@code @Retryable}, {@code @Transactional}, {@code @Cacheable} and friends
 * are implemented with proxies, and a call from one method of a bean to another method of the
 * <em>same</em> bean does not pass through the proxy. Annotate {@code send()} and call it from
 * {@code customerCreated()} in the same class and the retry policy silently never runs. Splitting
 * the responsibility across two beans is the fix that does not depend on anyone remembering the
 * rule.
 *
 * <p>Spring Framework 7 brings retry and concurrency limiting into the core framework, so the
 * common cases no longer need Spring Retry or Resilience4j:
 *
 * <ul>
 *   <li><b>Exponential backoff with jitter.</b> Jitter is the part teams forget: without it, every
 *       instance that failed at the same moment retries at the same moment, and the retry storm
 *       keeps the struggling downstream down.
 *   <li><b>Bounded by exception type.</b> Only transport-level failures are retried. Retrying a
 *       {@code 400} wastes time and can duplicate side effects — retries are for failures that
 *       might plausibly succeed next time.
 *   <li><b>{@code @ConcurrencyLimit}</b> caps in-flight calls, protecting the downstream from this
 *       service and this service's threads from a slow downstream.
 *   <li><b>Limits come from configuration</b> via the {@code *String} attributes, so they are
 *       tunable per environment without a rebuild.
 * </ul>
 */
@Component
class NotificationSender {

    private final NotificationApiClient client;

    NotificationSender(NotificationApiClient client) {
        this.client = client;
    }

    @Retryable(
            includes = RestClientException.class,
            maxRetriesString = "${app.notifications.max-retries}",
            delayString = "${app.notifications.retry-delay}",
            multiplier = 2.0,
            jitter = 250,
            maxDelay = 5_000)
    @ConcurrencyLimit(10)
    NotificationAck send(NotificationRequest request) {
        return client.send(request);
    }
}
