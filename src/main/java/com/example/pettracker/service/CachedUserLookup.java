package com.example.pettracker.service;

import com.example.pettracker.entity.User;

public record CachedUserLookup(
        Long id,
        String name,
        String email,
        String phone,
        User.Role role
) {
    public static CachedUserLookup from(User user) {
        return new CachedUserLookup(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole()
        );
    }

    public User toUser() {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(role);
        return user;
    }
}
