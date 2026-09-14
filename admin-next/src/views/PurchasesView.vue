<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Box,
  CirclePlus,
  RefreshRight,
  ShoppingCart,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import {
  createPurchaseOrder,
  decidePurchaseOrder,
  getInventoryBatches,
  getInventoryResources,
  getPurchaseOrder,
  getPurchaseOrders,
  receivePurchaseOrder,
  submitPurchaseOrder,
  type InventoryResources,
  type PurchaseOrderDetail,
  type PurchaseOrderPage,
  type PurchaseOrderStatus,
  type StockBatch,
} from '../services/api'

type PageTab = 'orders' | 'batches'
type PanelMode = 'create' | 'detail' | 'receive'

interface DraftLine {
  productId?: number
  quantity: number
  unitCost: number
}

interface ReceiptLine {
  purchaseOrderItemId: number
  locationId?: number
  receivedQuantity: number
  unitCost: number
  vendorBatchNo: string
  producedDate: string
  expiryDate: string
}

const auth = useAuthStore()
const activeTab = ref<PageTab>('orders')
const panelMode = ref<PanelMode>()
const loading = ref(true)
const saving = ref(false)
const actionId = ref<number>()
const error = ref('')
const resources = ref<InventoryResources>()
const orders = ref<PurchaseOrderPage>()
const selectedOrder = ref<PurchaseOrderDetail>()
const batches = ref<StockBatch[]>([])

const query = reactive({
  shopId: undefined as number | undefined,
  status: 'ALL' as PurchaseOrderStatus | 'ALL',
  page: 1,
  pageSize: 30,
})
const batchFilter = reactive({
  productId: undefined as number | undefined,
  locationId: undefined as number | undefined,
})
const createForm = reactive({
  supplierName: '',
  expectedDate: '',
  currencyCode: 'CNY',
  remark: '',
  lines: [{ quantity: 1, unitCost: 0 }] as DraftLine[],
})
const receiptForm = reactive({
  receivedAt: '',
  remark: '',
  lines: [] as ReceiptLine[],
})

const permissions = computed(() => auth.context?.permissions ?? [])
const canViewOrders = computed(() => permissions.value.includes('purchase:view'))
const canManage = computed(() => permissions.value.includes('purchase:manage'))
const canApprove = computed(() => permissions.value.includes('purchase:approve'))
const canReceive = computed(() => permissions.value.includes('purchase:receive'))
const currentLocations = computed(
  () => resources.value?.locations.filter((item) => item.shopId === query.shopId) ?? [],
)
const selectedShopName = computed(
  () => auth.shops.find((shop) => shop.id === query.shopId)?.name ?? '当前门店',
)
const draftTotal = computed(() =>
  createForm.lines.reduce(
    (total, line) => total + Number(line.quantity || 0) * Number(line.unitCost || 0),
    0,
  ),
)
const statusCounts = computed(() => {
  const records = orders.value?.records ?? []
  return {
    pending: records.filter((item) => item.status === 'SUBMITTED').length,
    receiving: records.filter((item) =>
      ['APPROVED', 'PARTIALLY_RECEIVED'].includes(item.status),
    ).length,
    completed: records.filter((item) => item.status === 'RECEIVED').length,
  }
})

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  query.shopId = auth.context?.homeShopId ?? auth.shops[0]?.id
  activeTab.value = canViewOrders.value ? 'orders' : 'batches'
  await Promise.all([loadResources(), activeTab.value === 'orders' ? loadOrders() : loadBatches()])
})

async function loadResources() {
  if (!query.shopId) return
  try {
    resources.value = await getInventoryResources(query.shopId)
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '采购基础数据加载失败')
  }
}

async function loadOrders() {
  if (!query.shopId || !canViewOrders.value) return
  loading.value = true
  error.value = ''
  try {
    orders.value = await getPurchaseOrders({
      shopId: query.shopId,
      status: query.status,
      page: query.page,
      pageSize: query.pageSize,
    })
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '采购单加载失败'
  } finally {
    loading.value = false
  }
}

async function loadBatches() {
  if (!query.shopId) return
  loading.value = true
  error.value = ''
  try {
    batches.value = await getInventoryBatches({
      shopId: query.shopId,
      productId: batchFilter.productId,
      locationId: batchFilter.locationId,
    })
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '库存批次加载失败'
  } finally {
    loading.value = false
  }
}

async function changeShop() {
  closePanel()
  batchFilter.productId = undefined
  batchFilter.locationId = undefined
  await loadResources()
  await (activeTab.value === 'orders' ? loadOrders() : loadBatches())
}

