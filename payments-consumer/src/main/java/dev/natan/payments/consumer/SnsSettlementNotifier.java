package dev.natan.payments.consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.natan.payments.contract.PaymentSettled;
import io.awspring.cloud.sns.core.SnsTemplate;

@Component
class SnsSettlementNotifier implements SettlementNotifier {

    private final SnsTemplate snsTemplate;
    private final String topicName;

    SnsSettlementNotifier(SnsTemplate snsTemplate, @Value("${payments.settled-topic}") String topicName) {
        this.snsTemplate = snsTemplate;
        this.topicName = topicName;
    }

    @Override
    public void publish(PaymentSettled settled) {
        snsTemplate.convertAndSend(topicName, settled);
    }
}
