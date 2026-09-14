<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, Box, Coin, Goods, Refresh, Van } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import ImageUploadField from '../components/ImageUploadField.vue'
import {
  adjustMallInventory, createMallPackage, createMallProduct,
  getMallAdministration, getPointsAdministration,
  grantMemberPoints, sendPointsExpiryReminders, updateMallProductImage, updatePointsRule, updatePointsTask,
} from '../services/api'

const auth = useAuthStore()
const shopId = ref<number>()
const loading = ref(true)
const saving = ref(false)
const imageSavingId = ref<number>()
const activeTab = ref('inventory')
const error = ref('')
const points = ref<Record<string, any>>({ summary: {}, rules: [], tasks: [] })
const mall = ref<Record<string, any>>({ inventory: [], orders: [], products: [] })

const grantForm = reactive({ memberId: undefined as number | undefined, points: 100, reason: '' })
const ruleForm = reactive({ ruleId: undefined as number | undefined, pointsPerYuan: 1, validityMonths: 12, singleCapPoints: undefined as number | undefined })
const taskForm = reactive({ taskId: undefined as number | undefined, cycleDays: 7, dailyRewards: '5,5,10,10,15,15,30', cycleBonusPoints: 50 })
const stockForm = reactive({ skuId: undefined as number | undefined, delta: 1, reason: '' })
const packageForm = reactive({ subOrderIds: '', logisticsCompany: '', trackingNo: '' })
const productForm = reactive({
  categoryCode: 'CARE', categoryName: '精选护理', productCode: '', productType: 'PHYSICAL',
  name: '', coverUrl: '', skuCode: '', cashPrice: 99, pointsPrice: 1000,
  cashEnabled: true, pointsEnabled: true, comboEnabled: false, initialStock: 10,
})

const ruleCodeLabels: Record<string, string> = {
  GLOBAL_EARN: '消费送积分',
  GLOBAL_REDEEM: '积分抵现金',
}
const ruleTypeLabels: Record<string, string> = {
  EARN: '送积分',
  REDEEM: '抵现金',
}
const ruleTargetLabels: Record<string, string> = {
  GLOBAL: '全店通用',
  SERVICE: '指定护理项目',
  PACKAGE: '指定会员卡或套餐',
  ACTIVITY: '指定活动',
  MALL_SKU: '指定商城商品',
}

function ruleTypeLabel(type?: string) {
  return ruleTypeLabels[type ?? ''] ?? '其他积分方式'
}

function ruleTargetLabel(target?: string) {
  return ruleTargetLabels[target ?? ''] ?? '指定范围'
}

function ruleName(rule: Record<string, any>) {
  return ruleCodeLabels[rule.ruleCode] ?? `${ruleTargetLabel(rule.targetType)} · ${ruleTypeLabel(rule.ruleType)}`
}

const lowStock = computed(() => (mall.value.inventory ?? []).filter(
  (item: any) => Number(item.availableQuantity) <= Number(item.warningThreshold),
).length)
const checkinTasks = computed(() => (points.value.tasks ?? []).filter(
  (item: any) => item.taskType === 'CHECKIN',
))

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  shopId.value = auth.context?.homeShopId ?? auth.shops[0]?.id
  await reload()
})

async function reload() {
  if (!shopId.value) return
  loading.value = true; error.value = ''
  try {
    ;[points.value, mall.value] = await Promise.all([
      getPointsAdministration(shopId.value), getMallAdministration(shopId.value),
    ])
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '积分与商城运营数据加载失败'
  } finally { loading.value = false }
}

async function reloadPoints() {
  if (!shopId.value) return
  points.value = await getPointsAdministration(shopId.value)
}

async function reloadMall() {
  if (!shopId.value) return
  mall.value = await getMallAdministration(shopId.value)
}

async function grant() {
  if (!shopId.value || !grantForm.memberId || !grantForm.reason.trim()) {
    ElMessage.warning('请填写会员编号和补发原因'); return
  }
  saving.value = true
  try {
    await grantMemberPoints({ shop_id: shopId.value, member_id: grantForm.memberId,
      points: grantForm.points, source_type: 'ADMIN', reference_type: 'ADMIN_GRANT',
      reason: grantForm.reason })
    ElMessage.success('积分已补发，系统会按当前规则计算到期时间'); grantForm.reason = ''; await reloadPoints()
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '积分发放失败') }
  finally { saving.value = false }
}

async function remind() {
  if (!shopId.value) return
  saving.value = true
  try {
    const result = await sendPointsExpiryReminders(shopId.value)
    ElMessage.success(`检查完成，新增 ${result.created} 条积分到期提醒`)
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '提醒扫描失败') }
  finally { saving.value = false }
}

