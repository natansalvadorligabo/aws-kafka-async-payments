package dev.natan.payments.consumer;

import org.springframework.stereotype.Component;

import dev.natan.payments.contract.PaymentRequested;
import io.awspring.cloud.sqs.annotation.SqsListener;

/**
 * Deletes the SQS message only when settlement returns normally. Any exception leaves the message on the queue:
 * it reappears after the visibility timeout and, after maxReceiveCount attempts, moves to the DLQ.
 */
@Component
class PaymentListener {

    static final String CONTAINER_ID = "payments";

    private final PaymentSettlement paymentSettlement;

    PaymentListener(PaymentSettlement paymentSettlement) {
        this.paymentSettlement = paymentSettlement;
    }

    @SqsListener(value = "${payments.queue}", id = CONTAINER_ID)
    void onPayment(PaymentRequested payment) {
        paymentSettlement.settle(payment);
    }
}
