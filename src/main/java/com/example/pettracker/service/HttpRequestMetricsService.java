package com.example.pettracker.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

@Service
public class HttpRequestMetricsService extends OncePerRequestFilter {

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong inFlightRequests = new AtomicLong();
    private final AtomicLong successfulResponses = new AtomicLong();
    private final AtomicLong clientErrorResponses = new AtomicLong();
    private final AtomicLong serverErrorResponses = new AtomicLong();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        totalRequests.incrementAndGet();
        inFlightRequests.incrementAndGet();
        try {
            filterChain.doFilter(request, response);
        } finally {
            inFlightRequests.decrementAndGet();
            recordStatus(response.getStatus());
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(
                totalRequests.get(),
                inFlightRequests.get(),
                successfulResponses.get(),
                clientErrorResponses.get(),
                serverErrorResponses.get()
        );
    }

    private void recordStatus(int status) {
        if (status >= 500) {
            serverErrorResponses.incrementAndGet();
        } else if (status >= 400) {
            clientErrorResponses.incrementAndGet();
        } else if (status >= 200 && status < 400) {
            successfulResponses.incrementAndGet();
        }
    }

    public record Snapshot(
            long totalRequests,
            long inFlightRequests,
            long successfulResponses,
            long clientErrorResponses,
            long serverErrorResponses
    ) {}
}
