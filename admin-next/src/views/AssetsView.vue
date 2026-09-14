<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  changeMemberAssetAccountStatus,
  changePackageStatus,
  createPackageProduct,
  getMemberAssetAccountLedger,
  getMemberAssetAccounts,
  getMemberPackages,
  getMembers,
  getPackageProducts,
  getServiceCatalog,
  issueMemberPackage,
  postMemberAccountEntry,
  reverseMemberAccountEntry,
  type MemberAssetAccount,
  type MemberSummary,
  type PackageInstance,
  type PackageProduct,
  type ServiceCatalogItem,
} from '../services/api'
import { useAuthStore } from '../stores/auth'

interface ProductEditor {
  packageCode: string
  name: string
  description: string
  salePrice: number
  validityDays: number
  status: 'DRAFT' | 'ACTIVE'
  items: Array<{ key: string; serviceId?: number; quantity: number }>
}

const auth = useAuthStore()
const activeTab = ref('products')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const selectedShopId = ref<number>()
const products = ref<PackageProduct[]>([])
const services = ref<ServiceCatalogItem[]>([])
const members = ref<MemberSummary[]>([])
const selectedMemberId = ref<number>()
const memberPackages = ref<PackageInstance[]>([])
const memberAccounts = ref<MemberAssetAccount[]>([])
const accountLedgers = ref<Record<number, Array<Record<string, unknown>>>>({})
const expandedAccountId = ref<number>()
const productEditorOpen = ref(false)
const productFormRef = ref<FormInstance>()
const issueForm = reactive({
  packageProductId: undefined as number | undefined,
  sourceOrderId: undefined as number | undefined,
})
const accountAction = reactive({
  open: false,
  account: undefined as MemberAssetAccount | undefined,
  direction: 'credits' as 'credits' | 'debits',
  amount: 0,
  remark: '',
})
const productEditor = reactive<ProductEditor>({
  packageCode: '',
  name: '',
  description: '',
  salePrice: 0,
  validityDays: 365,
  status: 'ACTIVE',
  items: [],
})

const canManagePackage = computed(() =>
  auth.context?.permissions?.includes('package:manage'),
)
const canManageAccount = computed(() =>
  auth.context?.permissions?.includes('account:manage'),
)
const activeProducts = computed(() => products.value.filter((item) => item.status === 'ACTIVE'))
const selectedMember = computed(() =>
  members.value.find((item) => item.id === selectedMemberId.value),
)

const productRules: FormRules = {
  packageCode: [{ required: true, message: '请填写套餐编号', trigger: 'blur' }],
  name: [{ required: true, message: '请填写套餐名称', trigger: 'blur' }],
  salePrice: [{ required: true, message: '请填写套餐价格', trigger: 'change' }],
  validityDays: [{ required: true, message: '请填写有效天数', trigger: 'change' }],
}

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  selectedShopId.value = auth.context?.homeShopId ?? auth.shops[0]?.id
  await loadWorkspace()
})

async function loadWorkspace() {
  if (!selectedShopId.value) {
    loading.value = false
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [productData, serviceData, memberData] = await Promise.all([
      getPackageProducts(selectedShopId.value),
      getServiceCatalog(selectedShopId.value),
      getMembers({
        shopId: selectedShopId.value,
        status: 'ACTIVE',
        page: 1,
        pageSize: 100,
      }),
    ])
    products.value = productData
    services.value = serviceData.filter((item) => item.status === 'ACTIVE')
    members.value = memberData.records
    if (selectedMemberId.value && !members.value.some((item) => item.id === selectedMemberId.value)) {
      selectedMemberId.value = undefined
      memberPackages.value = []
      memberAccounts.value = []
    }
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '资产中心加载失败'
  } finally {
    loading.value = false
  }
}

async function loadMemberAssets() {
  if (!selectedShopId.value || !selectedMemberId.value) {
    memberPackages.value = []
    memberAccounts.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [packageData, accountData] = await Promise.all([
      getMemberPackages(selectedShopId.value, selectedMemberId.value),
      getMemberAssetAccounts(selectedShopId.value, selectedMemberId.value),
    ])
    memberPackages.value = packageData
    memberAccounts.value = accountData
    accountLedgers.value = {}
    expandedAccountId.value = undefined
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '会员资产加载失败'
  } finally {
    loading.value = false
  }
}

