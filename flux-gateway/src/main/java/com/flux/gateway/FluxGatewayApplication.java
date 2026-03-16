package com.flux.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.context.annotation.Bean;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class FluxGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FluxGatewayApplication.class, args);
    }

    @Bean
    public RouterFunction<ServerResponse> healthRoute() {
        return route(GET("/api/health"),
                request -> ServerResponse.ok().bodyValue("{\"status\":\"UP\"}"));
    }
}