async function selectTab(tab: PageTab) {
  activeTab.value = tab
  closePanel()
  await (tab === 'orders' ? loadOrders() : loadBatches())
}

function openCreate() {
  Object.assign(createForm, {
    supplierName: '',
    expectedDate: '',
    currencyCode: 'CNY',
    remark: '',
    lines: [{ quantity: 1, unitCost: 0 }],
  })
  selectedOrder.value = undefined
  panelMode.value = 'create'
}

function addDraftLine() {
  createForm.lines.push({ quantity: 1, unitCost: 0 })
}

function removeDraftLine(index: number) {
  if (createForm.lines.length === 1) {
    ElMessage.warning('采购单至少保留一条商品明细')
    return
  }
  createForm.lines.splice(index, 1)
}

async function saveDraft() {
  if (!query.shopId || !createForm.supplierName.trim()) {
    ElMessage.warning('请填写供应商名称')
    return
  }
  if (
    createForm.lines.some(
      (line) => !line.productId || Number(line.quantity) <= 0 || Number(line.unitCost) < 0,
    )
  ) {
    ElMessage.warning('请完整填写商品、采购数量和采购单价')
    return
  }
  if (new Set(createForm.lines.map((line) => line.productId)).size !== createForm.lines.length) {
    ElMessage.warning('同一商品不能重复添加')
    return
  }
  saving.value = true
  try {
    const created = await createPurchaseOrder({
      shop_id: query.shopId,
      supplier_name: createForm.supplierName.trim(),
      expected_date: createForm.expectedDate || undefined,
      currency_code: createForm.currencyCode,
      remark: createForm.remark.trim() || undefined,
      lines: createForm.lines.map((line) => ({
        product_id: line.productId!,
        quantity: Number(line.quantity),
        unit_cost: Number(line.unitCost),
      })),
    })
    ElMessage.success(`采购单 ${created.purchaseOrderNo} 已保存为草稿`)
    await loadOrders()
    await openDetail(created.id)
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '采购单创建失败')
  } finally {
    saving.value = false
  }
}

async function openDetail(orderId: number) {
  if (!query.shopId) return
  panelMode.value = 'detail'
  selectedOrder.value = undefined
  try {
    selectedOrder.value = await getPurchaseOrder(query.shopId, orderId)
  } catch (reason) {
    closePanel()
    ElMessage.error(reason instanceof Error ? reason.message : '采购单详情加载失败')
  }
}

async function submitOrder() {
  if (!query.shopId || !selectedOrder.value) return
  try {
    await ElMessageBox.confirm(
      '提交后采购内容不可修改，并进入独立审批流程。',
      '提交采购单',
      { confirmButtonText: '确认提交', cancelButtonText: '返回' },
    )
    actionId.value = selectedOrder.value.id
    selectedOrder.value = await submitPurchaseOrder(
      selectedOrder.value.id,
      query.shopId,
      selectedOrder.value.version,
    )
    ElMessage.success('采购单已提交审批')
    await loadOrders()
  } catch (reasonOrCancel) {
    if (reasonOrCancel === 'cancel' || reasonOrCancel === 'close') return
    ElMessage.error(reasonOrCancel instanceof Error ? reasonOrCancel.message : '采购单提交失败')
  } finally {
    actionId.value = undefined
  }
}

async function decideOrder(action: 'APPROVE' | 'CLOSE') {
  if (!query.shopId || !selectedOrder.value) return
  try {
    let note = ''
    if (action === 'CLOSE') {
      const result = await ElMessageBox.prompt(
        '关闭后不能收货，请填写审批说明。',
        '关闭采购申请',
        {
          inputType: 'textarea',
          confirmButtonText: '确认关闭',
          cancelButtonText: '返回',
          inputValidator: (value) => Boolean(value?.trim()) || '必须填写关闭原因',
        },
      )
      note = result.value.trim()
    } else {
      await ElMessageBox.confirm(
        '批准后采购单可以分批收货，申请人与审批人必须为不同账号。',
        '批准采购单',
        { confirmButtonText: '确认批准', cancelButtonText: '返回' },
      )
    }
    actionId.value = selectedOrder.value.id
    selectedOrder.value = await decidePurchaseOrder(selectedOrder.value.id, {
      shop_id: query.shopId,
      version: selectedOrder.value.version,
      action,
      decision_note: note || undefined,
    })
    ElMessage.success(action === 'APPROVE' ? '采购单已批准' : '采购单已关闭')
    await loadOrders()
  } catch (reasonOrCancel) {
    if (reasonOrCancel === 'cancel' || reasonOrCancel === 'close') return
    ElMessage.error(reasonOrCancel instanceof Error ? reasonOrCancel.message : '采购审批失败')
  } finally {
    actionId.value = undefined
  }
}

