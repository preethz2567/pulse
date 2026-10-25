package dev.preethi.pulse.repository;

import dev.preethi.pulse.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByMonitorId(Long monitorId);

    List<Incident> findByMonitorIdOrderByStartedAtDesc(Long monitorId);

    List<Incident> findByMonitorIdAndStatusOrderByStartedAtDesc(Long monitorId, String status);

    List<Incident> findByMonitorIdAndStatusInOrderByStartedAtDesc(Long monitorId, Collection<String> statuses);

    Optional<Incident> findFirstByMonitorIdAndStatusInOrderByStartedAtDesc(Long monitorId, Collection<String> statuses);

    @Query("SELECT i FROM Incident i WHERE i.monitor.id = :monitorId AND i.status IN ('ONGOING', 'ACKNOWLEDGED') ORDER BY i.startedAt DESC")
    Optional<Incident> findActiveIncidentByMonitorId(@Param("monitorId") Long monitorId);

    List<Incident> findByMonitorUserIdOrderByStartedAtDesc(Long userId);

    List<Incident> findByStatusInOrderByStartedAtDesc(Collection<String> statuses);
}
