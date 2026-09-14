<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { CreditCard, Present, Refresh, Tickets } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import {
  createBenefitCardProduct,
  createCouponTemplate,
  getBenefitAdministration,
  getMembers,
  getPaymentChannelStatuses,
  getServiceCatalog,
  issueBenefitCard,
  issueMemberCoupon,
  type BenefitAdministration,
  type MemberSummary,
  type PaymentChannelStatus,
  type ServiceCatalogItem,
} from '../services/api'

const auth = useAuthStore()
const shopId = ref<number>()
const data = ref<BenefitAdministration>({ cardProducts: [], couponTemplates: [] })
const channels = ref<PaymentChannelStatus[]>([])
const members = ref<MemberSummary[]>([])
const services = ref<ServiceCatalogItem[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const activeTab = ref('cards')

const cardForm = reactive({
  packageCode: '', name: '', description: '', cardType: 'COMBO_TIMES', salePrice: 0,
  principalAmount: 0, giftAmount: 0, discountPercent: 90, minimumSpend: 0,
  maximumSavings: undefined as number | undefined, usageLimit: undefined as number | undefined,
  validityDays: 365, serviceId: undefined as number | undefined, quantity: 1,
})
const couponForm = reactive({
  templateCode: '', name: '', couponType: 'THRESHOLD_REDUCTION', thresholdAmount: 0,
  benefitValue: 0, serviceId: undefined as number | undefined, validityDays: 30,
  returnOnFullRefund: true,
})
const issueForm = reactive({
  kind: 'CARD', memberId: undefined as number | undefined,
  productId: undefined as number | undefined, templateId: undefined as number | undefined,
  cardSource: 'OFFLINE_SALE', couponSource: 'ADMIN_DIRECT', sourceReference: '',
})

const activeServices = computed(() => services.value.filter((item) => item.status === 'ACTIVE'))
const configuredCount = computed(() => channels.value.filter((item) => item.configured).length)

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  shopId.value = auth.context?.homeShopId ?? auth.shops[0]?.id
  await reload()
})

async function reload() {
  if (!shopId.value) return
  loading.value = true
  error.value = ''
  try {
    const [benefits, paymentChannels, memberPage, serviceItems] = await Promise.all([
      getBenefitAdministration(shopId.value),
      getPaymentChannelStatuses(shopId.value),
      getMembers({ shopId: shopId.value, status: 'ACTIVE', page: 1, pageSize: 200 }),
      getServiceCatalog(shopId.value),
    ])
    data.value = benefits
    channels.value = paymentChannels
    members.value = memberPage.records
    services.value = serviceItems
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '卡项与优惠中心加载失败'
  } finally {
    loading.value = false
  }
}

async function changeShop() {
  resetIssue()
  await reload()
}

async function saveCard() {
  if (!shopId.value || !cardForm.packageCode.trim() || !cardForm.name.trim()) {
    ElMessage.warning('请填写卡产品编码和名称')
    return
  }
  if (cardForm.cardType === 'COMBO_TIMES' && !cardForm.serviceId) {
    ElMessage.warning('次卡必须选择护理项目')
    return
  }
  saving.value = true
  try {
    await createBenefitCardProduct({
      shop_id: shopId.value,
      package_code: cardForm.packageCode.trim(),
      name: cardForm.name.trim(),
      description: cardForm.description.trim() || undefined,
      card_type: cardForm.cardType,
      sale_price: cardForm.salePrice,
      principal_amount: cardForm.cardType === 'STORED_VALUE' ? cardForm.principalAmount : 0,
      gift_amount: cardForm.cardType === 'STORED_VALUE' ? cardForm.giftAmount : 0,
      discount_percent: cardForm.cardType === 'DISCOUNT' ? cardForm.discountPercent : undefined,
      minimum_spend: cardForm.minimumSpend,
      maximum_savings: cardForm.cardType === 'DISCOUNT' ? cardForm.maximumSavings : undefined,
      usage_limit: cardForm.cardType === 'DISCOUNT' ? cardForm.usageLimit : undefined,
      validity_days: cardForm.validityDays,
      scope_json: '{}',
      items: cardForm.cardType === 'COMBO_TIMES'
        ? [{ service_id: cardForm.serviceId, quantity: cardForm.quantity }]
        : [],
    })
    ElMessage.success('卡产品已创建并上架')
    Object.assign(cardForm, { packageCode: '', name: '', description: '' })
    await reload()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '卡产品创建失败')
  } finally {
    saving.value = false
  }
}

