package dev.natan.payments.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.natan.payments.testsupport.PaymentsLocalStack;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Uses a function that is never invoked, wired to a queue nobody writes to: only the trigger's on/off state matters.
 */
class LambdaTriggerSwitchTest {

    private static final String FUNCTION = "switch-test";
    private static final LambdaClient lambda = PaymentsLocalStack.client(LambdaClient.builder());

    private final LambdaTriggerSwitch triggerSwitch = new LambdaTriggerSwitch(lambda, FUNCTION);

    @BeforeAll
    static void createFunctionWithAnSqsTrigger() throws IOException {
        SqsClient sqs = PaymentsLocalStack.client(SqsClient.builder());
        String queueUrl = sqs.createQueue(create -> create.queueName("switch-test")).queueUrl();
        String queueArn = sqs.getQueueAttributes(get -> get.queueUrl(queueUrl).attributeNamesWithStrings("QueueArn"))
                .attributesAsStrings().get("QueueArn");

        SdkBytes code = SdkBytes.fromByteArray(emptyZip());
        lambda.createFunction(create -> create
                .functionName(FUNCTION)
                .runtime(Runtime.JAVA25)
                .handler("never.Invoked::handleRequest")
                .role("arn:aws:iam::000000000000:role/" + FUNCTION)
                .code(zip -> zip.zipFile(code)));
        lambda.waiter().waitUntilFunctionActiveV2(get -> get.functionName(FUNCTION));
        lambda.createEventSourceMapping(create -> create.functionName(FUNCTION).eventSourceArn(queueArn));
    }

    @Test
    void turnsTheTriggerOffAndOn() {
        await().atMost(Duration.ofSeconds(30)).until(triggerSwitch::isOn);

        triggerSwitch.turnOff();
        await().atMost(Duration.ofSeconds(30)).until(() -> !triggerSwitch.isOn());

        triggerSwitch.turnOn();
        await().atMost(Duration.ofSeconds(30)).until(triggerSwitch::isOn);
    }

    @Test
    void aFunctionWithoutTriggerIsOffAndCannotBeTurnedOn() {
        LambdaTriggerSwitch missing = new LambdaTriggerSwitch(lambda, "not-deployed");

        assertThat(missing.isOn()).isFalse();
        assertThatThrownBy(missing::turnOn).hasMessageContaining("not-deployed");
    }

    private static byte[] emptyZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ZipOutputStream(bytes).close();
        return bytes.toByteArray();
    }
}