function openProductEditor() {
  Object.assign(productEditor, {
    packageCode: '',
    name: '',
    description: '',
    salePrice: 0,
    validityDays: 365,
    status: 'ACTIVE',
    items: [{ key: crypto.randomUUID(), serviceId: undefined, quantity: 1 }],
  })
  productEditorOpen.value = true
}

function addProductItem() {
  productEditor.items.push({
    key: crypto.randomUUID(),
    serviceId: undefined,
    quantity: 1,
  })
}

function removeProductItem(key: string) {
  if (productEditor.items.length === 1) {
    ElMessage.warning('套餐至少保留一个服务项目')
    return
  }
  productEditor.items = productEditor.items.filter((item) => item.key !== key)
}

async function saveProduct() {
  if (!selectedShopId.value || !productFormRef.value) return
  const valid = await productFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (productEditor.items.some((item) => !item.serviceId || item.quantity <= 0)) {
    ElMessage.warning('请完整选择服务项目并填写次数')
    return
  }
  if (new Set(productEditor.items.map((item) => item.serviceId)).size !== productEditor.items.length) {
    ElMessage.warning('套餐服务项目不能重复')
    return
  }
  saving.value = true
  try {
    await createPackageProduct({
      shop_id: selectedShopId.value,
      package_code: productEditor.packageCode.trim(),
      name: productEditor.name.trim(),
      description: productEditor.description.trim() || undefined,
      sale_price: productEditor.salePrice,
      validity_days: productEditor.validityDays,
      status: productEditor.status,
      items: productEditor.items.map((item) => ({
        service_id: item.serviceId!,
        quantity: item.quantity,
      })),
    })
    ElMessage.success('套餐产品已创建')
    productEditorOpen.value = false
    await loadWorkspace()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '套餐产品创建失败')
  } finally {
    saving.value = false
  }
}

async function issuePackage() {
  if (
    !selectedShopId.value
    || !selectedMemberId.value
    || !issueForm.packageProductId
    || !issueForm.sourceOrderId
  ) {
    ElMessage.warning('请选择套餐并填写已支付订单 ID')
    return
  }
  saving.value = true
  try {
    await issueMemberPackage(selectedMemberId.value, {
      shop_id: selectedShopId.value,
      package_product_id: issueForm.packageProductId,
      source_order_id: issueForm.sourceOrderId,
    })
    ElMessage.success('套餐已按支付订单发放')
    issueForm.packageProductId = undefined
    issueForm.sourceOrderId = undefined
    await loadMemberAssets()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '套餐发放失败')
  } finally {
    saving.value = false
  }
}

async function togglePackageStatus(instance: PackageInstance) {
  if (!selectedShopId.value) return
  const freezing = instance.status === 'ACTIVE'
  try {
    await ElMessageBox.confirm(
      freezing
        ? '冻结后该套餐不能继续核销，历史流水不受影响。'
        : '解冻后套餐可在有效期内继续核销。',
      freezing ? '冻结会员套餐' : '解冻会员套餐',
      {
        confirmButtonText: freezing ? '确认冻结' : '确认解冻',
        cancelButtonText: '返回',
        type: freezing ? 'warning' : 'info',
      },
    )
    await changePackageStatus(
      instance.id,
      freezing ? 'freeze' : 'unfreeze',
      selectedShopId.value,
      instance.version,
    )
    ElMessage.success(freezing ? '套餐已冻结' : '套餐已解冻')
    await loadMemberAssets()
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : '套餐状态修改失败')
  }
}

function openAccountAction(account: MemberAssetAccount, direction: 'credits' | 'debits') {
  Object.assign(accountAction, {
    open: true,
    account,
    direction,
    amount: 0,
    remark: '',
  })
}

async function submitAccountAction() {
  if (!selectedShopId.value || !accountAction.account || accountAction.amount <= 0) {
    ElMessage.warning('请输入大于零的金额或积分')
    return
  }
  saving.value = true
  try {
    await postMemberAccountEntry(accountAction.account.id, accountAction.direction, {
      shop_id: selectedShopId.value,
      entry_type: accountAction.direction === 'credits' ? 'MANUAL_CREDIT' : 'MANUAL_DEBIT',
      amount: accountAction.amount,
      remark: accountAction.remark.trim() || undefined,
      version: accountAction.account.version,
    })
    ElMessage.success(accountAction.direction === 'credits' ? '账户入账成功' : '账户扣减成功')
    accountAction.open = false
    await loadMemberAssets()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '账户操作失败')
  } finally {
    saving.value = false
  }
}

