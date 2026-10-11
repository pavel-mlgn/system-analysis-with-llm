package com.example.transfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class TransferApp {

    public static void main(String[] args) {
        SpringApplication.run(TransferApp.class, args);
    }
}
