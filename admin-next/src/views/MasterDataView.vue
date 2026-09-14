<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, Close, EditPen, Plus, Refresh, Tools } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import ImageUploadField from '../components/ImageUploadField.vue'
import { resolveMediaUrl } from '../utils/media'
import {
  changeStaffSkill,
  createServiceResource,
  createStaffSchedule,
  deactivateServiceResource,
  deactivateStaffSchedule,
  getServiceCatalog,
  getServiceResources,
  getStaffDirectory,
  getStaffSchedules,
  getStaffSkills,
  updateServiceCatalogItem,
  updateServiceResource,
  type ServiceCatalogItem,
  type ServiceResourceItem,
  type StaffDirectoryItem,
  type StaffScheduleItem,
  type StaffSkillItem,
} from '../services/api'

type TabKey = 'services' | 'resources' | 'skills' | 'schedules'
type EditorKind = 'service' | 'resource' | 'skill' | 'schedule' | null

const auth = useAuthStore()
const activeTab = ref<TabKey>('services')
const selectedShopId = ref<number>()
const selectedStaffId = ref<number>()
const services = ref<ServiceCatalogItem[]>([])
const resources = ref<ServiceResourceItem[]>([])
const staff = ref<StaffDirectoryItem[]>([])
const skills = ref<StaffSkillItem[]>([])
const schedules = ref<StaffScheduleItem[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const skillActionServiceId = ref<number>()
const error = ref('')
const editor = ref<EditorKind>(null)

const serviceForm = reactive({
  id: 0,
  name: '',
  coverUrl: '',
  durationMinutes: 60,
  cleanupMinutes: 10,
  listPrice: 0,
  memberPrice: 0,
  status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE',
  version: 1,
})

const resourceForm = reactive({
  id: 0,
  resourceCode: '',
  resourceName: '',
  resourceType: 'ROOM' as 'ROOM' | 'EQUIPMENT',
  capacity: 1,
  version: 1,
})

const skillForm = reactive({
  serviceId: undefined as number | undefined,
  enabled: true,
  customDurationMinutes: undefined as number | undefined,
  effectiveFrom: '',
  version: 0,
})

const scheduleForm = reactive({
  scheduleDate: '',
  startTime: '09:00:00',
  endTime: '18:00:00',
  scheduleType: 'WORK' as 'WORK' | 'LEAVE' | 'BLOCKED',
  remark: '',
})

const permissions = computed(() => auth.context?.permissions ?? [])
const canManageServices = computed(() => permissions.value.includes('service:manage'))
const canManageResources = computed(() => permissions.value.includes('resource:manage'))
const canManageStaff = computed(() => permissions.value.includes('staff:manage'))
const selectedStaff = computed(() => staff.value.find((item) => item.id === selectedStaffId.value))
const currentShop = computed(() => auth.shops.find((item) => item.id === selectedShopId.value))
const editorOpen = computed(() => editor.value !== null)
const newSkillServices = computed(() => services.value.filter((service) =>
  service.status === 'ACTIVE' && !skills.value.some((skill) => skill.serviceId === service.id),
))
const skillServiceOptions = computed(() => services.value.filter((service) => {
  if (service.status !== 'ACTIVE') return false
  if (skillForm.version > 0) return service.id === skillForm.serviceId
  return newSkillServices.value.some((item) => item.id === service.id)
}))
const activeCount = computed(() => ({
  services: services.value.filter((item) => item.status === 'ACTIVE').length,
  resources: resources.value.filter((item) => item.status === 'ACTIVE').length,
  staff: staff.value.filter((item) => item.status === 'ACTIVE').length,
}))

const tabs: Array<{ key: TabKey; label: string; count: () => number }> = [
  { key: 'services', label: '护理项目', count: () => services.value.length },
  { key: 'resources', label: '房间与设备', count: () => resources.value.length },
  { key: 'skills', label: '技师技能', count: () => skills.value.length },
  { key: 'schedules', label: '技师排班', count: () => schedules.value.length },
]

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  selectedShopId.value =
    auth.context?.homeShopId ?? auth.shops.find((item) => item.status === 'ACTIVE')?.id
})

watch(selectedShopId, async (shopId) => {
  closeEditor()
  selectedStaffId.value = undefined
  skills.value = []
  schedules.value = []
  if (shopId) await loadBaseData()
})

