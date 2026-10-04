package com.example.pettracker.service;

import com.example.pettracker.dto.AdminMetricsDto;
import com.example.pettracker.dto.AdminMetricsDto.DatabaseMetrics;
import com.example.pettracker.dto.AdminMetricsDto.GpsMetrics;
import com.example.pettracker.dto.AdminMetricsDto.HttpMetrics;
import com.example.pettracker.dto.AdminMetricsDto.PoolMetrics;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminMetricsService {

    private final JdbcTemplate jdbcTemplate;
    private final HikariDataSource hikariDataSource;
    private final HttpRequestMetricsService httpRequestMetricsService;
    private final GpsIngestionService gpsIngestionService;
    private final String applicationName;

    public AdminMetricsService(
            JdbcTemplate jdbcTemplate,
            HikariDataSource hikariDataSource,
            HttpRequestMetricsService httpRequestMetricsService,
            GpsIngestionService gpsIngestionService,
            @Value("${spring.datasource.hikari.data-source-properties.ApplicationName:pettracker-api}") String applicationName) {
        this.jdbcTemplate = jdbcTemplate;
        this.hikariDataSource = hikariDataSource;
        this.httpRequestMetricsService = httpRequestMetricsService;
        this.gpsIngestionService = gpsIngestionService;
        this.applicationName = applicationName;
    }

    public AdminMetricsDto getMetrics() {
        HttpRequestMetricsService.Snapshot http = httpRequestMetricsService.snapshot();
        DatabaseMetrics databaseMetrics = getDatabaseMetrics();
        PoolMetrics poolMetrics = getPoolMetrics();

        return new AdminMetricsDto(
                Instant.now(),
                new HttpMetrics(
                        http.totalRequests(),
                        http.inFlightRequests(),
                        http.successfulResponses(),
                        http.clientErrorResponses(),
                        http.serverErrorResponses()
                ),
                databaseMetrics,
                poolMetrics,
                new GpsMetrics(gpsIngestionService.getDroppedTasks())
        );
    }

    private DatabaseMetrics getDatabaseMetrics() {
        TransactionTotals transactions = jdbcTemplate.queryForObject("""
                SELECT xact_commit, xact_rollback
                FROM pg_stat_database
                WHERE datname = current_database()
                """,
                (rs, rowNum) -> new TransactionTotals(
                        rs.getLong("xact_commit"),
                        rs.getLong("xact_rollback")
                )
        );

        ConnectionTotals connections = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS open_connections,
                       COUNT(*) FILTER (WHERE application_name = ?) AS app_open_connections,
                       COUNT(*) FILTER (WHERE state = 'active') AS active_connections,
                       COUNT(*) FILTER (WHERE state = 'idle') AS idle_connections,
                       COUNT(*) FILTER (WHERE state = 'idle in transaction') AS idle_in_transaction_connections
                FROM pg_stat_activity
                WHERE datname = current_database()
                """,
                (rs, rowNum) -> new ConnectionTotals(
                        rs.getLong("open_connections"),
                        rs.getLong("app_open_connections"),
                        rs.getLong("active_connections"),
                        rs.getLong("idle_connections"),
                        rs.getLong("idle_in_transaction_connections")
                ),
                applicationName
        );

        return new DatabaseMetrics(
                transactions == null ? 0 : transactions.committedTransactions(),
                transactions == null ? 0 : transactions.rolledBackTransactions(),
                connections == null ? 0 : connections.openConnections(),
                connections == null ? 0 : connections.appOpenConnections(),
                connections == null ? 0 : connections.activeConnections(),
                connections == null ? 0 : connections.idleConnections(),
                connections == null ? 0 : connections.idleInTransactionConnections()
        );
    }

    private PoolMetrics getPoolMetrics() {
        HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
        if (pool == null) {
            return new PoolMetrics(hikariDataSource.getMaximumPoolSize(), 0, 0, 0, 0);
        }
        return new PoolMetrics(
                hikariDataSource.getMaximumPoolSize(),
                pool.getActiveConnections(),
                pool.getIdleConnections(),
                pool.getTotalConnections(),
                pool.getThreadsAwaitingConnection()
        );
    }

    private record TransactionTotals(long committedTransactions, long rolledBackTransactions) {
    }

    private record ConnectionTotals(
            long openConnections,
            long appOpenConnections,
            long activeConnections,
            long idleConnections,
            long idleInTransactionConnections
    ) {
    }
}
