package dev.natan.payments.dashboard;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Stops and starts the Spring consumer's SQS listener through its {@code /actuator/listener} endpoint. */
class SpringListenerSwitch implements ConsumerSwitch {

    private final RestClient consumerActuator;

    SpringListenerSwitch(RestClient consumerActuator) {
        this.consumerActuator = consumerActuator;
    }

    @Override
    public String consumer() {
        return "spring";
    }

    @Override
    public boolean isOn() {
        try {
            ListenerState state = consumerActuator.get().uri("/actuator/listener").retrieve().body(ListenerState.class);
            return state != null && state.running();
        } catch (RestClientException unreachable) {
            return false;
        }
    }

    @Override
    public void turnOff() {
        setRunning(false);
    }

    @Override
    public void turnOn() {
        setRunning(true);
    }

    private void setRunning(boolean running) {
        consumerActuator.post().uri("/actuator/listener")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("running", running))
                .retrieve()
                .toBodilessEntity();
    }

    record ListenerState(boolean running) {
    }
}
