package com.polling.platform.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class SseEmitterRegistry {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    public SseEmitter register(String pollId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(pollId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> remove(pollId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> remove(pollId, emitter));

        log.debug("SSE client registered for poll {}, total emitters: {}",
                pollId, emitters.getOrDefault(pollId, new CopyOnWriteArrayList<>()).size());
        return emitter;
    }

    public void broadcast(String pollId, Object data) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(pollId);
        if (list == null || list.isEmpty()) return;

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().data(data));
            } catch (Exception e) {
                log.debug("Removing dead SSE emitter for poll {}", pollId);
                dead.add(emitter);
            }
        }
        list.removeAll(dead);
    }

    private void remove(String pollId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(pollId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(pollId);
        }
    }
}
