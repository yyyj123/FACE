package com.face.platform.marketing;

import java.util.List;

public interface MarketingAudienceQueryPort {

    List<Candidate> activeMembers(long tenantId, long shopId);

    record Candidate(long memberId, long recipientAccountId) {
    }
}
