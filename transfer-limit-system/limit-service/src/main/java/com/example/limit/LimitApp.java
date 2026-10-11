package com.example.limit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class LimitApp {

    public static void main(String[] args) {
        SpringApplication.run(LimitApp.class, args);
    }
}
