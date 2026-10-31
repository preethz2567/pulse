package dev.preethi.pulse.repository;

import dev.preethi.pulse.entity.CheckResult;
import dev.preethi.pulse.dto.UptimeStatsProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

    List<CheckResult> findByMonitorIdOrderByCheckedAtDesc(Long monitorId);

    List<CheckResult> findByMonitorId(Long monitorId, Pageable pageable);

    @Query("SELECT COUNT(cr) AS totalChecks, " +
           "COALESCE(SUM(CASE WHEN cr.isSuccess = true THEN 1L ELSE 0L END), 0L) AS successfulChecks " +
           "FROM CheckResult cr " +
           "WHERE cr.monitor.id = :monitorId AND cr.checkedAt >= :since AND cr.checkedAt <= :until")
    UptimeStatsProjection findUptimeStats(
            @Param("monitorId") Long monitorId,
            @Param("since") Instant since,
            @Param("until") Instant until
    );
}
