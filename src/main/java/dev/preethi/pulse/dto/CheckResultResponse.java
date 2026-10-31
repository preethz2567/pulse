package dev.preethi.pulse.dto;

import dev.preethi.pulse.entity.CheckResult;

import java.time.Instant;

public record CheckResultResponse(
        Long id,
        Long monitorId,
        Integer statusCode,
        Integer responseTimeMs,
        Boolean isSuccess,
        String errorMessage,
        Instant checkedAt
) {
    public static CheckResultResponse from(CheckResult checkResult) {
        if (checkResult == null) {
            return null;
        }
        return new CheckResultResponse(
                checkResult.getId(),
                checkResult.getMonitor() != null ? checkResult.getMonitor().getId() : null,
                checkResult.getStatusCode(),
                checkResult.getResponseTimeMs(),
                checkResult.getIsSuccess(),
                checkResult.getErrorMessage(),
                checkResult.getCheckedAt()
        );
    }
}
