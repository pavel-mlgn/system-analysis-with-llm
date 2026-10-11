package com.example.screening.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "screening_checks")
public class ScreeningCheck {

    public static final String APPROVED = "APPROVED";
    public static final String DECLINED = "DECLINED";

    @Id
    private Long transferId;
    private Long senderId;
    private String result;
    private String reason;
    private LocalDateTime checkedAt;

    public ScreeningCheck() {
    }

    public ScreeningCheck(Long transferId, Long senderId, String result, String reason) {
        this.transferId = transferId;
        this.senderId = senderId;
        this.result = result;
        this.reason = reason;
        this.checkedAt = LocalDateTime.now();
    }

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }

    @Override
    public String toString() {
        return "ScreeningCheck{" +
                "transferId=" + transferId +
                ", senderId=" + senderId +
                ", result='" + result + '\'' +
                ", reason='" + reason + '\'' +
                ", checkedAt=" + checkedAt +
                '}';
    }
}