async function saveCoupon() {
  if (!shopId.value || !couponForm.templateCode.trim() || !couponForm.name.trim()) {
    ElMessage.warning('请填写优惠券编码和名称')
    return
  }
  if (couponForm.couponType === 'SERVICE_EXPERIENCE' && !couponForm.serviceId) {
    ElMessage.warning('项目体验券必须选择护理项目')
    return
  }
  saving.value = true
  try {
    await createCouponTemplate({
      shop_id: shopId.value,
      template_code: couponForm.templateCode.trim(),
      name: couponForm.name.trim(),
      coupon_type: couponForm.couponType,
      threshold_amount: couponForm.thresholdAmount,
      benefit_value: couponForm.benefitValue,
      service_id: couponForm.couponType === 'SERVICE_EXPERIENCE' ? couponForm.serviceId : undefined,
      validity_days: couponForm.validityDays,
      return_on_full_refund: couponForm.returnOnFullRefund,
    })
    ElMessage.success('优惠券模板已创建并启用')
    Object.assign(couponForm, { templateCode: '', name: '' })
    await reload()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '优惠券模板创建失败')
  } finally {
    saving.value = false
  }
}

async function issueBenefit() {
  if (!shopId.value || !issueForm.memberId) {
    ElMessage.warning('请选择会员')
    return
  }
  saving.value = true
  try {
    if (issueForm.kind === 'CARD') {
      if (!issueForm.productId) throw new Error('请选择卡产品')
      await issueBenefitCard({
        shop_id: shopId.value, member_id: issueForm.memberId,
        package_product_id: issueForm.productId, source_type: issueForm.cardSource,
        source_reference: issueForm.sourceReference.trim() || undefined,
      })
    } else {
      if (!issueForm.templateId) throw new Error('请选择优惠券模板')
      await issueMemberCoupon({
        shop_id: shopId.value, member_id: issueForm.memberId,
        template_id: issueForm.templateId, source_type: issueForm.couponSource,
        source_reference: issueForm.sourceReference.trim() || undefined,
      })
    }
    ElMessage.success(issueForm.kind === 'CARD' ? '卡项已发放' : '优惠券已发放')
    resetIssue()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '权益发放失败')
  } finally {
    saving.value = false
  }
}

function resetIssue() {
  issueForm.memberId = undefined
  issueForm.productId = undefined
  issueForm.templateId = undefined
  issueForm.sourceReference = ''
}

function money(value?: number) {
  return value == null ? '—' : `¥${Number(value).toFixed(2)}`
}

const cardTypeName: Record<string, string> = { COMBO_TIMES: '护理次卡', STORED_VALUE: '储值卡', DISCOUNT: '折扣卡' }
const couponTypeName: Record<string, string> = { THRESHOLD_REDUCTION: '满减券', CASH: '现金券', DISCOUNT: '折扣券', SERVICE_EXPERIENCE: '项目体验券' }
</script>

