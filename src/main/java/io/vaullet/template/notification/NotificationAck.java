package io.vaullet.template.notification;

/** Acknowledgement returned by the notification service. */
record NotificationAck(String id, String status) {}
