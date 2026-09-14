<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Box, Plus, RefreshLeft, Search, Sort, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import {
  adjustInventory,
  createInventoryTransfer,
  decideInventoryTransfer,
  getInventory,
  getInventoryMovements,
  getInventoryResources,
  getInventoryTransfers,
  type InventoryLocationBalance,
  type InventoryMovement,
  type InventoryMovementType,
  type InventoryPage,
  type InventoryProductSummary,
  type InventoryResources,
  type InventoryTransfer,
} from '../services/api'

type PageTab = 'stock' | 'movements' | 'transfers'
type EditorMode = 'adjust' | 'transfer'
type AdjustmentType =
  | 'PURCHASE_IN'
  | 'RETURN_IN'
  | 'MANUAL_IN'
  | 'MANUAL_OUT'
  | 'SERVICE_USE'
  | 'ADJUSTMENT'

const auth = useAuthStore()
const query = reactive({
  shopId: undefined as number | undefined,
  keyword: '',
  lowStockOnly: false,
  page: 1,
  pageSize: 30,
})
const activeTab = ref<PageTab>('stock')
const inventory = ref<InventoryPage>()
const resources = ref<InventoryResources>()
const movements = ref<InventoryMovement[]>([])
const movementTotal = ref(0)
const transfers = ref<InventoryTransfer[]>([])
const transferTotal = ref(0)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const editorMode = ref<EditorMode>()
const selectedProduct = ref<InventoryProductSummary>()
const actionId = ref<number>()

const movementFilter = reactive({
  productId: undefined as number | undefined,
  movementType: 'ALL' as InventoryMovementType | 'ALL',
  page: 1,
  pageSize: 30,
})
const transferFilter = reactive({
  status: 'ALL' as 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED',
  page: 1,
  pageSize: 30,
})
const adjustmentForm = reactive({
  locationId: undefined as number | undefined,
  movementType: 'PURCHASE_IN' as AdjustmentType,
  quantity: 1,
  referenceNo: '',
  remark: '',
})
const transferForm = reactive({
  sourceLocationId: undefined as number | undefined,
  destinationLocationId: undefined as number | undefined,
  quantity: 1,
  remark: '',
})

const adjustmentTypes: Array<{ value: AdjustmentType; label: string; direction: 'in' | 'out' | 'any' }> = [
  { value: 'PURCHASE_IN', label: '采购入库', direction: 'in' },
  { value: 'RETURN_IN', label: '退货入库', direction: 'in' },
  { value: 'MANUAL_IN', label: '其他入库', direction: 'in' },
  { value: 'MANUAL_OUT', label: '其他出库', direction: 'out' },
  { value: 'SERVICE_USE', label: '护理领用', direction: 'out' },
  { value: 'ADJUSTMENT', label: '盘点调整', direction: 'any' },
]

const movementTypes: Array<{ value: InventoryMovementType | 'ALL'; label: string }> = [
  { value: 'ALL', label: '全部流水' },
  { value: 'PURCHASE_IN', label: '采购入库' },
  { value: 'SALE_OUT', label: '销售出库' },
  { value: 'SERVICE_USE', label: '护理领用' },
  { value: 'RETURN_IN', label: '退货入库' },
  { value: 'TRANSFER_OUT', label: '调拨出库' },
  { value: 'TRANSFER_IN', label: '调拨入库' },
  { value: 'MANUAL_IN', label: '其他入库' },
  { value: 'MANUAL_OUT', label: '其他出库' },
  { value: 'ADJUSTMENT', label: '盘点调整' },
  { value: 'INITIAL_BALANCE', label: '期初建账' },
  { value: 'LEGACY_OUT', label: '历史出库' },
]

