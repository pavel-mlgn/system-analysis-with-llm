package com.example.screening;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class ScreeningApp {

    public static void main(String[] args) {
        SpringApplication.run(ScreeningApp.class, args);
    }
}
