package dev.natan.payments.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ListenerEndpointTest extends ConsumerIntegrationTest {

    @Autowired
    ListenerEndpoint listenerEndpoint;

    @Test
    void stopsAndRestartsThePaymentsListener() {
        try {
            listenerEndpoint.running(false);
            assertThat(listenerEndpoint.state().running()).isFalse();
        } finally {
            listenerEndpoint.running(true);
        }
        assertThat(listenerEndpoint.state().running()).isTrue();
    }
}
