package dev.natan.payments.lambda;

import static dev.natan.payments.testsupport.PaymentQueues.DEAD_LETTERS;
import static dev.natan.payments.testsupport.PaymentQueues.PAYMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.MountableFile;

import dev.natan.payments.contract.PaymentRequested;
import dev.natan.payments.testsupport.PaymentQueues;
import dev.natan.payments.testsupport.PaymentsLocalStack;
import dev.natan.payments.testsupport.SettledPaymentsAudit;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/** The packaged zip, deployed by the same script docker-compose uses, against the same queues as the consumer. */
class SettlePaymentsLambdaIT {

    private static final String FUNCTION = "settle-payments";
    private static final String DEPLOYED_ZIP = "/tmp/payments-lambda.zip";

    private static final LocalStackContainer localStack = PaymentsLocalStack.shared();
    private static final SqsClient sqs = PaymentsLocalStack.client(SqsClient.builder());
    private static final LambdaClient lambda = PaymentsLocalStack.client(LambdaClient.builder());
    private static final PaymentQueues queues = new PaymentQueues(sqs);
    private static final SettledPaymentsAudit audit = new SettledPaymentsAudit(sqs);

    @BeforeAll
    static void deployLambda() throws Exception {
        localStack.copyFileToContainer(MountableFile.forHostPath("target/payments-lambda.zip"), DEPLOYED_ZIP);
        ExecResult deploy = localStack.execInContainer("/opt/payments/deploy-lambda.sh", DEPLOYED_ZIP);
        assertThat(deploy.getExitCode()).as(deploy.getStderr()).isZero();

        // The first invocation pulls the java25 runtime image; pay that cost here, not inside a test's timeout.
        lambda.invoke(invoke -> invoke.functionName(FUNCTION).payload(SdkBytes.fromUtf8String("{\"Records\": []}")));
    }

    @Test
    void paymentsQueuedWhileTheTriggerIsDisabledAreSettledWhenItIsEnabled() {
        List<PaymentRequested> payments = Stream.generate(PaymentQueues::newPayment).limit(20).toList();

        setTriggerEnabled(false);
        try {
            payments.forEach(queues::send);
            assertThat(queues.messagesOn(PAYMENTS)).isGreaterThanOrEqualTo(payments.size());
        } finally {
            setTriggerEnabled(true);
        }

        await().atMost(Duration.ofSeconds(90)).untilAsserted(() -> assertThat(payments)
                .allSatisfy(payment -> assertThat(audit.settlementsOf(payment.paymentId())).isEqualTo(1)));
    }

    @Test
    void theSamePaymentDeliveredTwiceIsSettledOnce() {
        PaymentRequested payment = PaymentQueues.newPayment();

        queues.send(payment);
        queues.send(payment);

        await().atMost(Duration.ofSeconds(60)).until(() -> audit.settlementsOf(payment.paymentId()) == 1);
        await().atMost(Duration.ofSeconds(60)).until(() -> queues.messagesOn(PAYMENTS) == 0);
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
                .until(() -> audit.settlementsOf(payment.paymentId()) == 1);
    }

    @Test
    void recordThatNeverSettlesMovesToTheDlqAfterMaxReceives() {
        String poison = "{\"paymentId\": \"not-a-uuid\", \"marker\": \"" + UUID.randomUUID() + "\"}";

        queues.sendRaw(poison);

        await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(2))
                .until(() -> queues.bodiesOn(DEAD_LETTERS).contains(poison));
    }

    private static void setTriggerEnabled(boolean enabled) {
        String mapping = lambda.listEventSourceMappings(list -> list.functionName(FUNCTION))
                .eventSourceMappings().getFirst().uuid();
        lambda.updateEventSourceMapping(update -> update.uuid(mapping).enabled(enabled));
        String expected = enabled ? "Enabled" : "Disabled";
        await().atMost(Duration.ofSeconds(30))
                .until(() -> lambda.getEventSourceMapping(get -> get.uuid(mapping)).state().equals(expected));
    }
}
