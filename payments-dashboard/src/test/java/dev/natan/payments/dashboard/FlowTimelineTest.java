package dev.natan.payments.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.natan.payments.contract.PaymentSettled;
import dev.natan.payments.dashboard.FlowEvent.Type;
import tools.jackson.databind.json.JsonMapper;

class FlowTimelineTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    private final FlowTimeline timeline =
            new FlowTimeline(Clock.fixed(NOW, ZoneOffset.UTC), JsonMapper.builder().build());

    @Test
    void settlementReportsTheLatencySinceAcceptance() {
        UUID paymentId = UUID.randomUUID();
        timeline.accepted(paymentId);

        timeline.settled(settledAt(paymentId, NOW.plusMillis(250)));

        assertThat(timeline.recent()).extracting(FlowEvent::type).containsExactly(Type.ACCEPTED, Type.SETTLED);
        assertThat(timeline.recent().getLast().detail()).isEqualTo("settled 250 ms after acceptance");
    }

    @Test
    void duplicateSettledOnceIsReportedIgnoredOnlyWhenTheQueueDrains() {
        UUID paymentId = UUID.randomUUID();
        timeline.duplicateSent(paymentId);
        timeline.settled(settledAt(paymentId, NOW));

        assertThat(types()).doesNotContain(Type.DUPLICATE_IGNORED);

        timeline.queueDrained();
        timeline.queueDrained();

        assertThat(types()).containsExactly(Type.DUPLICATE_SENT, Type.SETTLED, Type.DUPLICATE_IGNORED);
    }

    @Test
    void duplicateThatSettledTwiceIsNotReportedIgnored() {
        UUID paymentId = UUID.randomUUID();
        timeline.duplicateSent(paymentId);
        timeline.settled(settledAt(paymentId, NOW));
        timeline.settled(settledAt(paymentId, NOW));

        timeline.queueDrained();

        assertThat(types()).doesNotContain(Type.DUPLICATE_IGNORED);
    }

    @Test
    void deadLetterKeepsThePaymentIdWhenTheBodyHasOne() {
        UUID paymentId = UUID.randomUUID();

        timeline.deadLettered(new DeadLetter("m-1", "{\"paymentId\": \"" + paymentId + "\"}"));
        timeline.deadLettered(new DeadLetter("m-2", "this is not a payment"));

        assertThat(timeline.recent()).extracting(FlowEvent::paymentId).containsExactly(paymentId, null);
    }

    @Test
    void subscribersSeeEveryEventInOrder() {
        List<Long> seen = new ArrayList<>();
        timeline.subscribe(event -> seen.add(event.sequence()));

        timeline.poisonSent();
        timeline.consumerStopped("lambda");

        assertThat(seen).containsExactly(1L, 2L);
    }

    private List<Type> types() {
        return timeline.recent().stream().map(FlowEvent::type).toList();
    }

    private static PaymentSettled settledAt(UUID paymentId, Instant settledAt) {
        return new PaymentSettled(paymentId, "acc-1", new BigDecimal("10.00"), settledAt);
    }
}
