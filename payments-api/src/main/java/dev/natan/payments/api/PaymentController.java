package dev.natan.payments.api;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.natan.payments.contract.PaymentRequested;
import jakarta.validation.Valid;

@RestController
class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentQueue paymentQueue;
    private final Clock clock;

    PaymentController(PaymentQueue paymentQueue, Clock clock) {
        this.paymentQueue = paymentQueue;
        this.clock = clock;
    }

    /** Accepts the payment once it is durably queued; settlement happens asynchronously. */
    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.ACCEPTED)
    AcceptedPayment accept(@Valid @RequestBody PaymentRequest request) {
        PaymentRequested payment = request.toEvent(UUID.randomUUID(), clock.instant());
        paymentQueue.enqueue(payment);
        log.atInfo()
                .addKeyValue("paymentId", payment.paymentId())
                .addKeyValue("accountId", payment.accountId())
                .log("payment accepted");
        return AcceptedPayment.of(payment.paymentId());
    }
}
