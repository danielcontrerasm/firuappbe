package com.example.pettracker.service;

import com.example.pettracker.repository.LocationRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class LocationHistoryCleanup {

    private LocationRepository locationRepository;
    // Create a thread pool for cleanup jobs
    private final ThreadPoolExecutor cleanupExecutor = new ThreadPoolExecutor(
            4,                      // core threads
            8,                      // max threads
            30, TimeUnit.SECONDS,   // keep-alive for idle threads
            new LinkedBlockingQueue<>(20), // queue capacity
            new ThreadFactory() {   // naming threads for clarity
                private int count = 1;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Cleanup-Worker-" + count++);
                    t.setDaemon(false);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // fallback if full
    );

    // Simulated list of pet IDs (in real life, you'd query DB)
    private final List<Integer> petIds = IntStream.rangeClosed(1, 10)
                                                  .boxed()
                                                  .collect(Collectors.toList());

    @Scheduled(fixedDelay = 60000) // every minute
    public void cleanupOldLocations() {
        System.out.println(Thread.currentThread().getName() + " starting cleanup...");

        // Split work: one task per pet
        for (Integer petId : petIds) {
            cleanupExecutor.submit(() -> {
                System.out.println(Thread.currentThread().getName() +
                        " deleting old locations for pet " + petId);
                try {
                    // Simulate deletion
                    Thread.sleep(1000);
                    // 👉 Here you’d call repository.deleteOldRecords(petId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    // Graceful shutdown when Spring stops
    @PreDestroy
    public void shutdown() {
        System.out.println("Shutting down cleanup executor...");
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
        }
    }


   /* public void deleteOldRecords(Integer petId) {
        locationRepository.deleteByPetIdAndTimestampBefore(
                petId,
                LocalDateTime.now().minusDays(30)
        );
    }*/
}
