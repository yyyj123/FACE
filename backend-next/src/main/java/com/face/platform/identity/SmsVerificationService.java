package com.face.platform.identity;

import com.face.platform.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SmsVerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration REQUEST_INTERVAL = Duration.ofSeconds(60);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SmsDeliveryPort deliveryPort;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SmsVerificationService(
        JdbcTemplate jdbcTemplate,
        PasswordEncoder passwordEncoder,
        SmsDeliveryPort deliveryPort,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.deliveryPort = deliveryPort;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> request(long tenantId, String rawPhone, String rawPurpose) {
        String phone = normalizePhone(rawPhone);
        String purpose = normalizePurpose(rawPurpose);
        Instant now = Instant.now(clock);
        Integer recent = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM sms_verification_challenge
            WHERE tenant_id = ? AND phone = ? AND purpose = ? AND created_at > ?
            """,
            Integer.class,
            tenantId,
            phone,
            purpose,
            Timestamp.from(now.minus(REQUEST_INTERVAL))
        );
        if (recent != null && recent > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "验证码请求过于频繁，请稍后再试");
        }

        String code = deliveryPort.demo()
            ? SmsVerificationPolicy.demoCode(true)
            : "%06d".formatted(random.nextInt(1_000_000));
        SmsDeliveryPort.DeliveryReceipt receipt = deliveryPort.send(phone, code, purpose);
        String encoded = deliveryPort.mode() + ":" + passwordEncoder.encode(code);
        jdbcTemplate.update(
            """
            INSERT INTO sms_verification_challenge
                (tenant_id, phone, purpose, code_hash, max_attempts, expires_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            tenantId,
            phone,
            purpose,
            encoded,
            SmsVerificationPolicy.MAX_ATTEMPTS,
            Timestamp.from(now.plus(CODE_TTL))
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", receipt.message());
        result.put("expires_in_seconds", CODE_TTL.toSeconds());
        result.put("retry_after_seconds", REQUEST_INTERVAL.toSeconds());
        if (deliveryPort.demo()) result.put("demo_code", code);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void consume(long tenantId, String rawPhone, String rawPurpose, String rawCode) {
        String phone = normalizePhone(rawPhone);
        String purpose = normalizePurpose(rawPurpose);
        String code = rawCode == null ? "" : rawCode.trim();
        if (!code.matches("\\d{6}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请输入6位验证码");
        }
        Instant now = Instant.now(clock);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, code_hash AS codeHash, attempt_count AS attemptCount,
                   max_attempts AS maxAttempts
            FROM sms_verification_challenge
            WHERE tenant_id = ? AND phone = ? AND purpose = ?
              AND consumed_at IS NULL AND expires_at > ?
            ORDER BY id DESC
            LIMIT 1
            FOR UPDATE
            """,
            tenantId,
            phone,
            purpose,
            Timestamp.from(now)
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码无效或已过期");
        }
        Map<String, Object> row = rows.getFirst();
        long id = number(row.get("id"));
        int attempts = (int) number(row.get("attemptCount"));
        int maxAttempts = (int) number(row.get("maxAttempts"));
        String stored = row.get("codeHash").toString();
        int separator = stored.indexOf(':');
        if (separator <= 0) throw new IllegalStateException("验证码散列格式无效");
        String storedMode = stored.substring(0, separator);
        String hash = stored.substring(separator + 1);
        if ("DEMO".equals(storedMode) && !deliveryPort.demo()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "演示验证码不能在当前环境使用");
        }
        if (attempts >= maxAttempts || !passwordEncoder.matches(code, hash)) {
            jdbcTemplate.update(
                "UPDATE sms_verification_challenge SET attempt_count = LEAST(attempt_count + 1, max_attempts) WHERE id = ?",
                id
            );
            throw new ApiException(HttpStatus.BAD_REQUEST, "验证码无效或已过期");
        }
        jdbcTemplate.update(
            "UPDATE sms_verification_challenge SET consumed_at = ? WHERE id = ? AND consumed_at IS NULL",
            Timestamp.from(now),
            id
        );
    }

    private String normalizePhone(String value) {
        try {
            return SmsVerificationPolicy.normalizePhone(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private String normalizePurpose(String value) {
        try {
            return SmsVerificationPolicy.normalizePurpose(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        throw new IllegalStateException("验证码数据不完整");
    }
}
