package com.flux.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${app.warehouse-url:http://flux-warehouse:8082}")
    private String warehouseUrl;

    @Bean
    public WebClient webClient() {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("flux-gateway")
            .maxConnections(100)
            .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
            .responseTimeout(Duration.ofSeconds(10))
            .responseTimeout(Duration.ofSeconds(5)); // connection timeout

        return WebClient.builder()
            .baseUrl(warehouseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
