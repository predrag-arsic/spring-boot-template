package io.vaullet.template.notification;

import org.springframework.http.MediaType;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative client for the downstream notification service.
 *
 * <p>An interface is the whole implementation. Spring generates a proxy backed by {@code RestClient};
 * the base URL, timeouts and default headers come from {@code spring.http.serviceclient.notifications.*}
 * because the interface is registered in a group by
 * {@code io.vaullet.template.config.HttpClientConfig}.
 *
 * <p>Compared with what this replaces: no {@code RestTemplate} bean per integration, no manual URL
 * concatenation, no {@code spring-cloud-openfeign} dependency (which pins you to the Spring Cloud
 * release train), and the request/response types are checked by the compiler.
 */
interface NotificationApiClient {

    @PostExchange(url = "/notifications", contentType = MediaType.APPLICATION_JSON_VALUE)
    NotificationAck send(NotificationRequest request);
}
