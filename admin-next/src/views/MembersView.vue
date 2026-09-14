<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import {
  createMember,
  deactivateMember,
  getMembers,
  restoreMember,
  updateMember,
  type MemberFormPayload,
  type MemberSummary,
} from '../services/api'

type MemberStatus = 'ACTIVE' | 'INACTIVE' | 'ALL'

interface MemberEditor {
  id?: number
  version?: number
  shopId?: number
  name: string
  phone: string
  gender: string
  birthday: string
  source: string
  notes: string
}

const auth = useAuthStore()
const records = ref<MemberSummary[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const editorOpen = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const formRef = ref<FormInstance>()
const total = ref(0)
const activeCount = ref(0)
const inactiveCount = ref(0)

const query = reactive({
  shopId: undefined as number | undefined,
  keyword: '',
  status: 'ACTIVE' as MemberStatus,
  page: 1,
  pageSize: 10,
})

const editor = reactive<MemberEditor>({
  name: '',
  phone: '',
  gender: '',
  birthday: '',
  source: '',
  notes: '',
})

const rules: FormRules<MemberEditor> = {
  shopId: [{ required: true, message: '请选择归属门店', trigger: 'change' }],
  name: [
    { required: true, message: '请填写会员姓名', trigger: 'blur' },
    { max: 50, message: '姓名不能超过50个字符', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    {
      pattern: /^[0-9+\-\s]{6,30}$/,
      message: '请输入正确的手机号',
      trigger: 'blur',
    },
  ],
}

const hasShops = computed(() => auth.shops.length > 0)
const selectedShopName = computed(() => {
  if (!query.shopId) return '全部可访问门店'
  return auth.shops.find((shop) => shop.id === query.shopId)?.name ?? '当前门店'
})
const pageStart = computed(() => (total.value === 0 ? 0 : (query.page - 1) * query.pageSize + 1))
const pageEnd = computed(() => Math.min(query.page * query.pageSize, total.value))

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  query.shopId =
    auth.context?.homeShopId && auth.shops.some((shop) => shop.id === auth.context?.homeShopId)
      ? auth.context.homeShopId
      : auth.shops[0]?.id
  await loadMembers()
})

async function loadMembers() {
  loading.value = true
  error.value = ''
  try {
    const result = await getMembers({
      shopId: query.shopId,
      keyword: query.keyword.trim() || undefined,
      status: query.status,
      page: query.page,
      pageSize: query.pageSize,
    })
    records.value = result.records
    total.value = result.total
    activeCount.value = result.active
    inactiveCount.value = result.inactive
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '会员列表加载失败'
  } finally {
    loading.value = false
  }
}

async function searchMembers() {
  query.page = 1
  await loadMembers()
}

async function resetFilters() {
  query.keyword = ''
  query.status = 'ACTIVE'
  query.page = 1
  await loadMembers()
}

function resetEditor() {
  Object.assign(editor, {
    id: undefined,
    version: undefined,
    shopId: query.shopId ?? auth.context?.homeShopId ?? auth.shops[0]?.id,
    name: '',
    phone: '',
    gender: '',
    birthday: '',
    source: '',
    notes: '',
  })
  formRef.value?.clearValidate()
}

async function openCreate() {
  editorMode.value = 'create'
  resetEditor()
  editorOpen.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEdit(member: MemberSummary) {
  editorMode.value = 'edit'
  Object.assign(editor, {
    id: member.id,
    version: member.version,
    shopId: query.shopId ?? member.homeShopId,
    name: member.name,
    phone: member.phone,
    gender: member.gender ?? '',
    birthday: member.birthday ?? '',
    source: member.source ?? '',
    notes: member.notes ?? '',
  })
  editorOpen.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

function closeEditor() {
  editorOpen.value = false
  resetEditor()
}

async function saveMember() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || !editor.shopId) return

  saving.value = true
  try {
    const payload: MemberFormPayload = {
      shopId: editor.shopId,
      name: editor.name.trim(),
      phone: editor.phone.trim(),
      gender: editor.gender || undefined,
      birthday: editor.birthday || undefined,
      source: editor.source.trim() || undefined,
      notes: editor.notes.trim() || undefined,
    }
    if (editorMode.value === 'create') {
      const result = await createMember(payload)
      ElMessage.success(result.linkedExisting ? '已关联现有会员到当前门店' : '会员创建成功')
    } else if (editor.id && editor.version !== undefined) {
      await updateMember(editor.id, { ...payload, version: editor.version })
      ElMessage.success('会员资料已保存')
    }
    closeEditor()
    await loadMembers()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function changeMemberStatus(member: MemberSummary) {
  const shopId = query.shopId
  if (!shopId) {
    ElMessage.warning('请先选择具体门店，再停用或恢复会员')
    return
  }
  const restoring = member.status === 'INACTIVE'
  try {
    await ElMessageBox.confirm(
      restoring
        ? `确认恢复 ${member.name} 在“${selectedShopName.value}”的会员资格？`
        : `确认停用 ${member.name} 在“${selectedShopName.value}”的会员资格？历史消费记录不会删除。`,
      restoring ? '恢复会员' : '停用会员',
      {
        confirmButtonText: restoring ? '确认恢复' : '确认停用',
        cancelButtonText: '取消',
        type: restoring ? 'info' : 'warning',
      },
    )
    if (restoring) {
      await restoreMember(member.id, shopId)
      ElMessage.success('会员已恢复')
    } else {
      await deactivateMember(member.id, shopId)
      ElMessage.success('会员已停用，历史记录已保留')
    }
    await loadMembers()
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : '状态修改失败')
  }
}

async function changePage(page: number) {
  query.page = page
  await loadMembers()
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
  }).format(Number(value || 0))
}

