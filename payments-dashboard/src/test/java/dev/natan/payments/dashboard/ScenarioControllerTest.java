package dev.natan.payments.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.natan.payments.dashboard.FlowEvent.Type;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(ScenarioController.class)
class ScenarioControllerTest {

    @TestConfiguration
    static class Doubles {

        @Bean
        SpyPaymentInjector paymentInjector() {
            return new SpyPaymentInjector();
        }

        @Bean
        FakeConsumerSwitch consumerSwitch() {
            return new FakeConsumerSwitch();
        }

        @Bean
        FlowTimeline flowTimeline() {
            return new FlowTimeline(Clock.systemUTC(), new MessageBodies(JsonMapper.builder().build()));
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    SpyPaymentInjector injector;

    @Autowired
    FakeConsumerSwitch consumerSwitch;

    @Autowired
    FlowTimeline timeline;

    @Test
    void sendsTheRequestedNumberOfPaymentsAndRecordsEachOne() {
        int before = injector.payments.get();

        assertThat(mvc.post().uri("/api/payments?count=3").exchange()).hasStatusOk();

        assertThat(injector.payments.get() - before).isEqualTo(3);
        assertThat(timeline.recent()).filteredOn(event -> event.type() == Type.ACCEPTED).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void rejectsABatchOutsideTheAllowedRange() {
        assertThat(mvc.post().uri("/api/payments?count=500").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void stopsAndStartsTheConsumer() {
        assertThat(mvc.post().uri("/api/consumer/off").exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(consumerSwitch.isOn()).isFalse();

        assertThat(mvc.post().uri("/api/consumer/on").exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(consumerSwitch.isOn()).isTrue();
    }

    @Test
    void sendsDuplicatesAndPoisonMessages() {
        assertThat(mvc.post().uri("/api/duplicates").exchange()).hasStatusOk();
        assertThat(mvc.post().uri("/api/poison").exchange()).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(injector.duplicates.get()).isPositive();
        assertThat(injector.poisons.get()).isPositive();
    }
}
