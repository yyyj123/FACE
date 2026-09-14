<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  assignTrainingRecord,
  commandIntegrationClient,
  createIntegrationClient,
  createTrainingCourse,
  getIntegrationClients,
  getStaffDirectory,
  getTrainingCourses,
  getTrainingRecords,
  transitionTrainingCourse,
  verifyTrainingRecord,
  type IntegrationClient,
  type StaffDirectoryItem,
  type TrainingCourse,
  type TrainingRecord,
} from '../services/api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const activeArea = ref<'training' | 'integration'>('training')
const loading = ref(true)
const busy = ref(false)
const shopId = ref<number>()
const courses = ref<TrainingCourse[]>([])
const records = ref<TrainingRecord[]>([])
const staff = ref<StaffDirectoryItem[]>([])
const clients = ref<IntegrationClient[]>([])
const showCourseForm = ref(false)
const showAssignForm = ref(false)
const showClientForm = ref(false)
const secretReveal = ref<{ clientName: string; secret: string }>()

const permissions = computed(() => auth.context?.permissions ?? [])
const canManageTraining = computed(() => permissions.value.includes('training:manage'))
const canVerifyTraining = computed(() => permissions.value.includes('training:verify'))
const canManageIntegration = computed(() => permissions.value.includes('integration:manage'))
const canRotateIntegration = computed(() => permissions.value.includes('integration:rotate'))
const activeCourses = computed(() => courses.value.filter((course) => course.status === 'ACTIVE'))

const courseForm = reactive({ courseCode: '', revision: 1, title: '', summary: '', passScore: 80, validityDays: 365 })
const assignForm = reactive({ courseId: undefined as number | undefined, staffId: undefined as number | undefined, dueAt: '' })
const clientForm = reactive({ clientCode: '', clientName: '', description: '', rateLimit: 30 })

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  shopId.value = auth.shops[0]?.id
  await load()
})

async function load() {
  if (!shopId.value) return
  loading.value = true
  try {
    const [courseData, recordData, staffData, clientData] = await Promise.all([
      getTrainingCourses(shopId.value),
      getTrainingRecords(shopId.value),
      getStaffDirectory(shopId.value),
      getIntegrationClients(shopId.value),
    ])
    courses.value = courseData
    records.value = recordData
    staff.value = staffData.filter((item) => item.status === 'ACTIVE')
    clients.value = clientData
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '培训与开放平台加载失败')
  } finally {
    loading.value = false
  }
}

async function createCourse() {
  if (!shopId.value) return
  busy.value = true
  try {
    await createTrainingCourse({
      shop_id: shopId.value,
      course_code: courseForm.courseCode,
      revision: courseForm.revision,
      title: courseForm.title,
      safe_summary: courseForm.summary,
      pass_score: courseForm.passScore,
      validity_days: courseForm.validityDays,
    })
    showCourseForm.value = false
    Object.assign(courseForm, { courseCode: '', revision: 1, title: '', summary: '', passScore: 80, validityDays: 365 })
    ElMessage.success('课程草稿已创建')
    await load()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '课程创建失败')
  } finally { busy.value = false }
}

async function courseAction(course: TrainingCourse, action: 'publish' | 'retire') {
  if (!shopId.value) return
  busy.value = true
  try {
    await transitionTrainingCourse(course, shopId.value, action)
    ElMessage.success(action === 'publish' ? '课程已发布，内容不可覆盖' : '课程已停用')
    await load()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '课程状态更新失败')
  } finally { busy.value = false }
}

async function assignTraining() {
  if (!shopId.value || !assignForm.courseId || !assignForm.staffId) {
    ElMessage.warning('请选择已发布课程和受训员工')
    return
  }
  busy.value = true
  try {
    await assignTrainingRecord({
      shop_id: shopId.value,
      course_id: assignForm.courseId,
      staff_id: assignForm.staffId,
      due_at: assignForm.dueAt || undefined,
    })
    showAssignForm.value = false
    Object.assign(assignForm, { courseId: undefined, staffId: undefined, dueAt: '' })
    ElMessage.success('培训已分配')
    await load()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '培训分配失败')
  } finally { busy.value = false }
}

async function verify(record: TrainingRecord) {
  if (!shopId.value) return
  try {
    const { value: scoreText } = await ElMessageBox.prompt('请输入 0–100 的验证得分', '验证培训结果', {
      inputPattern: /^(100|[1-9]?\d)$/,
      inputErrorMessage: '请输入 0–100 的整数',
      confirmButtonText: '继续',
    })
    const { value: reason } = await ElMessageBox.prompt('填写不包含客户信息的验证说明', '验证说明', {
      inputPattern: /\S+/,
      inputErrorMessage: '验证说明不能为空',
      confirmButtonText: '确认验证',
    })
    busy.value = true
    await verifyTrainingRecord(record, shopId.value, Number(scoreText), reason)
    ElMessage.success('培训结果已验证并保留历史')
    await load()
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : '培训验证失败')
  } finally { busy.value = false }
}

