package io.vaullet.template.notification;

import java.util.Map;

/** Payload sent to the notification service. */
record NotificationRequest(String event, String recipient, Map<String, String> attributes) {}
