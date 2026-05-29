package com.example.pettracker.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.util.StringUtils;

public class RailwayDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "railwayDatabase";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(environment.getProperty("SPRING_DATASOURCE_URL"))) {
            return;
        }

        String databaseUrl = firstPresent(
                environment.getProperty("DATABASE_PRIVATE_URL"),
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("DATABASE_PUBLIC_URL")
        );

        if (!StringUtils.hasText(databaseUrl)) {
            return;
        }

        RailwayJdbcUrl jdbcUrl = toJdbcUrl(databaseUrl);
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", jdbcUrl.url());
        if (StringUtils.hasText(jdbcUrl.username())) {
            properties.put("spring.datasource.username", jdbcUrl.username());
        }
        if (StringUtils.hasText(jdbcUrl.password())) {
            properties.put("spring.datasource.password", jdbcUrl.password());
        }

        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static RailwayJdbcUrl toJdbcUrl(String databaseUrl) {
        URI uri = URI.create(databaseUrl);
        String scheme = uri.getScheme();
        if (!"postgres".equals(scheme) && !"postgresql".equals(scheme)) {
            throw new IllegalArgumentException("Unsupported Railway database URL scheme: " + scheme);
        }

        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://");
        jdbcUrl.append(uri.getHost());
        if (uri.getPort() != -1) {
            jdbcUrl.append(':').append(uri.getPort());
        }
        jdbcUrl.append(uri.getRawPath());
        if (StringUtils.hasText(uri.getRawQuery())) {
            jdbcUrl.append('?').append(uri.getRawQuery());
        }

        String username = null;
        String password = null;
        String userInfo = uri.getRawUserInfo();
        if (StringUtils.hasText(userInfo)) {
            String[] parts = userInfo.split(":", 2);
            username = decode(parts[0]);
            if (parts.length > 1) {
                password = decode(parts[1]);
            }
        }

        return new RailwayJdbcUrl(jdbcUrl.toString(), username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private record RailwayJdbcUrl(String url, String username, String password) {
    }
}
