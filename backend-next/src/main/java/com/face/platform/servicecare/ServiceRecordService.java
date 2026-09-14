package com.face.platform.servicecare;

import com.face.platform.api.ApiException;
import com.face.platform.appointment.AppointmentLifecycleService;
import com.face.platform.inventory.InventoryConsumptionLine;
import com.face.platform.inventory.InventoryService;
import com.face.platform.outbox.OutboxEventService;
import com.face.platform.security.TenantAccessService;
import com.face.platform.security.TenantPrincipal;
import com.face.platform.transaction.TransactionService;
import com.face.platform.v3.auth.SessionTokenCodec;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ServiceRecordService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST =
        new TypeReference<>() {
        };

    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;
    private final InventoryService inventoryService;
    private final ServiceRecordLifecycleService lifecycleService;
    private final AppointmentLifecycleService appointmentLifecycleService;
    private final CustomerConfirmationService customerConfirmationService;
    private final TransactionService transactionService;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    public ServiceRecordService(
        JdbcTemplate jdbcTemplate,
        TenantAccessService tenantAccessService,
        InventoryService inventoryService,
        ServiceRecordLifecycleService lifecycleService,
        AppointmentLifecycleService appointmentLifecycleService,
        CustomerConfirmationService customerConfirmationService,
        TransactionService transactionService,
        OutboxEventService outboxEventService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantAccessService = tenantAccessService;
        this.inventoryService = inventoryService;
        this.lifecycleService = lifecycleService;
        this.appointmentLifecycleService = appointmentLifecycleService;
        this.customerConfirmationService = customerConfirmationService;
        this.transactionService = transactionService;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> list(
        TenantPrincipal principal,
        long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String status,
        String keyword,
        int page,
        int pageSize
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "service_record:view");
        LocalDate safeTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate safeFrom = fromDate == null ? safeTo.minusDays(30) : fromDate;
        if (safeTo.isBefore(safeFrom)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        if (safeFrom.plusDays(92).isBefore(safeTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "单次最多查询93天服务记录");
        }
        String normalizedStatus = normalizeStatus(status);
        String normalizedKeyword = trimToNull(keyword);
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(pageSize, 100));
        List<Object> args = new ArrayList<>();
        String where = buildWhere(
            principal, shopId, safeFrom, safeTo, normalizedStatus, normalizedKeyword, args
        );
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeLimit);
        listArgs.add((safePage - 1) * safeLimit);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
            """
            SELECT sr.id, sr.record_no AS recordNo, sr.shop_id AS shopId,
                   sh.name AS shopName, sr.appointment_id AS appointmentId,
                   ap.appointment_no AS appointmentNo,
                   sr.member_id AS memberId, m.member_no AS memberNo,
                   m.name AS memberName, m.phone AS memberPhone,
                   sr.staff_id AS staffId, st.staff_no AS staffNo,
                   st.name AS staffName, st.level_name AS staffLevel,
                   sr.actual_start_at AS actualStartAt,
                   sr.actual_end_at AS actualEndAt,
                   sr.service_summary AS serviceSummary,
                   sr.next_visit_recommendation AS nextVisitRecommendation,
                   sr.status, sr.version,
                   cr.id AS careRecordId, cr.version AS careVersion,
                   CASE WHEN cr.id IS NULL THEN 0 ELSE 1 END AS careCompleted,
                   cr.next_recommended_at AS nextRecommendedAt,
                   (SELECT GROUP_CONCAT(
                      sri.service_name_snapshot ORDER BY sri.sort_order SEPARATOR '、')
                    FROM service_record_item sri
                    WHERE sri.service_record_id = sr.id) AS serviceNames,
                   (SELECT COUNT(*)
                    FROM service_record_consumption src
                    WHERE src.service_record_id = sr.id) AS consumptionCount,
                   sr.created_at AS createdAt, sr.updated_at AS updatedAt
            FROM service_record sr
            JOIN shop sh ON sh.id = sr.shop_id
            JOIN member m ON m.id = sr.member_id
            JOIN staff st ON st.id = sr.staff_id
            LEFT JOIN appointment ap ON ap.id = sr.appointment_id
            LEFT JOIN care_record cr ON cr.service_record_id = sr.id
            %s
            ORDER BY sr.actual_start_at DESC, sr.id DESC
            LIMIT ? OFFSET ?
            """.formatted(where),
            listArgs.toArray()
        );
        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM service_record sr " + where,
            Long.class,
            args.toArray()
        );
        Map<String, Object> summary = jdbcTemplate.queryForMap(
            """
            SELECT COUNT(*) AS total,
                   SUM(CASE WHEN sr.status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS inProgress,
                   SUM(CASE WHEN sr.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
                   SUM(CASE WHEN sr.status = 'COMPLETED' AND cr.id IS NULL
                     THEN 1 ELSE 0 END) AS carePending
            FROM service_record sr
            LEFT JOIN care_record cr ON cr.service_record_id = sr.id
            WHERE sr.tenant_id = ? AND sr.shop_id = ?
              AND sr.actual_start_at >= ? AND sr.actual_start_at < ?
            """,
            principal.tenantId(),
            shopId,
            Timestamp.valueOf(safeFrom.atStartOfDay()),
            Timestamp.valueOf(safeTo.plusDays(1).atStartOfDay())
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total == null ? 0 : total);
        result.put("summary", summary);
        result.put("page", safePage);
        result.put("pageSize", safeLimit);
        return result;
    }

    public Map<String, Object> resources(TenantPrincipal principal, long shopId) {
        tenantAccessService.requireShopPermission(principal, shopId, "service_record:view");
        Long scopedStaffId = principal.roles().contains("BEAUTICIAN")
            ? tenantAccessService.requireStaffId(principal)
            : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("readyAppointments", jdbcTemplate.queryForList(
            """
            SELECT ap.id, ap.appointment_no AS appointmentNo,
                   ap.member_id AS memberId, m.name AS memberName,
                   m.phone AS memberPhone, ap.staff_id AS staffId,
                   st.name AS staffName, ap.start_at AS startAt,
                   ap.status, ap.version,
                   GROUP_CONCAT(ai.service_name_snapshot ORDER BY ai.sort_order
                     SEPARATOR '、') AS serviceNames
            FROM appointment ap
            JOIN member m ON m.id = ap.member_id
            JOIN staff st ON st.id = ap.staff_id
            JOIN appointment_item ai ON ai.appointment_id = ap.id
            LEFT JOIN service_record sr ON sr.appointment_id = ap.id
            WHERE ap.tenant_id = ? AND ap.shop_id = ?
              AND ap.status = 'CHECKED_IN'
              AND sr.id IS NULL
              AND (? IS NULL OR ap.staff_id = ?)
            GROUP BY ap.id, ap.appointment_no, ap.member_id, m.name, m.phone,
                     ap.staff_id, st.name, ap.start_at, ap.status, ap.version
            ORDER BY ap.start_at, ap.id
            """,
            principal.tenantId(),
            shopId,
            scopedStaffId,
            scopedStaffId
        ));
        result.put("consumables", jdbcTemplate.queryForList(
            """
            SELECT p.id AS productId, p.sku, p.name AS productName,
                   p.unit_name AS unitName, sl.id AS locationId,
                   sl.name AS locationName, sl.location_type AS locationType,
                   sb.quantity_on_hand AS quantityOnHand,
                   sb.quantity_reserved AS quantityReserved,
                   sb.quantity_on_hand - sb.quantity_reserved AS quantityAvailable,
                   sb.version AS balanceVersion
            FROM product p
            JOIN stock_balance sb
              ON sb.tenant_id = p.tenant_id AND sb.product_id = p.id
            JOIN stock_location sl
              ON sl.id = sb.location_id AND sl.shop_id = p.shop_id
            WHERE p.tenant_id = ? AND p.shop_id = ?
              AND p.status = 'ACTIVE' AND p.is_consumable = 1
              AND sl.status = 'ACTIVE'
            ORDER BY p.name,
                     FIELD(sl.location_type, 'ROOM', 'SHOP', 'HEADQUARTERS'),
                     sl.id
            """,
            principal.tenantId(),
            shopId
        ));
        List<Map<String, Object>> knowledge = queryLegacyKnowledge();
        result.put("knowledge", knowledge.isEmpty() ? defaultCareKnowledge() : knowledge);
        result.put("skinTypes", List.of("干性", "油性", "混合性", "中性", "敏感性"));
        return result;
    }

    private List<Map<String, Object>> queryLegacyKnowledge() {
        Integer tableCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 'guzhangpaicha'
            """,
            Integer.class
        );
        if (tableCount == null || tableCount == 0) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
            """
            SELECT id, guzhangmingcheng AS title, guzhangfenlei AS category,
                   guzhangyuanyin AS cause, paichabujian AS observationFocus,
                   guzhangpaicha AS advice
            FROM guzhangpaicha
            ORDER BY clicknum DESC, id
            LIMIT 50
            """
        );
    }

    private List<Map<String, Object>> defaultCareKnowledge() {
        return List.of(
            Map.of(
                "id", -1L,
                "title", "补水与屏障护理",
                "category", "干燥缺水",
                "cause", "含水量不足或清洁过度",
                "observationFocus", "观察紧绷、起屑及细纹情况",
                "advice", "温和清洁并加强保湿与屏障修护"
            ),
            Map.of(
                "id", -2L,
                "title", "敏感舒缓护理",
                "category", "敏感泛红",
                "cause", "屏障脆弱或受到刺激",
                "observationFocus", "观察泛红、灼热和刺痛区域",
                "advice", "减少刺激性成分，优先舒缓修护"
            ),
            Map.of(
                "id", -3L,
                "title", "油脂平衡护理",
                "category", "油脂旺盛",
                "cause", "皮脂分泌旺盛或水油失衡",
                "observationFocus", "观察T区出油与毛孔堵塞情况",
                "advice", "温和控油，避免过度去脂"
            ),
            Map.of(
                "id", -4L,
                "title", "净肤清洁护理",
                "category", "痘痘粉刺",
                "cause", "角质堆积与毛孔堵塞",
                "observationFocus", "记录粉刺、丘疹及炎症分布",
                "advice", "避免挤压，保持清洁并减少致痘刺激"
            ),
            Map.of(
                "id", -5L,
                "title", "细致毛孔护理",
                "category", "毛孔粗大",
                "cause", "油脂堵塞、弹性下降或缺水",
                "observationFocus", "观察毛孔集中区域与伴随出油",
                "advice", "做好清洁、补水与规律防晒"
            ),
            Map.of(
                "id", -6L,
                "title", "匀亮肤色护理",
                "category", "暗沉色斑",
                "cause", "日晒、代谢缓慢或色素沉着",
                "observationFocus", "记录暗沉和色斑位置及颜色变化",
                "advice", "坚持防晒并采用温和的提亮护理"
            ),
            Map.of(
                "id", -7L,
                "title", "紧致抗老护理",
                "category", "细纹松弛",
                "cause", "胶原流失、干燥或光老化",
                "observationFocus", "观察眼周、法令纹及轮廓松弛",
                "advice", "加强保湿、防晒与循序渐进的抗老护理"
            ),
            Map.of(
                "id", -8L,
                "title", "屏障修护护理",
                "category", "屏障受损",
                "cause", "频繁刷酸、清洁过度或环境刺激",
                "observationFocus", "观察脱屑、刺痛与耐受度",
                "advice", "暂停强刺激护理，精简步骤并持续修护"
            )
        );
    }

    public Map<String, Object> detail(
        TenantPrincipal principal,
        long shopId,
        long serviceRecordId
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "care_record:view");
        requireOwnServiceRecordIfTechnician(principal, shopId, serviceRecordId);
        return detailInternal(principal.tenantId(), shopId, serviceRecordId);
    }

    public Map<String, Object> packageWriteOffContext(
        TenantPrincipal principal,
        long shopId,
        long serviceRecordId
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "package:writeoff");
        requireOwnServiceRecordIfTechnician(principal, shopId, serviceRecordId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, member_id AS memberId, status
            FROM service_record
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            LIMIT 1
            """,
            serviceRecordId,
            principal.tenantId(),
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "服务记录不存在");
        }
        Map<String, Object> context = new LinkedHashMap<>(rows.getFirst());
        context.put(
            "serviceIds",
            jdbcTemplate.queryForList(
                """
                SELECT service_id
                FROM service_record_item
                WHERE service_record_id = ?
                ORDER BY sort_order, id
                """,
                Long.class,
                serviceRecordId
            )
        );
        return context;
    }

    public List<Map<String, Object>> memberHistory(
        TenantPrincipal principal,
        long shopId,
        long memberId
    ) {
        tenantAccessService.requireShopPermission(principal, shopId, "care_record:view");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT sr.id, sr.record_no AS recordNo,
                   sr.actual_start_at AS actualStartAt,
                   sr.service_summary AS serviceSummary,
                   sr.next_visit_recommendation AS nextVisitRecommendation,
                   st.name AS staffName,
                   cr.skin_type AS skinType, cr.concerns,
                   cr.observations, cr.home_care_advice AS homeCareAdvice,
                   cr.next_recommended_at AS nextRecommendedAt,
                   (SELECT GROUP_CONCAT(sri.service_name_snapshot
                      ORDER BY sri.sort_order SEPARATOR '、')
                    FROM service_record_item sri
                    WHERE sri.service_record_id = sr.id) AS serviceNames
            FROM service_record sr
            JOIN staff st ON st.id = sr.staff_id
            LEFT JOIN care_record cr ON cr.service_record_id = sr.id
            WHERE sr.tenant_id = ? AND sr.shop_id = ?
              AND sr.member_id = ? AND sr.status = 'COMPLETED'
            ORDER BY sr.actual_start_at DESC, sr.id DESC
            LIMIT 50
            """,
            principal.tenantId(),
            shopId,
            memberId
        );
        for (Map<String, Object> row : rows) {
            row.put("concerns", readStringList(row.get("concerns")));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> start(
        TenantPrincipal principal,
        ServiceStartRequest request
    ) {
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "service_record:manage"
        );
        appointmentLifecycleService.startService(
            principal,
            shopId,
            request.appointmentId(),
            request.appointmentVersion(),
            "从护理工作台开始服务"
        );
        long serviceRecordId = lifecycleService.ensureInProgress(
            principal, shopId, request.appointmentId()
        );
        audit(
            principal, shopId, "SERVICE_RECORD_START",
            "SERVICE_RECORD", serviceRecordId,
            Map.of("appointmentId", request.appointmentId())
        );
        return detailInternal(principal.tenantId(), shopId, serviceRecordId);
    }

    @Transactional
    public Map<String, Object> complete(
        TenantPrincipal principal,
        long serviceRecordId,
        ServiceCompleteRequest request
    ) {
        long shopId = tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "service_record:manage"
        );
        tenantAccessService.requireShopPermission(principal, shopId, "care_record:manage");
        String idempotencyKey = request.idempotencyKey().trim();
        String requestHash = completionRequestHash(serviceRecordId, request);
        List<Map<String, Object>> replay = jdbcTemplate.queryForList(
            """
            SELECT id, completion_request_hash AS requestHash
            FROM service_record
            WHERE tenant_id = ? AND completion_idempotency_key = ?
            """,
            principal.tenantId(),
            idempotencyKey
        );
        if (!replay.isEmpty()) {
            long existingId = number(replay.getFirst().get("id"));
            if (existingId != serviceRecordId) {
                throw new ApiException(HttpStatus.CONFLICT, "完成服务幂等键已用于其他记录");
            }
            Object existingHash = replay.getFirst().get("requestHash");
            if (existingHash != null && !requestHash.equals(existingHash.toString())) {
                throw new ApiException(HttpStatus.CONFLICT, "完成服务幂等键已用于不同请求");
            }
            return detailInternal(principal.tenantId(), shopId, serviceRecordId);
        }
        Map<String, Object> serviceRecord = lockServiceRecord(
            principal.tenantId(), shopId, serviceRecordId
        );
        if (principal.roles().contains("BEAUTICIAN")
            && tenantAccessService.requireStaffId(principal)
                != number(serviceRecord.get("staffId"))) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "技师只能完成分配给自己的护理记录"
            );
        }
        requireVersion(serviceRecord, request.version(), "服务记录");
        if (!"IN_PROGRESS".equals(serviceRecord.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "只有服务中的记录可以完成");
        }
        Long appointmentId = nullableNumber(serviceRecord.get("appointmentId"));

        List<InventoryConsumptionLine> consumptionLines = new ArrayList<>();
        if (request.consumptions() != null) {
            for (ServiceConsumptionRequest line : request.consumptions()) {
                consumptionLines.add(new InventoryConsumptionLine(
                    line.locationId(),
                    line.productId(),
                    line.quantity(),
                    line.balanceVersion()
                ));
            }
        }
        List<Map<String, Object>> movements = inventoryService.consumeForService(
            principal,
            shopId,
            serviceRecordId,
            consumptionLines,
            idempotencyKey
        );
        for (int index = 0; index < movements.size(); index++) {
            Map<String, Object> movement = movements.get(index);
            ServiceConsumptionRequest line = request.consumptions().stream()
                .filter(candidate ->
                    candidate.locationId().longValue() == number(movement.get("locationId"))
                        && candidate.productId().longValue() == number(movement.get("productId"))
                )
                .findFirst()
                .orElseThrow();
            jdbcTemplate.update(
                """
                INSERT INTO service_record_consumption (
                    tenant_id, shop_id, service_record_id, location_id,
                    product_id, quantity, inventory_movement_id, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                principal.tenantId(),
                shopId,
                serviceRecordId,
                line.locationId(),
                line.productId(),
                line.quantity(),
                movement.get("movementId"),
                principal.accountId()
            );
        }

        jdbcTemplate.update(
            """
            INSERT INTO care_record (
                tenant_id, shop_id, service_record_id, member_id,
                skin_type, concerns, observations, products_used,
                home_care_advice, next_recommended_at, version,
                created_by, updated_by
            ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), ?, CAST(? AS JSON), ?, ?, 0, ?, ?)
            """,
            principal.tenantId(),
            shopId,
            serviceRecordId,
            serviceRecord.get("memberId"),
            trimToNull(request.skinType()),
            writeJson(request.concerns() == null ? List.of() : request.concerns()),
            request.observations().trim(),
            writeJson(productUsageJson(request.consumptions())),
            trimToNull(request.homeCareAdvice()),
            request.nextRecommendedAt(),
            principal.accountId(),
            principal.accountId()
        );
        int changed = jdbcTemplate.update(
            """
            UPDATE service_record
            SET actual_end_at = CASE
                  WHEN CURRENT_TIMESTAMP(3) > actual_start_at
                    THEN CURRENT_TIMESTAMP(3)
                  ELSE DATE_ADD(actual_start_at, INTERVAL 1 SECOND)
                END,
                service_summary = ?, next_visit_recommendation = ?,
                completion_idempotency_key = ?, completion_request_hash = ?,
                status = 'COMPLETED', fulfillment_status = 'PENDING_CUSTOMER_CONFIRMATION',
                version = version + 1, updated_by = ?
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
              AND version = ? AND status = 'IN_PROGRESS'
            """,
            request.serviceSummary().trim(),
            trimToNull(request.nextVisitRecommendation()),
            idempotencyKey,
            requestHash,
            principal.accountId(),
            serviceRecordId,
            principal.tenantId(),
            shopId,
            request.version()
        );
        if (changed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "服务记录已被其他人修改，请刷新后重试");
        }
        if (appointmentId != null) {
            appointmentLifecycleService.completeFromServiceRecord(
                principal,
                shopId,
                appointmentId,
                "护理已结束，等待会员确认"
            );
            transactionService.ensureOrderForCompletedService(
                principal,
                shopId,
                appointmentId,
                number(serviceRecord.get("memberId")),
                serviceRecordId
            );
        }
        customerConfirmationService.createPending(
            principal,
            shopId,
            appointmentId,
            serviceRecordId,
            number(serviceRecord.get("memberId"))
        );
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("serviceRecordId", serviceRecordId);
        eventPayload.put("memberId", number(serviceRecord.get("memberId")));
        if (appointmentId != null) {
            eventPayload.put("appointmentId", appointmentId);
        }
        outboxEventService.append(
            principal,
            shopId,
            "SERVICE_RECORD",
            String.valueOf(serviceRecordId),
            "ServiceRecordAwaitingCustomerConfirmation",
            eventPayload
        );
        audit(
            principal, shopId, "SERVICE_RECORD_AWAITING_CONFIRMATION",
            "SERVICE_RECORD", serviceRecordId,
            Map.of(
                "consumptionCount", movements.size(),
                "careRecordCreated", true
            )
        );
        return detailInternal(principal.tenantId(), shopId, serviceRecordId);
    }

    @Transactional
    public Map<String, Object> updateCare(
        TenantPrincipal principal,
        long serviceRecordId,
        CareUpdateRequest request
    ) {
        tenantAccessService.requireShopPermission(
            principal,
            request.shopId(),
            "care_record:manage"
        );
        throw new ApiException(
            HttpStatus.CONFLICT,
            "已完成护理事实不可覆盖，请使用追加更正接口"
        );
    }

    private Map<String, Object> detailInternal(
        long tenantId,
        long shopId,
        long serviceRecordId
    ) {
        Map<String, Object> result;
        try {
            result = jdbcTemplate.queryForMap(
                """
                SELECT sr.id, sr.record_no AS recordNo, sr.shop_id AS shopId,
                       sh.name AS shopName, sr.appointment_id AS appointmentId,
                       ap.appointment_no AS appointmentNo,
                       sr.member_id AS memberId, m.member_no AS memberNo,
                       m.name AS memberName, m.phone AS memberPhone,
                       sr.staff_id AS staffId, st.staff_no AS staffNo,
                       st.name AS staffName, st.level_name AS staffLevel,
                       sr.actual_start_at AS actualStartAt,
                       sr.actual_end_at AS actualEndAt,
                       sr.service_summary AS serviceSummary,
                       sr.next_visit_recommendation AS nextVisitRecommendation,
                       sr.status, sr.version,
                       cr.id AS careRecordId, cr.skin_type AS skinType,
                       cr.concerns, cr.observations, cr.products_used AS productsUsed,
                       cr.home_care_advice AS homeCareAdvice,
                       cr.next_recommended_at AS nextRecommendedAt,
                       cr.version AS careVersion,
                       sr.created_at AS createdAt, sr.updated_at AS updatedAt
                FROM service_record sr
                JOIN shop sh ON sh.id = sr.shop_id
                JOIN member m ON m.id = sr.member_id
                JOIN staff st ON st.id = sr.staff_id
                LEFT JOIN appointment ap ON ap.id = sr.appointment_id
                LEFT JOIN care_record cr ON cr.service_record_id = sr.id
                WHERE sr.id = ? AND sr.tenant_id = ? AND sr.shop_id = ?
                """,
                serviceRecordId,
                tenantId,
                shopId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "服务记录不存在");
        }
        result.put("concerns", readStringList(result.get("concerns")));
        result.put("productsUsed", readMapList(result.get("productsUsed")));
        result.put("items", jdbcTemplate.queryForList(
            """
            SELECT id, service_id AS serviceId,
                   service_name_snapshot AS serviceName,
                   duration_minutes_snapshot AS durationMinutes,
                   price_snapshot AS price, sort_order AS sortOrder
            FROM service_record_item
            WHERE service_record_id = ?
            ORDER BY sort_order, id
            """,
            serviceRecordId
        ));
        result.put("consumptions", jdbcTemplate.queryForList(
            """
            SELECT src.id, src.location_id AS locationId, sl.name AS locationName,
                   src.product_id AS productId, p.sku,
                   p.name AS productName, p.unit_name AS unitName,
                   src.quantity, src.inventory_movement_id AS inventoryMovementId,
                   src.created_at AS createdAt
            FROM service_record_consumption src
            JOIN stock_location sl ON sl.id = src.location_id
            JOIN product p ON p.id = src.product_id
            WHERE src.service_record_id = ?
            ORDER BY src.id
            """,
            serviceRecordId
        ));
        return result;
    }

    private Map<String, Object> lockServiceRecord(
        long tenantId,
        long shopId,
        long serviceRecordId
    ) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, appointment_id AS appointmentId, member_id AS memberId,
                   staff_id AS staffId, status, version
            FROM service_record
            WHERE id = ? AND tenant_id = ? AND shop_id = ?
            FOR UPDATE
            """,
            serviceRecordId,
            tenantId,
            shopId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "服务记录不存在");
        }
        return rows.getFirst();
    }

    private String buildWhere(
        TenantPrincipal principal,
        long shopId,
        LocalDate fromDate,
        LocalDate toDate,
        String status,
        String keyword,
        List<Object> args
    ) {
        StringBuilder where = new StringBuilder(
            "WHERE sr.tenant_id = ? AND sr.shop_id = ?"
                + " AND sr.actual_start_at >= ? AND sr.actual_start_at < ?"
        );
        args.add(principal.tenantId());
        args.add(shopId);
        args.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        args.add(Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        if (principal.roles().contains("BEAUTICIAN")) {
            where.append(" AND sr.staff_id = ?");
            args.add(tenantAccessService.requireStaffId(principal));
        }
        if (status != null) {
            where.append(" AND sr.status = ?");
            args.add(status);
        }
        if (keyword != null) {
            where.append(
                """
                 AND (
                   sr.record_no LIKE ? OR m.name LIKE ? OR m.phone LIKE ?
                   OR st.name LIKE ? OR EXISTS (
                     SELECT 1 FROM service_record_item sri
                     WHERE sri.service_record_id = sr.id
                       AND sri.service_name_snapshot LIKE ?
                   )
                 )
                """
            );
            String like = "%" + keyword + "%";
            for (int index = 0; index < 5; index++) args.add(like);
        }
        return where.toString();
    }

    private String normalizeStatus(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("IN_PROGRESS", "COMPLETED", "VOID").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的服务记录状态");
        }
        return normalized;
    }

    private List<Map<String, Object>> productUsageJson(
        List<ServiceConsumptionRequest> consumptions
    ) {
        if (consumptions == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ServiceConsumptionRequest line : consumptions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", line.productId());
            item.put("locationId", line.locationId());
            item.put("quantity", line.quantity());
            result.add(item);
        }
        return result;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "护理档案内容格式不正确");
        }
    }

    private String completionRequestHash(
        long serviceRecordId,
        ServiceCompleteRequest request
    ) {
        return SessionTokenCodec.sha256(
            serviceRecordId + "|"
                + request.version() + "|"
                + request.serviceSummary().trim() + "|"
                + String.valueOf(request.nextVisitRecommendation()) + "|"
                + String.valueOf(request.skinType()) + "|"
                + writeJson(request.concerns() == null ? List.of() : request.concerns()) + "|"
                + request.observations().trim() + "|"
                + String.valueOf(request.homeCareAdvice()) + "|"
                + String.valueOf(request.nextRecommendedAt()) + "|"
                + writeJson(request.consumptions() == null ? List.of() : request.consumptions())
        );
    }

    private void requireOwnServiceRecordIfTechnician(
        TenantPrincipal principal,
        long shopId,
        long serviceRecordId
    ) {
        if (!principal.roles().contains("BEAUTICIAN")) {
            return;
        }
        long staffId = tenantAccessService.requireStaffId(principal);
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM service_record
            WHERE id = ? AND tenant_id = ? AND shop_id = ? AND staff_id = ?
            """,
            Integer.class,
            serviceRecordId,
            principal.tenantId(),
            shopId,
            staffId
        );
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "技师只能查看分配给自己的护理记录");
        }
    }

    private List<String> readStringList(Object value) {
        if (value == null) return List.of();
        try {
            return objectMapper.readValue(value.toString(), STRING_LIST);
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private List<Map<String, Object>> readMapList(Object value) {
        if (value == null) return List.of();
        try {
            return objectMapper.readValue(value.toString(), MAP_LIST);
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private void requireVersion(Map<String, Object> row, int expected, String entityName) {
        if (number(row.get("version")) != expected) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                entityName + "已被其他人修改，请刷新后重试"
            );
        }
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Long nullableNumber(Object value) {
        return value == null ? null : number(value);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void audit(
        TenantPrincipal principal,
        long shopId,
        String action,
        String entityType,
        long entityId,
        Map<String, Object> data
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_log (
                tenant_id, shop_id, account_id, action,
                entity_type, entity_id, after_data
            ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON))
            """,
            principal.tenantId(),
            shopId,
            principal.accountId(),
            action,
            entityType,
            entityId,
            writeJson(data)
        );
    }
}
