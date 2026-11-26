package com.web3task.webhook.config;

import com.web3task.webhook.security.DnsRebindingPreventingResolver;
import com.web3task.webhook.security.SafeUrlValidator;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${webhook.client.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${webhook.client.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Value("${webhook.security.allow-loopback-for-testing:false}")
    private boolean allowLoopbackForTesting;

    @Bean
    public WebClient webhookWebClient(SafeUrlValidator safeUrlValidator) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(readTimeoutMs))
                .resolver(new DnsRebindingPreventingResolver(safeUrlValidator, allowLoopbackForTesting))
                .followRedirect(false) // Prevent open-redirect SSRF bypasses
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
