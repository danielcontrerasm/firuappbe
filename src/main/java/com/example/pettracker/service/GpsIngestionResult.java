package com.example.pettracker.service;

/** Outcome used to decide whether a tracker should retry a GPS frame. */
public enum GpsIngestionResult {
    /** The location row was committed. Geofence evaluation has its own outcome. */
    PERSISTED,
    /** The frame from a known terminal was deliberately discarded by validation or ordering rules. */
    REJECTED,
    /** The terminal does not have a registered pet. */
    UNKNOWN_TERMINAL,
    /** Persistence could not be completed; a protocol acknowledgement must not claim success. */
    FAILED
}