async function toggleAccountStatus(account: MemberAssetAccount) {
  if (!selectedShopId.value) return
  const freezing = account.status === 'ACTIVE'
  try {
    await ElMessageBox.confirm(
      freezing ? '冻结后该账户不能继续入账、扣减或支付。' : '确认恢复该账户的正常使用？',
      freezing ? '冻结会员账户' : '解冻会员账户',
      {
        confirmButtonText: freezing ? '确认冻结' : '确认解冻',
        cancelButtonText: '返回',
        type: freezing ? 'warning' : 'info',
      },
    )
    await changeMemberAssetAccountStatus(
      account.id,
      freezing ? 'freeze' : 'unfreeze',
      selectedShopId.value,
      account.version,
    )
    ElMessage.success(freezing ? '账户已冻结' : '账户已解冻')
    await loadMemberAssets()
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : '账户状态修改失败')
  }
}

async function toggleAccountLedger(account: MemberAssetAccount) {
  if (!selectedShopId.value) return
  expandedAccountId.value = expandedAccountId.value === account.id ? undefined : account.id
  if (!expandedAccountId.value || accountLedgers.value[account.id]) return
  try {
    accountLedgers.value[account.id] = await getMemberAssetAccountLedger(
      selectedShopId.value,
      account.id,
    )
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '账户流水加载失败')
  }
}

async function reverseLedger(account: MemberAssetAccount, ledger: Record<string, unknown>) {
  if (!selectedShopId.value || ledger.entryType === 'REVERSAL' || ledger.reversalOfLedgerId) return
  try {
    const result = await ElMessageBox.prompt(
      '冲正会追加一条反向流水，原流水不会被删除或覆盖。',
      '账户流水冲正',
      {
        confirmButtonText: '确认冲正',
        cancelButtonText: '返回',
        inputType: 'textarea',
        inputPlaceholder: '请填写冲正原因',
        inputValidator: (value) => Boolean(value?.trim()) || '必须填写冲正原因',
      },
    )
    await reverseMemberAccountEntry(Number(ledger.id), {
      shop_id: selectedShopId.value,
      version: account.version,
      reason: result.value.trim(),
    })
    ElMessage.success('账户流水已冲正')
    await loadMemberAssets()
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : '账户流水冲正失败')
  }
}

function accountLabel(type: string) {
  return {
    BALANCE: '储值余额',
    GIFT_BALANCE: '赠送余额',
    POINTS: '会员积分',
  }[type] ?? type
}

function statusLabel(status: string) {
  return {
    DRAFT: '草稿',
    ACTIVE: '生效中',
    INACTIVE: '已下架',
    FROZEN: '已冻结',
    EXHAUSTED: '已用完',
    EXPIRED: '已过期',
    CANCELLED: '已取消',
  }[status] ?? status
}

function formatMoney(value: unknown) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 2,
  }).format(Number(value || 0))
}
</script>

