package com.example.limit.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
public class Reservation {

    public static final String APPROVED = "APPROVED";
    public static final String DECLINED = "DECLINED";

    public static final String STATE_RESERVED = "RESERVED";
    public static final String STATE_COMMITTED = "COMMITTED";
    public static final String STATE_RELEASED = "RELEASED";
    public static final String STATE_NONE = "NONE";

    @Id
    private Long transferId;
    private Long senderId;
    private BigDecimal amount;
    private String decision;
    private String reason;
    private String state;
    private LocalDate reservedOn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Reservation() {
    }

    public Reservation(Long transferId, Long senderId, BigDecimal amount, LocalDate reservedOn) {
        this.transferId = transferId;
        this.senderId = senderId;
        this.amount = amount;
        this.reservedOn = reservedOn;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void approve() {
        this.decision = APPROVED;
        this.reason = null;
        this.state = STATE_RESERVED;
    }

    public void decline(String reason) {
        this.decision = DECLINED;
        this.reason = reason;
        this.state = STATE_NONE;
    }

    public void markCommitted() {
        this.state = STATE_COMMITTED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markReleased() {
        this.state = STATE_RELEASED;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isReserved() {
        return STATE_RESERVED.equals(state);
    }

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public LocalDate getReservedOn() { return reservedOn; }
    public void setReservedOn(LocalDate reservedOn) { this.reservedOn = reservedOn; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "Reservation{" +
                "transferId=" + transferId +
                ", senderId=" + senderId +
                ", amount=" + amount +
                ", decision='" + decision + '\'' +
                ", reason='" + reason + '\'' +
                ", state='" + state + '\'' +
                ", reservedOn=" + reservedOn +
                '}';
    }
}
