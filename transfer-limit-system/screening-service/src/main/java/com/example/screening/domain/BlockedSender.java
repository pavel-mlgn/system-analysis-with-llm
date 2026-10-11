package com.example.screening.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "blocked_senders")
public class BlockedSender {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long senderId;
    private String reason;
    private LocalDateTime blockedAt;

    public BlockedSender() {
    }

    public BlockedSender(Long senderId, String reason) {
        this.senderId = senderId;
        this.reason = reason;
        this.blockedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getBlockedAt() { return blockedAt; }
    public void setBlockedAt(LocalDateTime blockedAt) { this.blockedAt = blockedAt; }

    @Override
    public String toString() {
        return "BlockedSender{" +
                "id=" + id +
                ", senderId=" + senderId +
                ", reason='" + reason + '\'' +
                ", blockedAt=" + blockedAt +
                '}';
    }
}