const currentLocations = computed(
  () => resources.value?.locations.filter((item) => item.shopId === query.shopId) ?? [],
)
const destinationLocations = computed(
  () => resources.value?.locations.filter((item) => item.id !== transferForm.sourceLocationId) ?? [],
)
const selectedAdjustmentBalance = computed(() =>
  selectedProduct.value?.locations.find((item) => item.locationId === adjustmentForm.locationId),
)
const selectedTransferBalance = computed(() =>
  selectedProduct.value?.locations.find((item) => item.locationId === transferForm.sourceLocationId),
)
const selectedAdjustmentType = computed(() =>
  adjustmentTypes.find((item) => item.value === adjustmentForm.movementType),
)
const selectedShopName = computed(
  () => auth.shops.find((shop) => shop.id === query.shopId)?.name ?? '当前门店',
)

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  query.shopId = auth.context?.homeShopId ?? auth.shops[0]?.id
  await Promise.all([loadStock(), loadResources()])
})

async function loadStock() {
  if (!query.shopId) return
  loading.value = true
  error.value = ''
  try {
    inventory.value = await getInventory({
      shopId: query.shopId,
      keyword: query.keyword.trim() || undefined,
      lowStockOnly: query.lowStockOnly,
      page: query.page,
      pageSize: query.pageSize,
    })
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '库存列表加载失败'
  } finally {
    loading.value = false
  }
}

async function loadResources() {
  if (!query.shopId) return
  try {
    resources.value = await getInventoryResources(query.shopId)
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '库存基础数据加载失败')
  }
}

async function loadMovements() {
  if (!query.shopId) return
  loading.value = true
  error.value = ''
  try {
    const page = await getInventoryMovements({
      shopId: query.shopId,
      productId: movementFilter.productId,
      movementType: movementFilter.movementType,
      page: movementFilter.page,
      pageSize: movementFilter.pageSize,
    })
    movements.value = page.records
    movementTotal.value = page.total
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '库存流水加载失败'
  } finally {
    loading.value = false
  }
}

async function loadTransfers() {
  if (!query.shopId) return
  loading.value = true
  error.value = ''
  try {
    const page = await getInventoryTransfers({
      shopId: query.shopId,
      status: transferFilter.status,
      page: transferFilter.page,
      pageSize: transferFilter.pageSize,
    })
    transfers.value = page.records
    transferTotal.value = page.total
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '调拨单加载失败'
  } finally {
    loading.value = false
  }
}

async function selectTab(tab: PageTab) {
  activeTab.value = tab
  closeEditor()
  if (tab === 'stock') await loadStock()
  if (tab === 'movements') await loadMovements()
  if (tab === 'transfers') await loadTransfers()
}

async function applyStockFilters() {
  query.page = 1
  closeEditor()
  await Promise.all([loadStock(), loadResources()])
}

async function resetStockFilters() {
  query.keyword = ''
  query.lowStockOnly = false
  query.page = 1
  await loadStock()
}

function openAdjust(product: InventoryProductSummary, location?: InventoryLocationBalance) {
  selectedProduct.value = product
  const preferred = location ?? product.locations.find((item) => item.locationType === 'SHOP')
  Object.assign(adjustmentForm, {
    locationId: preferred?.locationId,
    movementType: 'PURCHASE_IN',
    quantity: 1,
    referenceNo: '',
    remark: '',
  })
  editorMode.value = 'adjust'
}

function openTransfer(product: InventoryProductSummary) {
  selectedProduct.value = product
  const source = product.locations.find(
    (item) => item.locationType === 'SHOP' && Number(item.quantityAvailable) > 0,
  )
  Object.assign(transferForm, {
    sourceLocationId: source?.locationId,
    destinationLocationId: currentLocations.value.find((item) => item.id !== source?.locationId)?.id,
    quantity: 1,
    remark: '',
  })
  editorMode.value = 'transfer'
}

function closeEditor() {
  editorMode.value = undefined
  selectedProduct.value = undefined
}

function signedAdjustmentQuantity() {
  const direction = selectedAdjustmentType.value?.direction
  if (direction === 'out') return -Math.abs(adjustmentForm.quantity)
  if (direction === 'in') return Math.abs(adjustmentForm.quantity)
  return adjustmentForm.quantity
}

