package com.controller;

import com.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compatibility endpoints used by the existing Vue admin shell for new FACE accounts. */
@RestController
@RequestMapping("account")
public class AccountController {
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public AccountController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @RequestMapping("/session")
    public R session(HttpServletRequest request) {
        Object accountId = request.getSession().getAttribute("userId");
        if (accountId == null) return R.error(401, "登录状态已失效");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT a.id AS accountId, COALESCE(a.member_id, a.staff_id, a.id) AS id, a.username, " +
                "a.username AS zhanghao, a.username AS weixiuzhanghao, '******' AS mima, " +
                "a.role_code AS role, a.shop_id AS shopId, a.staff_id AS staffId, a.member_id AS memberId, " +
                "COALESCE(m.name, s.name, a.username) AS name, COALESCE(m.name, s.name, a.username) AS xingming, " +
                "COALESCE(s.name, m.name, a.username) AS weixiuxingming, m.gender AS xingbie, " +
                "COALESCE(m.phone, s.phone) AS shouji, COALESCE(s.phone, m.phone) AS lianxidianhua, " +
                "COALESCE(m.avatar_url, s.avatar_url) AS touxiang " +
                "FROM account a LEFT JOIN member m ON m.id = a.member_id " +
                "LEFT JOIN staff s ON s.id = a.staff_id WHERE a.id = ? AND a.status = 'ACTIVE' LIMIT 1",
            accountId
        );
        if (rows.isEmpty()) return R.error(401, "登录状态已失效");
        Map<String, Object> data = new LinkedHashMap<String, Object>(rows.get(0));
        return R.ok().put("data", data);
    }

    @PostMapping("/update")
    @Transactional
    public R update(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        Map<String, Object> account = jdbcTemplate.queryForMap(
            "SELECT shop_id, role_code, member_id, staff_id FROM account WHERE id = ? AND status = 'ACTIVE'",
            accountId
        );
        Object memberId = account.get("member_id");
        Object staffId = account.get("staff_id");
        if (memberId != null) {
            jdbcTemplate.update(
                "UPDATE member SET name = COALESCE(?, name), gender = COALESCE(?, gender), " +
                    "phone = COALESCE(?, phone), avatar_url = COALESCE(?, avatar_url) WHERE id = ? AND shop_id = ?",
                value(body, "xingming"), value(body, "xingbie"), value(body, "shouji"),
                value(body, "touxiang"), memberId, account.get("shop_id")
            );
        } else if (staffId != null) {
            jdbcTemplate.update(
                "UPDATE staff SET name = COALESCE(?, name), phone = COALESCE(?, phone), " +
                    "avatar_url = COALESCE(?, avatar_url) WHERE id = ? AND shop_id = ?",
                value(body, "weixiuxingming"), value(body, "lianxidianhua"), value(body, "touxiang"),
                staffId, account.get("shop_id")
            );
        }
        return R.ok();
    }

    @PostMapping("/password")
    public R password(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        String oldPassword = value(body, "oldPassword");
        String newPassword = value(body, "newPassword");
        if (oldPassword == null || newPassword == null || newPassword.length() < 6) {
            return R.error(400, "请输入原密码，新密码至少 6 位");
        }
        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM account WHERE id = ?", String.class, accountId);
        if (!passwordEncoder.matches(oldPassword, hash)) return R.error(400, "原密码错误");
        jdbcTemplate.update("UPDATE account SET password_hash = ? WHERE id = ?", passwordEncoder.encode(newPassword), accountId);
        return R.ok();
    }

    private Long requireAccountId(HttpServletRequest request) {
        Object accountId = request.getSession().getAttribute("userId");
        if (accountId == null) throw new IllegalStateException("登录状态已失效");
        return ((Number) accountId).longValue();
    }

    private String value(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
