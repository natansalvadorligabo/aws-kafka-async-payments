package dev.natan.payments.dashboard;

import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads what the dashboard needs from a raw message body, tolerating bodies that are not payments at all.
 *
 * <pre>{@code
 * UUID paymentId = messageBodies.paymentIdIn("{\"paymentId\": \"…\"}");
 * }</pre>
 */
public class MessageBodies {

    private final JsonMapper json;

    public MessageBodies(JsonMapper json) {
        this.json = json;
    }

    /** The body's paymentId, or null when the body is not JSON or carries no valid paymentId (a poison message). */
    public UUID paymentIdIn(String body) {
        try {
            JsonNode paymentId = json.readTree(body).path("paymentId");
            return paymentId.isString() ? UUID.fromString(paymentId.asString()) : null;
        } catch (JacksonException | IllegalArgumentException unreadable) {
            return null;
        }
    }
}
