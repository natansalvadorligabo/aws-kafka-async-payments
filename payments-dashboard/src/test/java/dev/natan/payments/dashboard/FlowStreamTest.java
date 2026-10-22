package dev.natan.payments.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(FlowStream.class)
class FlowStreamTest {

    @TestConfiguration
    static class Doubles {

        @Bean
        FlowTimeline flowTimeline() {
            return new FlowTimeline(Clock.systemUTC(), JsonMapper.builder().build());
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FlowTimeline timeline;

    @Autowired
    FlowStream stream;

    @Test
    void newTabCatchesUpOnPastEventsThenReceivesLiveStateAndEvents() throws Exception {
        timeline.poisonSent();

        MvcResult tab = mvc.perform(get("/api/stream")).andExpect(request().asyncStarted()).andReturn();
        stream.publish(new StackState(3, 1, 0, 0, 0, "lambda", true));
        timeline.consumerStopped("lambda");

        String received = tab.getResponse().getContentAsString();
        assertThat(received.indexOf("POISON_SENT"))
                .isLessThan(received.indexOf("event:state"))
                .isLessThan(received.indexOf("CONSUMER_STOPPED"));
        assertThat(received).contains("\"waiting\":3");
    }
}
