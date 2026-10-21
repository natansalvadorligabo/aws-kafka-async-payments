package dev.natan.payments.dashboard;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The dashboard buttons: each one provokes a scenario on the real stack and records it on the timeline. */
@RestController
class ScenarioController {

    private static final int MAX_BATCH = 50;

    private final PaymentInjector paymentInjector;
    private final ConsumerSwitch consumerSwitch;
    private final FlowTimeline timeline;

    ScenarioController(PaymentInjector paymentInjector, ConsumerSwitch consumerSwitch, FlowTimeline timeline) {
        this.paymentInjector = paymentInjector;
        this.consumerSwitch = consumerSwitch;
        this.timeline = timeline;
    }

    @PostMapping("/api/payments")
    List<UUID> sendPayments(@RequestParam(defaultValue = "1") int count) {
        if (count < 1 || count > MAX_BATCH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "expected count between 1 and " + MAX_BATCH + ", got " + count);
        }
        return IntStream.range(0, count).mapToObj(i -> {
            UUID paymentId = paymentInjector.sendPayment();
            timeline.accepted(paymentId);
            return paymentId;
        }).toList();
    }

    @PostMapping("/api/duplicates")
    UUID sendDuplicate() {
        UUID paymentId = paymentInjector.sendDuplicate();
        timeline.duplicateSent(paymentId);
        return paymentId;
    }

    @PostMapping("/api/poison")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void sendPoison() {
        paymentInjector.sendPoison();
        timeline.poisonSent();
    }

    @PostMapping("/api/consumer/off")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void stopConsumer() {
        consumerSwitch.turnOff();
        timeline.consumerStopped(consumerSwitch.consumer());
    }

    @PostMapping("/api/consumer/on")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void startConsumer() {
        consumerSwitch.turnOn();
        timeline.consumerStarted(consumerSwitch.consumer());
    }
}