async function saveRule() {
  if (!shopId.value || !ruleForm.ruleId) { ElMessage.warning('请选择积分规则'); return }
  const rule = points.value.rules.find((item: any) => item.id === ruleForm.ruleId)
  if (!rule) return
  saving.value = true
  try {
    await updatePointsRule(rule.id, {
      shop_id: shopId.value, points_per_yuan: ruleForm.pointsPerYuan,
      points_per_currency: Number(rule.pointsPerCurrency), minimum_points: Number(rule.minimumPoints),
      step_points: Number(rule.stepPoints), max_discount_ratio: Number(rule.maxDiscountRatio),
      max_discount_amount: rule.maxDiscountAmount, single_cap_points: ruleForm.singleCapPoints,
      member_period_cap_points: rule.memberPeriodCapPoints, validity_months: ruleForm.validityMonths,
      status: rule.status, version: rule.version,
    })
    ElMessage.success('积分设置已保存，新获得的积分将按新的有效期计算'); await reloadPoints()
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '积分规则保存失败') }
  finally { saving.value = false }
}

function selectRule(ruleId?: number) {
  const rule = points.value.rules.find((item: any) => item.id === ruleId)
  if (!rule) return
  ruleForm.pointsPerYuan = Number(rule.pointsPerYuan)
  ruleForm.validityMonths = Number(rule.validityMonths)
  ruleForm.singleCapPoints = rule.singleCapPoints == null ? undefined : Number(rule.singleCapPoints)
}

async function saveTask() {
  if (!shopId.value || !taskForm.taskId) { ElMessage.warning('请选择签到任务'); return }
  const task = points.value.tasks.find((item: any) => item.id === taskForm.taskId)
  const rewards = taskForm.dailyRewards.split(',').map((item) => Number(item.trim()))
  if (!task || rewards.length !== taskForm.cycleDays || rewards.some((item) => !Number.isFinite(item) || item < 0)) {
    ElMessage.warning('每天的奖励数必须和连续签到天数一致'); return
  }
  saving.value = true
  try {
    await updatePointsTask(task.id, {
      shop_id: shopId.value, reward_points: rewards[0] ?? 0, cycle_days: taskForm.cycleDays,
      daily_rewards: rewards, cycle_bonus_points: taskForm.cycleBonusPoints,
      repeat_cycle: Boolean(task.repeatCycle), member_period_cap_points: task.memberPeriodCapPoints,
      status: task.status, version: task.version,
    })
    ElMessage.success('签到奖励设置已保存'); await reloadPoints()
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '签到任务保存失败') }
  finally { saving.value = false }
}

function selectTask(taskId?: number) {
  const task = points.value.tasks.find((item: any) => item.id === taskId)
  if (!task) return
  taskForm.cycleDays = Number(task.cycleDays)
  const rewards = typeof task.dailyRewards === 'string' ? JSON.parse(task.dailyRewards) : task.dailyRewards
  taskForm.dailyRewards = (rewards ?? []).join(',')
  taskForm.cycleBonusPoints = Number(task.cycleBonusPoints)
}

async function adjustStock() {
  if (!shopId.value || !stockForm.skuId || !stockForm.reason.trim()) {
    ElMessage.warning('请选择 SKU 并填写调整原因'); return
  }
  saving.value = true
  try {
    await adjustMallInventory({ shop_id: shopId.value, sku_id: stockForm.skuId,
      delta: stockForm.delta, reason: stockForm.reason })
    ElMessage.success('共享库存已调整并记录流水'); stockForm.reason = ''; await reloadMall()
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '库存调整失败') }
  finally { saving.value = false }
}

async function createProduct() {
  if (!shopId.value || !productForm.productCode || !productForm.name || !productForm.skuCode) {
    ElMessage.warning('请填写商品编码、名称和 SKU 编码'); return
  }
  saving.value = true
  try {
    const created = await createMallProduct({
      shop_id: shopId.value, category_code: productForm.categoryCode,
      category_name: productForm.categoryName, product_code: productForm.productCode,
      product_type: productForm.productType, name: productForm.name, cover_url: productForm.coverUrl,
      brand_name: 'FACE 精选', delivery_mode: 'DELIVERY',
      separate_shipping: false,
      after_sale_policy: '商城售后将在 SC6 开放', sku_code: productForm.skuCode,
      spec: { title: '标准规格' }, cash_price: productForm.cashPrice,
      points_price: productForm.pointsPrice, combo_cash_price: 39,
      combo_points_price: 500, cash_enabled: productForm.cashEnabled,
      points_enabled: productForm.pointsEnabled, combo_enabled: productForm.comboEnabled,
      warning_threshold: 3,
    })
    if (productForm.initialStock > 0) await adjustMallInventory({
      shop_id: shopId.value, sku_id: created.skuId,
      delta: productForm.initialStock, reason: '商品上架初始库存',
    })
    ElMessage.success('商品已上架，三种模式共用同一 SKU 库存')
    productForm.productCode = ''; productForm.name = ''; productForm.coverUrl = ''; productForm.skuCode = ''; await reloadMall()
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '商品创建失败') }
  finally { saving.value = false }
}