<template>
  <section class="benefit-page">
    <header class="page-heading">
      <div>
        <span class="section-kicker">BENEFITS &amp; PAYMENT</span>
        <h1>卡项、优惠与支付</h1>
        <p>统一维护可售卡产品、护理优惠券和发放来源；支付配置仅展示脱敏状态。</p>
      </div>
      <div class="heading-actions">
        <el-select v-model="shopId" aria-label="选择门店" @change="changeShop">
          <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
        </el-select>
        <el-button :icon="Refresh" :loading="loading" @click="reload">刷新</el-button>
      </div>
    </header>

    <div class="summary-grid" aria-label="权益运营概览">
      <article><span>卡产品</span><strong>{{ data.cardProducts.length }}</strong><small>三类卡统一管理</small></article>
      <article><span>优惠券模板</span><strong>{{ data.couponTemplates.length }}</strong><small>四类护理优惠</small></article>
      <article><span>支付通道</span><strong>{{ configuredCount }}/{{ channels.length }}</strong><small>已配置 / 全部</small></article>
    </div>

    <el-alert v-if="error" class="page-alert" type="error" :closable="false" :title="error" show-icon />

    <section class="workspace" v-loading="loading">
      <el-tabs v-model="activeTab">
        <el-tab-pane name="cards">
          <template #label><el-icon><CreditCard /></el-icon><span>卡产品</span></template>
          <div class="split-layout">
            <div class="catalog-list">
              <article v-for="item in data.cardProducts" :key="item.id" class="catalog-card">
                <div><el-tag effect="plain">{{ cardTypeName[item.cardType] }}</el-tag><span>{{ item.packageCode }}</span></div>
                <h3>{{ item.name }}</h3>
                <p>{{ item.description || '暂无产品说明' }}</p>
                <footer><strong>{{ money(item.salePrice) }}</strong><span>有效 {{ item.validityDays }} 天</span></footer>
              </article>
              <el-empty v-if="!data.cardProducts.length" description="还没有卡产品" />
            </div>
            <el-form class="editor" label-position="top" @submit.prevent="saveCard">
              <h2>新增卡产品</h2><p>创建后立即在用户端卡项商城可见。</p>
              <div class="form-grid">
                <el-form-item label="产品类型"><el-select v-model="cardForm.cardType"><el-option v-for="(label, value) in cardTypeName" :key="value" :label="label" :value="value" /></el-select></el-form-item>
                <el-form-item label="有效天数"><el-input-number v-model="cardForm.validityDays" :min="1" /></el-form-item>
                <el-form-item label="产品编码"><el-input v-model="cardForm.packageCode" maxlength="48" /></el-form-item>
                <el-form-item label="产品名称"><el-input v-model="cardForm.name" maxlength="120" /></el-form-item>
                <el-form-item label="销售价"><el-input-number v-model="cardForm.salePrice" :min="0" :precision="2" /></el-form-item>
                <template v-if="cardForm.cardType === 'COMBO_TIMES'">
                  <el-form-item label="护理项目"><el-select v-model="cardForm.serviceId" filterable><el-option v-for="item in activeServices" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
                  <el-form-item label="包含次数"><el-input-number v-model="cardForm.quantity" :min="1" /></el-form-item>
                </template>
                <template v-else-if="cardForm.cardType === 'STORED_VALUE'">
                  <el-form-item label="本金"><el-input-number v-model="cardForm.principalAmount" :min="0" :precision="2" /></el-form-item>
                  <el-form-item label="赠送金"><el-input-number v-model="cardForm.giftAmount" :min="0" :precision="2" /></el-form-item>
                </template>
                <template v-else>
                  <el-form-item label="折扣比例（%）"><el-input-number v-model="cardForm.discountPercent" :min="1" :max="100" :precision="2" /></el-form-item>
                  <el-form-item label="最低消费"><el-input-number v-model="cardForm.minimumSpend" :min="0" :precision="2" /></el-form-item>
                  <el-form-item label="最高优惠"><el-input-number v-model="cardForm.maximumSavings" :min="0" :precision="2" /></el-form-item>
                  <el-form-item label="使用次数上限"><el-input-number v-model="cardForm.usageLimit" :min="1" /></el-form-item>
                </template>
              </div>
              <el-form-item label="产品说明"><el-input v-model="cardForm.description" type="textarea" :rows="3" /></el-form-item>
              <el-button native-type="submit" type="primary" :loading="saving">创建并上架</el-button>
            </el-form>
          </div>
        </el-tab-pane>

        <el-tab-pane name="coupons">
          <template #label><el-icon><Tickets /></el-icon><span>优惠券</span></template>
          <div class="split-layout">
            <div class="catalog-list">
              <article v-for="item in data.couponTemplates" :key="item.id" class="catalog-card">
                <div><el-tag effect="plain">{{ couponTypeName[item.couponType] }}</el-tag><span>{{ item.templateCode }}</span></div>
                <h3>{{ item.name }}</h3>
                <p>门槛 {{ money(item.thresholdAmount) }} · 优惠值 {{ item.benefitValue }}</p>
                <footer><strong>{{ item.status }}</strong><span>有效 {{ item.validityDays }} 天</span></footer>
              </article>
              <el-empty v-if="!data.couponTemplates.length" description="还没有优惠券模板" />
            </div>
            <el-form class="editor" label-position="top" @submit.prevent="saveCoupon">
              <h2>新增优惠券模板</h2><p>一次结算仅能选择一种优惠，默认不使用。</p>
              <div class="form-grid">
                <el-form-item label="券类型"><el-select v-model="couponForm.couponType"><el-option v-for="(label, value) in couponTypeName" :key="value" :label="label" :value="value" /></el-select></el-form-item>
                <el-form-item label="有效天数"><el-input-number v-model="couponForm.validityDays" :min="1" /></el-form-item>
                <el-form-item label="模板编码"><el-input v-model="couponForm.templateCode" maxlength="48" /></el-form-item>
                <el-form-item label="模板名称"><el-input v-model="couponForm.name" maxlength="120" /></el-form-item>
                <el-form-item label="使用门槛"><el-input-number v-model="couponForm.thresholdAmount" :min="0" :precision="2" /></el-form-item>
                <el-form-item label="优惠值"><el-input-number v-model="couponForm.benefitValue" :min="0" :precision="2" /></el-form-item>
                <el-form-item v-if="couponForm.couponType === 'SERVICE_EXPERIENCE'" label="护理项目"><el-select v-model="couponForm.serviceId" filterable><el-option v-for="item in activeServices" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
                <el-form-item label="全额退款退券"><el-switch v-model="couponForm.returnOnFullRefund" /></el-form-item>
              </div>
              <el-button native-type="submit" type="primary" :loading="saving">创建并启用</el-button>
            </el-form>
          </div>
        </el-tab-pane>

        <el-tab-pane name="issue">
          <template #label><el-icon><Present /></el-icon><span>发放权益</span></template>
          <el-form class="issue-form" label-position="top" @submit.prevent="issueBenefit">
            <div><span class="section-kicker">MANUAL ISSUE</span><h2>向会员发放权益</h2><p>人工入口仅允许线下售卡、赠送、补发，以及管理员、活动、售后发券。</p></div>
            <div class="form-grid">
              <el-form-item label="权益类型"><el-radio-group v-model="issueForm.kind"><el-radio-button value="CARD">卡项</el-radio-button><el-radio-button value="COUPON">优惠券</el-radio-button></el-radio-group></el-form-item>
              <el-form-item label="会员"><el-select v-model="issueForm.memberId" filterable><el-option v-for="item in members" :key="item.id" :label="`${item.name} · ${item.phone}`" :value="item.id" /></el-select></el-form-item>
              <template v-if="issueForm.kind === 'CARD'">
                <el-form-item label="卡产品"><el-select v-model="issueForm.productId"><el-option v-for="item in data.cardProducts" :key="item.id" :label="`${item.name} · ${cardTypeName[item.cardType]}`" :value="item.id" /></el-select></el-form-item>
                <el-form-item label="发卡来源"><el-select v-model="issueForm.cardSource"><el-option label="线下售卡" value="OFFLINE_SALE" /><el-option label="赠送" value="GIFT" /><el-option label="补发" value="REISSUE" /></el-select></el-form-item>
              </template>
              <template v-else>
                <el-form-item label="优惠券"><el-select v-model="issueForm.templateId"><el-option v-for="item in data.couponTemplates" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
                <el-form-item label="发券来源"><el-select v-model="issueForm.couponSource"><el-option label="管理员直发" value="ADMIN_DIRECT" /><el-option label="活动领取" value="ACTIVITY_CLAIM" /><el-option label="售后补偿" value="AFTERSALE_COMPENSATION" /></el-select></el-form-item>
              </template>
              <el-form-item class="span-two" label="来源备注"><el-input v-model="issueForm.sourceReference" maxlength="100" placeholder="选填，例如线下单号或补发原因" /></el-form-item>
            </div>
            <el-button native-type="submit" type="primary" :loading="saving">确认发放</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane name="channels">
          <template #label><el-icon><CreditCard /></el-icon><span>支付通道</span></template>
          <div class="channel-notice">本页只显示通道是否已配置，不返回密钥或原始配置。生产环境未配置的真实通道会明确拒绝下单。</div>
          <div class="channel-grid">
            <article v-for="item in channels" :key="item.code">
              <div><strong>{{ item.code }}</strong><el-tag :type="item.configured ? 'success' : 'info'" effect="plain">{{ item.message }}</el-tag></div>
              <span>配置：{{ item.maskedConfig }}</span>
            </article>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </section>
