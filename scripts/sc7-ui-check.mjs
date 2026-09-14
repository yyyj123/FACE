import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(process.argv[2] ?? process.cwd())
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8')
const requireText = (file, patterns) => {
  const source = read(file)
  for (const pattern of patterns) {
    if (!source.includes(pattern)) throw new Error(`${file} is missing ${pattern}`)
  }
}

requireText('admin-next/src/views/DataMigrationView.vue', [
  '老客户与历史卡导入', '下载 Excel 模板', '上传并预检', '冲突与错误不会覆盖现有资料',
  '正式导入仅超级管理员可执行', '@media (max-width: 900px)', '.migration-page { display: none; }',
])
requireText('admin-next/src/views/MobileOperationsView.vue', [
  '移动高频管理', '今日预约', '技师日程', '候补与履约提醒', '订单与发货', '售后沟通',
])
requireText('admin-next/src/components/AppShell.vue', [
  '今日工作台', '数据迁移', 'desktop-only-nav', 'SC7 数据迁移与运营完善',
])
requireText('admin-next/src/services/api.ts', [
  "'X-FACE-Admin-Surface': 'DESKTOP'", '/api/v3/legacy-import/preflight',
  '/api/v3/operations/workbench', 'currentAdminSurface', "'MOBILE' : 'DESKTOP'",
])
requireText('backend-next/src/main/java/com/face/platform/security/AdminSurfaceGuardFilter.java', [
  '/api/v3/legacy-import', '/api/v3/admin-accounts', '/api/v3/integrations/clients',
  '/api/v3/payment-settings', '/api/v3/system-initialization', '/api/v3/bulk-assets',
  'ADMIN_SURFACE_FORBIDDEN',
])
requireText('backend-next/src/main/java/com/face/platform/legacyimport/LegacyImportController.java', [
  '批量导入只能通过桌面端运营管理平台执行', 'X-FACE-Admin-Surface',
])
requireText('backend-next/src/main/java/com/face/platform/legacyimport/LegacyImportService.java', [
  'SUPER_ADMIN', 'LEGACY_IMPORT', 'PROFILE_CONFLICT', 'file_sha256',
])

const workbench = read('admin-next/src/views/MobileOperationsView.vue')
if (workbench.includes('正式导入')) throw new Error('Mobile workbench must not expose formal import actions.')
const shell = read('admin-next/src/components/AppShell.vue')
if (shell.includes('技师端')) throw new Error('Public admin shell must not expose an independent technician entry.')
console.log('SC7_RESPONSIVE_IMPORT_AND_OPERATIONS_CONTRACT=PASS')
