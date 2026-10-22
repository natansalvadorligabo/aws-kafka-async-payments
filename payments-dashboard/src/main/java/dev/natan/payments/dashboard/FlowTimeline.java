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
    private final MessageBodies messageBodies;
    private final Deque<FlowEvent> recent = new ArrayDeque<>();
    private final List<Consumer<FlowEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final Map<UUID, Instant> acceptedAt = new HashMap<>();
    private final Map<UUID, Integer> duplicateSettlements = new HashMap<>();
    private long sequence;

    public FlowTimeline(Clock clock, MessageBodies messageBodies) {
        this.clock = clock;
        this.messageBodies = messageBodies;
    }

    public void subscribe(Consumer<FlowEvent> subscriber) {
        subscribers.add(subscriber);
    }

    public synchronized List<FlowEvent> recent() {
        return List.copyOf(recent);
    }

    public synchronized void accepted(UUID paymentId) {
        acceptedAt.put(paymentId, clock.instant());
        record(Type.ACCEPTED, paymentId, "API answered 202; the payment is queued");
    }

    /** Uses the time Kafka appended the record, so records already on the topic replay as history, not as news. */
    public synchronized void appended(TopicRecord record) {
        record(Type.APPENDED_TO_PARTITION, record.paymentId(), record.appendedAt(),
                "key " + record.key() + " → partition " + record.partition() + ", offset " + record.offset());
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
        record(Type.DEAD_LETTERED, messageBodies.paymentIdIn(deadLetter.body()), "moved to the DLQ: " + abbreviate(deadLetter.body()));
    }

    public synchronized void consumerStopped(String consumer) {
        record(Type.CONSUMER_STOPPED, null, consumer + " consumer is down; new payments wait on SQS");
    }

    public synchronized void consumerStarted(String consumer) {
        record(Type.CONSUMER_STARTED, null, consumer + " consumer is up; it drains the queue");
    }

    private void record(Type type, UUID paymentId, String detail) {
        record(type, paymentId, clock.instant(), detail);
    }

    private void record(Type type, UUID paymentId, Instant at, String detail) {
        FlowEvent event = new FlowEvent(++sequence, at, type, paymentId, detail);
        recent.addLast(event);
        if (recent.size() > CAPACITY) {
            recent.removeFirst();
        }
        subscribers.forEach(subscriber -> subscriber.accept(event));
    }

    private static String abbreviate(String body) {
        return body.length() <= 60 ? body : body.substring(0, 57) + "...";
    }
}
