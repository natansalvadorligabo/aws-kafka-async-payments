package dev.natan.payments.dashboard;

import java.net.URI;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class DashboardConfiguration {

    private final URI awsEndpoint;

    DashboardConfiguration(@Value("${payments.aws-endpoint}") URI awsEndpoint) {
        this.awsEndpoint = awsEndpoint;
    }

    @Bean
    SqsClient sqsClient() {
        return localStack(SqsClient.builder());
    }

    @Bean
    DynamoDbClient dynamoDbClient() {
        return localStack(DynamoDbClient.builder());
    }

    @Bean
    LambdaClient lambdaClient() {
        return localStack(LambdaClient.builder());
    }

    @Bean
    StackReader stackReader(SqsClient sqs, DynamoDbClient dynamoDb, JsonMapper json) {
        return new AwsStackReader(sqs, dynamoDb, json);
    }

    @Bean
    PaymentInjector paymentInjector(
            RestClient.Builder http, @Value("${payments.api-url}") String apiUrl, SqsClient sqs, JsonMapper json,
            Clock clock) {
        return new StackPaymentInjector(http.baseUrl(apiUrl).build(), sqs, json, clock);
    }

    @Bean
    ConsumerSwitch consumerSwitch(
            @Value("${payments.consumer}") String consumer, LambdaClient lambda,
            @Value("${payments.lambda-function}") String functionName,
            RestClient.Builder http, @Value("${payments.consumer-url}") String consumerUrl) {
        return switch (consumer) {
            case "lambda" -> new LambdaTriggerSwitch(lambda, functionName);
            case "spring" -> new SpringListenerSwitch(http.baseUrl(consumerUrl).build());
            default -> throw new IllegalArgumentException(
                    "expected payments.consumer to be lambda or spring, got " + consumer);
        };
    }

    @Bean
    FlowTimeline flowTimeline(Clock clock, JsonMapper json) {
        return new FlowTimeline(clock, json);
    }

    private <B extends AwsClientBuilder<B, C>, C> C localStack(B builder) {
        return builder
                .endpointOverride(awsEndpoint)
                .region(Region.US_EAST_1)
                // LocalStack accepts any credentials; nothing here touches a real AWS account.
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();
    }
}
