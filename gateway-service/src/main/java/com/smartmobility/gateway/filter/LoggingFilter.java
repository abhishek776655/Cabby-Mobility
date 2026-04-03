package com.smartmobility.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getPath().toString();
        String method = exchange.getRequest().getMethod().name();

        long start = System.currentTimeMillis();

        System.out.println("REQ → " + method + " " + path);

        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    long time = System.currentTimeMillis() - start;
                    HttpStatus status = (HttpStatus) exchange.getResponse().getStatusCode();

                    System.out.println("RES → " + status + " (" + time + "ms)");
                }));
    }

    @Override
    public int getOrder() {
        return 0;
    }
}