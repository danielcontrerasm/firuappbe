package com.example.pettracker.topin;

import java.time.Instant;

/**
 * Event for an accepted device. Future timestamps remain visible but are marked ineligible
 * for a live location. Store receivedAt separately; never silently replace deviceTime.
 */
public record TopinDeviceEvent(String imei, Instant receivedAt, TopinMessage message,
                               boolean eligibleForLiveLocation, String qualityNote) { }
