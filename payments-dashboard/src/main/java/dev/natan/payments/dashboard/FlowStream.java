package dev.natan.payments.dashboard;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Pushes {@link StackState} and {@link FlowEvent}s to every open browser tab over Server-Sent Events. */
@RestController
class FlowStream {

    private final FlowTimeline timeline;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicReference<StackState> lastState = new AtomicReference<>();

    FlowStream(FlowTimeline timeline) {
        this.timeline = timeline;
        timeline.subscribe(event -> broadcast("flow", event));
    }

    @GetMapping(path = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter open() throws IOException {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        // A new tab first catches up on what already happened.
        for (FlowEvent event : timeline.recent()) {
            emitter.send(SseEmitter.event().name("flow").data(event));
        }
        StackState state = lastState.get();
        if (state != null) {
            emitter.send(SseEmitter.event().name("state").data(state));
        }
        emitters.add(emitter);
        return emitter;
    }

    void publish(StackState state) {
        lastState.set(state);
        broadcast("state", state);
    }

    private void broadcast(String name, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data));
            } catch (IOException | IllegalStateException closed) {
                emitters.remove(emitter);
            }
        }
    }
}
