package com.smartmobility.gateway.filter;

import com.smartmobility.gateway.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class JwtGatewayFilterTest {

    @Test
    void riderCanAccessRideCreationRoute() {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        when(jwtUtils.validateToken("token")).thenReturn(true);
        when(jwtUtils.extractUserId("token")).thenReturn(42L);
        when(jwtUtils.extractRoles("token")).thenReturn(Set.of("RIDER"));
        when(redisTemplate.hasKey("blacklist:token")).thenReturn(Mono.just(false));
        when(chain.filter(any())).thenReturn(Mono.empty());

        JwtGatewayFilter filter = new JwtGatewayFilter(jwtUtils, redisTemplate);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/rides")
                        .header("Authorization", "Bearer token")
                        .build()
        );

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNotEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void riderCanAccessDispatchStatusRoute() {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        when(jwtUtils.validateToken("token")).thenReturn(true);
        when(jwtUtils.extractUserId("token")).thenReturn(42L);
        when(jwtUtils.extractRoles("token")).thenReturn(Set.of("RIDER"));
        when(redisTemplate.hasKey("blacklist:token")).thenReturn(Mono.just(false));
        when(chain.filter(any())).thenReturn(Mono.empty());

        JwtGatewayFilter filter = new JwtGatewayFilter(jwtUtils, redisTemplate);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/dispatch/6b86fcbb-1132-4598-8bc6-4a085c2c265d")
                        .header("Authorization", "Bearer token")
                        .build()
        );

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNotEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void riderCannotAccessRideStartRoute() {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        when(jwtUtils.validateToken("token")).thenReturn(true);
        when(jwtUtils.extractUserId("token")).thenReturn(42L);
        when(jwtUtils.extractRoles("token")).thenReturn(Set.of("RIDER"));
        when(redisTemplate.hasKey("blacklist:token")).thenReturn(Mono.just(false));

        JwtGatewayFilter filter = new JwtGatewayFilter(jwtUtils, redisTemplate);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/rides/31647e2e-06da-4e4b-b8b0-464fc4b78ec2/start")
                        .header("Authorization", "Bearer token")
                        .build()
        );

        filter.filter(exchange, chain).block();

        verify(chain, org.mockito.Mockito.never()).filter(any());
        assertEquals(HttpStatus.FORBIDDEN.value(), exchange.getResponse().getStatusCode().value());
    }

    @Test
    void driverCanAccessDriverResponseRoute() {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        when(jwtUtils.validateToken("token")).thenReturn(true);
        when(jwtUtils.extractUserId("token")).thenReturn(42L);
        when(jwtUtils.extractRoles("token")).thenReturn(Set.of("DRIVER"));
        when(redisTemplate.hasKey("blacklist:token")).thenReturn(Mono.just(false));
        when(chain.filter(any())).thenReturn(Mono.empty());

        JwtGatewayFilter filter = new JwtGatewayFilter(jwtUtils, redisTemplate);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/dispatch/driver-response")
                        .header("Authorization", "Bearer token")
                        .build()
        );

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNotEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void driverCanAccessDriversServiceRoutes() {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        when(jwtUtils.validateToken("token")).thenReturn(true);
        when(jwtUtils.extractUserId("token")).thenReturn(42L);
        when(jwtUtils.extractRoles("token")).thenReturn(Set.of("DRIVER"));
        when(redisTemplate.hasKey("blacklist:token")).thenReturn(Mono.just(false));
        when(chain.filter(any())).thenReturn(Mono.empty());

        JwtGatewayFilter filter = new JwtGatewayFilter(jwtUtils, redisTemplate);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/drivers/42")
                        .header("Authorization", "Bearer token")
                        .build()
        );

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertNotEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void driverCanAccessRideStartAndCompleteRoutes() {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        when(jwtUtils.validateToken("token")).thenReturn(true);
        when(jwtUtils.extractUserId("token")).thenReturn(42L);
        when(jwtUtils.extractRoles("token")).thenReturn(Set.of("DRIVER"));
        when(redisTemplate.hasKey("blacklist:token")).thenReturn(Mono.just(false));
        when(chain.filter(any())).thenReturn(Mono.empty());

        JwtGatewayFilter filter = new JwtGatewayFilter(jwtUtils, redisTemplate);
        MockServerWebExchange startExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/rides/31647e2e-06da-4e4b-b8b0-464fc4b78ec2/start")
                        .header("Authorization", "Bearer token")
                        .build()
        );
        MockServerWebExchange completeExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/rides/31647e2e-06da-4e4b-b8b0-464fc4b78ec2/complete")
                        .header("Authorization", "Bearer token")
                        .build()
        );

        filter.filter(startExchange, chain).block();
        filter.filter(completeExchange, chain).block();

        verify(chain, org.mockito.Mockito.times(2)).filter(any());
        assertNotEquals(HttpStatus.FORBIDDEN, startExchange.getResponse().getStatusCode());
        assertNotEquals(HttpStatus.FORBIDDEN, completeExchange.getResponse().getStatusCode());
    }
}
