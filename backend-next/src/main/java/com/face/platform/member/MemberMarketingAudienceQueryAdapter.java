package com.face.platform.member;

import com.face.platform.marketing.MarketingAudienceQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MemberMarketingAudienceQueryAdapter implements MarketingAudienceQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public MemberMarketingAudienceQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Candidate> activeMembers(long tenantId, long shopId) {
        return jdbcTemplate.query(
            """
            SELECT m.id AS member_id, MIN(a.id) AS recipient_account_id
            FROM member_shop_profile msp
            JOIN member m
              ON m.id = msp.member_id
             AND m.tenant_id = msp.tenant_id
             AND m.status = 'ACTIVE'
            JOIN account a
              ON a.member_id = m.id
             AND a.tenant_id = m.tenant_id
             AND a.status = 'ACTIVE'
            WHERE msp.tenant_id = ? AND msp.shop_id = ? AND msp.status = 'ACTIVE'
            GROUP BY m.id
            ORDER BY m.id
            """,
            (rs, rowNum) -> new Candidate(
                rs.getLong("member_id"), rs.getLong("recipient_account_id")
            ),
            tenantId, shopId
        );
    }
}