<template>
  <section class="assets-view">
    <header class="view-header">
      <div>
        <span class="section-label">套餐与会员资产</span>
        <h1>套餐账户</h1>
        <p>套餐发放必须关联已支付订单；核销、账户变动和冲正均保留不可变流水。</p>
      </div>
      <el-select v-model="selectedShopId" class="shop-select" @change="loadWorkspace">
        <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
      </el-select>
    </header>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />

    <el-tabs v-model="activeTab" class="asset-tabs">
      <el-tab-pane label="套餐产品" name="products">
        <div class="package-flow-note">
          <strong>套餐产品是销售模板，不会在创建时直接增加会员权益。</strong>
          <span>创建并生效 → 通过已支付的套餐订单发放 → 会员资产出现可核销次数；下架只停止后续销售，不影响已经发放的套餐。</span>
        </div>
        <div class="toolbar-row">
          <div>
            <strong>{{ products.length }} 个套餐产品</strong>
            <span>发布后项目内容不可修改，只能下架。</span>
          </div>
          <el-button v-if="canManagePackage" type="primary" @click="openProductEditor">
            新建套餐
          </el-button>
        </div>
        <el-table v-loading="loading" :data="products" row-key="id">
          <el-table-column prop="packageCode" label="套餐编号" min-width="130" />
          <el-table-column label="套餐名称" min-width="210">
            <template #default="{ row }">
              <div class="primary-cell">
                <strong>{{ row.name }}</strong>
                <span>{{ row.items.map((item: PackageProduct['items'][number]) => `${item.serviceName} × ${item.quantity}`).join('、') }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="售价" width="120">
            <template #default="{ row }">{{ formatMoney(row.salePrice) }}</template>
          </el-table-column>
          <el-table-column prop="validityDays" label="有效期" width="100">
            <template #default="{ row }">{{ row.validityDays }} 天</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'DRAFT' ? 'info' : 'warning'">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="80" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="会员资产" name="members">
        <div class="member-selector">
          <div>
            <label for="member-asset-select">选择会员</label>
            <p>查看与操作前先锁定具体门店和会员。</p>
          </div>
          <el-select
            id="member-asset-select"
            v-model="selectedMemberId"
            filterable
            clearable
            placeholder="按姓名或会员号选择"
            @change="loadMemberAssets"
          >
            <el-option
              v-for="member in members"
              :key="member.id"
              :label="`${member.name} · ${member.memberNo}`"
              :value="member.id"
            />
          </el-select>
        </div>

        <div v-if="selectedMember" class="member-context">
          <strong>{{ selectedMember.name }}</strong>
          <span>{{ selectedMember.memberNo }} · {{ selectedMember.homeShopName }}</span>
        </div>

        <div v-if="selectedMemberId" class="member-assets-layout">
          <section class="asset-panel">
            <div class="panel-heading">
              <div>
                <h2>会员套餐</h2>
                <p>{{ memberPackages.length }} 个历史与当前套餐</p>
              </div>
            </div>

            <div v-if="canManagePackage" class="issue-form">
              <el-select v-model="issueForm.packageProductId" placeholder="选择生效套餐">
                <el-option
                  v-for="product in activeProducts"
                  :key="product.id"
                  :label="`${product.name} · ${formatMoney(product.salePrice)}`"
                  :value="product.id"
                />
              </el-select>
              <el-input-number
                v-model="issueForm.sourceOrderId"
                :min="1"
                :controls="false"
                placeholder="已支付订单 ID"
              />
              <el-button type="primary" :loading="saving" @click="issuePackage">按订单发放</el-button>
            </div>
            <p v-if="canManagePackage" class="operation-hint">
              系统会校验订单会员、套餐项目及支付状态，不能手工绕过支付发放。
            </p>

            <div class="package-list">
              <article v-for="instance in memberPackages" :key="instance.id" class="package-row">
                <div class="package-summary">
                  <div>
                    <strong>{{ instance.packageName }}</strong>
                    <span>{{ instance.instanceNo }} · 有效至 {{ instance.validUntil }}</span>
                  </div>
                  <div class="package-balance">
                    <strong>{{ instance.remainingQuantity }} / {{ instance.totalQuantity }}</strong>
                    <el-tag :type="instance.status === 'ACTIVE' ? 'success' : 'info'">
                      {{ statusLabel(instance.status) }}
                    </el-tag>
                  </div>
                </div>
                <div class="package-items">
                  <span v-for="item in instance.items" :key="item.id">
                    {{ item.serviceName }} {{ item.remainingQuantity }}/{{ item.totalQuantity }}
                  </span>
                </div>
                <el-button
                  v-if="canManagePackage && ['ACTIVE', 'FROZEN'].includes(instance.status)"
                  link
                  type="primary"
                  @click="togglePackageStatus(instance)"
                >
                  {{ instance.status === 'ACTIVE' ? '冻结套餐' : '解冻套餐' }}
                </el-button>
              </article>
              <el-empty v-if="!memberPackages.length" description="该会员暂无套餐" :image-size="72" />
            </div>
          </section>

          <section class="asset-panel">
            <div class="panel-heading">
              <div>
                <h2>会员账户</h2>
                <p>储值、赠送余额和积分分别记账。</p>
              </div>
            </div>
            <div class="account-list">
              <article v-for="account in memberAccounts" :key="account.id" class="account-row">
                <div class="account-summary">
                  <div>
                    <span>{{ accountLabel(account.accountType) }}</span>
                    <strong>
                      {{ account.accountType === 'POINTS' ? account.balance : formatMoney(account.balance) }}
                    </strong>
                  </div>
                  <el-tag :type="account.status === 'ACTIVE' ? 'success' : 'danger'">
                    {{ account.status === 'ACTIVE' ? '正常' : '已冻结' }}
                  </el-tag>
                </div>
                <div class="account-actions">
                  <template v-if="canManageAccount">
                    <el-button link type="primary" :disabled="account.status !== 'ACTIVE'" @click="openAccountAction(account, 'credits')">
                      入账
                    </el-button>
                    <el-button link type="primary" :disabled="account.status !== 'ACTIVE'" @click="openAccountAction(account, 'debits')">
                      扣减
                    </el-button>
                    <el-button link @click="toggleAccountStatus(account)">
                      {{ account.status === 'ACTIVE' ? '冻结' : '解冻' }}
                    </el-button>
                  </template>
                  <el-button link @click="toggleAccountLedger(account)">
                    {{ expandedAccountId === account.id ? '收起流水' : '查看流水' }}
                  </el-button>
                </div>
                <div v-if="expandedAccountId === account.id" class="ledger-list">
                  <div
                    v-for="ledger in accountLedgers[account.id] ?? []"
                    :key="String(ledger.id)"
                    class="ledger-row"
                  >
                    <div>
                      <strong>{{ ledger.entryType }}</strong>
                      <span>{{ ledger.createdAt }}</span>
                    </div>
                    <div>
                      <strong>{{ Number(ledger.amountDelta) > 0 ? '+' : '' }}{{ ledger.amountDelta }}</strong>
                      <el-button
                        v-if="canManageAccount && ledger.entryType !== 'REVERSAL' && !ledger.reversalOfLedgerId"
                        link
                        type="danger"
                        @click="reverseLedger(account, ledger)"
                      >
                        冲正
                      </el-button>
                    </div>
                  </div>
                  <el-empty
                    v-if="!accountLedgers[account.id]?.length"
                    description="暂无账户流水"
                    :image-size="56"
                  />
                </div>
              </article>
            </div>
          </section>
        </div>
        <el-empty v-else description="请选择会员后查看资产" :image-size="88" />
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="productEditorOpen" title="新建套餐产品" size="560px">
      <el-form ref="productFormRef" :model="productEditor" :rules="productRules" label-position="top">
        <div class="form-grid">
          <el-form-item label="套餐编号" prop="packageCode">
            <el-input v-model="productEditor.packageCode" maxlength="48" placeholder="例如 FACE-SPA-10" />
          </el-form-item>
          <el-form-item label="套餐名称" prop="name">
            <el-input v-model="productEditor.name" maxlength="120" />
          </el-form-item>
          <el-form-item label="销售价格" prop="salePrice">
            <el-input-number v-model="productEditor.salePrice" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item label="有效天数" prop="validityDays">
            <el-input-number v-model="productEditor.validityDays" :min="1" :max="3650" />
          </el-form-item>
          <el-form-item label="发布状态">
            <el-radio-group v-model="productEditor.status">
              <el-radio value="ACTIVE">立即生效</el-radio>
              <el-radio value="DRAFT">保存草稿</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item class="full" label="套餐说明">
            <el-input v-model="productEditor.description" type="textarea" :rows="3" maxlength="1000" show-word-limit />
          </el-form-item>
        </div>
        <div class="item-editor-heading">
          <div>
            <strong>包含项目</strong>
            <span>发布后将按此内容生成会员权益快照。</span>
          </div>
          <el-button @click="addProductItem">添加项目</el-button>
        </div>
        <div v-for="item in productEditor.items" :key="item.key" class="item-editor-row">
          <el-select v-model="item.serviceId" filterable placeholder="选择服务项目">
            <el-option
              v-for="service in services"
              :key="service.id"
              :label="service.name"
              :value="service.id"
            />
          </el-select>
          <el-input-number v-model="item.quantity" :min="0.0001" :precision="4" />
          <el-button link type="danger" @click="removeProductItem(item.key)">移除</el-button>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="productEditorOpen = false">返回</el-button>
        <el-button type="primary" :loading="saving" @click="saveProduct">保存套餐</el-button>
      </template>
    </el-drawer>

    <el-dialog
      v-model="accountAction.open"
      :title="accountAction.direction === 'credits' ? '会员账户入账' : '会员账户扣减'"
      width="440px"
    >
      <el-form label-position="top">
        <el-form-item label="金额 / 积分">
          <el-input-number
            v-model="accountAction.amount"
            :min="0.01"
            :precision="accountAction.account?.accountType === 'POINTS' ? 0 : 2"
            class="full-control"
          />
        </el-form-item>
        <el-form-item label="操作说明">
          <el-input v-model="accountAction.remark" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountAction.open = false">返回</el-button>
        <el-button type="primary" :loading="saving" @click="submitAccountAction">确认提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.assets-view {
  display: grid;
  gap: 22px;
}

.view-header,
.toolbar-row,
.member-selector,
.member-context,
.panel-heading,
.package-summary,
.account-summary,
.ledger-row,
.item-editor-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.view-header {
  align-items: end;
}

.view-header h1,
.panel-heading h2 {
  margin: 5px 0 0;
}

.view-header p,
.toolbar-row span,
.member-selector p,
.panel-heading p,
.primary-cell span,
.package-summary span,
.item-editor-heading span,
.ledger-row span {
  color: var(--oc-text-muted);
}

.view-header p,
.member-selector p,
.panel-heading p {
  margin: 7px 0 0;
}

.section-label {
  color: var(--oc-primary);
  font-size: 13px;
  font-weight: 700;
}

.shop-select {
  width: 220px;
}

.asset-tabs {
  min-height: 560px;
  padding: 8px 24px 24px;
  background: var(--oc-surface);
  border: 1px solid var(--oc-border);
  border-radius: 12px;
}

.toolbar-row {
  padding: 12px 0 20px;
}

.package-flow-note {
  display: grid;
  gap: 6px;
  margin: 8px 0 10px;
  padding: 14px 16px;
  border: 1px solid var(--oc-border);
  border-radius: 10px;
  color: var(--oc-text-muted);
  background: var(--oc-surface-2);
  font-size: 13px;
  line-height: 1.6;
}

.package-flow-note strong {
  color: var(--oc-text);
}

.toolbar-row > div,
.primary-cell,
.member-context {
  display: grid;
  gap: 5px;
}

.member-selector {
  padding: 14px 0 22px;
  border-bottom: 1px solid var(--oc-border);
}

.member-selector .el-select {
  width: min(420px, 100%);
}

.member-context {
  justify-content: start;
  margin-top: 20px;
  padding: 14px 16px;
  background: var(--oc-surface-muted);
}

.member-assets-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(360px, 0.92fr);
  gap: 20px;
  margin-top: 20px;
}

