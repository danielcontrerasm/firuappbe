package com.example.pettracker.service;

import com.example.pettracker.entity.User;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Central source of truth for the limits sold with each PetTracker plan. */
public final class PlanPolicy {
    private PlanPolicy() {
    }

    public static User.Plan planOf(User user) {
        if (user == null || user.getPlan() == null) {
            return User.Plan.BASIC;
        }
        return user.getPlan();
    }

    public static int historyDays(User user) {
        if (isAdmin(user)) return Integer.MAX_VALUE;
        return switch (planOf(user)) {
            case FAMILY -> 90;
            case PREMIUM -> 365;
            case BASIC -> 30;
        };
    }

    public static int authorizedPeoplePerPet(User user) {
        if (isAdmin(user)) return Integer.MAX_VALUE;
        return switch (planOf(user)) {
            case FAMILY -> 10;
            case PREMIUM -> 20;
            case BASIC -> 5;
        };
    }

    public static int searchGroups(User user) {
        if (isAdmin(user)) return Integer.MAX_VALUE;
        return switch (planOf(user)) {
            case FAMILY -> 3;
            case PREMIUM -> 5;
            case BASIC -> 1;
        };
    }

    public static int geofencesPerPet(User user) {
        if (isAdmin(user)) return Integer.MAX_VALUE;
        return switch (planOf(user)) {
            case FAMILY -> 5;
            case PREMIUM -> 10;
            case BASIC -> 3;
        };
    }

    public static boolean canExportReports(User user) {
        return isAdmin(user) || planOf(user) == User.Plan.PREMIUM;
    }

    public static boolean canCaregiverAccess(User user) {
        return isAdmin(user) || planOf(user) != User.Plan.BASIC;
    }

    public static boolean hasRecurringCaregiverSchedule(User user) {
        return isAdmin(user) || planOf(user) == User.Plan.PREMIUM;
    }

    public static void requirePremiumExport(User user) {
        if (!canExportReports(user)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "La descarga de reportes está disponible únicamente para usuarios Premium");
        }
    }

    public static void requireHistoryWindow(User user, LocalDateTime from, LocalDateTime now) {
        if (from == null || isAdmin(user)) return;
        LocalDateTime cutoff = now.minusDays(historyDays(user));
        if (from.isBefore(cutoff)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tu plan " + planOf(user).name() + " permite consultar solo los últimos "
                            + historyDays(user) + " días de historial");
        }
    }

    private static boolean isAdmin(User user) {
        return user != null && user.getRole() == User.Role.ADMIN;
    }
}
