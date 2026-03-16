package com.flux.generator.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${app.gateway-url:http://localhost:8881}")
    private String gatewayUrl;

    @Value("${app.api-key}")
    private String apiKey;

    @PostConstruct
    void validateApiKey() {
        if (apiKey == null || apiKey.length() < 16) {
            throw new IllegalStateException("app.api-key must be at least 16 characters");
        }
    }

    @Bean
    public WebClient webClient() {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("flux-generator")
            .maxConnections(500)
            .pendingAcquireMaxCount(1000)
            .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
            .responseTimeout(Duration.ofSeconds(10))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .doOnConnected(conn -> {
                conn.addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS));
                conn.addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS));
            });

        return WebClient.builder()
            .baseUrl(gatewayUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader("X-API-Key", apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