</template>

<style scoped>
.benefit-page { display: grid; gap: 18px; }
.page-heading,.heading-actions,.summary-grid article,.catalog-card footer,.catalog-card > div,.channel-grid article > div { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.page-heading h1 { margin: 4px 0 0; font-size: clamp(28px,3vw,38px); line-height: 1.12; }
.page-heading p,.editor p,.issue-form p { margin: 8px 0 0; color: var(--oc-text-muted); }
.section-kicker { color: var(--oc-copper); font-size: 12px; font-weight: 700; letter-spacing: .16em; }
.heading-actions { min-width: 320px; }
.summary-grid { display: grid; grid-template-columns: repeat(3,1fr); border: 1px solid var(--oc-border); border-radius: var(--oc-radius-md); overflow: hidden; background: var(--oc-surface-1); }
.summary-grid article { min-height: 100px; padding: 18px 22px; align-items: flex-start; flex-direction: column; border-right: 1px solid var(--oc-border); }
.summary-grid article:last-child { border-right: 0; }.summary-grid strong { font-size: 30px; }.summary-grid span,.summary-grid small { color: var(--oc-text-soft); }
.workspace { padding: 0 20px 20px; border: 1px solid var(--oc-border); border-radius: var(--oc-radius-md); background: var(--oc-surface-1); }
.workspace :deep(.el-tabs__header) { margin-bottom: 20px; }.workspace :deep(.el-tabs__item) { gap: 6px; }
.split-layout { display: grid; grid-template-columns: minmax(0,1.2fr) minmax(360px,.8fr); gap: 18px; align-items: start; }
.catalog-list { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; }
.catalog-card,.editor,.issue-form,.channel-grid article { padding: 18px; border: 1px solid var(--oc-border); border-radius: var(--oc-radius-sm); background: var(--oc-surface-2); }
.catalog-card h3,.editor h2,.issue-form h2 { margin: 14px 0 0; }.catalog-card p { min-height: 40px; color: var(--oc-text-muted); line-height: 1.55; }.catalog-card span { color: var(--oc-text-soft); font-size: 12px; }
.editor { position: sticky; top: 18px; }.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 12px; margin-top: 18px; }.form-grid :deep(.el-select),.form-grid :deep(.el-input-number) { width: 100%; }.span-two { grid-column: 1/-1; }
.issue-form { max-width: 840px; }.channel-notice { margin-bottom: 14px; padding: 14px 16px; border: 1px solid var(--oc-border); border-radius: var(--oc-radius-sm); background: var(--oc-surface-2); color: var(--oc-text-muted); line-height: 1.6; }.channel-grid { display: grid; grid-template-columns: repeat(2,1fr); gap: 12px; }.channel-grid article span { display: block; margin-top: 18px; color: var(--oc-text-soft); font-family: ui-monospace,monospace; }
@media (max-width: 980px) { .page-heading { align-items: stretch; flex-direction: column; }.heading-actions { min-width: 0; }.split-layout { grid-template-columns: 1fr; }.editor { position: static; } }
@media (max-width: 640px) { .summary-grid,.catalog-list,.form-grid,.channel-grid { grid-template-columns: 1fr; }.summary-grid article { border-right: 0; border-bottom: 1px solid var(--oc-border); }.summary-grid article:last-child { border-bottom: 0; }.heading-actions { align-items: stretch; flex-direction: column; }.workspace { padding-inline: 14px; }.span-two { grid-column: auto; } }
</style>
