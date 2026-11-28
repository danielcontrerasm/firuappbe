package com.example.pettracker.service;

import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.gps.GpsMessageHandler;
import com.example.pettracker.gps.GpsProtocolDetector;
import com.example.pettracker.gps.Jt808TimeZones;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.PetRepository;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;

import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GpsGeofenceRetryTest {
    @Test
    void savedTrackerPointGetsPositiveAckAndGeofenceRetriesWhenExecutorRecovers() {
        String terminal = "013345678906";
        PetRepository pets = mock(PetRepository.class);
        when(pets.findByTerminalId(terminal)).thenReturn(Optional.of(Pet.builder().id(1L).name("Luna").build()));
        LocationRepository locations = mock(LocationRepository.class);
        when(locations.save(any(Location.class))).thenAnswer(call -> call.getArgument(0));
        GeofencingService geofences = mock(GeofencingService.class);
        doThrow(new TaskRejectedException("geofence executor is full"))
                .doThrow(new TaskRejectedException("geofence executor is still full"))
                .doNothing()
                .when(geofences).checkGeofence(any(Location.class));

        AppTimeService clock = new AppTimeService("America/Bogota");
        // This fixture uses a fixed device timestamp.
        Duration fixtureWindow = Duration.ofDays(36_500);
        GpsIngestionService ingestion = new GpsIngestionService(locations, pets, geofences,
                Runnable::run, Runnable::run, clock, Caffeine.newBuilder().build(), Duration.ofDays(1),
                Duration.ofMinutes(1), fixtureWindow, fixtureWindow, false, 10, 1, 30, 500, 4);
        EmbeddedChannel channel = new EmbeddedChannel(new GpsProtocolDetector(
                new GpsMessageHandler(ingestion, clock, new Jt808TimeZones()), ingestion));
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(HexFormat.of().parseHex(
                    "7e0200001f0133456789060012000000000000000a0047e24d046a4204000000000000261105123456e10157af7e")));
            ByteBuf ack = channel.readOutbound();
            assertThat(ack).isNotNull();
            try {
                assertThat(ByteBufUtil.hexDump(ack)).isEqualTo("7e80010005013345678906000000120200000b7e");
            } finally {
                ack.release();
            }
            assertThat(ingestion.getPersistedLocations()).isEqualTo(1);
            assertThat(ingestion.getFailedTasks()).isZero();

            ArgumentCaptor<Location> saved = ArgumentCaptor.forClass(Location.class);
            verify(locations).save(saved.capture());
            verify(geofences).checkGeofence(saved.getValue());
            ingestion.retryPendingGeofences();
            verify(geofences, times(2)).checkGeofence(saved.getValue());
            ingestion.retryPendingGeofences();
            ingestion.retryPendingGeofences();
            verify(geofences, times(3)).checkGeofence(saved.getValue());
            verify(locations).save(any(Location.class));
            assertThat((Object) channel.readOutbound()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