watch(selectedStaffId, async (staffId) => {
  closeEditor()
  if (!staffId || !selectedShopId.value) {
    skills.value = []
    schedules.value = []
    return
  }
  await loadStaffDetail()
})

watch(activeTab, async (tab) => {
  closeEditor()
  if ((tab === 'skills' || tab === 'schedules') && !selectedStaffId.value && staff.value.length) {
    selectedStaffId.value = staff.value[0]?.id
    await nextTick()
  }
})

async function loadBaseData() {
  if (!selectedShopId.value) return
  loading.value = true
  error.value = ''
  try {
    const [serviceData, resourceData, staffData] = await Promise.all([
      getServiceCatalog(selectedShopId.value),
      getServiceResources(selectedShopId.value),
      getStaffDirectory(selectedShopId.value),
    ])
    services.value = serviceData
    resources.value = resourceData
    staff.value = staffData
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '基础资料加载失败'
  } finally {
    loading.value = false
  }
}

async function reloadServices() {
  if (!selectedShopId.value) return
  services.value = await getServiceCatalog(selectedShopId.value)
}

async function reloadResources() {
  if (!selectedShopId.value) return
  resources.value = await getServiceResources(selectedShopId.value)
}

async function loadStaffDetail() {
  if (!selectedShopId.value || !selectedStaffId.value) return
  detailLoading.value = true
  error.value = ''
  try {
    const [skillData, scheduleData] = await Promise.all([
      getStaffSkills(selectedShopId.value, selectedStaffId.value),
      getStaffSchedules(selectedShopId.value, selectedStaffId.value),
    ])
    skills.value = skillData
    schedules.value = scheduleData
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '技师配置加载失败'
  } finally {
    detailLoading.value = false
  }
}

function closeEditor() {
  editor.value = null
}

function editService(item: ServiceCatalogItem) {
  Object.assign(serviceForm, {
    id: item.id,
    name: item.name,
    coverUrl: item.coverUrl ?? '',
    durationMinutes: item.durationMinutes,
    cleanupMinutes: item.cleanupMinutes,
    listPrice: Number(item.listPrice),
    memberPrice: Number(item.memberPrice),
    status: item.status,
    version: item.version,
  })
  editor.value = 'service'
}

function editResource(item?: ServiceResourceItem) {
  Object.assign(resourceForm, {
    id: item?.id ?? 0,
    resourceCode: item?.resourceCode ?? '',
    resourceName: item?.resourceName ?? '',
    resourceType: item?.resourceType ?? 'ROOM',
    capacity: item?.capacity ?? 1,
    version: item?.version ?? 1,
  })
  editor.value = 'resource'
}

function editSkill(item?: StaffSkillItem) {
  Object.assign(skillForm, {
    serviceId: item?.serviceId,
    enabled: true,
    customDurationMinutes: item?.customDurationMinutes,
    effectiveFrom: nextSkillEffectiveFrom(item),
    version: item?.version ?? 0,
  })
  editor.value = 'skill'
}

function createSchedule() {
  Object.assign(scheduleForm, {
    scheduleDate: formatDate(new Date()),
    startTime: '09:00:00',
    endTime: '18:00:00',
    scheduleType: 'WORK',
    remark: '',
  })
  editor.value = 'schedule'
}

async function saveService() {
  if (!selectedShopId.value || !serviceForm.name.trim()) return
  saving.value = true
  try {
    await updateServiceCatalogItem(serviceForm.id, {
      shop_id: selectedShopId.value,
      name: serviceForm.name.trim(),
      cover_url: serviceForm.coverUrl,
      duration_minutes: serviceForm.durationMinutes,
      cleanup_minutes: serviceForm.cleanupMinutes,
      list_price: serviceForm.listPrice,
      member_price: serviceForm.memberPrice,
      status: serviceForm.status,
      version: serviceForm.version,
    })
    ElMessage.success('护理项目已保存')
    closeEditor()
    await reloadServices()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '护理项目保存失败')
  } finally {
    saving.value = false
  }
}

async function saveResource() {
  if (!selectedShopId.value || !resourceForm.resourceCode || !resourceForm.resourceName) return
  saving.value = true
  try {
    const payload = {
      shop_id: selectedShopId.value,
      resource_code: resourceForm.resourceCode.trim().toUpperCase(),
      resource_name: resourceForm.resourceName.trim(),
      resource_type: resourceForm.resourceType,
      capacity: resourceForm.capacity,
    }
    if (resourceForm.id) {
      await updateServiceResource(resourceForm.id, {
        ...payload,
        version: resourceForm.version,
      })
    } else {
      await createServiceResource(payload)
    }
    ElMessage.success(resourceForm.id ? '资源资料已更新' : '资源已新增')
    closeEditor()
    await reloadResources()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '资源保存失败')
  } finally {
    saving.value = false
  }
}

