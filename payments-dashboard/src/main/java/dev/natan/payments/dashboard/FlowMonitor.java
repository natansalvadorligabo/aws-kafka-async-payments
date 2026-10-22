package dev.natan.payments.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Samples the stack twice a second and turns what changed into state updates and flow events. */
class FlowMonitor {

    private static final Logger log = LoggerFactory.getLogger(FlowMonitor.class);

    private final StackReader stackReader;
    private final ConsumerSwitch consumerSwitch;
    private final FlowTimeline timeline;
    private final FlowStream stream;
    private final String broker;

    FlowMonitor(StackReader stackReader, ConsumerSwitch consumerSwitch, FlowTimeline timeline, FlowStream stream,
            String broker) {
        this.stackReader = stackReader;
        this.broker = broker;
        this.consumerSwitch = consumerSwitch;
        this.timeline = timeline;
        this.stream = stream;
    }

    @Scheduled(fixedDelay = 500)
    void sample() {
        try {
            stackReader.drainSettled().forEach(timeline::settled);
            stackReader.pollNewDeadLetters().forEach(timeline::deadLettered);
            StackCounts counts = stackReader.counts();
            if (counts.waiting() == 0 && counts.inFlight() == 0) {
                timeline.queueDrained();
            }
            stream.publish(new StackState(counts.waiting(), counts.inFlight(), counts.deadLetters(),
                    counts.settled(), counts.pendingClaims(), broker, consumerSwitch.consumer(), consumerSwitch.isOn()));
        } catch (RuntimeException e) {
            log.atWarn().setCause(e).log("stack not readable yet");
        }
    }
}