async function saveProductImage(item: Record<string, any>) {
  if (!shopId.value) return
  imageSavingId.value = Number(item.id)
  try {
    await updateMallProductImage(Number(item.id), shopId.value, String(item.coverUrl || ''))
    ElMessage.success(item.coverUrl ? '商品图片已保存' : '商品图片已移除')
    await reloadMall()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '商品图片保存失败')
  } finally {
    imageSavingId.value = undefined
  }
}

async function ship() {
  if (!shopId.value) return
  const ids = packageForm.subOrderIds.split(',').map((item) => Number(item.trim())).filter(Boolean)
  if (!ids.length) { ElMessage.warning('请输入待发货子订单 ID'); return }
  saving.value = true
  try {
    await createMallPackage({ shop_id: shopId.value, sub_order_ids: ids,
      logistics_company: packageForm.logisticsCompany || undefined,
      tracking_no: packageForm.trackingNo || undefined })
    ElMessage.success('包裹已创建并关联子订单'); packageForm.subOrderIds = ''; await reloadMall()
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '创建包裹失败') }
  finally { saving.value = false }
}
</script>

<template>
  <section class="points-mall-page">
    <header class="page-heading">
      <div><span class="section-kicker">会员积分与商城</span><h1>积分与商城管理</h1><p>在这里统一管理会员积分、商品库存、商城订单和发货记录。</p></div>
      <div class="heading-actions"><el-select v-model="shopId" aria-label="选择门店" @change="reload"><el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" /></el-select><el-button :icon="Refresh" :loading="loading" @click="reload">刷新</el-button></div>
    </header>
    <div class="summary-grid">
      <article><span>会员可用积分</span><strong>{{ Number(points.summary?.availablePoints || 0).toLocaleString() }}</strong><small>暂不可用 {{ Number(points.summary?.frozenPoints || 0).toLocaleString() }}</small></article>
      <article><span>商城商品库存</span><strong>{{ mall.inventory?.length || 0 }}</strong><small>{{ lowStock }} 件库存不足</small></article>
      <article><span>商城订单</span><strong>{{ mall.orders?.length || 0 }}</strong><small>可查看下单和发货进度</small></article>
    </div>
    <el-alert v-if="error" type="error" :closable="false" :title="error" show-icon />
    <section class="ops-workspace" v-loading="loading"><el-tabs v-model="activeTab">
      <el-tab-pane name="inventory"><template #label><el-icon><Box /></el-icon><span>库存与订单</span></template>
        <div class="ops-grid"><div class="data-panel"><h2>共享 SKU 库存</h2><el-table :data="mall.inventory" empty-text="暂无库存"><el-table-column prop="productName" label="商品" min-width="150" /><el-table-column prop="skuCode" label="SKU" /><el-table-column prop="availableQuantity" label="可售" /><el-table-column prop="reservedQuantity" label="冻结" /><el-table-column prop="soldQuantity" label="已售" /></el-table></div>
          <el-form class="editor-panel" label-position="top" @submit.prevent="adjustStock"><h2>库存调整</h2><p>仅支持简单入库与校正，不进入采购、调拨和复杂成本。</p><el-form-item label="SKU"><el-select v-model="stockForm.skuId" filterable><el-option v-for="item in mall.inventory" :key="item.skuId" :label="`${item.productName} · ${item.skuCode}`" :value="item.skuId" /></el-select></el-form-item><el-form-item label="数量变化"><el-input-number v-model="stockForm.delta" /></el-form-item><el-form-item label="原因"><el-input v-model="stockForm.reason" /></el-form-item><el-button native-type="submit" type="primary" :loading="saving">确认调整</el-button></el-form></div>
        <div class="data-panel order-panel"><h2>商城订单</h2><el-table :data="mall.orders" empty-text="暂无订单"><el-table-column prop="orderNo" label="母订单" min-width="190" /><el-table-column prop="memberId" label="会员" /><el-table-column prop="cashAmount" label="现金" /><el-table-column prop="pointsAmount" label="积分" /><el-table-column prop="subOrderCount" label="子单" /><el-table-column prop="packageCount" label="包裹" /><el-table-column prop="status" label="状态" /></el-table></div>
      </el-tab-pane>
      <el-tab-pane name="points"><template #label><el-icon><Coin /></el-icon><span>积分设置</span></template>
        <div class="ops-grid">
          <div class="data-panel">
            <h2>当前积分设置与签到奖励</h2>
            <el-table :data="points.rules" empty-text="暂无积分设置">
              <el-table-column label="积分规则" min-width="140"><template #default="{ row }">{{ ruleName(row) }}</template></el-table-column>
              <el-table-column label="怎么使用"><template #default="{ row }">{{ ruleTypeLabel(row.ruleType) }}</template></el-table-column>
              <el-table-column label="适用范围"><template #default="{ row }">{{ ruleTargetLabel(row.targetType) }}</template></el-table-column>
              <el-table-column label="积分有效期"><template #default="{ row }">{{ row.validityMonths }} 个月</template></el-table-column>
            </el-table>
            <el-table :data="points.tasks" class="task-table" empty-text="暂无签到奖励">
              <el-table-column prop="name" label="签到活动" min-width="140" />
              <el-table-column label="连续签到"><template #default="{ row }">{{ row.cycleDays }} 天</template></el-table-column>
              <el-table-column label="每天奖励"><template #default="{ row }">{{ row.rewardPoints }} 积分</template></el-table-column>
              <el-table-column label="完成额外奖励"><template #default="{ row }">{{ row.cycleBonusPoints }} 积分</template></el-table-column>
            </el-table>
          </div>
          <div class="editor-stack">
            <el-form class="editor-panel" label-position="top" @submit.prevent="saveRule">
              <h2>调整积分设置</h2>
              <p>这里设置全店通用积分。若活动、护理项目、会员卡或商城商品另有设置，则按单独设置执行。积分有效期仅超级管理员可以修改。</p>
              <el-form-item label="选择要调整的规则">
                <el-select v-model="ruleForm.ruleId" placeholder="请选择要调整的规则" @change="selectRule">
                  <el-option v-for="rule in points.rules" :key="rule.id" :label="ruleName(rule)" :value="rule.id" />
                </el-select>
              </el-form-item>
              <div class="form-grid">
                <el-form-item label="每 1 元对应积分"><el-input-number v-model="ruleForm.pointsPerYuan" :min="0.0001" :precision="4" /></el-form-item>
                <el-form-item label="积分有效期（月）"><el-input-number v-model="ruleForm.validityMonths" :min="1" :max="60" /></el-form-item>
                <el-form-item label="每笔最多使用积分"><el-input-number v-model="ruleForm.singleCapPoints" :min="1" /></el-form-item>
              </div>
              <el-button native-type="submit" type="primary" :loading="saving">保存积分设置</el-button>
            </el-form>
            <el-form class="editor-panel" label-position="top" @submit.prevent="saveTask">
              <h2>设置签到奖励</h2>
              <el-form-item label="选择签到活动"><el-select v-model="taskForm.taskId" placeholder="请选择签到活动" @change="selectTask"><el-option v-for="task in checkinTasks" :key="task.id" :label="task.name" :value="task.id" /></el-select></el-form-item>
              <div class="form-grid"><el-form-item label="连续签到天数"><el-input-number v-model="taskForm.cycleDays" :min="1" :max="365" /></el-form-item><el-form-item label="完成一轮额外奖励"><el-input-number v-model="taskForm.cycleBonusPoints" :min="0" /></el-form-item></div>
              <el-form-item label="每天分别送多少积分"><el-input v-model="taskForm.dailyRewards" placeholder="例如：5,5,10,10,15,15,30" /></el-form-item>
              <el-button native-type="submit" type="primary" :loading="saving">保存签到设置</el-button>
            </el-form>
            <el-form class="editor-panel" label-position="top" @submit.prevent="grant">
              <h2>手动补发积分</h2>
              <p>适合活动补发或客户补偿。系统会按当前积分有效期计算到期时间。</p>
              <el-form-item label="会员编号"><el-input-number v-model="grantForm.memberId" :min="1" /></el-form-item>
              <el-form-item label="补发积分数"><el-input-number v-model="grantForm.points" :min="1" /></el-form-item>
              <el-form-item label="补发原因"><el-input v-model="grantForm.reason" placeholder="例如：活动积分补发" /></el-form-item>
              <el-button native-type="submit" type="primary" :loading="saving">确认补发</el-button>
              <el-button :icon="Bell" :loading="saving" @click="remind">检查即将过期的积分</el-button>
            </el-form>
          </div>
        </div>
      </el-tab-pane>
      <el-tab-pane name="products"><template #label><el-icon><Goods /></el-icon><span>商品上架</span></template>
        <div class="ops-grid">
          <div class="data-panel product-list">
            <article v-for="item in mall.products" :key="item.id">
              <span>{{ item.productType }}</span><h3>{{ item.name }}</h3><small>{{ item.productCode }} · {{ item.status }}</small>
              <ImageUploadField v-model="item.coverUrl" label="商品图片" />
              <el-button type="primary" plain :loading="imageSavingId === item.id" @click="saveProductImage(item)">保存商品图片</el-button>
            </article>
            <el-empty v-if="!mall.products?.length" description="暂无商城商品" />
          </div>
          <el-form class="editor-panel" label-position="top" @submit.prevent="createProduct">
            <h2>新增商品与 SKU</h2>
            <el-form-item label="商品图片"><ImageUploadField v-model="productForm.coverUrl" label="商品图片" /></el-form-item>
            <div class="form-grid"><el-form-item label="商品编码"><el-input v-model="productForm.productCode" /></el-form-item><el-form-item label="SKU 编码"><el-input v-model="productForm.skuCode" /></el-form-item><el-form-item label="商品名称"><el-input v-model="productForm.name" /></el-form-item><el-form-item label="现金价"><el-input-number v-model="productForm.cashPrice" :min="0" :precision="2" /></el-form-item><el-form-item label="积分价"><el-input-number v-model="productForm.pointsPrice" :min="0" /></el-form-item><el-form-item label="初始库存"><el-input-number v-model="productForm.initialStock" :min="0" /></el-form-item></div><div class="mode-switches"><el-checkbox v-model="productForm.cashEnabled">现金购买</el-checkbox><el-checkbox v-model="productForm.pointsEnabled">纯积分</el-checkbox><el-checkbox v-model="productForm.comboEnabled">积分 + 现金</el-checkbox></div><el-button native-type="submit" type="primary" :loading="saving">创建并上架</el-button>
          </el-form>
        </div>
      </el-tab-pane>
      <el-tab-pane name="packages"><template #label><el-icon><Van /></el-icon><span>发货管理</span></template><el-form class="package-editor" label-position="top" @submit.prevent="ship"><h2>创建发货单</h2><p>请选择需要一起发货的订单，并填写物流信息。</p><el-form-item label="待发货订单编号（用逗号分开）"><el-input v-model="packageForm.subOrderIds" placeholder="例如：101, 102" /></el-form-item><div class="form-grid"><el-form-item label="物流公司"><el-input v-model="packageForm.logisticsCompany" /></el-form-item><el-form-item label="物流单号"><el-input v-model="packageForm.trackingNo" /></el-form-item></div><el-button native-type="submit" type="primary" :icon="Van" :loading="saving">确认发货</el-button></el-form></el-tab-pane>
    </el-tabs></section>
  </section>