async function saveSkill() {
  if (!selectedShopId.value || !selectedStaffId.value || !skillForm.serviceId) return
  saving.value = true
  try {
    await changeStaffSkill(
      selectedShopId.value,
      selectedStaffId.value,
      skillForm.serviceId,
      {
        enabled: true,
        custom_duration_minutes: skillForm.customDurationMinutes,
        effective_from: skillForm.effectiveFrom,
        version: skillForm.version,
      },
    )
    ElMessage.success(skillForm.version ? '技能修改已保存' : '技能已新增')
    closeEditor()
    await loadStaffDetail()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '技师技能保存失败')
  } finally {
    saving.value = false
  }
}

async function changeSkillStatus(item: StaffSkillItem, enabled: boolean) {
  if (!selectedShopId.value || !selectedStaffId.value) return
  try {
    await ElMessageBox.confirm(
      enabled
        ? `恢复“${item.serviceName}”后，这位技师将重新参与该项目的预约匹配。`
        : `删除“${item.serviceName}”后，这位技师将不再接收该项目的新预约，历史预约不会删除。`,
      enabled ? '确认恢复技能' : '确认删除技能',
      {
        confirmButtonText: enabled ? '确认恢复' : '确认删除',
        cancelButtonText: '返回',
        type: enabled ? 'info' : 'warning',
      },
    )
    skillActionServiceId.value = item.serviceId
    await changeStaffSkill(
      selectedShopId.value,
      selectedStaffId.value,
      item.serviceId,
      {
        enabled,
        custom_duration_minutes: item.customDurationMinutes,
        effective_from: nextSkillEffectiveFrom(item),
        version: item.version,
      },
    )
    ElMessage.success(enabled ? '技能已恢复' : '技能已删除')
    await loadStaffDetail()
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : enabled ? '技能恢复失败' : '技能删除失败')
  } finally {
    skillActionServiceId.value = undefined
  }
}

async function saveSchedule() {
  if (!selectedShopId.value || !selectedStaffId.value || !scheduleForm.scheduleDate) return
  saving.value = true
  try {
    const wholeDay = scheduleForm.scheduleType !== 'WORK'
      && !scheduleForm.startTime
      && !scheduleForm.endTime
    await createStaffSchedule(selectedStaffId.value, {
      shop_id: selectedShopId.value,
      schedule_date: scheduleForm.scheduleDate,
      start_time: wholeDay ? undefined : scheduleForm.startTime,
      end_time: wholeDay ? undefined : scheduleForm.endTime,
      schedule_type: scheduleForm.scheduleType,
      remark: scheduleForm.remark.trim() || undefined,
    })
    ElMessage.success('排班已保存')
    closeEditor()
    await loadStaffDetail()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '排班保存失败')
  } finally {
    saving.value = false
  }
}

async function deactivateResource(item: ServiceResourceItem) {
  if (!selectedShopId.value) return
  await ElMessageBox.confirm(
    `停用“${item.resourceName}”后，新预约将不能再选择它，历史占用记录仍会保留。`,
    '确认停用资源',
    { confirmButtonText: '确认停用', cancelButtonText: '返回', type: 'warning' },
  )
  try {
    await deactivateServiceResource(item.id, selectedShopId.value, item.version)
    ElMessage.success('资源已停用，历史记录未删除')
    await reloadResources()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '资源停用失败')
  }
}

async function deactivateSchedule(item: StaffScheduleItem) {
  if (!selectedShopId.value || !selectedStaffId.value) return
  await ElMessageBox.confirm(
    '停用后该排班不再参与预约时段计算，历史记录仍会保留。',
    '确认停用排班',
    { confirmButtonText: '确认停用', cancelButtonText: '返回', type: 'warning' },
  )
  try {
    await deactivateStaffSchedule(
      selectedStaffId.value,
      item.id,
      selectedShopId.value,
      item.version,
    )
    ElMessage.success('排班已停用')
    await loadStaffDetail()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '排班停用失败')
  }
}

