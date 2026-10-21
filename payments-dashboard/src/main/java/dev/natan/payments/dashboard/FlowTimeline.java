package dev.natan.payments.dashboard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import dev.natan.payments.contract.PaymentSettled;
import dev.natan.payments.dashboard.FlowEvent.Type;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The recent history of payments through the stack, told as {@link FlowEvent}s to whoever subscribes.
 *
 * <pre>{@code
 * timeline.subscribe(event -> System.out.println(event));
 * timeline.accepted(paymentId);
 * }</pre>
 */
public class FlowTimeline {

    private static final int CAPACITY = 300;

    private final Clock clock;
    private final JsonMapper json;
    private final Deque<FlowEvent> recent = new ArrayDeque<>();
    private final List<Consumer<FlowEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final Map<UUID, Instant> acceptedAt = new HashMap<>();
    private final Map<UUID, Integer> duplicateSettlements = new HashMap<>();
    private long sequence;

    public FlowTimeline(Clock clock, JsonMapper json) {
        this.clock = clock;
        this.json = json;
    }

    public void subscribe(Consumer<FlowEvent> subscriber) {
        subscribers.add(subscriber);
    }

    public synchronized List<FlowEvent> recent() {
        return List.copyOf(recent);
    }

    public synchronized void accepted(UUID paymentId) {
        acceptedAt.put(paymentId, clock.instant());
        record(Type.ACCEPTED, paymentId, "API answered 202; the payment is on SQS");
    }

    public synchronized void duplicateSent(UUID paymentId) {
        acceptedAt.put(paymentId, clock.instant());
        duplicateSettlements.put(paymentId, 0);
        record(Type.DUPLICATE_SENT, paymentId, "the same message put on SQS twice, as an at-least-once redelivery would");
    }

    public synchronized void poisonSent() {
        record(Type.POISON_SENT, null, "a message no consumer can parse");
    }

    public synchronized void settled(PaymentSettled settled) {
        duplicateSettlements.computeIfPresent(settled.paymentId(), (id, count) -> count + 1);
        Instant accepted = acceptedAt.remove(settled.paymentId());
        String detail = accepted == null
                ? "settled"
                : "settled " + Duration.between(accepted, settled.settledAt()).toMillis() + " ms after acceptance";
        record(Type.SETTLED, settled.paymentId(), detail);
    }

    /**
     * Called when the payments queue is empty. Every sent duplicate that settled exactly once had its second copy
     * deleted without a settlement: the consumer recognised it. SQS shows no single delivery, so this is inferred.
     */
    public synchronized void queueDrained() {
        duplicateSettlements.entrySet().removeIf(entry -> {
            if (entry.getValue() != 1) {
                return false;
            }
            record(Type.DUPLICATE_IGNORED, entry.getKey(),
                    "inferred: 2 copies delivered, 1 settlement, queue empty");
            return true;
        });
    }

    public synchronized void deadLettered(DeadLetter deadLetter) {
        record(Type.DEAD_LETTERED, paymentIdIn(deadLetter.body()), "moved to the DLQ: " + abbreviate(deadLetter.body()));
    }

    public synchronized void consumerStopped(String consumer) {
        record(Type.CONSUMER_STOPPED, null, consumer + " consumer is down; new payments wait on SQS");
    }

    public synchronized void consumerStarted(String consumer) {
        record(Type.CONSUMER_STARTED, null, consumer + " consumer is up; it drains the queue");
    }

    private void record(Type type, UUID paymentId, String detail) {
        FlowEvent event = new FlowEvent(++sequence, clock.instant(), type, paymentId, detail);
        recent.addLast(event);
        if (recent.size() > CAPACITY) {
            recent.removeFirst();
        }
        subscribers.forEach(subscriber -> subscriber.accept(event));
    }

    private UUID paymentIdIn(String body) {
        try {
            JsonNode paymentId = json.readTree(body).path("paymentId");
            return paymentId.isString() ? UUID.fromString(paymentId.asString()) : null;
        } catch (JacksonException | IllegalArgumentException unreadable) {
            return null;
        }
    }

    private static String abbreviate(String body) {
        return body.length() <= 60 ? body : body.substring(0, 57) + "...";
    }
}
