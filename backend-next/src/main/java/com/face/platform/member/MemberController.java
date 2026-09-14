package com.face.platform.member;

import com.face.platform.api.ApiResponse;
import com.face.platform.security.TenantContextFilter;
import com.face.platform.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v2/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
        @RequestParam(required = false) Long shopId,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "ACTIVE") String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(memberService.list(
            principal(request),
            shopId,
            keyword,
            status,
            page,
            pageSize
        ));
    }

    @GetMapping("/{memberId}")
    public ApiResponse<Map<String, Object>> detail(
        @PathVariable long memberId,
        @RequestParam(required = false) Long shopId,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(memberService.detail(principal(request), memberId, shopId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
        @Valid @RequestBody MemberCreateRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(memberService.create(principal(request), body));
    }

    @PutMapping("/{memberId}")
    public ApiResponse<Map<String, Object>> update(
        @PathVariable long memberId,
        @Valid @RequestBody MemberUpdateRequest body,
        HttpServletRequest request
    ) {
        return ApiResponse.ok(memberService.update(principal(request), memberId, body));
    }

    @DeleteMapping("/{memberId}")
    public ApiResponse<Void> deactivate(
        @PathVariable long memberId,
        @Valid @RequestBody MemberStatusRequest body,
        HttpServletRequest request
    ) {
        memberService.changeStatus(
            principal(request),
            memberId,
            new MemberStatusRequest(body.shopId(), "INACTIVE")
        );
        return ApiResponse.ok(null);
    }

    @PostMapping("/{memberId}/restore")
    public ApiResponse<Void> restore(
        @PathVariable long memberId,
        @Valid @RequestBody MemberStatusRequest body,
        HttpServletRequest request
    ) {
        memberService.changeStatus(
            principal(request),
            memberId,
            new MemberStatusRequest(body.shopId(), "ACTIVE")
        );
        return ApiResponse.ok(null);
    }

    private TenantPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(TenantContextFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof TenantPrincipal principal)) {
            throw new IllegalStateException("租户上下文未建立");
        }
        return principal;
    }
}

