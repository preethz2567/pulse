package dev.preethi.pulse.repository;

import dev.preethi.pulse.entity.CheckResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

    List<CheckResult> findByMonitorIdOrderByCheckedAtDesc(Long monitorId);
}