async function submitAdjustment() {
  if (!query.shopId || !selectedProduct.value || !selectedAdjustmentBalance.value) {
    ElMessage.warning('请选择库存地点')
    return
  }
  const delta = signedAdjustmentQuantity()
  if (!delta) {
    ElMessage.warning('变动数量不能为0')
    return
  }
  if (!adjustmentForm.remark.trim()) {
    ElMessage.warning('请填写库存变动原因')
    return
  }
  saving.value = true
  try {
    await adjustInventory({
      shopId: query.shopId,
      locationId: selectedAdjustmentBalance.value.locationId,
      productId: selectedProduct.value.id,
      quantityDelta: delta,
      movementType: adjustmentForm.movementType,
      version: selectedAdjustmentBalance.value.version,
      idempotencyKey: uniqueKey('inventory'),
      referenceNo: adjustmentForm.referenceNo.trim() || undefined,
      remark: adjustmentForm.remark.trim(),
    })
    ElMessage.success('库存变动已入账')
    closeEditor()
    await Promise.all([loadStock(), loadResources()])
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '库存变动提交失败')
  } finally {
    saving.value = false
  }
}

async function submitTransfer() {
  if (
    !query.shopId ||
    !selectedProduct.value ||
    !selectedTransferBalance.value ||
    !transferForm.destinationLocationId
  ) {
    ElMessage.warning('请选择调出和调入库存地点')
    return
  }
  if (transferForm.quantity <= 0) {
    ElMessage.warning('调拨数量必须大于0')
    return
  }
  if (transferForm.quantity > Number(selectedTransferBalance.value.quantityAvailable)) {
    ElMessage.warning('调拨数量不能超过可用库存')
    return
  }
  saving.value = true
  try {
    const created = await createInventoryTransfer({
      sourceShopId: query.shopId,
      sourceLocationId: selectedTransferBalance.value.locationId,
      destinationLocationId: transferForm.destinationLocationId,
      productId: selectedProduct.value.id,
      quantity: transferForm.quantity,
      version: selectedTransferBalance.value.version,
      idempotencyKey: uniqueKey('transfer'),
      remark: transferForm.remark.trim() || undefined,
    })
    ElMessage.success(`调拨单 ${created.transferNo} 已提交审核`)
    closeEditor()
    activeTab.value = 'transfers'
    await Promise.all([loadTransfers(), loadStock()])
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '创建调拨单失败')
  } finally {
    saving.value = false
  }
}

async function decideTransfer(transfer: InventoryTransfer, action: 'APPROVE' | 'REJECT') {
  let decisionNote = ''
  try {
    if (action === 'REJECT') {
      const result = await ElMessageBox.prompt(
        '拒绝后会释放已冻结库存，请填写审核原因。',
        '拒绝调拨',
        {
          inputType: 'textarea',
          confirmButtonText: '确认拒绝',
          cancelButtonText: '返回',
          inputValidator: (value) => Boolean(value?.trim()) || '必须填写审核原因',
        },
      )
      decisionNote = result.value.trim()
    } else {
      await ElMessageBox.confirm(
        `确认将 ${formatQuantity(transfer.quantity)} 件 ${transfer.productName} 调入 ${transfer.destinationLocationName}？`,
        '批准调拨',
        { confirmButtonText: '确认调拨', cancelButtonText: '返回', type: 'warning' },
      )
    }
    actionId.value = transfer.id
    await decideInventoryTransfer(transfer.id, {
      shopId: query.shopId!,
      action,
      version: transfer.version,
      decisionNote: decisionNote || undefined,
    })
    ElMessage.success(action === 'APPROVE' ? '调拨已完成' : '调拨已拒绝')
    await Promise.all([loadTransfers(), loadStock(), loadResources()])
  } catch (reasonOrCancel) {
    if (reasonOrCancel === 'cancel' || reasonOrCancel === 'close') return
    ElMessage.error(reasonOrCancel instanceof Error ? reasonOrCancel.message : '调拨审核失败')
  } finally {
    actionId.value = undefined
  }
}

