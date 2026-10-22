package dev.natan.payments.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.natan.payments.contract.PaymentSettled;
import dev.natan.payments.dashboard.FlowEvent.Type;
import tools.jackson.databind.json.JsonMapper;

class FlowMonitorTest {

    private final StubStackReader stackReader = new StubStackReader();
    private final FlowTimeline timeline = new FlowTimeline(Clock.systemUTC(), new MessageBodies(JsonMapper.builder().build()));
    private final FlowMonitor monitor =
            new FlowMonitor(stackReader, new FakeConsumerSwitch(), timeline, new FlowStream(timeline), "sqs");

    @Test
    void turnsSettlementsAndDeadLettersIntoEvents() {
        UUID paymentId = UUID.randomUUID();
        stackReader.willReport(new PaymentSettled(paymentId, "acc-1", BigDecimal.ONE, Instant.now()));
        stackReader.willReport(new DeadLetter("m-1", "not a payment"));

        monitor.sample();

        assertThat(timeline.recent()).extracting(FlowEvent::type).containsExactly(Type.SETTLED, Type.DEAD_LETTERED);
    }

    @Test
    void infersIgnoredDuplicatesOnlyOnceTheQueueIsEmpty() {
        UUID paymentId = UUID.randomUUID();
        timeline.duplicateSent(paymentId);
        stackReader.willReport(new PaymentSettled(paymentId, "acc-1", BigDecimal.ONE, Instant.now()));
        stackReader.willReport(new StackCounts(0, 1, 0, 1, 0));

        monitor.sample();
        assertThat(timeline.recent()).extracting(FlowEvent::type).doesNotContain(Type.DUPLICATE_IGNORED);

        stackReader.willReport(new StackCounts(0, 0, 0, 1, 0));
        monitor.sample();
        assertThat(timeline.recent()).extracting(FlowEvent::type).contains(Type.DUPLICATE_IGNORED);
    }
}
