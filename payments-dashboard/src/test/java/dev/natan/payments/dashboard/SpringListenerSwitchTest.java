package dev.natan.payments.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SpringListenerSwitchTest {

    private final RestClient.Builder http = RestClient.builder().baseUrl("http://consumer:8082");
    private final MockRestServiceServer consumer = MockRestServiceServer.bindTo(http).build();
    private final SpringListenerSwitch listenerSwitch = new SpringListenerSwitch(http.build());

    @Test
    void readsTheListenerStateFromTheActuator() {
        consumer.expect(requestTo("http://consumer:8082/actuator/listener"))
                .andRespond(withSuccess("{\"running\": true}", MediaType.APPLICATION_JSON));

        assertThat(listenerSwitch.isOn()).isTrue();
    }

    @Test
    void anUnreachableConsumerCountsAsOff() {
        consumer.expect(requestTo("http://consumer:8082/actuator/listener")).andRespond(withServerError());

        assertThat(listenerSwitch.isOn()).isFalse();
    }

    @Test
    void turningOffPostsRunningFalse() {
        consumer.expect(requestTo("http://consumer:8082/actuator/listener"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"running\": false}"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        listenerSwitch.turnOff();

        consumer.verify();
    }
}
