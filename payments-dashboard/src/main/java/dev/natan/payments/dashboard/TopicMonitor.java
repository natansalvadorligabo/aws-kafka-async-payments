package dev.natan.payments.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Samples the Kafka payments topic twice a second: partition ends for the lanes, new records for the timeline. */
class TopicMonitor {

    private static final Logger log = LoggerFactory.getLogger(TopicMonitor.class);

    private final PaymentsTopic paymentsTopic;
    private final FlowTimeline timeline;
    private final FlowStream stream;

    TopicMonitor(PaymentsTopic paymentsTopic, FlowTimeline timeline, FlowStream stream) {
        this.paymentsTopic = paymentsTopic;
        this.timeline = timeline;
        this.stream = stream;
    }

    @Scheduled(fixedDelay = 500)
    void sample() {
        try {
            // Partitions first: the page draws a record into its partition's lane, which must already exist.
            stream.publish(paymentsTopic.partitions());
            for (TopicRecord record : paymentsTopic.pollNewRecords()) {
                timeline.appended(record);
                stream.publish(record);
            }
        } catch (RuntimeException e) {
            log.atWarn().setCause(e).log("payments topic not readable yet");
        }
    }
}
