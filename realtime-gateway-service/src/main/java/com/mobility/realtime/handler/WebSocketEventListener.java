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

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class WebSocketEventListener {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final AtomicInteger activeConnections;

    public WebSocketEventListener(StompAuthChannelInterceptor stompAuthChannelInterceptor, io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.activeConnections = new AtomicInteger(0);
        meterRegistry.gauge("websocket.connections.active", this.activeConnections);
    }

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        int count = activeConnections.incrementAndGet();
        log.info("WebSocket connected sessionId={} (Total active: {})", accessor.getSessionId(), count);
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        log.info("WebSocket subscribed sessionId={} destination={}",
                accessor.getSessionId(), accessor.getDestination());
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        int count = activeConnections.decrementAndGet();
        log.info("WebSocket disconnected sessionId={} closeStatus={} (Total active: {})",
                event.getSessionId(), event.getCloseStatus(), count);
        stompAuthChannelInterceptor.removeSession(event.getSessionId());
    }
}
