package com.example.pettracker.gps.protocol.v41;

import com.example.pettracker.dto.LocationDTO;
import com.example.pettracker.entity.Location;
import com.example.pettracker.entity.Pet;
import com.example.pettracker.gps.GpsMessageHandler;
import com.example.pettracker.gps.GpsProtocolDetector;
import com.example.pettracker.gps.Jt808TimeZones;
import com.example.pettracker.mapper.LocationMapper;
import com.example.pettracker.repository.LocationRepository;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.service.AppTimeService;
import com.example.pettracker.service.GeofencingService;
import com.example.pettracker.service.GpsIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BsjBatteryDecoderTest {
    @Test
    void decodesTheReportedHw10BatteryItems() {
        // Reconstructed frame using the exact EB item bytes supplied from the HW10 log.
        V41ProtocolDecoder.GpsPosition position = decode("eb0b0004002d0f17000300a83c");

        assertThat(position.getBattery()).isEqualTo(60);
        assertThat(position.getBatteryVoltage()).isEqualTo(3.863);
        assertThat(position.getTerminalId()).isEqualTo("014166272782");
    }

    @ParameterizedTest
    @CsvSource({"0f17,3.863", "0f1e,3.870", "0f11,3.857"})
    void retainsMillivoltPrecisionFromTheReportedReadings(String voltageHex, double expectedVoltage) {
        V41ProtocolDecoder.GpsPosition position = decode(eb("0004002d" + voltageHex + "000300a83c"));

        assertThat(position.getBattery()).isEqualTo(60);
        assertThat(position.getBatteryVoltage()).isEqualTo(expectedVoltage);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "unknown", value = {"00,0", "3c,60", "64,100", "65,unknown", "80,unknown", "ff,unknown"})
    void validatesPercentageWithoutClampingOrLosingVoltage(String raw, Integer expected) {
        V41ProtocolDecoder.GpsPosition position = decode(eb("000300a8" + raw + "0004002d0f17"));

        assertThat(position.getBattery()).isEqualTo(expected);
        assertThat(position.getBatteryVoltage()).isEqualTo(3.863);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000", "ffff"})
    void ignoresUnavailableVoltageWithoutLosingPercentage(String voltageHex) {
        V41ProtocolDecoder.GpsPosition position = decode(eb("0004002d" + voltageHex + "000300a83c"));

        assertThat(position.getBatteryVoltage()).isNull();
        assertThat(position.getBattery()).isEqualTo(60);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "000200a8", // no percentage value
            "000400a8003c", // percentage encoded as two bytes is not this format
            "0003002d0f", // short voltage value
            "0006002d00000f17" // external-power variant must not become battery voltage
    })
    void requiresTheExactPayloadWidthForEachBatteryField(String record) {
        V41ProtocolDecoder.GpsPosition position = decode(eb(record));

        assertThat(position.getBattery()).isNull();
        assertThat(position.getBatteryVoltage()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "0000", "0001", "0004002d0f", "ffff002d0f17"})
    void malformedNestedLengthsDoNotOverrunTheExtensionOrDropTheLocation(String records) {
        V41ProtocolDecoder.GpsPosition position = decode(eb(records) + "e1015701040000000a");

        assertThat(position.getBattery()).isEqualTo(87); // following legacy TLV still decodes
        assertThat(position.getBatteryVoltage()).isNull();
        assertThat(position.getMileageKm()).isEqualTo(1.0);
        assertThat(position.getLatitude()).isEqualTo(4.710989);
        assertThat(position.getAdditionalFields()).containsKey("malformed_EB");
    }

    @Test
    void truncatedOuterExtensionIsNotReadAsBatteryData() {
        V41ProtocolDecoder.GpsPosition position = decode("eb3c0004002d0f17");

        assertThat(position.getBattery()).isNull();
        assertThat(position.getBatteryVoltage()).isNull();
        assertThat(position.getAdditionalFields()).containsKey("malformed_EB");
    }

    @Test
    void unknownRecordsAreRetainedAndSkippedUsingTheirDeclaredLength() {
        V41ProtocolDecoder.GpsPosition position = decode(eb("0004abcd7d7e0002ffff000300a83c0004002d0f17"));

        assertThat(position.getBattery()).isEqualTo(60);
        assertThat(position.getBatteryVoltage()).isEqualTo(3.863);
        assertThat(position.getAdditionalFields()).containsEntry("bsj_0xABCD", "7d7e");
    }

    @Test
    void invalidDuplicateValuesDoNotReplaceValidReadings() {
        V41ProtocolDecoder.GpsPosition position = decode(eb("000300a83c000300a8ff0004002d0f170004002d0000"));

        assertThat(position.getBattery()).isEqualTo(60);
        assertThat(position.getBatteryVoltage()).isEqualTo(3.863);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "383639393533303839353139353831", // 15 ASCII digits
            "38363939353330383935313935383100", // null-terminated ASCII
            "0869953089519581", // BCD with leading zero padding
            "869953089519581f" // BCD with trailing F padding
    })
    void decodesReportedImeiAsMetadataWithoutChangingTheHeaderTerminalId(String encodedImei) {
        int recordLength = encodedImei.length() / 2 + 2;
        V41ProtocolDecoder.GpsPosition position = decode(eb(String.format("%04x00d5%s", recordLength, encodedImei)));

        assertThat(position.getImei()).isEqualTo("869953089519581");
        assertThat(position.getAdditionalFields()).containsEntry("imei", "869953089519581");
        assertThat(position.getTerminalId()).isEqualTo("014166272782");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000000000000000", "ffffffffffffffff", "08699530895195ab", "0102"})
    void malformedImeiDoesNotDiscardValidBatteryData(String encodedImei) {
        int recordLength = encodedImei.length() / 2 + 2;
        V41ProtocolDecoder.GpsPosition position = decode(eb(String.format("%04x00d5%s", recordLength, encodedImei)
                + "000300a83c0004002d0f17"));

        assertThat(position.getImei()).isNull();
        assertThat(position.getBattery()).isEqualTo(60);
        assertThat(position.getBatteryVoltage()).isEqualTo(3.863);
    }

    @Test
    void batteryFieldsAlsoDecodeWithTheVersion2019Header() {
        ByteBuf frame = Unpooled.wrappedBuffer(frame("eb0b0004002d0f17000300a83c", true));
        try {
            var position = new V41ProtocolDecoder(ZoneId.of("Asia/Shanghai")).decode(frame).getPosition();
            assertThat(position.getBattery()).isEqualTo(60);
            assertThat(position.getBatteryVoltage()).isEqualTo(3.863);
        } finally {
            frame.release();
        }
    }

    @Test
    void batterySurvivesTcpIngestionPersistenceAndLocationApiMapping() throws Exception {
        AppTimeService appTime = new AppTimeService("America/Bogota");
        LocationRepository locations = mock(LocationRepository.class);
        PetRepository pets = mock(PetRepository.class);
        Pet pet = Pet.builder().id(7L).name("Firulais").terminalId("014166272782").imei("123456789012345").build();
        when(pets.findByTerminalId("014166272782")).thenReturn(Optional.of(pet));
        when(locations.save(any(Location.class))).thenAnswer(call -> call.getArgument(0));
        GpsIngestionService ingestion = new GpsIngestionService(locations, pets, mock(GeofencingService.class),
                Runnable::run, appTime, Caffeine.newBuilder().build(), Duration.ofDays(1), Duration.ofMinutes(1),
                false, 100, 1);
        Jt808TimeZones zones = new Jt808TimeZones();
        zones.setTerminalTimeZones(Map.of("014166272782", "Asia/Shanghai"));
        EmbeddedChannel channel = new EmbeddedChannel(new GpsProtocolDetector(
                new GpsMessageHandler(ingestion, appTime, zones), ingestion));
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(frame("eb0b0004002d0f11000300a83c")));
            ArgumentCaptor<Location> captured = ArgumentCaptor.forClass(Location.class);
            verify(locations).save(captured.capture());
            Location saved = captured.getValue();
            assertThat(saved.getBatteryPercent()).isEqualTo(60);
            assertThat(saved.getBatteryVoltage()).isEqualTo(3.857);
            assertThat(saved.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 11, 4, 11, 30));
            LocationDTO dto = LocationMapper.INSTANCE.toDto(saved);
            var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(dto));
            assertThat(json.get("batteryPercent").intValue()).isEqualTo(60);
            assertThat(json.get("batteryVoltage").doubleValue()).isEqualTo(3.857);
            assertThat(json.get("petId").longValue()).isEqualTo(7L);
            verify(pets, never()).save(any());
            assertThat(pet.getImei()).isEqualTo("123456789012345");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private String eb(String records) {
        return String.format("eb%02x%s", records.length() / 2, records);
    }

    private V41ProtocolDecoder.GpsPosition decode(String additionalFields) {
        ByteBuf frame = Unpooled.wrappedBuffer(frame(additionalFields));
        try {
            return new V41ProtocolDecoder(ZoneId.of("Asia/Shanghai")).decode(frame).getPosition();
        } finally {
            frame.release();
        }
    }

    private byte[] frame(String additionalFields) {
        return frame(additionalFields, false);
    }

    private byte[] frame(String additionalFields, boolean version2019) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(HexFormat.of().parseHex(
                "000000000000000a0047e24d046a4204000000000000261105003000"));
        body.writeBytes(HexFormat.of().parseHex(additionalFields));
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.writeBytes(new byte[] {0x02, 0x00});
        payload.write(((body.size() >>> 8) & 0xFF) | (version2019 ? 0x40 : 0));
        payload.write(body.size() & 0xFF);
        if (version2019) payload.write(1);
        payload.writeBytes(HexFormat.of().parseHex((version2019 ? "00000000" : "") + "0141662727820012"));
        payload.writeBytes(body.toByteArray());
        int checksum = 0;
        for (byte value : payload.toByteArray()) checksum ^= value & 0xFF;
        payload.write(checksum);
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        framed.write(0x7E);
        for (byte value : payload.toByteArray()) {
            int unsigned = value & 0xFF;
            if (unsigned == 0x7D || unsigned == 0x7E) {
                framed.write(0x7D);
                framed.write(unsigned == 0x7D ? 1 : 2);
            } else framed.write(unsigned);
        }
        framed.write(0x7E);
        return framed.toByteArray();
    }
}