function formatDateTime(value?: string) {
  if (!value) return '暂无到店记录'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <section class="page-heading member-page-heading">
    <div>
      <p class="environment-label">连锁会员资产</p>
      <h1>会员中心</h1>
      <p>统一管理会员基础资料、门店归属与账户概况，所有操作均按当前账号的门店权限隔离。</p>
    </div>
    <el-button type="primary" :disabled="!hasShops" @click="openCreate">
      <el-icon><Plus /></el-icon>
      新增会员
    </el-button>
  </section>

  <div class="member-page-layout" :class="{ 'member-page-layout--editing': editorOpen }">
    <div class="member-list-column">
      <section class="content-section member-toolbar" aria-label="会员筛选">
        <div class="member-filters">
          <el-select
            v-model="query.shopId"
            clearable
            placeholder="全部可访问门店"
            aria-label="筛选门店"
            @change="searchMembers"
          >
            <el-option
              v-for="shop in auth.shops"
              :key="shop.id"
              :label="shop.name"
              :value="shop.id"
            />
          </el-select>
          <el-select
            v-model="query.status"
            aria-label="会员状态"
            @change="searchMembers"
          >
            <el-option label="在店会员" value="ACTIVE" />
            <el-option label="已停用" value="INACTIVE" />
            <el-option label="全部状态" value="ALL" />
          </el-select>
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="姓名、手机号或会员编号"
            aria-label="搜索会员"
            @keyup.enter="searchMembers"
            @clear="searchMembers"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button :loading="loading" @click="searchMembers">查询</el-button>
          <el-button text @click="resetFilters">
            <el-icon><RefreshLeft /></el-icon>
            重置
          </el-button>
        </div>
        <div class="member-result-summary" aria-live="polite">
          <span>{{ selectedShopName }}</span>
          <span>在店 {{ activeCount }}</span>
          <span>停用 {{ inactiveCount }}</span>
        </div>
      </section>

      <el-alert
        v-if="error"
        class="member-error"
        type="error"
        :title="error"
        :closable="false"
        show-icon
      >
        <template #default>
          <el-button size="small" @click="loadMembers">重新加载</el-button>
        </template>
      </el-alert>

      <section class="content-section member-results">
        <div v-if="loading" class="member-loading" aria-busy="true" aria-label="正在加载会员">
          <el-skeleton :rows="7" animated />
        </div>

        <template v-else-if="records.length">
          <div class="member-table-wrap">
            <table class="member-table">
              <thead>
                <tr>
                  <th>会员</th>
                  <th>归属与来源</th>
                  <th>账户概况</th>
                  <th>到店情况</th>
                  <th>状态</th>
                  <th><span class="visually-hidden">操作</span></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="member in records" :key="member.id">
                  <td>
                    <div class="member-identity">
                      <strong>{{ member.name }}</strong>
                      <span>{{ member.phone }}</span>
                      <code>{{ member.memberNo }}</code>
                    </div>
                  </td>
                  <td>
                    <div class="member-cell-stack">
                      <strong>{{ member.shopNames || member.homeShopName }}</strong>
                      <span>{{ member.source || '来源未填写' }}</span>
                    </div>
                  </td>
                  <td>
                    <div class="member-cell-stack">
                      <strong>{{ formatMoney(member.balance) }}</strong>
                      <span>赠送金 {{ formatMoney(member.giftBalance) }} · {{ member.points }} 积分</span>
                    </div>
                  </td>
                  <td>
                    <div class="member-cell-stack">
                      <strong>{{ member.visitCount }} 次</strong>
                      <span>{{ formatDateTime(member.lastVisitAt) }}</span>
                    </div>
                  </td>
                  <td>
                    <span
                      class="status-badge"
                      :class="member.status === 'ACTIVE' ? 'status-badge--success' : 'status-badge--muted'"
                    >
                      {{ member.status === 'ACTIVE' ? '在店' : '已停用' }}
                    </span>
                  </td>
                  <td>
                    <div class="member-actions">
                      <el-button text size="small" @click="openEdit(member)">
                        <el-icon><Edit /></el-icon>
                        编辑
                      </el-button>
                      <el-button
                        text
                        size="small"
                        :type="member.status === 'ACTIVE' ? 'danger' : 'primary'"
                        @click="changeMemberStatus(member)"
                      >
                        <el-icon v-if="member.status === 'ACTIVE'"><Delete /></el-icon>
                        <el-icon v-else><RefreshLeft /></el-icon>
                        {{ member.status === 'ACTIVE' ? '停用' : '恢复' }}
                      </el-button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="member-mobile-list">
            <article v-for="member in records" :key="`mobile-${member.id}`" class="member-mobile-item">
              <div class="member-mobile-title">
                <div>
                  <strong>{{ member.name }}</strong>
                  <span>{{ member.phone }} · {{ member.memberNo }}</span>
                </div>
                <span
                  class="status-badge"
                  :class="member.status === 'ACTIVE' ? 'status-badge--success' : 'status-badge--muted'"
                >
                  {{ member.status === 'ACTIVE' ? '在店' : '已停用' }}
                </span>
              </div>
              <dl>
                <div><dt>门店</dt><dd>{{ member.shopNames || member.homeShopName }}</dd></div>
                <div><dt>余额</dt><dd>{{ formatMoney(member.balance) }}</dd></div>
                <div><dt>到店</dt><dd>{{ member.visitCount }} 次</dd></div>
              </dl>
              <div class="member-actions">
                <el-button text size="small" @click="openEdit(member)">编辑资料</el-button>
                <el-button
                  text
                  size="small"
                  :type="member.status === 'ACTIVE' ? 'danger' : 'primary'"
                  @click="changeMemberStatus(member)"
                >
                  {{ member.status === 'ACTIVE' ? '停用会员' : '恢复会员' }}
                </el-button>
              </div>
            </article>
          </div>

          <footer class="member-pagination">
            <span>显示 {{ pageStart }}–{{ pageEnd }}，共 {{ total }} 位</span>
            <el-pagination
              background
              layout="prev, pager, next"
              :current-page="query.page"
              :page-size="query.pageSize"
              :total="total"
              @current-change="changePage"
            />
          </footer>
        </template>

        <div v-else class="empty-state member-empty">
          <h2>{{ query.keyword ? '没有找到匹配会员' : '当前范围还没有会员' }}</h2>
          <p>
            {{
              query.keyword
                ? '请检查姓名、手机号或会员编号，也可以清除筛选后重新查询。'
                : '新增第一位会员后，系统会自动建立门店关系和三类会员账户。'
            }}
          </p>
          <el-button v-if="query.keyword" @click="resetFilters">清除筛选</el-button>
          <el-button v-else type="primary" :disabled="!hasShops" @click="openCreate">
            新增第一位会员
          </el-button>
        </div>
      </section>
    </div>

    <aside v-if="editorOpen" class="member-editor" aria-label="会员资料编辑">
      <div class="member-editor-heading">
        <div>
          <span class="environment-label">{{ editorMode === 'create' ? '建立会员档案' : '更新会员资料' }}</span>
          <h2>{{ editorMode === 'create' ? '新增会员' : `编辑 ${editor.name}` }}</h2>
        </div>
        <el-button text aria-label="关闭会员编辑" @click="closeEditor">关闭</el-button>
      </div>

      <el-form ref="formRef" :model="editor" :rules="rules" label-position="top">
        <el-form-item label="归属门店" prop="shopId">
          <el-select v-model="editor.shopId" placeholder="请选择归属门店" :disabled="editorMode === 'edit'">
            <el-option
              v-for="shop in auth.shops"
              :key="shop.id"
              :label="shop.name"
              :value="shop.id"
            />
          </el-select>
          <p class="form-helper">
            {{ editorMode === 'edit' ? '编辑时按当前门店权限校验。' : '同手机号会员存在时会自动关联到该门店。' }}
          </p>
        </el-form-item>
        <div class="member-form-row">
          <el-form-item label="会员姓名" prop="name">
            <el-input v-model="editor.name" maxlength="50" placeholder="请输入真实姓名" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="editor.phone" maxlength="30" placeholder="用于跨店识别会员" />
          </el-form-item>
        </div>
        <div class="member-form-row">
          <el-form-item label="性别" prop="gender">
            <el-select v-model="editor.gender" clearable placeholder="选填">
              <el-option label="女" value="女" />
              <el-option label="男" value="男" />
              <el-option label="其他" value="其他" />
            </el-select>
          </el-form-item>
          <el-form-item label="生日" prop="birthday">
            <el-date-picker
              v-model="editor.birthday"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选填"
              :disabled-date="(date: Date) => date.getTime() > Date.now()"
            />
          </el-form-item>
        </div>
        <el-form-item label="会员来源" prop="source">
          <el-select v-model="editor.source" filterable allow-create clearable placeholder="到店、转介绍、线上等">
            <el-option label="自然到店" value="自然到店" />
            <el-option label="老客转介绍" value="老客转介绍" />
            <el-option label="线上预约" value="线上预约" />
            <el-option label="活动引流" value="活动引流" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="notes">
          <el-input
            v-model="editor.notes"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="记录服务偏好或需要门店留意的信息"
          />
        </el-form-item>
      </el-form>

      <div class="member-editor-actions">
        <el-button @click="closeEditor">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMember">
          {{ saving ? '正在保存' : '保存会员' }}
        </el-button>
      </div>
    </aside>
  </div>
</template>