.asset-panel {
  min-width: 0;
  border: 1px solid var(--oc-border);
  border-radius: 10px;
}

.panel-heading {
  padding: 18px 20px;
  border-bottom: 1px solid var(--oc-border);
}

.panel-heading h2 {
  font-size: 18px;
}

.issue-form {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 150px auto;
  gap: 10px;
  padding: 18px 20px 8px;
}

.operation-hint {
  margin: 0;
  padding: 0 20px 16px;
  color: var(--oc-text-muted);
  font-size: 12px;
}

.package-row,
.account-row {
  padding: 18px 20px;
  border-top: 1px solid var(--oc-border);
}

.package-list .package-row:first-child,
.account-list .account-row:first-child {
  border-top: 0;
}

.package-summary {
  align-items: start;
}

.package-summary > div:first-child,
.account-summary > div:first-child {
  display: grid;
  gap: 5px;
}

.package-balance {
  display: grid;
  justify-items: end;
  gap: 7px;
}

.package-items {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin: 14px 0 10px;
}

.package-items span {
  padding: 5px 8px;
  color: var(--oc-text-secondary);
  background: var(--oc-surface-muted);
  font-size: 12px;
}

.account-summary {
  align-items: start;
}

.account-summary strong {
  font-size: 22px;
}

.account-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 10px;
}

.ledger-list {
  margin-top: 14px;
  border-top: 1px solid var(--oc-border);
}

.ledger-row {
  padding: 12px 0;
  border-bottom: 1px solid var(--oc-border);
}

.ledger-row > div {
  display: grid;
  gap: 4px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.form-grid .full {
  grid-column: 1 / -1;
}

.item-editor-heading {
  margin: 8px 0 12px;
  padding-top: 18px;
  border-top: 1px solid var(--oc-border);
}

.item-editor-heading > div {
  display: grid;
  gap: 4px;
}

.item-editor-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 130px auto;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.full-control {
  width: 100%;
}

@media (max-width: 1100px) {
  .member-assets-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .view-header,
  .member-selector,
  .toolbar-row {
    align-items: stretch;
    flex-direction: column;
  }

  .shop-select,
  .member-selector .el-select {
    width: 100%;
  }

  .issue-form,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .form-grid .full {
    grid-column: auto;
  }
}
</style>
