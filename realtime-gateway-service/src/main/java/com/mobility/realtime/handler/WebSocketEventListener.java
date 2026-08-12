package com.mobility.realtime.handler;

import com.mobility.realtime.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketEventListener {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    // Spring can publish SessionDisconnectEvent more than once for the same session (once for
    // a STOMP-level DISCONNECT, again for the underlying WebSocket close) — a raw
    // increment/decrement counter double-decrements on the duplicate and drifts negative over
    // time (observed directly: "Total active: -3" in logs). A session-ID set is idempotent by
    // construction: removing an already-removed id is a no-op, so the size stays correct
    // regardless of how many times disconnect fires for the same session.
    private final Set<String> activeSessionIds = ConcurrentHashMap.newKeySet();

    public WebSocketEventListener(StompAuthChannelInterceptor stompAuthChannelInterceptor, io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        meterRegistry.gauge("websocket.connections.active", this.activeSessionIds, Set::size);
    }

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        activeSessionIds.add(accessor.getSessionId());
        log.info("WebSocket connected sessionId={} (Total active: {})", accessor.getSessionId(), activeSessionIds.size());
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        log.info("WebSocket subscribed sessionId={} destination={}",
                accessor.getSessionId(), accessor.getDestination());
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        activeSessionIds.remove(event.getSessionId());
        log.info("WebSocket disconnected sessionId={} closeStatus={} (Total active: {})",
                event.getSessionId(), event.getCloseStatus(), activeSessionIds.size());
        stompAuthChannelInterceptor.removeSession(event.getSessionId());
    }
}
