package com.example.transfer.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_history")
public class TransferHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long transferId;
    @Enumerated(EnumType.STRING)
    private HistoryStage stage;
    private String status;
    private String decidedBy;
    private String messageInfo;
    private LocalDateTime changedAt;

    public TransferHistoryEntry() {
    }

    public TransferHistoryEntry(Long transferId, HistoryStage stage, String status, String decidedBy, String messageInfo) {
        this.transferId = transferId;
        this.stage = stage;
        this.status = status;
        this.decidedBy = decidedBy;
        this.messageInfo = messageInfo;
        this.changedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public HistoryStage getStage() { return stage; }
    public void setStage(HistoryStage stage) { this.stage = stage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public String getMessageInfo() { return messageInfo; }
    public void setMessageInfo(String messageInfo) { this.messageInfo = messageInfo; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }

    @Override
    public String toString() {
        return "TransferHistoryEntry{" +
                "id=" + id +
                ", transferId=" + transferId +
                ", stage=" + stage +
                ", status='" + status + '\'' +
                ", decidedBy='" + decidedBy + '\'' +
                ", messageInfo='" + messageInfo + '\'' +
                ", changedAt=" + changedAt +
                '}';
    }
}