async function createClient() {
  if (!shopId.value) return
  busy.value = true
  try {
    const result = await createIntegrationClient({
      shop_id: shopId.value,
      client_code: clientForm.clientCode,
      client_name: clientForm.clientName,
      safe_description: clientForm.description || undefined,
      scopes: ['catalog:read'],
      rate_limit_per_minute: clientForm.rateLimit,
    })
    if (result.clientSecret) secretReveal.value = { clientName: result.clientName, secret: result.clientSecret }
    showClientForm.value = false
    Object.assign(clientForm, { clientCode: '', clientName: '', description: '', rateLimit: 30 })
    ElMessage.success('客户端已创建，请立即保存一次性密钥')
    await load()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '集成客户端创建失败')
  } finally { busy.value = false }
}

async function clientAction(client: IntegrationClient, action: 'rotate-secret' | 'revoke') {
  if (!shopId.value) return
  try {
    const { value: reason } = await ElMessageBox.prompt(
      action === 'rotate-secret' ? '说明本次密钥轮换原因' : '说明撤销原因，撤销后不可恢复',
      action === 'rotate-secret' ? '轮换客户端密钥' : '撤销集成客户端',
      { inputPattern: /\S+/, inputErrorMessage: '原因不能为空', confirmButtonText: '确认' },
    )
    busy.value = true
    const result = await commandIntegrationClient(client, shopId.value, action, reason)
    if (result.clientSecret) secretReveal.value = { clientName: result.clientName, secret: result.clientSecret }
    ElMessage.success(action === 'rotate-secret' ? '密钥已轮换，旧密钥立即失效' : '客户端已撤销')
    await load()
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : '客户端状态更新失败')
  } finally { busy.value = false }
}

async function copySecret() {
  if (!secretReveal.value) return
  await navigator.clipboard.writeText(secretReveal.value.secret)
  ElMessage.success('密钥已复制，请保存到密钥管理系统')
}

function statusLabel(status: string) {
  return {
    DRAFT: '草稿', ACTIVE: '已发布', RETIRED: '已停用', ASSIGNED: '待开始',
    IN_PROGRESS: '进行中', SUBMITTED: '待验证', PASSED: '已通过', FAILED: '未通过',
    EXPIRED: '已过期', CANCELLED: '已取消', REVOKED: '已撤销',
  }[status] ?? status
}

function courseRow(value: unknown) { return value as TrainingCourse }
function trainingRow(value: unknown) { return value as TrainingRecord }
function clientRow(value: unknown) { return value as IntegrationClient }
</script>

