package com.smartmobility.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InternalApiFilterTest {

    @Test
    void blocksInternalRoutesAtTheGateway() {
        InternalApiFilter filter = new InternalApiFilter();
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/dispatch/ride-1").build()
        );

        filter.filter(exchange, chain).block();

        verify(chain, org.mockito.Mockito.never()).filter(org.mockito.ArgumentMatchers.any());
        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void allowsNonInternalRoutesToProceed() {
        InternalApiFilter filter = new InternalApiFilter();
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        org.mockito.Mockito.when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/rides/ride-1").build()
        );

        filter.filter(exchange, chain).block();

        verify(chain).filter(org.mockito.ArgumentMatchers.any());
        org.junit.jupiter.api.Assertions.assertNotEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }
}
