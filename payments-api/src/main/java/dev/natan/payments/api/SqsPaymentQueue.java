package dev.natan.payments.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import dev.natan.payments.contract.PaymentRequested;
import io.awspring.cloud.sqs.operations.SqsTemplate;

@Component
@ConditionalOnProperty(name = "payments.broker", havingValue = "sqs", matchIfMissing = true)
class SqsPaymentQueue implements PaymentQueue {

    private final SqsTemplate sqsTemplate;
    private final String queueName;

    SqsPaymentQueue(SqsTemplate sqsTemplate, @Value("${payments.queue}") String queueName) {
        this.sqsTemplate = sqsTemplate;
        this.queueName = queueName;
    }

    @Override
    public void enqueue(PaymentRequested payment) {
        sqsTemplate.send(queueName, payment);
    }
}
