package com.example.common;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Transfer(Long id,
                       Long senderId,
                       Long recipientId,
                       BigDecimal amount,
                       String currency,
                       String status,
                       String decidedBy,
                       String messageInfo,
                       LocalDateTime createdAt) {

    public static final String NEW = "NEW";

    public static Transfer newTransfer(Long id, Long senderId, Long recipientId, BigDecimal amount,
                                       String currency, LocalDateTime createdAt) {
        return new Transfer(id, senderId, recipientId, amount, currency, NEW, null, null, createdAt);
    }

    public Transfer withDecision(String status, String decidedBy, String messageInfo) {
        return new Transfer(id, senderId, recipientId, amount, currency, status, decidedBy, messageInfo, createdAt);
    }
}
