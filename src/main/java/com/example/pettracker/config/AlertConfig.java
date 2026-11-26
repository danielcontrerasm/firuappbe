package com.example.pettracker.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AlertConfig {
    @Value("${gps.ingestion.core-threads:2}")
    private int gpsCoreThreads;

    @Value("${gps.ingestion.max-threads:4}")
    private int gpsMaxThreads;

    @Value("${gps.ingestion.queue-capacity:500}")
    private int gpsQueueCapacity;

    @Value("${gps.lookup.core-threads:2}")
    private int gpsLookupCoreThreads;

    @Value("${gps.lookup.max-threads:4}")
    private int gpsLookupMaxThreads;

    @Value("${gps.lookup.queue-capacity:1000}")
    private int gpsLookupQueueCapacity;

    @Bean("alertExecutor")
    public Executor alertExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("AlertExec-");
        executor.initialize();
        return executor;
    }
    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Notify-");
        executor.initialize();
        return executor;
    }

    @Bean("geofenceExecutor")
    public Executor geofenceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() + 2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("GeoFence-");
        executor.initialize();
        return executor;
    }

    @Bean("gpsExecutor")
    public Executor gpsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(gpsCoreThreads);
        executor.setMaxPoolSize(gpsMaxThreads);
        executor.setQueueCapacity(gpsQueueCapacity);
        executor.setThreadNamePrefix("GpsIngest-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("gpsTerminalLookupExecutor")
    public Executor gpsTerminalLookupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(gpsLookupCoreThreads);
        executor.setMaxPoolSize(gpsLookupMaxThreads);
        executor.setQueueCapacity(gpsLookupQueueCapacity);
        executor.setThreadNamePrefix("GpsTerminalLookup-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

}
