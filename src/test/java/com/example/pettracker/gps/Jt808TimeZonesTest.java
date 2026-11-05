package com.example.pettracker.gps;

import java.io.IOException;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class Jt808TimeZonesTest {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Test
    void applicationConfigTargetsTheExactHw10IdIncludingItsLeadingZero() throws IOException {
        context().run(context -> {
            assertThat(context).hasNotFailed();
            Jt808TimeZones zones = context.getBean(Jt808TimeZones.class);
            assertThat(zones.resolve("014166272782", BOGOTA)).isEqualTo(ZoneId.of("Asia/Shanghai"));
            assertThat(zones.resolve("14166272782", BOGOTA)).isEqualTo(BOGOTA);
            assertThat(zones.resolve("013345678906", BOGOTA)).isEqualTo(BOGOTA);
        });
    }

    @Test
    void hw10ClockZoneCanBeOverriddenWithoutChangingTheAppZone() throws IOException {
        context().withPropertyValues("GPS_HW10_TIME_ZONE=UTC").run(context -> {
            assertThat(context).hasNotFailed();
            Jt808TimeZones zones = context.getBean(Jt808TimeZones.class);
            assertThat(zones.resolve("014166272782", BOGOTA)).isEqualTo(ZoneId.of("UTC"));
            assertThat(zones.resolve("013345678906", BOGOTA)).isEqualTo(BOGOTA);
        });
    }

    @Test
    void additionalVersion2019IdsKeepAllTwentyDigits() throws IOException {
        context().withPropertyValues("gps.jt808.terminal-time-zones[00000001234567890123]=UTC").run(context -> {
            assertThat(context).hasNotFailed();
            Jt808TimeZones zones = context.getBean(Jt808TimeZones.class);
            assertThat(zones.resolve("00000001234567890123", BOGOTA)).isEqualTo(ZoneId.of("UTC"));
            assertThat(zones.resolve("014166272782", BOGOTA)).isEqualTo(ZoneId.of("Asia/Shanghai"));
        });
    }

    @Test
    void invalidTimezoneFailsAtStartupInsteadOfSilentlyDroppingPositions() throws IOException {
        context().withPropertyValues("GPS_HW10_TIME_ZONE=Invalid/Timezone").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(ZoneRulesException.class);
        });
    }

    private ApplicationContextRunner context() throws IOException {
        var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(Jt808TimeZones.class)
                .withInitializer(context -> sources.forEach(source ->
                        context.getEnvironment().getPropertySources().addLast(source)));
    }
}
