package com.example.pettracker.service;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Shared account and synthetic-pet checks for demo seeding and scheduled locations. */
@Component
public class DemoAccount {
    private final String email;
    private final boolean enabled;

    public DemoAccount(@Value("${demo.account-email:danico411@gmail.com}") String email,
                       @Value("${demo.gps.enabled:false}") boolean enabled) {
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.enabled = enabled;
    }

    public String email() {
        return email;
    }

    public boolean matches(User user) {
        return enabled && user != null && user.getId() != null && user.getEmail() != null
                && email.equals(user.getEmail().trim().toLowerCase(Locale.ROOT));
    }

    public String imeiFor(User owner, String petName) {
        return "DEMO-" + petName.toUpperCase(Locale.ROOT) + "-" + owner.getId();
    }

    public boolean ownsDemoPet(User owner, Pet pet, String petName) {
        return matches(owner) && pet != null && matches(pet.getOwner())
                && Objects.equals(owner.getId(), pet.getOwner().getId())
                && imeiFor(owner, petName).equals(pet.getImei())
                && !StringUtils.hasText(pet.getTerminalId());
    }
}
