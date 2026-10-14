package com.zam.sunat_service.response;

public record ClientStatsResponse(
        long total,
        long active,
        long inactive
) {
}
