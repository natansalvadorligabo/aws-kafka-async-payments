package dev.natan.payments.settlement;

import dev.natan.payments.contract.PaymentSettled;
import software.amazon.awssdk.services.sns.SnsClient;
import tools.jackson.databind.json.JsonMapper;

/** Publishes {@link PaymentSettled} as JSON on an SNS topic. */
public class SnsSettlementNotifier implements SettlementNotifier {

    private final SnsClient sns;
    private final String topicArn;
    private final JsonMapper json;

    public SnsSettlementNotifier(SnsClient sns, String topicArn, JsonMapper json) {
        this.sns = sns;
        this.topicArn = topicArn;
        this.json = json;
    }

    @Override
    public void publish(PaymentSettled settled) {
        sns.publish(publish -> publish.topicArn(topicArn).message(json.writeValueAsString(settled)));
    }
}
