package com.example.transfer.controller;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull Long id,
        @NotNull Long senderId,
        @NotNull Long recipientId,
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @Pattern(regexp = "[A-Z]{3}") String currency) {

    public static final String DEFAULT_CURRENCY = "RUB";

    public String currencyOrDefault() {
        return currency == null ? DEFAULT_CURRENCY : currency;
    }
}