</template>

<style scoped>
.points-mall-page{display:grid;gap:22px}.ops-workspace,.data-panel,.editor-panel,.package-editor{border:1px solid var(--line);background:var(--surface)}.ops-workspace{padding:8px 22px 24px}.ops-grid{display:grid;grid-template-columns:minmax(0,1.55fr) minmax(300px,.65fr);gap:18px}.editor-stack{display:grid;gap:14px}.data-panel,.editor-panel,.package-editor{padding:22px}.data-panel h2,.editor-panel h2,.package-editor h2{margin:0 0 6px}.editor-panel>p,.package-editor>p{margin:0 0 20px;color:var(--text-muted)}.order-panel,.task-table{margin-top:18px}.product-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));align-content:start;gap:12px}.product-list article{display:grid;align-content:start;gap:10px;padding:18px;border:1px solid var(--line)}.product-list article>img{width:100%;aspect-ratio:16/9;object-fit:cover;border-radius:8px}.product-list h3{margin:0}.product-list span,.product-list small{color:var(--text-muted);font-size:12px}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 12px}.mode-switches{display:flex;flex-wrap:wrap;gap:10px;margin-bottom:20px}.package-editor{max-width:760px}.summary-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.summary-grid article{display:flex;flex-direction:column;padding:20px;border:1px solid var(--line);background:var(--surface)}.summary-grid strong{font-size:30px;margin:8px 0}.summary-grid span,.summary-grid small{color:var(--text-muted)}
@media(max-width:900px){.ops-grid,.summary-grid,.product-list,.form-grid{grid-template-columns:1fr}.ops-workspace{padding:6px 12px 18px}.data-panel,.editor-panel,.package-editor{padding:16px}}
</style>
