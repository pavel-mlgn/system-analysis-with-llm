package com.example.transfer.service;

import org.springframework.stereotype.Service;
import com.example.common.Transfer;

@Service
public class TransferDecisionService {

    private static final String APPROVED = "APPROVED";
    private static final String DECLINED = "DECLINED";

    public Transfer decide(Transfer limitResult, Transfer screeningResult) {
        boolean limitApproved = APPROVED.equals(limitResult.status());
        boolean limitDeclined = DECLINED.equals(limitResult.status());
        boolean screeningApproved = APPROVED.equals(screeningResult.status());
        boolean screeningDeclined = DECLINED.equals(screeningResult.status());

        if (limitApproved && screeningApproved) {
            return limitResult.withDecision("COMPLETED", null, null);
        } else if (limitDeclined && screeningDeclined) {
            return limitResult.withDecision("DENIED", null,
                    limitResult.messageInfo() + "; " + screeningResult.messageInfo());
        } else if (limitDeclined && screeningApproved) {
            return limitResult.withDecision("CANCELLED", "limit", limitResult.messageInfo());
        } else if (limitApproved && screeningDeclined) {
            return limitResult.withDecision("CANCELLED", "screening", screeningResult.messageInfo());
        } else {
            return limitResult.withDecision("CANCELLED", null, "UNEXPECTED_RESULT: limit=" + limitResult.status()
                    + ", screening=" + screeningResult.status());
        }
    }
}
