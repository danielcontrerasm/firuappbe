package com.example.pettracker.repository;

import com.example.pettracker.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT user FROM User user WHERE LOWER(user.email) = LOWER(:email)")
    Optional<User> findByEmail(@Param("email") String email);
}
