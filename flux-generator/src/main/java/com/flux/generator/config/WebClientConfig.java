package com.flux.generator.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import javax.net.ssl.SSLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${app.gateway-url:http://localhost:8081}")
    private String gatewayUrl;

    @Value("${app.api-key:changeme}")
    private String apiKey;

    @Bean
    public WebClient webClient() throws SSLException {
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
            })
            .secure(sslSpec -> {
                try {
                    sslSpec.sslContext(SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build());
                } catch (SSLException e) {
                    throw new RuntimeException(e);
                }
            });

        return WebClient.builder()
            .baseUrl(gatewayUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader("X-API-Key", apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
