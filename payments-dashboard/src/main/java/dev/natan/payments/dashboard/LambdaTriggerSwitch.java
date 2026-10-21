package dev.natan.payments.dashboard;

import java.util.Optional;

import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.EventSourceMappingConfiguration;

/** Disables and enables the SQS event source mapping, the Lambda equivalent of stopping a consumer. */
class LambdaTriggerSwitch implements ConsumerSwitch {

    private final LambdaClient lambda;
    private final String functionName;

    LambdaTriggerSwitch(LambdaClient lambda, String functionName) {
        this.lambda = lambda;
        this.functionName = functionName;
    }

    @Override
    public String consumer() {
        return "lambda";
    }

    @Override
    public boolean isOn() {
        // Before make up deploys the Lambda there is no mapping: nothing consumes the queue yet.
        return trigger().map(mapping -> "Enabled".equals(mapping.state())).orElse(false);
    }

    @Override
    public void turnOff() {
        setEnabled(false);
    }

    @Override
    public void turnOn() {
        setEnabled(true);
    }

    private void setEnabled(boolean enabled) {
        EventSourceMappingConfiguration mapping = trigger().orElseThrow(() -> new IllegalStateException(
                "expected an SQS trigger on Lambda " + functionName + ", found none: was it deployed (make up)?"));
        lambda.updateEventSourceMapping(update -> update.uuid(mapping.uuid()).enabled(enabled));
    }

    private Optional<EventSourceMappingConfiguration> trigger() {
        return lambda.listEventSourceMappings(list -> list.functionName(functionName))
                .eventSourceMappings().stream().findFirst();
    }
}
