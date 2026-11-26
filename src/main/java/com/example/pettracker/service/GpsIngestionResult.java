package com.example.pettracker.service;

/** Outcome used to decide whether a tracker should retry a GPS frame. */
public enum GpsIngestionResult {
    /** The location row was committed and geofence evaluation was submitted. */
    PERSISTED,
    /** The frame was deliberately discarded by validation, admission, or ordering rules. */
    REJECTED,
    /** Persistence could not be completed; a protocol acknowledgement must not claim success. */
    FAILED
}
