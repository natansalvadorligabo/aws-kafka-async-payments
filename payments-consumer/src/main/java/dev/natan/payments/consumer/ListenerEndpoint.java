package dev.natan.payments.consumer;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import io.awspring.cloud.sqs.listener.MessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;

/**
 * {@code /actuator/listener}: stops and starts polling the payments queue without killing the process, so the
 * dashboard can show messages waiting on SQS while the consumer is down.
 *
 * <pre>{@code
 * curl -X POST localhost:8082/actuator/listener -H 'Content-Type: application/json' -d '{"running": false}'
 * }</pre>
 */
@Component
@Endpoint(id = "listener")
class ListenerEndpoint {

    private final MessageListenerContainerRegistry listeners;

    ListenerEndpoint(MessageListenerContainerRegistry listeners) {
        this.listeners = listeners;
    }

    @ReadOperation
    ListenerState state() {
        return new ListenerState(paymentsListener().isRunning());
    }

    @WriteOperation
    void running(boolean running) {
        if (running) {
            paymentsListener().start();
        } else {
            paymentsListener().stop();
        }
    }

    private MessageListenerContainer<?> paymentsListener() {
        return listeners.getContainerById(PaymentListener.CONTAINER_ID);
    }

    record ListenerState(boolean running) {
    }
}