function movementLabel(type: InventoryMovementType) {
  return movementTypes.find((item) => item.value === type)?.label ?? type
}

function movementClass(delta: number) {
  return Number(delta) > 0 ? 'inventory-delta inventory-delta--in' : 'inventory-delta inventory-delta--out'
}

function transferStatusLabel(status: InventoryTransfer['status']) {
  return { PENDING: '待审核', APPROVED: '已完成', REJECTED: '已拒绝' }[status]
}

function formatQuantity(value: number, unit = '') {
  const number = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 3 }).format(Number(value ?? 0))
  return unit ? `${number} ${unit}` : number
}

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function uniqueKey(prefix: string) {
  const value = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
  return `${prefix}-${value}`.slice(0, 80)
}
</script>

<template>
  <section class="page-heading inventory-page-heading">
    <div>
      <p class="environment-label">门店实物资产</p>
      <h1>库存与调拨</h1>
      <p>统一查看门店库存、冻结数量和完整流水，调拨经过审核后才会改变账面库存。</p>
    </div>
  </section>

  <section class="inventory-metrics" aria-label="库存摘要">
    <div>
      <span>在售商品</span>
      <strong>{{ inventory?.summary.productCount ?? 0 }}</strong>
      <small>{{ selectedShopName }}</small>
    </div>
    <div>
      <span>账面库存</span>
      <strong>{{ formatQuantity(inventory?.summary.quantityOnHand ?? 0) }}</strong>
      <small>所有库存地点合计</small>
    </div>
    <div>
      <span>调拨冻结</span>
      <strong>{{ formatQuantity(inventory?.summary.quantityReserved ?? 0) }}</strong>
      <small>待审核调拨占用</small>
    </div>
    <div :class="{ 'inventory-metric--warning': Number(inventory?.summary.lowStockCount ?? 0) > 0 }">
      <span>低库存预警</span>
      <strong>{{ inventory?.summary.lowStockCount ?? 0 }}</strong>
      <small>库存小于或等于预警线</small>
    </div>
  </section>

  <nav class="inventory-tabs" aria-label="库存中心视图">
    <button :class="{ active: activeTab === 'stock' }" type="button" @click="selectTab('stock')">
      <el-icon><Box /></el-icon>
      库存台账
    </button>
    <button
      :class="{ active: activeTab === 'movements' }"
      type="button"
      @click="selectTab('movements')"
    >
      <el-icon><Sort /></el-icon>
      库存流水
    </button>
    <button
      :class="{ active: activeTab === 'transfers' }"
      type="button"
      @click="selectTab('transfers')"
    >
      调拨审核
      <span v-if="transfers.filter((item) => item.status === 'PENDING').length">
        {{ transfers.filter((item) => item.status === 'PENDING').length }}
      </span>
    </button>
  </nav>

  <el-alert
    v-if="error"
    class="inventory-error"
    :title="error"
    type="error"
    show-icon
    :closable="false"
  />

  <template v-if="activeTab === 'stock'">
    <section class="content-section inventory-toolbar" aria-label="库存筛选">
      <div class="inventory-filters">
        <el-select v-model="query.shopId" aria-label="库存门店" @change="applyStockFilters">
          <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
        </el-select>
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="商品名称、SKU 或品牌"
          aria-label="搜索库存商品"
          @keyup.enter="applyStockFilters"
          @clear="applyStockFilters"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-checkbox v-model="query.lowStockOnly" @change="applyStockFilters">只看低库存</el-checkbox>
        <el-button :loading="loading" @click="applyStockFilters">查询</el-button>
        <el-button text @click="resetStockFilters">
          <el-icon><RefreshLeft /></el-icon>
          重置
        </el-button>
      </div>
      <span>共 {{ inventory?.total ?? 0 }} 个商品</span>
    </section>

    <div
      :class="[
        'inventory-page-layout',
        { 'inventory-page-layout--editing': Boolean(editorMode) },
      ]"
    >
      <section class="content-section inventory-results">
        <div v-if="loading" class="inventory-loading">
          <el-skeleton :rows="8" animated />
        </div>
        <template v-else-if="inventory?.records.length">
          <div class="inventory-table-wrap">
            <table class="inventory-table">
              <thead>
                <tr>
                  <th>商品</th>
                  <th>库存地点</th>
                  <th>账面 / 冻结 / 可用</th>
                  <th>预警线</th>
                  <th>状态</th>
                  <th><span class="visually-hidden">操作</span></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="product in inventory.records" :key="product.id">
                  <td>
                    <div class="inventory-product">
                      <strong>{{ product.name }}</strong>
                      <span>{{ product.brandName || '未设置品牌' }}</span>
                      <code>{{ product.sku }}</code>
                    </div>
                  </td>
                  <td>
                    <div class="inventory-location-list">
                      <span v-for="location in product.locations" :key="location.id">
                        {{ location.locationName }}
                      </span>
                    </div>
                  </td>
                  <td>
                    <div class="inventory-quantity-list">
                      <span v-for="location in product.locations" :key="location.id">
                        <strong>{{ formatQuantity(location.quantityOnHand) }}</strong>
                        <small>/ {{ formatQuantity(location.quantityReserved) }} / {{ formatQuantity(location.quantityAvailable) }}</small>
                      </span>
                    </div>
                  </td>
                  <td>{{ formatQuantity(product.warningQuantity, product.unitName) }}</td>
                  <td>
                    <span v-if="product.lowStock" class="inventory-stock-state inventory-stock-state--low">
                      <el-icon><WarningFilled /></el-icon>
                      库存不足
                    </span>
                    <span v-else class="inventory-stock-state">库存正常</span>
                  </td>
                  <td>
                    <div class="inventory-actions">
                      <el-button text type="primary" @click="openAdjust(product)">
                        变动
                      </el-button>
                      <el-button text @click="openTransfer(product)">调拨</el-button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <footer class="inventory-pagination">
            <span>第 {{ query.page }} 页</span>
            <el-pagination
              v-model:current-page="query.page"
              v-model:page-size="query.pageSize"
              layout="prev, pager, next"
              :total="inventory.total"
              @current-change="loadStock"
            />
          </footer>
        </template>
        <div v-else class="empty-state inventory-empty">
          <el-icon><Box /></el-icon>
          <h2>{{ query.lowStockOnly ? '当前没有低库存商品' : '还没有库存商品' }}</h2>
          <p>{{ query.lowStockOnly ? '所有商品均高于预警线。' : '先在商品目录中建立商品，再录入期初库存。' }}</p>
        </div>
      </section>

      <aside v-if="editorMode === 'adjust' && selectedProduct" class="inventory-editor">
        <header class="inventory-editor-heading">
          <div>
            <p class="environment-label">库存入账</p>
            <h2>{{ selectedProduct.name }}</h2>
            <span>{{ selectedProduct.sku }}</span>
          </div>
          <el-button text aria-label="关闭库存变动面板" @click="closeEditor">关闭</el-button>
        </header>
        <el-form label-position="top">
          <el-form-item label="库存地点">
            <el-select v-model="adjustmentForm.locationId">
              <el-option
                v-for="location in selectedProduct.locations"
                :key="location.id"
                :label="`${location.locationName} · 可用 ${formatQuantity(location.quantityAvailable)}`"
                :value="location.locationId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="业务类型">
            <el-select v-model="adjustmentForm.movementType">
              <el-option
                v-for="type in adjustmentTypes"
                :key="type.value"
                :label="type.label"
                :value="type.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="selectedAdjustmentType?.direction === 'out' ? '出库数量' : '变动数量'">
            <el-input-number
              v-model="adjustmentForm.quantity"
              :min="selectedAdjustmentType?.direction === 'any' ? -999999 : 0.001"
              :max="999999"
              :precision="3"
              :step="1"
            />
            <p v-if="selectedAdjustmentType?.direction === 'any'" class="form-helper">
              盘盈填正数，盘亏填负数。
            </p>
          </el-form-item>
          <el-form-item label="业务单号（可选）">
            <el-input v-model="adjustmentForm.referenceNo" maxlength="80" placeholder="采购单、退货单或盘点单号" />
          </el-form-item>
          <el-form-item label="变动原因">
            <el-input
              v-model="adjustmentForm.remark"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="说明库存为什么发生变化"
            />
          </el-form-item>
        </el-form>
        <footer class="inventory-editor-actions">
          <el-button @click="closeEditor">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitAdjustment">
            确认入账
          </el-button>
        </footer>
      </aside>

      <aside v-else-if="editorMode === 'transfer' && selectedProduct" class="inventory-editor">
        <header class="inventory-editor-heading">
          <div>
            <p class="environment-label">创建调拨</p>
            <h2>{{ selectedProduct.name }}</h2>
            <span>{{ selectedProduct.sku }}</span>
          </div>
          <el-button text aria-label="关闭调拨面板" @click="closeEditor">关闭</el-button>
        </header>
        <el-form label-position="top">
          <el-form-item label="调出库存地点">
            <el-select v-model="transferForm.sourceLocationId">
              <el-option
                v-for="location in selectedProduct.locations"
                :key="location.id"
                :disabled="Number(location.quantityAvailable) <= 0"
                :label="`${location.locationName} · 可用 ${formatQuantity(location.quantityAvailable)}`"
                :value="location.locationId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="调入库存地点">
            <el-select v-model="transferForm.destinationLocationId" filterable>
              <el-option
                v-for="location in destinationLocations"
                :key="location.id"
                :label="`${location.shopName} · ${location.name}`"
                :value="location.id"
              />
            </el-select>
            <p class="form-helper">跨店调拨要求调入门店已配置相同 SKU。</p>
          </el-form-item>
          <el-form-item label="调拨数量">
            <el-input-number
              v-model="transferForm.quantity"
              :min="0.001"
              :max="Number(selectedTransferBalance?.quantityAvailable ?? 0)"
              :precision="3"
              :step="1"
            />
          </el-form-item>
          <el-form-item label="调拨说明（可选）">
            <el-input
              v-model="transferForm.remark"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="说明调拨用途或接收要求"
            />
          </el-form-item>
        </el-form>
        <div class="inventory-reservation-note">
          提交后将先冻结调出库存，审核通过才会正式调出并计入目标地点。
        </div>
        <footer class="inventory-editor-actions">
          <el-button @click="closeEditor">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitTransfer">
            提交审核
          </el-button>
        </footer>
      </aside>
    </div>
  </template>

  <template v-else-if="activeTab === 'movements'">
    <section class="content-section inventory-toolbar">
      <div class="inventory-filters">
        <el-select
          v-model="movementFilter.productId"
          clearable
          filterable
          placeholder="全部商品"
          @change="loadMovements"
        >
          <el-option
            v-for="product in resources?.products"
            :key="product.id"
            :label="`${product.name} · ${product.sku}`"
            :value="product.id"
          />
        </el-select>
        <el-select v-model="movementFilter.movementType" @change="loadMovements">
          <el-option
            v-for="type in movementTypes"
            :key="type.value"
            :label="type.label"
            :value="type.value"
          />
        </el-select>
      </div>
      <span>共 {{ movementTotal }} 条不可变流水</span>
    </section>
    <section class="content-section inventory-results">
      <div v-if="loading" class="inventory-loading"><el-skeleton :rows="8" animated /></div>
      <div v-else-if="movements.length" class="inventory-table-wrap">
        <table class="inventory-table inventory-movement-table">
          <thead>
            <tr>
              <th>发生时间</th>
              <th>商品</th>
              <th>库存地点</th>
              <th>业务类型</th>
              <th>变动数量</th>
              <th>变动后库存</th>
              <th>单号与说明</th>
              <th>操作人</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="movement in movements" :key="movement.id">
              <td>{{ formatDateTime(movement.createdAt) }}</td>
              <td>
                <div class="inventory-product">
                  <strong>{{ movement.productName }}</strong>
                  <code>{{ movement.sku }}</code>
                </div>
              </td>
              <td>{{ movement.locationName }}</td>
              <td>{{ movementLabel(movement.movementType) }}</td>
              <td>
                <strong :class="movementClass(movement.quantityDelta)">
                  {{ Number(movement.quantityDelta) > 0 ? '+' : '' }}{{ formatQuantity(movement.quantityDelta) }}
                </strong>
              </td>
              <td>{{ formatQuantity(movement.balanceAfter, movement.unitName) }}</td>
              <td>
                <div class="inventory-reference">
                  <code>{{ movement.referenceNo || '—' }}</code>
                  <span>{{ movement.remark || '无补充说明' }}</span>
                </div>
              </td>
              <td>{{ movement.createdByName }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state inventory-empty">
        <el-icon><Sort /></el-icon>
        <h2>没有符合条件的库存流水</h2>
        <p>库存变动完成后会自动留在这里，历史记录不能删除或修改。</p>
      </div>
    </section>
  </template>

  <template v-else>
    <section class="content-section inventory-toolbar">
      <div class="inventory-filters">
        <el-select v-model="transferFilter.status" @change="loadTransfers">
          <el-option label="全部状态" value="ALL" />
          <el-option label="待审核" value="PENDING" />
          <el-option label="已完成" value="APPROVED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
      </div>
      <span>共 {{ transferTotal }} 张调拨单</span>
    </section>
    <section class="content-section inventory-results">
      <div v-if="loading" class="inventory-loading"><el-skeleton :rows="8" animated /></div>
      <div v-else-if="transfers.length" class="inventory-table-wrap">
        <table class="inventory-table inventory-transfer-table">
          <thead>
            <tr>
              <th>调拨单</th>
              <th>商品</th>
              <th>调出</th>
              <th>调入</th>
              <th>数量</th>
              <th>状态</th>
              <th>申请时间</th>
              <th><span class="visually-hidden">操作</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="transfer in transfers" :key="transfer.id">
              <td>
                <div class="inventory-reference">
                  <code>{{ transfer.transferNo }}</code>
                  <span>{{ transfer.remark || transfer.decisionNote || '无补充说明' }}</span>
                </div>
              </td>
              <td>
                <div class="inventory-product">
                  <strong>{{ transfer.productName }}</strong>
                  <code>{{ transfer.sku }}</code>
                </div>
              </td>
              <td>{{ transfer.sourceShopName }}<br /><small>{{ transfer.sourceLocationName }}</small></td>
              <td>{{ transfer.destinationShopName }}<br /><small>{{ transfer.destinationLocationName }}</small></td>
              <td>{{ formatQuantity(transfer.quantity) }}</td>
              <td>
                <span :class="`inventory-transfer-status inventory-transfer-status--${transfer.status.toLowerCase()}`">
                  {{ transferStatusLabel(transfer.status) }}
                </span>
              </td>
              <td>{{ formatDateTime(transfer.createdAt) }}</td>
              <td>
                <div v-if="transfer.status === 'PENDING'" class="inventory-actions">
                  <el-button
                    text
                    type="primary"
                    :loading="actionId === transfer.id"
                    @click="decideTransfer(transfer, 'APPROVE')"
                  >
                    批准
                  </el-button>
                  <el-button text type="danger" @click="decideTransfer(transfer, 'REJECT')">
                    拒绝
                  </el-button>
                </div>
                <span v-else class="inventory-reviewed-by">
                  {{ transfer.approvedByName || '系统' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state inventory-empty">
        <el-icon><Plus /></el-icon>
        <h2>还没有调拨单</h2>
        <p>在库存台账中选择商品并发起调拨，待审核记录会出现在这里。</p>
      </div>
    </section>
  </template>
</template>
