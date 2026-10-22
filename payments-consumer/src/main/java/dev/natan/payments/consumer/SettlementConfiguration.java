package dev.natan.payments.consumer;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.natan.payments.settlement.DynamoDbProcessedPayments;
import dev.natan.payments.settlement.PaymentSettlement;
import dev.natan.payments.settlement.ProcessedPayments;
import dev.natan.payments.settlement.SettlementNotifier;
import dev.natan.payments.settlement.SnsSettlementNotifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sns.SnsClient;
import tools.jackson.databind.json.JsonMapper;

/** Wires the framework-free settlement logic to the AWS clients Spring Cloud AWS configures. */
@Configuration(proxyBeanMethods = false)
class SettlementConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ProcessedPayments processedPayments(
            DynamoDbClient dynamoDb, @Value("${payments.processed-table}") String table, Clock clock,
            @Value("${payments.claim-lease}") Duration lease) {
        return new DynamoDbProcessedPayments(dynamoDb, table, clock, lease);
    }

    @Bean
    SnsSettlementNotifier settlementNotifier(
            SnsClient sns, @Value("${payments.settled-topic-arn}") String topicArn, JsonMapper json) {
        return new SnsSettlementNotifier(sns, topicArn, json);
    }

    @Bean
    PaymentSettlement paymentSettlement(
            ProcessedPayments processedPayments, SettlementNotifier settlementNotifier, Clock clock) {
        return new PaymentSettlement(processedPayments, settlementNotifier, clock);
    }
}
