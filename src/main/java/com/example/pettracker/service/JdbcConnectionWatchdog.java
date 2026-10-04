package com.example.pettracker.service;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

@Service
@Slf4j
@ConditionalOnProperty(name = "pettracker.jdbc-watchdog.enabled", havingValue = "true")
public class JdbcConnectionWatchdog {

    private final JdbcTemplate jdbcTemplate;
    private final String applicationName;
    private final long maxIdleTransactionSeconds;

    public JdbcConnectionWatchdog(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.datasource.hikari.data-source-properties.ApplicationName:pettracker-api}") String applicationName,
            @Value("${pettracker.jdbc-watchdog.max-idle-transaction:PT1M}") Duration maxIdleTransaction) {
        this.jdbcTemplate = jdbcTemplate;
        this.applicationName = applicationName;
        this.maxIdleTransactionSeconds = maxIdleTransaction.toSeconds();
    }

    @Scheduled(
            fixedDelayString = "${pettracker.jdbc-watchdog.check-delay-ms:30000}",
            initialDelayString = "${pettracker.jdbc-watchdog.initial-delay-ms:30000}"
    )
    public void closeStaleIdleTransactions() {
        List<StaleConnection> staleConnections = findStaleIdleTransactions();
        for (StaleConnection connection : staleConnections) {
            Boolean terminated = jdbcTemplate.queryForObject(
                    "SELECT pg_terminate_backend(?)",
                    Boolean.class,
                    connection.pid()
            );
            log.warn("JDBC watchdog terminated stale idle transaction pid={} ageSeconds={} state={} applicationName={} terminated={}",
                    connection.pid(), connection.ageSeconds(), connection.state(), applicationName, terminated);
        }
    }

    private List<StaleConnection> findStaleIdleTransactions() {
        return jdbcTemplate.query("""
                SELECT pid,
                       state,
                       EXTRACT(EPOCH FROM (now() - xact_start))::bigint AS age_seconds
                FROM pg_stat_activity
                WHERE datname = current_database()
                  AND usename = current_user
                  AND application_name = ?
                  AND pid <> pg_backend_pid()
                  AND state = 'idle in transaction'
                  AND xact_start IS NOT NULL
                  AND now() - xact_start > (? * INTERVAL '1 second')
                """,
                (rs, rowNum) -> new StaleConnection(
                        rs.getInt("pid"),
                        rs.getString("state"),
                        rs.getLong("age_seconds")
                ),
                applicationName,
                maxIdleTransactionSeconds
        );
    }

    private record StaleConnection(int pid, String state, long ageSeconds) {
    }
}
