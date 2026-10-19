package com.example.pettracker.gps;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GpsMessageHandlerTest {

    private final GpsMessageHandler handler = new GpsMessageHandler(null, null);

    @Test
    void extractsBatteryPercentFromUdLteLocationPayload() {
        Integer batteryPercent = handler.extractBatteryPercent(
                "UD_LTE",
                "UD_LTE,041026,163152,V,10.973063,N,-74.7920035,W,0.00,0.0,0.0,10,100,51,0,0"
        );

        assertThat(batteryPercent).isEqualTo(51);
    }

    @Test
    void extractsBatteryPercentFromLkPayloadLastField() {
        Integer batteryPercent = handler.extractBatteryPercent("LK", "LK,0,0,51");

        assertThat(batteryPercent).isEqualTo(51);
    }

    @Test
    void ignoresBatteryPercentOutsideValidRange() {
        Integer batteryPercent = handler.extractBatteryPercent(
                "UD_LTE",
                "UD_LTE,041026,163152,V,10.973063,N,-74.7920035,W,0.00,0.0,0.0,10,100,151,0,0"
        );

        assertThat(batteryPercent).isNull();
    }
}