<template>
  <section class="training-integration-page">
    <header class="page-heading">
      <div>
        <h1>培训与开放平台</h1>
        <p>把员工资格与外部访问放在可追溯边界内；密钥只显示一次，开放接口只返回公开项目目录。</p>
      </div>
      <el-select v-model="shopId" class="shop-picker" aria-label="选择门店" @change="load">
        <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
      </el-select>
    </header>

    <nav class="area-switch" aria-label="任务区域">
      <button type="button" :aria-current="activeArea === 'training' ? 'page' : undefined" @click="activeArea = 'training'">员工培训</button>
      <button type="button" :aria-current="activeArea === 'integration' ? 'page' : undefined" @click="activeArea = 'integration'">开放平台</button>
    </nav>

    <div v-if="secretReveal" class="secret-reveal" role="alert">
      <div>
        <strong>{{ secretReveal.clientName }} 的密钥仅显示本次</strong>
        <code>{{ secretReveal.secret }}</code>
        <span>请立即保存到受控密钥管理系统。离开或关闭后无法再次查看。</span>
      </div>
      <div class="secret-actions">
        <el-button type="primary" @click="copySecret">复制密钥</el-button>
        <el-button @click="secretReveal = undefined">我已安全保存</el-button>
      </div>
    </div>

    <div v-loading="loading" class="task-surface">
      <template v-if="activeArea === 'training'">
        <section class="section-heading">
          <div><h2>课程版本</h2><p>发布后内容不可覆盖；调整课程时创建新版本。</p></div>
          <el-button v-if="canManageTraining" type="primary" @click="showCourseForm = true">新建课程版本</el-button>
        </section>
        <el-table :data="courses" empty-text="暂无课程版本">
          <el-table-column prop="courseCode" label="课程编码" width="150" />
          <el-table-column label="课程" min-width="260">
            <template #default="scope"><div class="primary-cell"><strong>{{ scope.row.title }}</strong><span>{{ scope.row.safeSummary }}</span></div></template>
          </el-table-column>
          <el-table-column label="规则" width="170"><template #default="scope">通过分 {{ scope.row.passScore }} · {{ scope.row.validityDays ? `${scope.row.validityDays} 天` : '长期有效' }}</template></el-table-column>
          <el-table-column label="状态" width="100"><template #default="scope">{{ statusLabel(scope.row.status) }}</template></el-table-column>
          <el-table-column label="操作" width="170"><template #default="scope"><el-button v-if="canManageTraining && scope.row.status === 'DRAFT'" link type="primary" :disabled="busy" @click="courseAction(courseRow(scope.row), 'publish')">发布</el-button><el-button v-if="canManageTraining && scope.row.status === 'ACTIVE'" link :disabled="busy" @click="courseAction(courseRow(scope.row), 'retire')">停用</el-button></template></el-table-column>
        </el-table>

        <section class="section-heading records-heading">
          <div><h2>员工培训记录</h2><p>员工本人提交，具备验证权限的其他账号确认结果。</p></div>
          <el-button v-if="canManageTraining" :disabled="!activeCourses.length" @click="showAssignForm = true">分配培训</el-button>
        </section>
        <el-table :data="records" empty-text="暂无培训记录">
          <el-table-column label="员工" width="170"><template #default="scope"><div class="primary-cell"><strong>{{ scope.row.staffName }}</strong><span>{{ scope.row.staffNo }}</span></div></template></el-table-column>
          <el-table-column label="课程" min-width="220"><template #default="scope">{{ scope.row.courseTitle }} · V{{ scope.row.courseRevision }}</template></el-table-column>
          <el-table-column label="状态" width="110"><template #default="scope">{{ statusLabel(scope.row.status) }}</template></el-table-column>
          <el-table-column label="结果" width="170"><template #default="scope">{{ scope.row.score == null ? '—' : `${scope.row.score} 分` }}<small v-if="scope.row.certificateNo" class="certificate">{{ scope.row.certificateNo }}</small></template></el-table-column>
          <el-table-column label="操作" width="130"><template #default="scope"><el-button v-if="canVerifyTraining && scope.row.status === 'SUBMITTED'" link type="primary" :disabled="busy" @click="verify(trainingRow(scope.row))">验证结果</el-button></template></el-table-column>
        </el-table>
      </template>

      <template v-else>
        <section class="integration-boundary" role="note">
          <strong>当前开放边界</strong>
          <span>仅 `catalog:read` · 仅客户端绑定门店 · 每请求时间戳与 Nonce · 数据库限流与审计</span>
          <span>会员、护理、健康、支付、库存和员工联系方式均未开放。</span>
        </section>
        <section class="section-heading">
          <div><h2>集成客户端</h2><p>数据库仅保存密钥摘要；列表和日志永不回显完整密钥。</p></div>
          <el-button v-if="canManageIntegration" type="primary" @click="showClientForm = true">创建客户端</el-button>
        </section>
        <el-table :data="clients" empty-text="暂无集成客户端">
          <el-table-column label="客户端" min-width="240"><template #default="scope"><div class="primary-cell"><strong>{{ scope.row.clientName }}</strong><span>{{ scope.row.clientCode }} · {{ scope.row.secretPrefix }}…</span></div></template></el-table-column>
          <el-table-column label="作用域" width="150"><template #default>catalog:read</template></el-table-column>
          <el-table-column label="限流" width="130"><template #default="scope">{{ scope.row.rateLimitPerMinute }} 次/分钟</template></el-table-column>
          <el-table-column label="状态" width="100"><template #default="scope">{{ statusLabel(scope.row.status) }}</template></el-table-column>
          <el-table-column prop="lastUsedAt" label="最近调用" width="190" />
          <el-table-column label="操作" width="190"><template #default="scope"><template v-if="canRotateIntegration && scope.row.status === 'ACTIVE'"><el-button link type="primary" :disabled="busy" @click="clientAction(clientRow(scope.row), 'rotate-secret')">轮换密钥</el-button><el-button link type="danger" :disabled="busy" @click="clientAction(clientRow(scope.row), 'revoke')">撤销</el-button></template></template></el-table-column>
        </el-table>
      </template>
    </div>

    <el-dialog v-model="showCourseForm" title="新建课程版本" width="520px">
      <el-form label-position="top">
        <div class="form-pair"><el-form-item label="课程编码"><el-input v-model="courseForm.courseCode" placeholder="例如 SKIN_CARE_BASE" /></el-form-item><el-form-item label="版本"><el-input-number v-model="courseForm.revision" :min="1" /></el-form-item></div>
        <el-form-item label="课程名称"><el-input v-model="courseForm.title" /></el-form-item>
        <el-form-item label="安全摘要"><el-input v-model="courseForm.summary" type="textarea" :rows="3" placeholder="不要填写客户或健康信息" /></el-form-item>
        <div class="form-pair"><el-form-item label="通过分"><el-input-number v-model="courseForm.passScore" :min="0" :max="100" /></el-form-item><el-form-item label="有效天数"><el-input-number v-model="courseForm.validityDays" :min="1" :max="3650" /></el-form-item></div>
      </el-form>
      <template #footer><el-button @click="showCourseForm = false">返回</el-button><el-button type="primary" :loading="busy" @click="createCourse">创建草稿</el-button></template>
    </el-dialog>

    <el-dialog v-model="showAssignForm" title="分配培训" width="500px">
      <el-form label-position="top">
        <el-form-item label="已发布课程"><el-select v-model="assignForm.courseId" class="full-width"><el-option v-for="course in activeCourses" :key="course.id" :label="`${course.title} · V${course.revision}`" :value="course.id" /></el-select></el-form-item>
        <el-form-item label="受训员工"><el-select v-model="assignForm.staffId" class="full-width"><el-option v-for="item in staff" :key="item.id" :label="`${item.name} · ${item.staffNo}`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="截止时间（可选）"><el-date-picker v-model="assignForm.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" class="full-width" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showAssignForm = false">返回</el-button><el-button type="primary" :loading="busy" @click="assignTraining">确认分配</el-button></template>
    </el-dialog>

    <el-dialog v-model="showClientForm" title="创建集成客户端" width="520px">
      <el-alert title="密钥只显示一次" description="创建后立即复制并保存；系统不会在列表、日志或后续详情中再次返回。" type="warning" :closable="false" />
      <el-form label-position="top" class="client-form">
        <div class="form-pair"><el-form-item label="客户端编码"><el-input v-model="clientForm.clientCode" placeholder="例如 CRM_SYNC" /></el-form-item><el-form-item label="每分钟上限"><el-input-number v-model="clientForm.rateLimit" :min="1" :max="60" /></el-form-item></div>
        <el-form-item label="客户端名称"><el-input v-model="clientForm.clientName" /></el-form-item>
        <el-form-item label="用途说明"><el-input v-model="clientForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="作用域"><el-input model-value="catalog:read（仅公开项目目录）" disabled /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showClientForm = false">返回</el-button><el-button type="primary" :loading="busy" @click="createClient">创建并显示密钥</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.training-integration-page { display: grid; gap: 24px; }
