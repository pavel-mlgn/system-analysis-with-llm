package com.example.limit.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Table(name = "sender_usage")
public class SenderUsage {

    @Id
    private Long senderId;
    private BigDecimal dailyUsed;
    private BigDecimal monthlyUsed;
    private LocalDate lastDailyReset;
    private LocalDate lastMonthlyReset;

    @Version
    private Long version;

    public SenderUsage() {
    }

    public SenderUsage(Long senderId, LocalDate today) {
        this.senderId = senderId;
        this.dailyUsed = BigDecimal.ZERO;
        this.monthlyUsed = BigDecimal.ZERO;
        this.lastDailyReset = today;
        this.lastMonthlyReset = today;
    }

    public void lazyReset(LocalDate today) {
        if (!today.equals(lastDailyReset)) {
            dailyUsed = BigDecimal.ZERO;
            lastDailyReset = today;
        }
        if (!YearMonth.from(today).equals(YearMonth.from(lastMonthlyReset))) {
            monthlyUsed = BigDecimal.ZERO;
            lastMonthlyReset = today;
        }
    }

    public void reserve(BigDecimal amount) {
        dailyUsed = dailyUsed.add(amount);
        monthlyUsed = monthlyUsed.add(amount);
    }

    public void release(BigDecimal amount, LocalDate reservedOn, LocalDate today) {
        lazyReset(today);
        if (reservedOn.equals(today)) {
            dailyUsed = dailyUsed.subtract(amount).max(BigDecimal.ZERO);
        }
        if (YearMonth.from(reservedOn).equals(YearMonth.from(today))) {
            monthlyUsed = monthlyUsed.subtract(amount).max(BigDecimal.ZERO);
        }
    }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public BigDecimal getDailyUsed() { return dailyUsed; }
    public void setDailyUsed(BigDecimal dailyUsed) { this.dailyUsed = dailyUsed; }
    public BigDecimal getMonthlyUsed() { return monthlyUsed; }
    public void setMonthlyUsed(BigDecimal monthlyUsed) { this.monthlyUsed = monthlyUsed; }
    public LocalDate getLastDailyReset() { return lastDailyReset; }
    public void setLastDailyReset(LocalDate lastDailyReset) { this.lastDailyReset = lastDailyReset; }
    public LocalDate getLastMonthlyReset() { return lastMonthlyReset; }
    public void setLastMonthlyReset(LocalDate lastMonthlyReset) { this.lastMonthlyReset = lastMonthlyReset; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    @Override
    public String toString() {
        return "SenderUsage{" +
                "senderId=" + senderId +
                ", dailyUsed=" + dailyUsed +
                ", monthlyUsed=" + monthlyUsed +
                ", lastDailyReset=" + lastDailyReset +
                ", lastMonthlyReset=" + lastMonthlyReset +
                ", version=" + version +
                '}';
    }
}