function openReceive() {
  if (!selectedOrder.value) return
  const defaultLocation = currentLocations.value.find((item) => item.locationType === 'SHOP')
    ?? currentLocations.value[0]
  receiptForm.receivedAt = ''
  receiptForm.remark = ''
  receiptForm.lines = selectedOrder.value.items
    .filter((item) => Number(item.outstandingQuantity) > 0)
    .map((item) => ({
      purchaseOrderItemId: item.id,
      locationId: defaultLocation?.id,
      receivedQuantity: Number(item.outstandingQuantity),
      unitCost: Number(item.unitCost),
      vendorBatchNo: '',
      producedDate: '',
      expiryDate: '',
    }))
  panelMode.value = 'receive'
}

async function postReceipt() {
  if (!query.shopId || !selectedOrder.value) return
  const lines = receiptForm.lines.filter((line) => Number(line.receivedQuantity) > 0)
  if (!lines.length || lines.some((line) => !line.locationId)) {
    ElMessage.warning('至少填写一条有效收货明细并选择库存地点')
    return
  }
  const invalidDates = lines.some(
    (line) => line.producedDate && line.expiryDate && line.producedDate > line.expiryDate,
  )
  if (invalidDates) {
    ElMessage.warning('失效日期不能早于生产日期')
    return
  }
  saving.value = true
  try {
    await receivePurchaseOrder(selectedOrder.value.id, {
      shop_id: query.shopId,
      version: selectedOrder.value.version,
      received_at: receiptForm.receivedAt
        ? new Date(receiptForm.receivedAt).toISOString()
        : undefined,
      remark: receiptForm.remark.trim() || undefined,
      lines: lines.map((line) => ({
        purchase_order_item_id: line.purchaseOrderItemId,
        location_id: line.locationId!,
        received_quantity: Number(line.receivedQuantity),
        unit_cost: Number(line.unitCost),
        vendor_batch_no: line.vendorBatchNo.trim() || undefined,
        produced_date: line.producedDate || undefined,
        expiry_date: line.expiryDate || undefined,
      })),
    })
    ElMessage.success('收货已入账，库存批次和库存流水同步生成')
    await Promise.all([loadOrders(), loadBatches()])
    await openDetail(selectedOrder.value.id)
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '采购收货失败')
  } finally {
    saving.value = false
  }
}

function closePanel() {
  panelMode.value = undefined
  selectedOrder.value = undefined
}

function statusLabel(status: PurchaseOrderStatus) {
  return {
    DRAFT: '草稿',
    SUBMITTED: '待审批',
    APPROVED: '待收货',
    PARTIALLY_RECEIVED: '部分收货',
    RECEIVED: '已完成',
    CLOSED: '已关闭',
  }[status]
}

function formatMoney(value: number, currency = 'CNY') {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format(Number(value ?? 0))
}

function formatQuantity(value: number, unit = '') {
  const formatted = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 3 }).format(Number(value ?? 0))
  return unit ? `${formatted} ${unit}` : formatted
}

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function daysUntil(value?: string) {
  if (!value) return undefined
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.ceil((new Date(`${value}T00:00:00`).getTime() - today.getTime()) / 86400000)
}

function batchExpiryText(batch: StockBatch) {
  const days = daysUntil(batch.expiryDate)
  if (days === undefined) return '未设置效期'
  if (days < 0) return `已过期 ${Math.abs(days)} 天`
  if (days === 0) return '今天到期'
  return `${days} 天后到期`
}
</script>

