-- Complete the intended demo technician coverage without weakening the
-- production booking rule: only staff explicitly mapped in staff_service are
-- eligible for a service.
INSERT INTO staff_service (staff_id, service_id, enabled)
SELECT DISTINCT st.id, si.id, 1
FROM staff st
JOIN staff_shop_assignment ssa
  ON ssa.staff_id = st.id
 AND ssa.tenant_id = st.tenant_id
 AND ssa.status = 'ACTIVE'
 AND ssa.effective_from <= CURRENT_DATE
 AND (ssa.effective_to IS NULL OR ssa.effective_to >= CURRENT_DATE)
JOIN service_item si
  ON si.tenant_id = st.tenant_id
 AND si.shop_id = ssa.shop_id
 AND si.status = 'ACTIVE'
WHERE st.staff_no LIKE 'DEMO-ST-%'
  AND st.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM staff_service existing
      WHERE existing.staff_id = st.id
        AND existing.service_id = si.id
  );
