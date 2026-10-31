package dev.preethi.pulse.service;

import dev.preethi.pulse.entity.CheckResult;
import dev.preethi.pulse.entity.Incident;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.exception.SsrfProtectionException;
import dev.preethi.pulse.repository.CheckResultRepository;
import dev.preethi.pulse.repository.IncidentRepository;
import dev.preethi.pulse.repository.MonitorRepository;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorHealthCheckServiceTest {

    @Mock
    private MonitorRepository monitorRepository;

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private EmailAlertService emailAlertService;

    @Mock
    private SsrfProtectionService ssrfProtectionService;

    @Mock
    private CloseableHttpClient httpClient;

    @InjectMocks
    private MonitorHealthCheckService healthCheckService;

    private User user;
    private Monitor monitor;

    @BeforeEach
    void setUp() {
        user = new User("alice@example.com", "hash", "ROLE_USER");
        user.setId(1L);

        monitor = new Monitor(user, "Test API", "https://api.example.com/health");
        monitor.setId(10L);
        monitor.setHttpMethod("GET");
        monitor.setIntervalSeconds(60);
        monitor.setTimeoutSeconds(10);
        monitor.setExpectedStatusCode(200);
        monitor.setStatus("PENDING");
        monitor.setIsActive(true);
    }

    @Test
    void performCheck_expectedSuccessfulResponse_updatesStatusToUpAndRecordsSuccess() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl("https://api.example.com/health");
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any())).thenReturn(200);
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(i -> i.getArgument(0));

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertTrue(result.getIsSuccess());
        assertEquals(200, result.getStatusCode());
        assertNull(result.getErrorMessage());
        assertNotNull(result.getResponseTimeMs());

        assertEquals("UP", monitor.getStatus());
        assertNotNull(monitor.getLastCheckedAt());

        verify(monitorRepository).save(monitor);
        verify(checkResultRepository).save(any(CheckResult.class));
        verify(incidentRepository, never()).save(any(Incident.class));
    }

    @Test
    void performCheck_unexpectedHttpStatus_updatesStatusToDownAndRecordsFailure() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl("https://api.example.com/health");
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any())).thenReturn(503);
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(i -> i.getArgument(0));

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertEquals(503, result.getStatusCode());
        assertTrue(result.getErrorMessage().contains("Expected status 200 but received 503"));

        assertEquals("DOWN", monitor.getStatus());
        assertNotNull(monitor.getLastCheckedAt());

        verify(monitorRepository).save(monitor);
        verify(checkResultRepository).save(any(CheckResult.class));
        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void performCheck_connectionFailure_updatesStatusToDownAndRecordsFailure() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl("https://api.example.com/health");
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any()))
                .thenThrow(new IOException("Connection refused by target host"));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(i -> i.getArgument(0));

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertNull(result.getStatusCode());
        assertTrue(result.getErrorMessage().contains("Connection error"));

        assertEquals("DOWN", monitor.getStatus());
        assertNotNull(monitor.getLastCheckedAt());

        verify(monitorRepository).save(monitor);
        verify(checkResultRepository).save(any(CheckResult.class));
        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void performCheck_timeout_updatesStatusToDownAndRecordsFailure() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl("https://api.example.com/health");
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any()))
                .thenThrow(new SocketTimeoutException("request timed out"));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(i -> i.getArgument(0));

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertNull(result.getStatusCode());
        assertTrue(result.getErrorMessage().contains("timed out"));

        assertEquals("DOWN", monitor.getStatus());
        assertNotNull(monitor.getLastCheckedAt());

        verify(monitorRepository).save(monitor);
        verify(checkResultRepository).save(any(CheckResult.class));
        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void performCheck_ssrfPrivateAddressRejection_updatesStatusToDownAndDoesNotMakeHttpRequest() throws Exception {
        doThrow(new SsrfProtectionException("Access to loopback address is prohibited: 127.0.0.1"))
                .when(ssrfProtectionService).validateUrl("https://api.example.com/health");
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(i -> i.getArgument(0));

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertNull(result.getStatusCode());
        assertTrue(result.getErrorMessage().contains("SSRF Protection"));

        assertEquals("DOWN", monitor.getStatus());
        assertNotNull(monitor.getLastCheckedAt());

        verify(httpClient, never()).execute(any(ClassicHttpRequest.class), any(), any());
        verify(monitorRepository).save(monitor);
        verify(checkResultRepository).save(any(CheckResult.class));
        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void performCheck_dnsRebindingBlockedBySafeResolver_updatesStatusToDownAndRecordsSsrfFailure() throws Exception {
        // Pre-validation passes
        doNothing().when(ssrfProtectionService).validateUrl("https://api.example.com/health");
        // Connection-time resolution throws SSRF protection UnknownHostException
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any()))
                .thenThrow(new UnknownHostException("SSRF Protection: Access to cloud metadata address is prohibited: 169.254.169.254"));
        when(checkResultRepository.save(any(CheckResult.class))).thenAnswer(i -> i.getArgument(0));

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertNull(result.getStatusCode());
        assertTrue(result.getErrorMessage().contains("SSRF Protection: Access to cloud metadata address is prohibited: 169.254.169.254"));
        assertEquals("DOWN", monitor.getStatus());

        verify(monitorRepository).save(monitor);
        verify(checkResultRepository).save(any(CheckResult.class));
        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void performCheck_updatesLastCheckedAtTimestamp() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        doNothing().when(ssrfProtectionService).validateUrl(any());
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any())).thenReturn(200);
        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        healthCheckService.performCheck(monitor);

        assertNotNull(monitor.getLastCheckedAt());
        assertTrue(monitor.getLastCheckedAt().isAfter(before));

        ArgumentCaptor<Monitor> captor = ArgumentCaptor.forClass(Monitor.class);
        verify(monitorRepository).save(captor.capture());
        assertEquals(monitor.getLastCheckedAt(), captor.getValue().getLastCheckedAt());
        verify(incidentRepository, never()).save(any(Incident.class));
    }

    @Test
    void checkMonitor_inactiveMonitor_isNotChecked() throws Exception {
        monitor.setIsActive(false);
        when(monitorRepository.findById(10L)).thenReturn(Optional.of(monitor));

        CheckResult result = healthCheckService.checkMonitor(10L);

        assertNull(result);
        verify(httpClient, never()).execute(any(ClassicHttpRequest.class), any(), any());
        verify(ssrfProtectionService, never()).validateUrl(any());
        verify(checkResultRepository, never()).save(any());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void performCheck_downWithNoActiveIncident_createsOneOngoingIncident() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any())).thenReturn(500);
        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(incidentRepository.findActiveIncidentByMonitorId(10L)).thenReturn(Optional.empty());

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertEquals("DOWN", monitor.getStatus());

        ArgumentCaptor<Incident> incidentCaptor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(incidentCaptor.capture());

        Incident created = incidentCaptor.getValue();
        assertEquals(Incident.STATUS_ONGOING, created.getStatus());
        assertEquals(monitor, created.getMonitor());
        assertNotNull(created.getStartedAt());
        assertNull(created.getResolvedAt());
        assertTrue(created.getCause().contains("Expected status 200 but received 500"));

        verify(emailAlertService).sendIncidentOpenedAlert(created);
    }

    @Test
    void performCheck_downWithExistingActiveIncident_doesNotCreateAnotherIncident() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any())).thenReturn(500);
        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Incident existingIncident = new Incident(monitor, Incident.STATUS_ONGOING, "Previous 500 error", Instant.now().minusSeconds(120));
        when(incidentRepository.findActiveIncidentByMonitorId(10L)).thenReturn(Optional.of(existingIncident));

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertEquals("DOWN", monitor.getStatus());

        verify(incidentRepository, never()).save(any(Incident.class));
        verify(emailAlertService, never()).sendIncidentOpenedAlert(any());
    }

    @Test
    void performCheck_upWithActiveOngoingIncident_resolvesIt() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any())).thenReturn(200);
        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Instant incidentStartTime = Instant.now().minusSeconds(300);
        Incident activeIncident = new Incident(monitor, Incident.STATUS_ONGOING, "Prior timeout", incidentStartTime);
        when(incidentRepository.findActiveIncidentByMonitorId(10L)).thenReturn(Optional.of(activeIncident));

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertTrue(result.getIsSuccess());
        assertEquals("UP", monitor.getStatus());

        verify(incidentRepository).save(activeIncident);
        assertEquals(Incident.STATUS_RESOLVED, activeIncident.getStatus());
        assertNotNull(activeIncident.getResolvedAt());
        assertTrue(activeIncident.getResolvedAt().isAfter(incidentStartTime));

        verify(emailAlertService).sendIncidentResolvedAlert(activeIncident);
    }

    @Test
    void performCheck_upWithActiveAcknowledgedIncident_resolvesIt() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any())).thenReturn(200);
        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Instant incidentStartTime = Instant.now().minusSeconds(600);
        Incident acknowledgedIncident = new Incident(monitor, Incident.STATUS_ACKNOWLEDGED, "Service unavailable", incidentStartTime);
        acknowledgedIncident.setAcknowledgedAt(Instant.now().minusSeconds(300));
        when(incidentRepository.findActiveIncidentByMonitorId(10L)).thenReturn(Optional.of(acknowledgedIncident));

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertTrue(result.getIsSuccess());
        assertEquals("UP", monitor.getStatus());

        verify(incidentRepository).save(acknowledgedIncident);
        assertEquals(Incident.STATUS_RESOLVED, acknowledgedIncident.getStatus());
        assertNotNull(acknowledgedIncident.getResolvedAt());
        assertNotNull(acknowledgedIncident.getAcknowledgedAt());

        verify(emailAlertService).sendIncidentResolvedAlert(acknowledgedIncident);
    }

    @Test
    void performCheck_upWithNoActiveIncident_doesNotCreateOrSaveIncident() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any())).thenReturn(200);
        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(incidentRepository.findActiveIncidentByMonitorId(10L)).thenReturn(Optional.empty());

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertTrue(result.getIsSuccess());
        assertEquals("UP", monitor.getStatus());

        verify(incidentRepository, never()).save(any(Incident.class));
        verify(emailAlertService, never()).sendIncidentResolvedAlert(any());
    }

    @Test
    void performCheck_ssrfFailure_createsDownResultAndIncidentWithCause() throws Exception {
        doThrow(new SsrfProtectionException("Access to loopback address is prohibited: 127.0.0.1"))
                .when(ssrfProtectionService).validateUrl(monitor.getUrl());
        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(incidentRepository.findActiveIncidentByMonitorId(10L)).thenReturn(Optional.empty());

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertEquals("DOWN", monitor.getStatus());

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(captor.capture());
        Incident incident = captor.getValue();
        assertEquals(Incident.STATUS_ONGOING, incident.getStatus());
        assertEquals(monitor, incident.getMonitor());
        assertNotNull(incident.getStartedAt());
        assertTrue(incident.getCause().contains("SSRF Protection: Access to loopback address is prohibited: 127.0.0.1"));
    }

    @Test
    void performCheck_timeoutFailure_createsDownResultAndIncidentWithCause() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any()))
                .thenThrow(new SocketTimeoutException("Connection timed out after 10000ms"));
        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(incidentRepository.findActiveIncidentByMonitorId(10L)).thenReturn(Optional.empty());

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertEquals("DOWN", monitor.getStatus());

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(captor.capture());
        Incident incident = captor.getValue();
        assertEquals(Incident.STATUS_ONGOING, incident.getStatus());
        assertEquals(monitor, incident.getMonitor());
        assertNotNull(incident.getStartedAt());
        assertTrue(incident.getCause().contains("Request timed out: Connection timed out after 10000ms"));
    }

    @Test
    void performCheck_connectionFailure_createsDownResultAndIncidentWithCause() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any()))
                .thenThrow(new IOException("Connection refused: connect"));
        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(incidentRepository.findActiveIncidentByMonitorId(10L)).thenReturn(Optional.empty());

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertEquals("DOWN", monitor.getStatus());

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(captor.capture());
        Incident incident = captor.getValue();
        assertEquals(Incident.STATUS_ONGOING, incident.getStatus());
        assertEquals(monitor, incident.getMonitor());
        assertNotNull(incident.getStartedAt());
        assertTrue(incident.getCause().contains("Connection error: Connection refused: connect"));
    }

    @Test
    void performCheck_whenEmailAlertServiceThrowsException_checkResultAndIncidentStillPersistSuccessfully() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any())).thenReturn(503);
        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(incidentRepository.findActiveIncidentByMonitorId(10L)).thenReturn(Optional.empty());

        doThrow(new RuntimeException("Mailpit connection failed"))
                .when(emailAlertService).sendIncidentOpenedAlert(any());

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertEquals(503, result.getStatusCode());
        assertEquals("DOWN", monitor.getStatus());

        verify(monitorRepository).save(monitor);
        verify(checkResultRepository).save(any(CheckResult.class));
        verify(incidentRepository).save(any(Incident.class));
        verify(emailAlertService).sendIncidentOpenedAlert(any());
    }

    @Test
    void performCheck_whenTargetReturns302Redirect_treatedAsFailureWhenExpectedIs200() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any())).thenReturn(302);
        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(incidentRepository.findActiveIncidentByMonitorId(10L)).thenReturn(Optional.empty());

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertEquals(302, result.getStatusCode());
        assertEquals("DOWN", monitor.getStatus());
        assertTrue(result.getErrorMessage().contains("Expected status 200 but received 302"));

        verify(monitorRepository).save(monitor);
        verify(checkResultRepository).save(any(CheckResult.class));
        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void performCheck_whenTargetReturns301AndExpectedIs301_treatedAsSuccess() throws Exception {
        monitor.setExpectedStatusCode(301);
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());
        when(httpClient.execute(any(ClassicHttpRequest.class), any(HttpClientContext.class), any())).thenReturn(301);
        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertTrue(result.getIsSuccess());
        assertEquals(301, result.getStatusCode());
        assertEquals("UP", monitor.getStatus());
        assertNull(result.getErrorMessage());

        verify(monitorRepository).save(monitor);
        verify(checkResultRepository).save(any(CheckResult.class));
        verify(incidentRepository, never()).save(any());
    }
}
