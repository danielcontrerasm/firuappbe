package com.example.pettracker.dto;

import java.time.Instant;

public record AdminMetricsDto(
        Instant collectedAt,
        HttpMetrics http,
        DatabaseMetrics database,
        PoolMetrics pool,
        GpsMetrics gps
) {

    public record HttpMetrics(
            long totalRequests,
            long inFlightRequests,
            long successfulResponses,
            long clientErrorResponses,
            long serverErrorResponses
    ) {}

    public record DatabaseMetrics(
            long committedTransactions,
            long rolledBackTransactions,
            long openConnections,
            long appOpenConnections,
            long activeConnections,
            long idleConnections,
            long idleInTransactionConnections
    ) {}

    public record PoolMetrics(
            int maxPoolSize,
            int activeConnections,
            int idleConnections,
            int totalConnections,
            int threadsAwaitingConnection
    ) {}

    public record GpsMetrics(
            long droppedTasks
    ) {}
}