<template>
  <section class="page-heading purchase-page-heading">
    <div>
      <p class="environment-label">采购与库存协同</p>
      <h1>采购与批次</h1>
      <p>采购申请独立审批，到货后一次生成收货单、库存批次和不可变库存流水。</p>
    </div>
    <el-button v-if="activeTab === 'orders' && canManage" type="primary" @click="openCreate">
      <el-icon><CirclePlus /></el-icon>
      新建采购单
    </el-button>
  </section>

  <section class="purchase-metrics" aria-label="采购摘要">
    <div>
      <span>当前门店</span>
      <strong>{{ selectedShopName }}</strong>
      <small>数据按租户与门店隔离</small>
    </div>
    <div>
      <span>待审批</span>
      <strong>{{ statusCounts.pending }}</strong>
      <small>申请人与审批人分离</small>
    </div>
    <div>
      <span>待收货</span>
      <strong>{{ statusCounts.receiving }}</strong>
      <small>支持多次部分到货</small>
    </div>
    <div>
      <span>本页已完成</span>
      <strong>{{ statusCounts.completed }}</strong>
      <small>库存与批次同步入账</small>
    </div>
  </section>

  <nav class="purchase-tabs" aria-label="采购中心视图">
    <button
      v-if="canViewOrders"
      :class="{ active: activeTab === 'orders' }"
      type="button"
      @click="selectTab('orders')"
    >
      <el-icon><ShoppingCart /></el-icon>
      采购单
    </button>
    <button :class="{ active: activeTab === 'batches' }" type="button" @click="selectTab('batches')">
      <el-icon><Box /></el-icon>
      批次库存
    </button>
  </nav>

  <el-alert v-if="error" class="purchase-error" :title="error" type="error" show-icon :closable="false" />

  <template v-if="activeTab === 'orders'">
    <section class="content-section purchase-toolbar">
      <div class="purchase-filters">
        <el-select v-model="query.shopId" aria-label="采购门店" @change="changeShop">
          <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
        </el-select>
        <el-select v-model="query.status" aria-label="采购单状态" @change="loadOrders">
          <el-option label="全部状态" value="ALL" />
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待审批" value="SUBMITTED" />
          <el-option label="待收货" value="APPROVED" />
          <el-option label="部分收货" value="PARTIALLY_RECEIVED" />
          <el-option label="已完成" value="RECEIVED" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
        <el-button :loading="loading" @click="loadOrders">
          <el-icon><RefreshRight /></el-icon>
          刷新
        </el-button>
      </div>
      <span>共 {{ orders?.total ?? 0 }} 张采购单</span>
    </section>

    <div :class="['purchase-layout', { 'purchase-layout--panel': panelMode }]">
      <section class="content-section purchase-results">
        <div v-if="loading" class="purchase-loading"><el-skeleton :rows="8" animated /></div>
        <div v-else-if="orders?.records.length" class="purchase-table-wrap">
          <table class="purchase-table">
            <thead>
              <tr>
                <th>采购单</th>
                <th>供应商</th>
                <th>金额</th>
                <th>数量进度</th>
                <th>状态</th>
                <th>申请人</th>
                <th>创建时间</th>
                <th><span class="visually-hidden">操作</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in orders.records" :key="order.id">
                <td>
                  <div class="purchase-identity">
                    <code>{{ order.purchaseOrderNo }}</code>
                    <span>预计 {{ order.expectedDate || '未设置' }}</span>
                  </div>
                </td>
                <td>{{ order.supplierName }}</td>
                <td>{{ formatMoney(order.totalAmount, order.currencyCode) }}</td>
                <td>
                  <div class="purchase-progress">
                    <strong>{{ formatQuantity(order.receivedQuantity) }} / {{ formatQuantity(order.orderedQuantity) }}</strong>
                    <span>{{ order.itemCount }} 个商品</span>
                  </div>
                </td>
                <td>
                  <span :class="`purchase-status purchase-status--${order.status.toLowerCase()}`">
                    {{ statusLabel(order.status) }}
                  </span>
                </td>
                <td>{{ order.createdByName }}</td>
                <td>{{ formatDateTime(order.createdAt) }}</td>
                <td class="purchase-actions">
                  <el-button text type="primary" @click="openDetail(order.id)">查看</el-button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty-state purchase-empty">
          <el-icon><ShoppingCart /></el-icon>
          <h2>当前筛选下没有采购单</h2>
          <p>新建采购单后，提交、审批、收货都会留在同一条业务记录上。</p>
          <el-button v-if="canManage" type="primary" @click="openCreate">新建采购单</el-button>
        </div>
        <footer v-if="orders?.total" class="purchase-pagination">
          <span>第 {{ query.page }} 页</span>
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.pageSize"
            layout="prev, pager, next"
            :total="orders.total"
            @current-change="loadOrders"
          />
        </footer>
      </section>

      <aside v-if="panelMode === 'create'" class="purchase-panel">
        <header class="purchase-panel-heading">
          <div><p class="environment-label">采购申请</p><h2>新建采购单</h2></div>
          <el-button text @click="closePanel">关闭</el-button>
        </header>
        <el-form label-position="top">
          <el-form-item label="供应商名称">
            <el-input v-model="createForm.supplierName" maxlength="160" placeholder="供应商全称" />
          </el-form-item>
          <div class="purchase-form-row">
            <el-form-item label="预计到货日">
              <el-date-picker v-model="createForm.expectedDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-form-item label="币种">
              <el-select v-model="createForm.currencyCode"><el-option label="人民币 CNY" value="CNY" /></el-select>
            </el-form-item>
          </div>
          <el-form-item label="采购备注（可选）">
            <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>
        <div class="purchase-line-heading">
          <strong>商品明细</strong>
          <el-button text type="primary" @click="addDraftLine">添加商品</el-button>
        </div>
        <div class="purchase-line-list">
          <div v-for="(line, index) in createForm.lines" :key="index" class="purchase-line-card">
            <el-select v-model="line.productId" filterable placeholder="选择商品">
              <el-option
                v-for="product in resources?.products"
                :key="product.id"
                :disabled="createForm.lines.some((item, itemIndex) => itemIndex !== index && item.productId === product.id)"
                :label="`${product.name} · ${product.sku}`"
                :value="product.id"
              />
            </el-select>
            <div class="purchase-form-row">
              <el-input-number v-model="line.quantity" :min="0.001" :precision="3" />
              <el-input-number v-model="line.unitCost" :min="0" :precision="4" />
            </div>
            <div class="purchase-line-foot">
              <span>数量 / 单价</span>
              <el-button text type="danger" @click="removeDraftLine(index)">移除</el-button>
            </div>
          </div>
        </div>
        <div class="purchase-total"><span>采购合计</span><strong>{{ formatMoney(draftTotal) }}</strong></div>
        <footer class="purchase-panel-actions">
          <el-button @click="closePanel">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveDraft">保存草稿</el-button>
        </footer>
      </aside>

      <aside v-else-if="panelMode === 'detail'" class="purchase-panel">
        <header class="purchase-panel-heading">
          <div>
            <p class="environment-label">采购单详情</p>
            <h2>{{ selectedOrder?.purchaseOrderNo || '正在加载' }}</h2>
          </div>
          <el-button text @click="closePanel">关闭</el-button>
        </header>
        <el-skeleton v-if="!selectedOrder" :rows="10" animated />
        <template v-else>
          <dl class="purchase-detail-summary">
            <div><dt>供应商</dt><dd>{{ selectedOrder.supplierName }}</dd></div>
            <div><dt>采购金额</dt><dd>{{ formatMoney(selectedOrder.totalAmount, selectedOrder.currencyCode) }}</dd></div>
            <div><dt>状态</dt><dd>{{ statusLabel(selectedOrder.status) }}</dd></div>
            <div><dt>版本</dt><dd>v{{ selectedOrder.version }}</dd></div>
            <div><dt>申请人</dt><dd>{{ selectedOrder.createdByName }}</dd></div>
            <div><dt>审批人</dt><dd>{{ selectedOrder.approvedByName || '—' }}</dd></div>
          </dl>
          <div v-if="selectedOrder.decisionNote" class="purchase-note">
            审批说明：{{ selectedOrder.decisionNote }}
          </div>
          <div class="purchase-line-heading"><strong>商品与到货进度</strong></div>
          <div class="purchase-detail-lines">
            <article v-for="item in selectedOrder.items" :key="item.id">
              <div><strong>{{ item.productName }}</strong><code>{{ item.sku }}</code></div>
              <span>{{ formatQuantity(item.receivedQuantity) }} / {{ formatQuantity(item.orderedQuantity) }} {{ item.unitName }}</span>
              <small>{{ formatMoney(item.unitCost) }} / {{ item.unitName }}</small>
            </article>
          </div>
          <div v-if="selectedOrder.receipts.length" class="purchase-line-heading">
            <strong>历史收货</strong><span>{{ selectedOrder.receipts.length }} 次</span>
          </div>
          <div v-if="selectedOrder.receipts.length" class="purchase-receipt-history">
            <article v-for="receipt in selectedOrder.receipts" :key="receipt.id">
              <code>{{ receipt.receiptNo }}</code>
              <span>{{ formatDateTime(receipt.receivedAt) }} · {{ receipt.createdByName }}</span>
            </article>
          </div>
          <footer class="purchase-panel-actions purchase-panel-actions--wrap">
            <el-button
              v-if="canManage && selectedOrder.status === 'DRAFT'"
              type="primary"
              :loading="actionId === selectedOrder.id"
              @click="submitOrder"
            >提交审批</el-button>
            <el-button
              v-if="canApprove && selectedOrder.status === 'SUBMITTED' && selectedOrder.createdBy !== auth.context?.accountId"
              type="primary"
              :loading="actionId === selectedOrder.id"
              @click="decideOrder('APPROVE')"
            >批准</el-button>
            <el-button
              v-if="canApprove && selectedOrder.status === 'SUBMITTED' && selectedOrder.createdBy !== auth.context?.accountId"
              type="danger"
              plain
              @click="decideOrder('CLOSE')"
            >关闭申请</el-button>
            <el-button
              v-if="canReceive && ['APPROVED', 'PARTIALLY_RECEIVED'].includes(selectedOrder.status)"
              type="primary"
              @click="openReceive"
            >登记到货</el-button>
          </footer>
        </template>
      </aside>

      <aside v-else-if="panelMode === 'receive' && selectedOrder" class="purchase-panel purchase-panel--wide">
        <header class="purchase-panel-heading">
          <div><p class="environment-label">采购收货</p><h2>{{ selectedOrder.purchaseOrderNo }}</h2></div>
          <el-button text @click="panelMode = 'detail'">返回详情</el-button>
        </header>
        <div class="purchase-note">只提交本次实际到货数量；未到齐的明细仍保留在采购单中。</div>
        <el-form label-position="top">
          <el-form-item label="收货时间（留空使用当前时间）">
            <el-date-picker
              v-model="receiptForm.receivedAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="当前时间"
            />
          </el-form-item>
          <el-form-item label="收货备注（可选）">
            <el-input v-model="receiptForm.remark" type="textarea" :rows="2" maxlength="500" />
          </el-form-item>
        </el-form>
        <div class="purchase-line-list">
          <article v-for="line in receiptForm.lines" :key="line.purchaseOrderItemId" class="purchase-receive-card">
            <header>
              <strong>{{ selectedOrder.items.find((item) => item.id === line.purchaseOrderItemId)?.productName }}</strong>
              <span>待收 {{ formatQuantity(selectedOrder.items.find((item) => item.id === line.purchaseOrderItemId)?.outstandingQuantity ?? 0) }}</span>
            </header>
            <el-select v-model="line.locationId" placeholder="库存地点">
              <el-option v-for="location in currentLocations" :key="location.id" :label="location.name" :value="location.id" />
            </el-select>
            <div class="purchase-form-row">
              <el-input-number v-model="line.receivedQuantity" :min="0" :max="Number(selectedOrder.items.find((item) => item.id === line.purchaseOrderItemId)?.outstandingQuantity ?? 0)" :precision="3" />
              <el-input-number v-model="line.unitCost" :min="0" :precision="4" />
            </div>
            <el-input v-model="line.vendorBatchNo" maxlength="100" placeholder="供应商批号（可选）" />
            <div class="purchase-form-row">
              <el-date-picker v-model="line.producedDate" type="date" value-format="YYYY-MM-DD" placeholder="生产日期" />
              <el-date-picker v-model="line.expiryDate" type="date" value-format="YYYY-MM-DD" placeholder="失效日期" />
            </div>
          </article>
        </div>
        <footer class="purchase-panel-actions">
          <el-button @click="panelMode = 'detail'">取消</el-button>
          <el-button type="primary" :loading="saving" @click="postReceipt">确认收货并入库</el-button>
        </footer>
      </aside>
    </div>
  </template>

  <template v-else>
    <section class="content-section purchase-toolbar">
      <div class="purchase-filters">
        <el-select v-model="query.shopId" aria-label="批次门店" @change="changeShop">
          <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
        </el-select>
        <el-select v-model="batchFilter.productId" clearable filterable placeholder="全部商品" @change="loadBatches">
          <el-option v-for="product in resources?.products" :key="product.id" :label="`${product.name} · ${product.sku}`" :value="product.id" />
        </el-select>
        <el-select v-model="batchFilter.locationId" clearable placeholder="全部库存地点" @change="loadBatches">
          <el-option v-for="location in currentLocations" :key="location.id" :label="location.name" :value="location.id" />
        </el-select>
      </div>
      <span>按商品、地点和效期顺序，共 {{ batches.length }} 个批次</span>
    </section>
    <section class="content-section purchase-results">
      <div v-if="loading" class="purchase-loading"><el-skeleton :rows="8" animated /></div>
      <div v-else-if="batches.length" class="purchase-table-wrap">
        <table class="purchase-table purchase-batch-table">
          <thead><tr><th>商品</th><th>系统批次 / 供应商批号</th><th>库存地点</th><th>效期</th><th>收货 / 在手 / 冻结</th><th>单位成本</th><th>来源</th></tr></thead>
          <tbody>
            <tr v-for="batch in batches" :key="batch.id">
              <td><div class="purchase-identity"><strong>{{ batch.productName }}</strong><code>{{ batch.sku }}</code></div></td>
              <td><div class="purchase-identity"><code>{{ batch.batchNo }}</code><span>{{ batch.vendorBatchNo || '无供应商批号' }}</span></div></td>
              <td>{{ batch.locationName }}</td>
              <td>
                <div class="purchase-expiry">
                  <strong>{{ batch.expiryDate || '长期有效' }}</strong>
                  <span :class="{ warning: (daysUntil(batch.expiryDate) ?? 999) <= 30 }">{{ batchExpiryText(batch) }}</span>
                </div>
              </td>
              <td>{{ formatQuantity(batch.quantityReceived) }} / <strong>{{ formatQuantity(batch.quantityOnHand) }}</strong> / {{ formatQuantity(batch.quantityReserved) }} {{ batch.unitName }}</td>
              <td>{{ formatMoney(batch.unitCost) }}</td>
              <td><div class="purchase-identity"><span>{{ batch.sourceType }}</span><code>#{{ batch.sourceId }}</code></div></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state purchase-empty">
        <el-icon><Box /></el-icon>
        <h2>当前没有匹配的库存批次</h2>
        <p>采购收货或期初迁移完成后，批次会按近效期优先排列在这里。</p>
      </div>
    </section>
  </template>
