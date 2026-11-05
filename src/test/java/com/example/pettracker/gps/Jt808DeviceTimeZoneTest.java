package com.example.pettracker.gps;

import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.service.AppTimeService;
import com.example.pettracker.service.GeofencingService;
import com.example.pettracker.service.GpsIngestionService;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Jt808DeviceTimeZoneTest {
    // Both packets report 2026-11-05 00:30:00, without an explicit timezone.
    private static final String HW10_PACKET =
            "7e0200001f0141662727820012000000000000000a0047e24d046a4204000000000000261105003000e10157d47e";
    private static final String LEGACY_PACKET =
            "7e0200001f0133456789060012000000000000000a0047e24d046a4204000000000000261105003000e10157ef7e";

    @Test
    void hw10UtcTimestampIsSavedAsBogotaTimeIncludingTheDateChange() {
        Location saved = ingest("014166272782", HW10_PACKET);

        assertThat(saved.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 11, 4, 19, 30));
    }

    @Test
    void otherDevicesKeepTheirExistingBogotaInterpretation() {
        Location saved = ingest("013345678906", LEGACY_PACKET);

        assertThat(saved.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 11, 5, 0, 30));
    }

    @Test
    void hw10CanUseShanghaiIfItsFirmwareReportsGmt8() {
        Location saved = ingest("014166272782", HW10_PACKET, "Asia/Shanghai");

        assertThat(saved.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 11, 4, 11, 30));
    }

    private Location ingest(String terminal, String packet) {
        return ingest(terminal, packet, "UTC");
    }

    private Location ingest(String terminal, String packet, String hw10Zone) {
        AppTimeService appTime = new AppTimeService("America/Bogota");
        LocationRepository locations = mock(LocationRepository.class);
        PetRepository pets = mock(PetRepository.class);
        when(pets.findByTerminalId(terminal)).thenReturn(Optional.of(
                Pet.builder().id(7L).name("Tracker pet").terminalId(terminal).build()));
        when(locations.save(any(Location.class))).thenAnswer(call -> call.getArgument(0));
        GpsIngestionService ingestion = new GpsIngestionService(locations, pets, mock(GeofencingService.class),
                Runnable::run, appTime, Caffeine.newBuilder().build(), Duration.ofDays(1), Duration.ofMinutes(1),
                false, 100, 1);
        Jt808TimeZones timeZones = new Jt808TimeZones();
        timeZones.setTerminalTimeZones(Map.of("014166272782", hw10Zone));
        EmbeddedChannel channel = new EmbeddedChannel(new GpsProtocolDetector(
                new GpsMessageHandler(ingestion, appTime, timeZones), ingestion));
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(HexFormat.of().parseHex(packet)));
            ArgumentCaptor<Location> captured = ArgumentCaptor.forClass(Location.class);
            verify(locations).save(captured.capture());
            return captured.getValue();
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
