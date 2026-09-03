package io.vaullet.template.notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Registers the declarative HTTP client for this integration.
 *
 * <p>Spring Framework 7 / Boot 4 make {@code @HttpExchange} interfaces first-class: annotate the
 * interface, list it in a <em>group</em> here, and configure that group's base URL, timeouts and
 * default headers in {@code application.yaml} under
 * {@code spring.http.serviceclient.notifications.*}. That replaces OpenFeign, hand-rolled
 * {@code RestTemplate} wrappers and a builder bean per integration.
 *
 * <p>Note the direction of control: configuration belongs to the <em>consumer</em>, not to the
 * interface. The short-lived {@code @HttpServiceClient} annotation from the Boot 4 milestones put
 * the base URL on the interface itself and was dropped before GA precisely because a service that
 * both exposed and consumed an interface would try to proxy itself at startup.
 *
 * <p>This lives beside the client rather than in the shared {@code config} package so that
 * {@link NotificationApiClient} can stay package-private — nothing outside this package should be
 * able to call the downstream directly, bypassing {@link NotificationService}'s policy. Adding a
 * second integration means a new package with its own two-line configuration, not an edit to a
 * central file every team touches.
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "notifications", types = NotificationApiClient.class)
class NotificationClientConfig {}
