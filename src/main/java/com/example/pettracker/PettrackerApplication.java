package com.example.pettracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PettrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PettrackerApplication.class, args);
    }
}
