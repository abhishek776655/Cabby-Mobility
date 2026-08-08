package com.mobility.realtime.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gates STOMP CONNECT/SUBSCRIBE: no anonymous connections, and no subscribing to another
 * user's trip/driver topic. Without this, the WebSocket endpoint had zero authN/authZ and
 * anyone could enumerate rideId/driverUserId and read someone else's live trip data.
 *
 * <p>{@code accessor.setUser()} on the CONNECT message does NOT persist to later frames —
 * session-to-principal association happens inside {@code StompSubProtocolHandler} at
 * handshake time (there is no HTTP-level principal here; auth happens in-band over STOMP),
 * not via anything a downstream ChannelInterceptor sets. So this interceptor tracks the
 * authenticated principal per sessionId itself, and cleans up on disconnect via
 * {@link #removeSession(String)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern DRIVER_TOPIC = Pattern.compile("^/topic/driver/(\\d+)$");
    private static final Pattern TRIP_TOPIC = Pattern.compile("^/topic/trip/([^/]+)$");
    private static final String ADMIN_ROLE = "ADMIN";

    private final JwtUtils jwtUtils;
    private final RideOwnershipClient rideOwnershipClient;
    private final Map<String, RealtimePrincipal> sessionPrincipals = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        String sessionId = accessor.getSessionId();

        if (StompCommand.CONNECT.equals(command)) {
            RealtimePrincipal principal = authenticate(accessor);
            if (principal == null) {
                log.warn("Rejected WebSocket CONNECT: missing or invalid token");
                throw new StompAuthorizationException("Missing or invalid authentication token");
            }
            if (sessionId != null) {
                sessionPrincipals.put(sessionId, principal);
            }
            accessor.setUser(principal);
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            RealtimePrincipal principal = sessionId != null ? sessionPrincipals.get(sessionId) : null;
            String destination = accessor.getDestination();
            if (principal == null || destination == null || !isAuthorizedSubscription(principal, destination)) {
                log.warn("Rejected WebSocket SUBSCRIBE destination={} user={}",
                        destination, principal == null ? null : principal.userId());
                throw new StompAuthorizationException("Not authorized to subscribe to " + destination);
            }
            return message;
        }

        if (StompCommand.DISCONNECT.equals(command) && sessionId != null) {
            sessionPrincipals.remove(sessionId);
        }

        return message;
    }

    /**
     * Called on WebSocket session close (abrupt disconnects don't always send a STOMP
     * DISCONNECT frame) so this map doesn't grow unbounded.
     */
    public void removeSession(String sessionId) {
        sessionPrincipals.remove(sessionId);
    }

    private RealtimePrincipal authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return jwtUtils.authenticate(header.substring("Bearer ".length()));
    }

    private boolean isAuthorizedSubscription(RealtimePrincipal principal, String destination) {
        Matcher driverMatcher = DRIVER_TOPIC.matcher(destination);
        if (driverMatcher.matches()) {
            Long driverUserId = Long.valueOf(driverMatcher.group(1));
            return principal.hasRole(ADMIN_ROLE) || driverUserId.equals(principal.userId());
        }

        Matcher tripMatcher = TRIP_TOPIC.matcher(destination);
        if (tripMatcher.matches()) {
            String rideId = tripMatcher.group(1);
            return principal.hasRole(ADMIN_ROLE) || rideOwnershipClient.isRideParticipant(rideId, principal.userId());
        }

        // Deny by default: only the known trip/driver topics are subscribable.
        return false;
    }
}
