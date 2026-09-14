package com.face.platform.v3.booking;

import com.face.platform.booking.WeeklyScheduleService;
import com.face.platform.v3.api.V3ApiResponse;
import com.face.platform.v3.api.V3RequestSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
public class V3WeeklyScheduleController {

    private final WeeklyScheduleService service;

    public V3WeeklyScheduleController(WeeklyScheduleService service) {
        this.service = service;
    }

    @GetMapping("/api/v3/open/staff-weekly-schedule")
    public V3ApiResponse<Map<String, Object>> publicWeek(
        @RequestParam(name = "shop_id", required = false) Long shopId,
        @RequestParam(name = "from_date", required = false) LocalDate fromDate,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.publicWeek(shopId, fromDate),
            V3RequestSupport.requestId(request)
        );
    }

    @GetMapping("/api/v3/booking/admin/weekly-schedule")
    public V3ApiResponse<Map<String, Object>> adminWeek(
        @RequestParam(name = "shop_id", required = false) Long shopId,
        @RequestParam(name = "from", required = false) LocalDate from,
        HttpServletRequest request
    ) {
        return V3ApiResponse.success(
            service.adminWeek(V3RequestSupport.principal(request), shopId, from),
            V3RequestSupport.requestId(request)
        );
    }
}
