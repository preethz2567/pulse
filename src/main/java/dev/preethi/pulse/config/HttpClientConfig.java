package dev.preethi.pulse.config;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {

    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    public static final int DEFAULT_SOCKET_TIMEOUT_SECONDS = 30;
    public static final int MAX_CONN_TOTAL = 50;
    public static final int MAX_CONN_PER_ROUTE = 10;
    public static final int CONN_TIME_TO_LIVE_SECONDS = 30;

    @Bean(destroyMethod = "close")
    public PoolingHttpClientConnectionManager httpClientConnectionManager(DnsResolver ssrfSafeDnsResolver) {
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(ssrfSafeDnsResolver)
                .setMaxConnTotal(MAX_CONN_TOTAL)
                .setMaxConnPerRoute(MAX_CONN_PER_ROUTE)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS))
                        .build())
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(DEFAULT_SOCKET_TIMEOUT_SECONDS))
                        .build())
                .setConnectionTimeToLive(TimeValue.ofSeconds(CONN_TIME_TO_LIVE_SECONDS))
                .setValidateAfterInactivity(TimeValue.ofSeconds(5))
                .build();
    }

    @Bean(destroyMethod = "close")
    public CloseableHttpClient httpClient(PoolingHttpClientConnectionManager connectionManager) {
        RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS))
                .setResponseTimeout(Timeout.ofSeconds(DEFAULT_SOCKET_TIMEOUT_SECONDS))
                .setConnectionRequestTimeout(Timeout.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS))
                .setRedirectsEnabled(false)
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(defaultRequestConfig)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(10))
                .setUserAgent("Pulse-Monitor/1.0")
                .build();
    }
}