</template>

<style scoped>
.purchase-page-heading { margin-bottom: 18px; }
.purchase-page-heading .el-button { min-height: 42px; }
.purchase-metrics { display: grid; grid-template-columns: 1.4fr repeat(3, 1fr); margin-bottom: 18px; overflow: hidden; background: var(--oc-surface-1); border: 1px solid var(--oc-border); border-radius: var(--oc-radius-md); }
.purchase-metrics > div { display: flex; min-width: 0; flex-direction: column; gap: 3px; padding: 18px 20px; }
.purchase-metrics > div + div { border-left: 1px solid var(--oc-border); }
.purchase-metrics span, .purchase-metrics small { color: var(--oc-text-soft); font-size: 11px; }
.purchase-metrics strong { overflow: hidden; font-size: 21px; line-height: 1.25; text-overflow: ellipsis; white-space: nowrap; }
.purchase-tabs { display: flex; gap: 3px; margin-bottom: 14px; border-bottom: 1px solid var(--oc-border); }
.purchase-tabs button { position: relative; display: flex; align-items: center; gap: 8px; padding: 12px 14px; border: 0; background: transparent; color: var(--oc-text-muted); cursor: pointer; }
.purchase-tabs button::after { position: absolute; right: 14px; bottom: -1px; left: 14px; height: 2px; background: transparent; content: ""; }
.purchase-tabs button:hover, .purchase-tabs button.active { color: var(--oc-text); }
.purchase-tabs button.active::after { background: var(--oc-accent); }
.purchase-error { margin-bottom: 14px; }
.purchase-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 14px 18px; margin-bottom: 16px; overflow: visible; }
.purchase-toolbar > span { color: var(--oc-text-soft); font-size: 12px; }
.purchase-filters { display: flex; min-width: 0; flex-wrap: wrap; gap: 10px; }
.purchase-filters .el-select { width: 190px; }
.purchase-layout { display: grid; grid-template-columns: minmax(0, 1fr); gap: 22px; align-items: start; }
.purchase-layout--panel { grid-template-columns: minmax(0, 1fr) minmax(390px, 460px); }
.purchase-results { min-width: 0; min-height: 430px; }
.purchase-loading { padding: 28px 24px; }
.purchase-table-wrap { overflow-x: auto; }
.purchase-table { width: 100%; min-width: 980px; border-collapse: collapse; text-align: left; }
.purchase-table th, .purchase-table td { padding: 15px 16px; border-bottom: 1px solid var(--oc-border); vertical-align: middle; }
.purchase-table th { background: var(--oc-surface-2); color: var(--oc-text-muted); font-size: 12px; font-weight: 620; white-space: nowrap; }
.purchase-table tbody tr:hover { background: rgba(168, 79, 100, 0.045); }
.purchase-identity, .purchase-progress, .purchase-expiry { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.purchase-identity code { width: fit-content; color: var(--oc-copper); font-family: "SFMono-Regular", Consolas, monospace; font-size: 10px; }
.purchase-identity span, .purchase-progress span, .purchase-expiry span { color: var(--oc-text-soft); font-size: 11px; }
.purchase-expiry span.warning { color: #8a5a16; font-weight: 650; }
.purchase-status { display: inline-flex; min-height: 26px; align-items: center; padding: 0 9px; border: 1px solid var(--oc-border); border-radius: 6px; color: var(--oc-text-muted); font-size: 11px; font-weight: 650; white-space: nowrap; }
.purchase-status--submitted, .purchase-status--approved, .purchase-status--partially_received { border-color: rgba(168, 79, 100, 0.35); color: var(--oc-accent-strong); }
.purchase-status--received { border-color: rgba(53, 112, 75, 0.34); color: var(--oc-success); }
.purchase-actions { text-align: right; }
.purchase-pagination { display: flex; min-height: 66px; align-items: center; justify-content: space-between; padding: 12px 18px; color: var(--oc-text-soft); font-size: 12px; }
.purchase-empty { display: flex; min-height: 426px; flex-direction: column; align-items: center; justify-content: center; }
.purchase-empty > .el-icon { margin-bottom: 16px; color: var(--oc-text-soft); font-size: 34px; }
.purchase-empty .el-button { margin-top: 20px; }
.purchase-panel { position: sticky; top: 94px; max-height: calc(100vh - 116px); padding: 22px; overflow-y: auto; background: var(--oc-surface-1); border: 1px solid var(--oc-border-strong); border-radius: var(--oc-radius-md); }
.purchase-panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 18px; margin-bottom: 20px; border-bottom: 1px solid var(--oc-border); }
.purchase-panel-heading h2 { margin: 5px 0 0; font-size: 21px; letter-spacing: -0.02em; }
.purchase-panel .el-select, .purchase-panel .el-date-editor, .purchase-panel .el-input-number { width: 100%; }
.purchase-form-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.purchase-line-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 0 10px; border-top: 1px solid var(--oc-border); }
.purchase-line-heading > span { color: var(--oc-text-soft); font-size: 11px; }
.purchase-line-list, .purchase-detail-lines, .purchase-receipt-history { display: flex; flex-direction: column; gap: 10px; }
.purchase-line-card, .purchase-receive-card { display: flex; flex-direction: column; gap: 10px; padding: 12px; background: var(--oc-surface-2); border-radius: var(--oc-radius-sm); }
.purchase-line-foot { display: flex; align-items: center; justify-content: space-between; color: var(--oc-text-soft); font-size: 11px; }
.purchase-total { display: flex; align-items: center; justify-content: space-between; padding: 18px 0; }
.purchase-total strong { font-size: 20px; }
.purchase-panel-actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 18px; border-top: 1px solid var(--oc-border); }
.purchase-panel-actions--wrap { flex-wrap: wrap; }
.purchase-detail-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin: 0 0 18px; }
.purchase-detail-summary div { min-width: 0; }
.purchase-detail-summary dt { color: var(--oc-text-soft); font-size: 11px; }
.purchase-detail-summary dd { margin: 3px 0 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.purchase-note { padding: 11px 13px; margin-bottom: 16px; border: 1px solid var(--oc-border); border-radius: var(--oc-radius-sm); background: var(--oc-surface-2); color: var(--oc-text-muted); font-size: 12px; line-height: 1.6; }
.purchase-detail-lines article { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 3px 12px; padding: 11px 0; border-bottom: 1px solid var(--oc-border); }
.purchase-detail-lines article div { display: flex; min-width: 0; flex-direction: column; }
.purchase-detail-lines code, .purchase-receipt-history code { color: var(--oc-copper); font-size: 10px; }
.purchase-detail-lines small { grid-column: 2; color: var(--oc-text-soft); text-align: right; }
.purchase-receipt-history article { display: flex; justify-content: space-between; gap: 12px; padding-bottom: 8px; color: var(--oc-text-soft); font-size: 11px; }
.purchase-receive-card header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.purchase-receive-card header span { color: var(--oc-text-soft); font-size: 11px; }
.purchase-batch-table { min-width: 1060px; }
@media (max-width: 1500px) {
  .purchase-layout--panel { grid-template-columns: minmax(0, 1fr); }
  .purchase-panel { position: static; grid-row: 1; max-height: none; }
  .purchase-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .purchase-metrics > div:nth-child(3) { border-top: 1px solid var(--oc-border); border-left: 0; }
  .purchase-metrics > div:nth-child(4) { border-top: 1px solid var(--oc-border); }
}
@media (max-width: 720px) {
  .purchase-page-heading .el-button { width: 100%; }
  .purchase-metrics { grid-template-columns: 1fr; }
  .purchase-metrics > div + div, .purchase-metrics > div:nth-child(4) { border-top: 1px solid var(--oc-border); border-left: 0; }
  .purchase-toolbar { align-items: flex-start; flex-direction: column; }
  .purchase-filters, .purchase-filters .el-select { width: 100%; }
  .purchase-form-row, .purchase-detail-summary { grid-template-columns: 1fr; }
  .purchase-panel { padding: 18px; }
  .purchase-pagination { align-items: flex-start; flex-direction: column; }
}
</style>