.page-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; }
.page-heading h1, .section-heading h2 { margin: 0; }
.page-heading p, .section-heading p { max-width: 72ch; margin: 8px 0 0; color: var(--oc-text-muted); }
.shop-picker { width: 220px; }
.area-switch { display: flex; gap: 4px; border-bottom: 1px solid var(--oc-border); }
.area-switch button { padding: 12px 18px; border: 0; border-bottom: 2px solid transparent; background: transparent; color: var(--oc-text-muted); cursor: pointer; }
.area-switch button[aria-current='page'] { border-bottom-color: var(--oc-accent); color: var(--oc-text); font-weight: 700; }
.area-switch button:focus-visible { outline: 3px solid var(--oc-focus); outline-offset: 2px; }
.task-surface { min-height: 360px; }
.section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.records-heading { margin-top: 42px; }
.primary-cell { display: grid; gap: 4px; }
.primary-cell span, .certificate { display: block; color: var(--oc-text-muted); font-size: 12px; }
.secret-reveal { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 18px; border: 1px solid var(--oc-border-strong); border-radius: 10px; background: var(--oc-surface-2); }
.secret-reveal > div:first-child { display: grid; gap: 7px; min-width: 0; }
.secret-reveal code { overflow-wrap: anywhere; font-size: 14px; }
.secret-reveal span { color: var(--oc-text-muted); }
.secret-actions { display: flex; flex-shrink: 0; gap: 8px; }
.integration-boundary { display: grid; gap: 6px; margin-bottom: 28px; padding: 16px 18px; border: 1px solid var(--oc-border-strong); border-radius: 10px; background: var(--oc-surface-2); }
.integration-boundary span { color: var(--oc-text-muted); }
.form-pair { display: grid; grid-template-columns: minmax(0, 1fr) minmax(140px, .5fr); gap: 16px; }
.full-width { width: 100%; }
.client-form { margin-top: 18px; }
@media (max-width: 760px) {
  .page-heading, .section-heading, .secret-reveal { align-items: stretch; flex-direction: column; }
  .shop-picker { width: 100%; }
  .secret-actions { flex-wrap: wrap; }
  .form-pair { grid-template-columns: 1fr; gap: 0; }
}
@media (prefers-reduced-motion: reduce) { .area-switch button { transition: none; } }
</style>
