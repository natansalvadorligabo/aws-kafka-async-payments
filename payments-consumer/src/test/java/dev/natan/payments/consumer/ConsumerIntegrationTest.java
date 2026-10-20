package dev.natan.payments.consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.localstack.LocalStackContainer;

import dev.natan.payments.settlement.SnsSettlementNotifier;
import dev.natan.payments.testsupport.PaymentQueues;
import dev.natan.payments.testsupport.PaymentsLocalStack;
import dev.natan.payments.testsupport.SettledPaymentsAudit;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * All consumer integration tests share this one Spring context, so a single listener polls the queue. Separate
 * contexts would keep their listeners alive in the context cache and compete for the same messages.
 */
@SpringBootTest
@Import(ConsumerIntegrationTest.Doubles.class)
abstract class ConsumerIntegrationTest {

    @ServiceConnection
    static LocalStackContainer localStack = PaymentsLocalStack.shared();

    static final SqsClient sqs = PaymentsLocalStack.client(SqsClient.builder());
    static final PaymentQueues queues = new PaymentQueues(sqs);
    static final SettledPaymentsAudit audit = new SettledPaymentsAudit(sqs);

    @TestConfiguration
    static class Doubles {

        @Bean
        @Primary
        FaultInjectingNotifier faultInjectingNotifier(SnsSettlementNotifier snsSettlementNotifier) {
            return new FaultInjectingNotifier(snsSettlementNotifier);
        }
    }

    @Autowired
    FaultInjectingNotifier notifier;
}
