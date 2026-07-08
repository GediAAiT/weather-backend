package com.weatherapp.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        ConnectionProvider provider = ConnectionProvider.builder("weather-backend-http")
                .maxConnections(100)
                .pendingAcquireMaxCount(500)
                .pendingAcquireTimeout(Duration.ofSeconds(2))
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .compress(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1500)
                .responseTimeout(Duration.ofSeconds(3))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(3, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(3, TimeUnit.SECONDS)));

        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "weather-backend/1.0")
                .build();
    }

    /** Pool used to fan out upstream weather calls (e.g. /api/weather/batch) in parallel. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService weatherExecutor() {
        return Executors.newFixedThreadPool(8);
    }
}
