package com.mobility.realtime.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Slf4j
@Component
public class WebSocketEventListener {

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("WebSocket connected sessionId={}", accessor.getSessionId());
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        log.info("WebSocket subscribed sessionId={} destination={}",
                accessor.getSessionId(), accessor.getDestination());
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        log.info("WebSocket disconnected sessionId={} closeStatus={}",
                event.getSessionId(), event.getCloseStatus());
    }
}
