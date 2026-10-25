package dev.preethi.pulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "incidents")
public class Incident {

    public static final String STATUS_ONGOING = "ONGOING";
    public static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    public static final String STATUS_RESOLVED = "RESOLVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monitor_id", nullable = false)
    private Monitor monitor;

    @Column(name = "status", nullable = false, length = 50)
    private String status = STATUS_ONGOING;

    @Column(name = "cause", columnDefinition = "TEXT")
    private String cause;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Incident() {
        // Required by JPA
    }

    public Incident(Monitor monitor, String cause) {
        this.monitor = monitor;
        this.cause = cause;
        this.status = STATUS_ONGOING;
        this.startedAt = Instant.now();
    }

    public Incident(Monitor monitor, String status, String cause, Instant startedAt) {
        this.monitor = monitor;
        this.status = (status != null) ? status : STATUS_ONGOING;
        this.cause = cause;
        this.startedAt = (startedAt != null) ? startedAt : Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        if (this.status == null) {
            this.status = STATUS_ONGOING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isOpen() {
        return STATUS_ONGOING.equals(this.status) || STATUS_ACKNOWLEDGED.equals(this.status);
    }

    public boolean isResolved() {
        return STATUS_RESOLVED.equals(this.status);
    }

    public void acknowledge(Instant acknowledgedAt) {
        this.status = STATUS_ACKNOWLEDGED;
        this.acknowledgedAt = (acknowledgedAt != null) ? acknowledgedAt : Instant.now();
    }

    public void resolve(Instant resolvedAt) {
        this.status = STATUS_RESOLVED;
        this.resolvedAt = (resolvedAt != null) ? resolvedAt : Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Monitor getMonitor() {
        return monitor;
    }

    public void setMonitor(Monitor monitor) {
        this.monitor = monitor;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCause() {
        return cause;
    }

    public void setCause(String cause) {
        this.cause = cause;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Incident incident = (Incident) o;
        return Objects.equals(id, incident.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Incident{" +
                "id=" + id +
                ", monitorId=" + (monitor != null ? monitor.getId() : null) +
                ", status='" + status + '\'' +
                ", cause='" + cause + '\'' +
                ", startedAt=" + startedAt +
                ", acknowledgedAt=" + acknowledgedAt +
                ", resolvedAt=" + resolvedAt +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
