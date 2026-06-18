package com.example.pettracker.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RailwayDeploymentValidator {

    private final Environment environment;

    public RailwayDeploymentValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (!isRailwayDeployment()) {
            return;
        }

        String jwtSecret = environment.getProperty("jwt.secret");
        String defaultJwtSecret = environment.getProperty("pettracker.security.default-jwt-secret");

        if (!StringUtils.hasText(jwtSecret) || jwtSecret.equals(defaultJwtSecret)) {
            throw new IllegalStateException(
                    "Railway deployment requires JWT_SECRET to be set to a non-development value."
            );
        }
    }

    private boolean isRailwayDeployment() {
        return StringUtils.hasText(environment.getProperty("RAILWAY_PROJECT_ID"))
                || StringUtils.hasText(environment.getProperty("RAILWAY_SERVICE_ID"))
                || StringUtils.hasText(environment.getProperty("RAILWAY_ENVIRONMENT_ID"));
    }
}
