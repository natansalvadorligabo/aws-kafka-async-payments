package dev.natan.payments.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.natan.payments.dashboard.FlowEvent.Type;
import tools.jackson.databind.json.JsonMapper;

class TopicMonitorTest {

    private final StubPaymentsTopic topic = new StubPaymentsTopic();
    private final FlowTimeline timeline =
            new FlowTimeline(Clock.systemUTC(), new MessageBodies(JsonMapper.builder().build()));
    private final TopicMonitor monitor = new TopicMonitor(topic, timeline, new FlowStream(timeline));

    @Test
    void eachNewRecordBecomesOneTimelineEventStampedWithItsAppendTime() {
        Instant appendedAt = Instant.parse("2026-10-22T10:00:00Z");
        UUID paymentId = UUID.randomUUID();
        topic.willReport(new TopicRecord(3, 7, "acc-1", paymentId, appendedAt));

        monitor.sample();
        monitor.sample();

        assertThat(timeline.recent()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(Type.APPENDED_TO_PARTITION);
            assertThat(event.paymentId()).isEqualTo(paymentId);
            assertThat(event.at()).isEqualTo(appendedAt);
            assertThat(event.detail()).isEqualTo("key acc-1 → partition 3, offset 7");
        });
    }
}
