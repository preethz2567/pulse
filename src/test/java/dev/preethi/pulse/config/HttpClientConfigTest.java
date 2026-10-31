package dev.preethi.pulse.config;

import com.sun.net.httpserver.HttpServer;
import dev.preethi.pulse.entity.CheckResult;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.repository.CheckResultRepository;
import dev.preethi.pulse.repository.IncidentRepository;
import dev.preethi.pulse.repository.MonitorRepository;
import dev.preethi.pulse.service.EmailAlertService;
import dev.preethi.pulse.service.MonitorHealthCheckService;
import dev.preethi.pulse.service.SsrfProtectionService;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpClientConfigTest {

    private static HttpServer mockServer;
    private static int serverPort;

    @BeforeAll
    static void startServer() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        mockServer.createContext("/redirect-to-metadata", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://169.254.169.254/latest/meta-data/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        mockServer.createContext("/redirect-to-internal", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://192.168.1.1/admin");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });

        mockServer.createContext("/ok", exchange -> {
            byte[] response = "OK".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
            exchange.close();
        });

        mockServer.start();
        serverPort = mockServer.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    void connectionManager_configuredWithCorrectPoolLimitsAndParameters() {
        HttpClientConfig config = new HttpClientConfig();
        SsrfProtectionService ssrfService = new SsrfProtectionService();
        SsrfSafeDnsResolver dnsResolver = new SsrfSafeDnsResolver(ssrfService);

        PoolingHttpClientConnectionManager cm = config.httpClientConnectionManager(dnsResolver);
        assertNotNull(cm);
        assertEquals(HttpClientConfig.MAX_CONN_TOTAL, cm.getMaxTotal());
        assertEquals(HttpClientConfig.MAX_CONN_PER_ROUTE, cm.getDefaultMaxPerRoute());
        cm.close();
    }

    @Test
    void client_usesCustomDnsResolverDuringConnection() throws Exception {
        HttpClientConfig config = new HttpClientConfig();
        DnsResolver mockResolver = mock(DnsResolver.class);
        when(mockResolver.resolve("custom-target.local"))
                .thenReturn(new InetAddress[]{InetAddress.getByName("127.0.0.1")});

        PoolingHttpClientConnectionManager cm = config.httpClientConnectionManager(mockResolver);
        CloseableHttpClient client = config.httpClient(cm);

        ClassicHttpRequest request = ClassicRequestBuilder.get("http://custom-target.local:" + serverPort + "/ok").build();

        int statusCode = client.execute(request, response -> response.getCode());
        assertEquals(200, statusCode);

        // Verify that the custom DNS resolver was indeed invoked by the connection manager
        verify(mockResolver).resolve("custom-target.local");

        client.close();
    }

    @Test
    void client_whenDnsResolverRejectsAddress_preventsConnectionAttempt() throws Exception {
        HttpClientConfig config = new HttpClientConfig();
        SsrfProtectionService ssrfService = new SsrfProtectionService();
        DnsResolver mockDelegate = mock(DnsResolver.class);
        // Simulate DNS rebinding returning a link-local metadata address at connection time
        when(mockDelegate.resolve("rebind.target.com"))
                .thenReturn(new InetAddress[]{InetAddress.getByName("169.254.169.254")});

        SsrfSafeDnsResolver safeResolver = new SsrfSafeDnsResolver(ssrfService, mockDelegate);
        PoolingHttpClientConnectionManager cm = config.httpClientConnectionManager(safeResolver);
        CloseableHttpClient client = config.httpClient(cm);

        ClassicHttpRequest request = ClassicRequestBuilder.get("http://rebind.target.com:" + serverPort + "/ok").build();

        // Must fail with UnknownHostException before any TCP socket connects
        UnknownHostException ex = assertThrows(UnknownHostException.class, () -> {
            client.execute(request, response -> response.getCode());
        });
        assertTrue(ex.getMessage().contains("SSRF Protection"));

        client.close();
    }

    @Test
    void httpClient_whenReceiving302ToCloudMetadata_doesNotFollowRedirect() throws Exception {
        HttpClientConfig config = new HttpClientConfig();
        // Use a test resolver that resolves localhost/127.0.0.1 for local test server
        DnsResolver testResolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
        PoolingHttpClientConnectionManager cm = config.httpClientConnectionManager(testResolver);
        CloseableHttpClient client = config.httpClient(cm);

        ClassicHttpRequest request = ClassicRequestBuilder.get("http://127.0.0.1:" + serverPort + "/redirect-to-metadata").build();

        client.execute(request, response -> {
            assertEquals(302, response.getCode());
            assertEquals("http://169.254.169.254/latest/meta-data/", response.getFirstHeader("Location").getValue());
            return null;
        });

        client.close();
    }

    @Test
    void httpClient_whenReceiving301ToInternalNetwork_doesNotFollowRedirect() throws Exception {
        HttpClientConfig config = new HttpClientConfig();
        DnsResolver testResolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
        PoolingHttpClientConnectionManager cm = config.httpClientConnectionManager(testResolver);
        CloseableHttpClient client = config.httpClient(cm);

        ClassicHttpRequest request = ClassicRequestBuilder.get("http://127.0.0.1:" + serverPort + "/redirect-to-internal").build();

        client.execute(request, response -> {
            assertEquals(301, response.getCode());
            assertEquals("http://192.168.1.1/admin", response.getFirstHeader("Location").getValue());
            return null;
        });

        client.close();
    }

    @Test
    void monitorHealthCheckService_withRealHttpClient_treatsRedirectAsFailureWithoutFollowingToMetadata() {
        HttpClientConfig config = new HttpClientConfig();
        DnsResolver testResolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
        PoolingHttpClientConnectionManager cm = config.httpClientConnectionManager(testResolver);
        CloseableHttpClient realClient = config.httpClient(cm);

        MonitorRepository monitorRepository = mock(MonitorRepository.class);
        CheckResultRepository checkResultRepository = mock(CheckResultRepository.class);
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        EmailAlertService emailAlertService = mock(EmailAlertService.class);
        SsrfProtectionService ssrfProtectionService = mock(SsrfProtectionService.class);

        MonitorHealthCheckService service = new MonitorHealthCheckService(
                monitorRepository,
                checkResultRepository,
                incidentRepository,
                emailAlertService,
                ssrfProtectionService,
                realClient
        );

        User user = new User("alice@example.com", "hash", "ROLE_USER");
        user.setId(1L);

        String testUrl = "http://127.0.0.1:" + serverPort + "/redirect-to-metadata";
        Monitor monitor = new Monitor(user, "Redirecting Monitor", testUrl);
        monitor.setId(20L);
        monitor.setExpectedStatusCode(200);

        doNothing().when(ssrfProtectionService).validateUrl(testUrl);
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(i -> i.getArgument(0));
        when(incidentRepository.findActiveIncidentByMonitorId(20L)).thenReturn(Optional.empty());

        CheckResult result = service.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertEquals(302, result.getStatusCode());
        assertTrue(result.getErrorMessage().contains("Expected status 200 but received 302"));
        assertEquals("DOWN", monitor.getStatus());

        verify(monitorRepository).save(monitor);
        verify(checkResultRepository).save(any(CheckResult.class));
        verify(incidentRepository).save(any());
        verify(emailAlertService).sendIncidentOpenedAlert(any());
    }

    @Test
    void client_tlsStrategy_enforcesStrictHostnameVerificationAndValidation() throws Exception {
        HttpClientConfig config = new HttpClientConfig();
        DnsResolver testResolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
        PoolingHttpClientConnectionManager cm = config.httpClientConnectionManager(testResolver);
        CloseableHttpClient client = config.httpClient(cm);

        // Attempting to connect via HTTPS to an endpoint that does not provide valid TLS handshake
        // must fail with an IOException (SSLHandshakeException/SocketException) rather than ignoring TLS errors
        ClassicHttpRequest request = ClassicRequestBuilder.get("https://127.0.0.1:" + serverPort + "/ok").build();

        assertThrows(IOException.class, () -> {
            client.execute(request, response -> response.getCode());
        });

        client.close();
    }
}
