package dev.preethi.pulse.repository;

import dev.preethi.pulse.entity.AlertChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertChannelRepository extends JpaRepository<AlertChannel, Long> {

    List<AlertChannel> findByUserId(Long userId);

    List<AlertChannel> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<AlertChannel> findByIdAndUserId(Long id, Long userId);

    List<AlertChannel> findByMonitorId(Long monitorId);

    List<AlertChannel> findByMonitorIdAndIsEnabledTrue(Long monitorId);

    List<AlertChannel> findByMonitorIdAndTypeIgnoreCaseAndIsEnabledTrue(Long monitorId, String type);

    List<AlertChannel> findByMonitorIdAndUserId(Long monitorId, Long userId);

    List<AlertChannel> findByMonitorIdAndUserIdOrderByCreatedAtDesc(Long monitorId, Long userId);
}
