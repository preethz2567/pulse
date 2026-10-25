package dev.preethi.pulse.service;

import dev.preethi.pulse.entity.CheckResult;
import dev.preethi.pulse.entity.Incident;
import dev.preethi.pulse.entity.Monitor;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.exception.SsrfProtectionException;
import dev.preethi.pulse.repository.CheckResultRepository;
import dev.preethi.pulse.repository.IncidentRepository;
import dev.preethi.pulse.repository.MonitorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
    private SsrfProtectionService ssrfProtectionService;

    @Mock
    private HttpClient httpClient;

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

        @SuppressWarnings("unchecked")
        HttpResponse<Void> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

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

        @SuppressWarnings("unchecked")
        HttpResponse<Void> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(503);
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

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

        when(httpClient.send(any(HttpRequest.class), any()))
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

        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new HttpTimeoutException("request timed out"));

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

        verify(httpClient, never()).send(any(), any());
        verify(monitorRepository).save(monitor);
        verify(checkResultRepository).save(any(CheckResult.class));
        verify(incidentRepository).save(any(Incident.class));
    }

    @Test
    void performCheck_updatesLastCheckedAtTimestamp() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        doNothing().when(ssrfProtectionService).validateUrl(any());

        @SuppressWarnings("unchecked")
        HttpResponse<Void> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

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
        verify(httpClient, never()).send(any(), any());
        verify(ssrfProtectionService, never()).validateUrl(any());
        verify(checkResultRepository, never()).save(any());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void performCheck_downWithNoActiveIncident_createsOneOngoingIncident() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());

        @SuppressWarnings("unchecked")
        HttpResponse<Void> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

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
    }

    @Test
    void performCheck_downWithExistingActiveIncident_doesNotCreateAnotherIncident() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());

        @SuppressWarnings("unchecked")
        HttpResponse<Void> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Incident existingIncident = new Incident(monitor, Incident.STATUS_ONGOING, "Previous 500 error", Instant.now().minusSeconds(120));
        when(incidentRepository.findActiveIncidentByMonitorId(10L)).thenReturn(Optional.of(existingIncident));

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertFalse(result.getIsSuccess());
        assertEquals("DOWN", monitor.getStatus());

        // Should NOT create or save any incident
        verify(incidentRepository, never()).save(any(Incident.class));
    }

    @Test
    void performCheck_upWithActiveOngoingIncident_resolvesIt() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());

        @SuppressWarnings("unchecked")
        HttpResponse<Void> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

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
    }

    @Test
    void performCheck_upWithActiveAcknowledgedIncident_resolvesIt() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());

        @SuppressWarnings("unchecked")
        HttpResponse<Void> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

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
    }

    @Test
    void performCheck_upWithNoActiveIncident_doesNotCreateOrSaveIncident() throws Exception {
        doNothing().when(ssrfProtectionService).validateUrl(monitor.getUrl());

        @SuppressWarnings("unchecked")
        HttpResponse<Void> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

        when(checkResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(incidentRepository.findActiveIncidentByMonitorId(10L)).thenReturn(Optional.empty());

        CheckResult result = healthCheckService.performCheck(monitor);

        assertNotNull(result);
        assertTrue(result.getIsSuccess());
        assertEquals("UP", monitor.getStatus());

        verify(incidentRepository, never()).save(any(Incident.class));
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

        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new HttpTimeoutException("Connection timed out after 10000ms"));

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

        when(httpClient.send(any(HttpRequest.class), any()))
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
}
