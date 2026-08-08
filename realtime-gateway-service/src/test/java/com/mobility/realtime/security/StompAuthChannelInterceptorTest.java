package com.mobility.realtime.security;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompAuthChannelInterceptorTest {

    private final JwtUtils jwtUtils = mock(JwtUtils.class);
    private final RideOwnershipClient rideOwnershipClient = mock(RideOwnershipClient.class);
    private final StompAuthChannelInterceptor interceptor =
            new StompAuthChannelInterceptor(jwtUtils, rideOwnershipClient);

    private Message<byte[]> connectMessage(String sessionId, String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(sessionId);
        if (authorizationHeader != null) {
            accessor.addNativeHeader("Authorization", authorizationHeader);
        }
        return org.springframework.messaging.support.MessageBuilder
                .withPayload(new byte[0]).setHeaders(accessor).build();
    }

    private Message<byte[]> subscribeMessage(String sessionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setDestination(destination);
        return org.springframework.messaging.support.MessageBuilder
                .withPayload(new byte[0]).setHeaders(accessor).build();
    }

    /** CONNECT with the given token on sessionId, so the interceptor's session map is populated. */
    private void connectAs(String sessionId, String token, RealtimePrincipal principal) {
        when(jwtUtils.authenticate(token)).thenReturn(principal);
        interceptor.preSend(connectMessage(sessionId, "Bearer " + token), null);
    }

    @Test
    void connectWithoutAuthorizationHeaderIsRejected() {
        assertThrows(StompAuthorizationException.class,
                () -> interceptor.preSend(connectMessage("s1", null), null));
    }

    @Test
    void connectWithInvalidTokenIsRejected() {
        when(jwtUtils.authenticate("bad-token")).thenReturn(null);
        assertThrows(StompAuthorizationException.class,
                () -> interceptor.preSend(connectMessage("s1", "Bearer bad-token"), null));
    }

    @Test
    void connectWithValidTokenIsAccepted() {
        RealtimePrincipal principal = new RealtimePrincipal(1L, Set.of("RIDER"));
        when(jwtUtils.authenticate("good-token")).thenReturn(principal);

        Message<?> result = interceptor.preSend(connectMessage("s1", "Bearer good-token"), null);

        assertNotNull(result);
    }

    @Test
    void subscribeWithoutAPriorConnectOnTheSameSessionIsRejected() {
        assertThrows(StompAuthorizationException.class,
                () -> interceptor.preSend(subscribeMessage("never-connected", "/topic/driver/42"), null));
    }

    @Test
    void driverCanSubscribeToOwnDriverTopic() {
        connectAs("s1", "driver-token", new RealtimePrincipal(42L, Set.of("DRIVER")));

        Message<?> result = interceptor.preSend(subscribeMessage("s1", "/topic/driver/42"), null);

        assertNotNull(result);
    }

    @Test
    void driverCannotSubscribeToAnotherDriversTopic() {
        connectAs("s1", "driver-token", new RealtimePrincipal(42L, Set.of("DRIVER")));

        assertThrows(StompAuthorizationException.class,
                () -> interceptor.preSend(subscribeMessage("s1", "/topic/driver/99"), null));
    }

    @Test
    void adminCanSubscribeToAnyDriverTopic() {
        connectAs("s1", "admin-token", new RealtimePrincipal(1L, Set.of("ADMIN")));

        Message<?> result = interceptor.preSend(subscribeMessage("s1", "/topic/driver/99"), null);

        assertNotNull(result);
    }

    @Test
    void riderCanSubscribeToOwnTripTopicWhenOwnershipConfirmed() {
        connectAs("s1", "rider-token", new RealtimePrincipal(7L, Set.of("RIDER")));
        when(rideOwnershipClient.isRideParticipant("ride-1", 7L)).thenReturn(true);

        Message<?> result = interceptor.preSend(subscribeMessage("s1", "/topic/trip/ride-1"), null);

        assertNotNull(result);
    }

    @Test
    void riderCannotSubscribeToTripTopicWhenNotAParticipant() {
        connectAs("s1", "rider-token", new RealtimePrincipal(7L, Set.of("RIDER")));
        when(rideOwnershipClient.isRideParticipant("ride-1", 7L)).thenReturn(false);

        assertThrows(StompAuthorizationException.class,
                () -> interceptor.preSend(subscribeMessage("s1", "/topic/trip/ride-1"), null));
    }

    @Test
    void unknownDestinationIsRejectedByDefault() {
        connectAs("s1", "rider-token", new RealtimePrincipal(7L, Set.of("RIDER")));

        assertThrows(StompAuthorizationException.class,
                () -> interceptor.preSend(subscribeMessage("s1", "/topic/something-else"), null));
    }

    @Test
    void differentSessionsDoNotShareAuthenticatedPrincipals() {
        connectAs("s1", "driver-42-token", new RealtimePrincipal(42L, Set.of("DRIVER")));
        // s2 never connected — must not incorrectly see s1's principal.
        assertThrows(StompAuthorizationException.class,
                () -> interceptor.preSend(subscribeMessage("s2", "/topic/driver/42"), null));
    }

    @Test
    void removeSessionForgetsThePrincipalSoLaterSubscribesAreRejected() {
        connectAs("s1", "driver-token", new RealtimePrincipal(42L, Set.of("DRIVER")));
        interceptor.removeSession("s1");

        assertThrows(StompAuthorizationException.class,
                () -> interceptor.preSend(subscribeMessage("s1", "/topic/driver/42"), null));
    }
}