function formatDate(value: Date) {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function nextSkillEffectiveFrom(item?: StaffSkillItem) {
  let value = new Date()
  value.setSeconds(0, 0)
  value.setMinutes(value.getMinutes() + 1)
  if (item?.effectiveFrom) {
    const current = new Date(item.effectiveFrom)
    if (!Number.isNaN(current.getTime()) && current >= value) {
      current.setMinutes(current.getMinutes() + 1)
      value = current
    }
  }
  return `${formatDate(value)}T${String(value.getHours()).padStart(2, '0')}:${String(value.getMinutes()).padStart(2, '0')}:00`
}

function statusText(status: string) {
  return status === 'ACTIVE' ? '启用' : '停用'
}

function resourceTypeText(type: string) {
  return type === 'ROOM' ? '护理房间' : '护理设备'
}

function scheduleTypeText(type: string) {
  return { WORK: '工作', LEAVE: '休假', BLOCKED: '锁定' }[type] ?? type
}
</script>

<template>
  <section class="page-heading master-page-heading">
    <div>
      <p class="environment-label">基础资料与资源</p>
      <h1>把可预约条件配置清楚</h1>
      <p>护理项目、技师技能、排班与房间设备统一在这里维护，所有写入都有版本、权限和审计保护。</p>
    </div>
    <div class="master-heading-actions">
      <el-select
        v-model="selectedShopId"
        aria-label="选择门店"
        placeholder="选择门店"
        :disabled="loading"
      >
        <el-option
          v-for="shop in auth.shops"
          :key="shop.id"
          :label="shop.name"
          :value="shop.id"
        />
      </el-select>
      <el-button :icon="Refresh" :loading="loading" @click="loadBaseData">刷新</el-button>
    </div>
  </section>

  <section class="master-summary" aria-label="当前门店基础资料摘要">
    <div>
      <span>当前门店</span>
      <strong>{{ currentShop?.name ?? '请选择门店' }}</strong>
    </div>
    <div>
      <span>启用项目</span>
      <strong>{{ activeCount.services }}</strong>
    </div>
    <div>
      <span>在岗技师</span>
      <strong>{{ activeCount.staff }}</strong>
    </div>
    <div>
      <span>可用资源</span>
      <strong>{{ activeCount.resources }}</strong>
    </div>
  </section>

  <div class="master-tabs" role="tablist" aria-label="基础资料配置类型">
    <button
      v-for="tab in tabs"
      :key="tab.key"
      type="button"
      role="tab"
      :aria-selected="activeTab === tab.key"
      :class="{ active: activeTab === tab.key }"
      @click="activeTab = tab.key"
    >
      {{ tab.label }}
      <span>{{ tab.count() }}</span>
    </button>
  </div>

  <el-alert
    v-if="error"
    class="master-error"
    :title="error"
    type="error"
    show-icon
    :closable="false"
  />

  <div class="master-layout" :class="{ 'master-layout--editing': editorOpen }">
    <section class="content-section master-results" :aria-busy="loading || detailLoading">
      <div v-if="loading" class="master-loading">
        <el-skeleton :rows="7" animated />
      </div>

      <template v-else-if="activeTab === 'services'">
        <div class="section-heading master-section-heading">
          <div>
            <h2>护理项目</h2>
            <p>维护顾客看到和预约计算使用的名称、时长、价格与启停状态。</p>
          </div>
        </div>
        <div v-if="services.length" class="master-table-wrap">
          <table class="master-table">
            <thead>
              <tr>
                <th>项目</th>
                <th>分类</th>
                <th>服务时长</th>
                <th>价格</th>
                <th>状态</th>
                <th><span class="visually-hidden">操作</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in services" :key="item.id">
                <td>
                  <div class="master-identity">
                    <div class="master-service-name">
                      <img
                        v-if="item.coverUrl"
                        :src="resolveMediaUrl(item.coverUrl)"
                        :alt="item.name"
                        loading="lazy"
                        decoding="async"
                      />
                      <strong>{{ item.name }}</strong>
                    </div>
                    <code>{{ item.serviceCode }}</code>
                  </div>
                </td>
                <td>{{ item.categoryName || '未分类' }}</td>
                <td>{{ item.durationMinutes }} + {{ item.cleanupMinutes }} 分钟</td>
                <td>
                  <div class="master-money">
                    <strong>¥{{ Number(item.memberPrice).toFixed(2) }}</strong>
                    <span>门市 ¥{{ Number(item.listPrice).toFixed(2) }}</span>
                  </div>
                </td>
                <td>
                  <span class="master-state" :class="{ inactive: item.status !== 'ACTIVE' }">
                    {{ statusText(item.status) }}
                  </span>
                </td>
                <td class="master-actions">
                  <el-button
                    text
                    :icon="EditPen"
                    :disabled="!canManageServices"
                    @click="editService(item)"
                  >
                    编辑
                  </el-button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state master-empty">
          <Tools />
          <h2>还没有护理项目</h2>
          <p>先完成项目资料导入，再配置技师技能与排班。</p>
        </div>
      </template>

      <template v-else-if="activeTab === 'resources'">
        <div class="section-heading master-section-heading">
          <div>
            <h2>房间与设备</h2>
            <p>资源在预约时按时间段加锁；停用只影响新预约，不删除历史占用。</p>
          </div>
          <el-button
            type="primary"
            :icon="Plus"
            :disabled="!canManageResources"
            @click="editResource()"
          >
            新增资源
          </el-button>
        </div>
        <div v-if="resources.length" class="master-table-wrap">
          <table class="master-table">
            <thead>
              <tr>
                <th>资源</th>
                <th>类型</th>
                <th>容量</th>
                <th>状态</th>
                <th>版本</th>
                <th><span class="visually-hidden">操作</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in resources" :key="item.id">
                <td>
                  <div class="master-identity">
                    <strong>{{ item.resourceName }}</strong>
                    <code>{{ item.resourceCode }}</code>
                  </div>
                </td>
                <td>{{ resourceTypeText(item.resourceType) }}</td>
                <td>{{ item.capacity }}</td>
                <td>
                  <span class="master-state" :class="{ inactive: item.status !== 'ACTIVE' }">
                    {{ statusText(item.status) }}
                  </span>
                </td>
                <td>v{{ item.version }}</td>
                <td class="master-actions">
                  <el-button
                    text
                    :disabled="!canManageResources"
                    @click="editResource(item)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    v-if="item.status === 'ACTIVE'"
                    text
                    type="danger"
                    :disabled="!canManageResources"
                    @click="deactivateResource(item)"
                  >
                    停用
                  </el-button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state master-empty">
          <Tools />
          <h2>还没有房间或设备</h2>
          <p>新增第一个可预约资源后，预约冲突校验会自动接管占用时段。</p>
          <el-button
            type="primary"
            :disabled="!canManageResources"
            @click="editResource()"
          >
            新增资源
          </el-button>
        </div>
      </template>

      <template v-else>
        <div class="master-staff-toolbar">
          <div>
            <strong>{{ activeTab === 'skills' ? '选择技师配置技能' : '选择技师查看排班' }}</strong>
            <span>配置只作用于当前门店范围</span>
          </div>
          <el-select
            v-model="selectedStaffId"
            filterable
            placeholder="选择技师"
            aria-label="选择技师"
          >
            <el-option
              v-for="item in staff"
              :key="item.id"
              :label="`${item.name} · ${item.staffNo}`"
              :value="item.id"
            />
          </el-select>
        </div>

        <div v-if="detailLoading" class="master-loading">
          <el-skeleton :rows="6" animated />
        </div>

        <template v-else-if="activeTab === 'skills'">
          <div class="section-heading master-section-heading">
            <div>
              <h2>{{ selectedStaff?.name || '技师' }}的项目技能</h2>
              <p>每次调整都会关闭旧版本并新增生效版本，历史预约不受影响。</p>
            </div>
            <el-button
              type="primary"
              :icon="Plus"
              :disabled="!selectedStaffId || !canManageStaff || !newSkillServices.length"
              @click="editSkill()"
            >
              新增技能
            </el-button>
          </div>
          <div v-if="skills.length" class="master-table-wrap">
            <table class="master-table">
              <thead>
                <tr>
                  <th>护理项目</th>
                  <th>自定义时长</th>
                  <th>生效时间</th>
                  <th>状态</th>
                  <th>版本</th>
                  <th><span class="visually-hidden">操作</span></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in skills" :key="item.id">
                  <td>
                    <div class="master-identity">
                      <strong>{{ item.serviceName }}</strong>
                      <code>{{ item.serviceCode }}</code>
                    </div>
                  </td>
                  <td>{{ item.customDurationMinutes ? `${item.customDurationMinutes} 分钟` : '跟随项目' }}</td>
                  <td>{{ item.effectiveFrom?.replace('T', ' ') }}</td>
                  <td>
                    <span class="master-state" :class="{ inactive: !item.enabled }">
                      {{ item.enabled ? '可服务' : '已删除' }}
                    </span>
                  </td>
                  <td>v{{ item.version }}</td>
                  <td class="master-actions">
                    <el-button
                      v-if="item.enabled"
                      text
                      :disabled="!canManageStaff"
                      @click="editSkill(item)"
                    >
                      编辑技能
                    </el-button>
                    <el-button
                      v-if="item.enabled"
                      text
                      type="danger"
                      :loading="skillActionServiceId === item.serviceId"
                      :disabled="!canManageStaff || Boolean(skillActionServiceId)"
                      @click="changeSkillStatus(item, false)"
                    >
                      删除技能
                    </el-button>
                    <el-button
                      v-else
                      text
                      type="primary"
                      :loading="skillActionServiceId === item.serviceId"
                      :disabled="!canManageStaff || Boolean(skillActionServiceId)"
                      @click="changeSkillStatus(item, true)"
                    >
                      恢复技能
                    </el-button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-state master-empty">
            <Tools />
            <h2>该技师还没有项目技能</h2>
            <p>新增技能后，预约系统才会把对应项目匹配给这位技师。</p>
          </div>
        </template>

        <template v-else>
          <div class="section-heading master-section-heading">
            <div>
              <h2>{{ selectedStaff?.name || '技师' }}的排班</h2>
              <p>工作、休假与锁定时段共同参与可预约时间计算。</p>
            </div>
            <el-button
              type="primary"
              :icon="Calendar"
              :disabled="!selectedStaffId || !canManageStaff"
              @click="createSchedule"
            >
              新增排班
            </el-button>
          </div>
          <div v-if="schedules.length" class="master-table-wrap">
            <table class="master-table">
              <thead>
                <tr>
                  <th>日期</th>
                  <th>时段</th>
                  <th>类型</th>
                  <th>说明</th>
                  <th>状态</th>
                  <th><span class="visually-hidden">操作</span></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in schedules" :key="item.id">
                  <td>{{ item.scheduleDate }}</td>
                  <td>{{ item.startTime ? `${item.startTime}–${item.endTime}` : '全天' }}</td>
                  <td>{{ scheduleTypeText(item.scheduleType) }}</td>
                  <td>{{ item.remark || '—' }}</td>
                  <td>
                    <span class="master-state" :class="{ inactive: item.status !== 'ACTIVE' }">
                      {{ statusText(item.status) }}
                    </span>
                  </td>
                  <td class="master-actions">
                    <el-button
                      v-if="item.status === 'ACTIVE'"
                      text
                      type="danger"
                      :disabled="!canManageStaff"
                      @click="deactivateSchedule(item)"
                    >
                      停用
                    </el-button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-state master-empty">
            <Calendar />
            <h2>该技师还没有排班</h2>
            <p>创建工作时段后，预约系统才能计算可选时间。</p>
          </div>
        </template>
      </template>
    </section>

    <aside v-if="editorOpen" class="master-editor" aria-label="基础资料编辑区">
      <div class="master-editor-heading">
        <div>
          <span>{{ currentShop?.name }}</span>
          <h2>
            {{
              editor === 'service'
                ? '编辑护理项目'
                : editor === 'resource'
                  ? resourceForm.id ? '编辑资源' : '新增资源'
                  : editor === 'skill'
                    ? skillForm.version ? '编辑技师技能' : '新增技师技能'
                    : '新增技师排班'
            }}
          </h2>
        </div>
        <el-button circle text :icon="Close" aria-label="关闭编辑区" @click="closeEditor" />
      </div>

      <el-form v-if="editor === 'service'" label-position="top" @submit.prevent>
        <el-form-item label="项目名称" required>
          <el-input v-model="serviceForm.name" maxlength="120" />
        </el-form-item>
        <el-form-item label="项目图片">
          <ImageUploadField v-model="serviceForm.coverUrl" label="项目图片" />
        </el-form-item>
        <div class="master-form-row">
          <el-form-item label="服务时长（分钟）" required>
            <el-input-number v-model="serviceForm.durationMinutes" :min="1" :max="1440" />
          </el-form-item>
          <el-form-item label="清场时长（分钟）" required>
            <el-input-number v-model="serviceForm.cleanupMinutes" :min="0" :max="1440" />
          </el-form-item>
        </div>
        <div class="master-form-row">
          <el-form-item label="门市价" required>
            <el-input-number v-model="serviceForm.listPrice" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item label="会员价" required>
            <el-input-number v-model="serviceForm.memberPrice" :min="0" :precision="2" />
          </el-form-item>
        </div>
        <el-form-item label="状态" required>
          <el-segmented
            v-model="serviceForm.status"
            :options="[{ label: '启用', value: 'ACTIVE' }, { label: '停用', value: 'INACTIVE' }]"
          />
        </el-form-item>
      </el-form>

      <el-form v-else-if="editor === 'resource'" label-position="top" @submit.prevent>
        <el-form-item label="资源编码" required>
          <el-input
            v-model="resourceForm.resourceCode"
            maxlength="50"
            placeholder="例如 ROOM_01"
          />
          <p class="form-helper">保存后编码统一转为大写，仅支持字母、数字、下划线和短横线。</p>
        </el-form-item>
        <el-form-item label="资源名称" required>
          <el-input v-model="resourceForm.resourceName" maxlength="100" />
        </el-form-item>
        <div class="master-form-row">
          <el-form-item label="资源类型" required>
            <el-select v-model="resourceForm.resourceType">
              <el-option label="护理房间" value="ROOM" />
              <el-option label="护理设备" value="EQUIPMENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="并行容量" required>
            <el-input-number v-model="resourceForm.capacity" :min="1" :max="100" />
          </el-form-item>
        </div>
      </el-form>

      <el-form v-else-if="editor === 'skill'" label-position="top" @submit.prevent>
        <el-form-item label="技师">
          <el-input :model-value="selectedStaff?.name" disabled />
        </el-form-item>
        <el-form-item label="护理项目" required>
          <el-select v-model="skillForm.serviceId" filterable :disabled="skillForm.version > 0">
            <el-option
              v-for="item in skillServiceOptions"
              :key="item.id"
              :label="`${item.name} · ${item.serviceCode}`"
              :value="item.id"
            />
          </el-select>
          <p v-if="!skillForm.version && !skillServiceOptions.length" class="form-helper">
            当前启用项目都已配置；如需恢复已删除技能，请在列表中点击“恢复”。
          </p>
        </el-form-item>
        <el-form-item label="自定义服务时长（分钟）">
          <el-input-number
            v-model="skillForm.customDurationMinutes"
            :min="1"
            :max="1440"
            placeholder="跟随项目"
          />
          <p class="form-helper">留空时沿用护理项目的默认时长。</p>
        </el-form-item>
        <el-form-item label="生效时间" required>
          <el-date-picker
            v-model="skillForm.effectiveFrom"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
          />
        </el-form-item>
      </el-form>

      <el-form v-else label-position="top" @submit.prevent>
        <el-form-item label="技师">
          <el-input :model-value="selectedStaff?.name" disabled />
        </el-form-item>
        <el-form-item label="排班日期" required>
          <el-date-picker
            v-model="scheduleForm.scheduleDate"
            type="date"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="排班类型" required>
          <el-segmented
            v-model="scheduleForm.scheduleType"
            :options="[
              { label: '工作', value: 'WORK' },
              { label: '休假', value: 'LEAVE' },
              { label: '锁定', value: 'BLOCKED' },
            ]"
          />
        </el-form-item>
        <div class="master-form-row">
          <el-form-item label="开始时间">
            <el-time-picker
              v-model="scheduleForm.startTime"
              value-format="HH:mm:ss"
              format="HH:mm"
              clearable
            />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-time-picker
              v-model="scheduleForm.endTime"
              value-format="HH:mm:ss"
              format="HH:mm"
              clearable
            />
          </el-form-item>
        </div>
        <p class="form-helper master-form-note">工作排班必须填写时段；休假或锁定可清空时段表示全天。</p>
        <el-form-item label="说明">
          <el-input
            v-model="scheduleForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <div class="master-editor-actions">
        <el-button :disabled="saving" @click="closeEditor">返回</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="saving || (editor === 'skill' && (!skillForm.serviceId || !skillForm.effectiveFrom))"
          @click="
            editor === 'service'
              ? saveService()
              : editor === 'resource'
                ? saveResource()
                : editor === 'skill'
                  ? saveSkill()
                  : saveSchedule()
          "
        >
          {{ editor === 'skill' ? skillForm.version ? '保存修改' : '确认新增' : '保存' }}
        </el-button>
      </div>
    </aside>
  </div>
</template>
