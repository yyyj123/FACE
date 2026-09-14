<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelMarketingCampaign,
  createMarketingCampaign,
  decideMarketingCampaign,
  executeMarketingCampaign,
  getMarketingCampaigns,
  submitMarketingCampaign,
  type MarketingCampaignPage,
  type MarketingCampaignStatus,
  type MarketingCampaignSummary,
} from '../services/api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const loading = ref(true)
const saving = ref(false)
const busyId = ref<number>()
const campaigns = ref<MarketingCampaignPage>()
const status = ref<MarketingCampaignStatus | undefined>()
const showCreate = ref(false)
const shopId = ref<number>()
const form = reactive({
  title: '',
  safeSummary: '',
  channel: 'IN_APP',
  actionPath: '/notifications',
  scheduledAt: '',
})

const permissions = computed(() => auth.context?.permissions ?? [])
const canManage = computed(() => permissions.value.includes('marketing:manage'))
const canApprove = computed(() => permissions.value.includes('marketing:approve'))
const canExecute = computed(() => permissions.value.includes('marketing:execute'))
const currentAccountId = computed(() => auth.context?.accountId)
const unavailableChannels = ['SMS', 'EMAIL', 'WECHAT']
const statusOptions: Array<{ label: string; value: MarketingCampaignStatus }> = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待审批', value: 'PENDING_APPROVAL' },
  { label: '已批准', value: 'APPROVED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已取消', value: 'CANCELLED' },
]

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  shopId.value = auth.shops[0]?.id
  await load()
})

async function load(page = 1) {
  if (!shopId.value) return
  loading.value = true
  try {
    campaigns.value = await getMarketingCampaigns(shopId.value, status.value, page)
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '营销活动加载失败')
  } finally {
    loading.value = false
  }
}

async function createCampaign() {
  if (!shopId.value || !form.title.trim() || !form.safeSummary.trim()) {
    ElMessage.warning('请填写活动标题和安全摘要')
    return
  }
  saving.value = true
  try {
    await createMarketingCampaign({
      shop_id: shopId.value,
      title: form.title.trim(),
      safe_summary: form.safeSummary.trim(),
      channel: form.channel,
      action_path: form.actionPath.trim() || undefined,
      scheduled_at: form.scheduledAt || undefined,
    })
    showCreate.value = false
    Object.assign(form, {
      title: '', safeSummary: '', channel: 'IN_APP',
      actionPath: '/notifications', scheduledAt: '',
    })
    ElMessage.success('营销活动草稿已创建')
    await load()
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '营销活动创建失败')
  } finally {
    saving.value = false
  }
}

async function command(campaign: MarketingCampaignSummary, action: string) {
  if (!shopId.value) return
  busyId.value = campaign.id
  try {
    if (action === 'submit') await submitMarketingCampaign(campaign, shopId.value)
    if (action === 'approve') {
      await decideMarketingCampaign(campaign, shopId.value, 'APPROVE')
    }
    if (action === 'reject') {
      const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝营销活动', {
        inputPattern: /\S+/,
        inputErrorMessage: '拒绝原因不能为空',
      })
      await decideMarketingCampaign(campaign, shopId.value, 'REJECT', value)
    }
    if (action === 'execute') {
      await ElMessageBox.confirm(
        '系统会按当前明确同意的会员冻结受众，并仅发送站内通知。确定执行？',
        '执行营销活动',
        { type: 'warning', confirmButtonText: '确认执行' },
      )
      await executeMarketingCampaign(campaign, shopId.value)
    }
    if (action === 'cancel') {
      const { value } = await ElMessageBox.prompt('请输入取消原因', '取消营销活动', {
        inputPattern: /\S+/,
        inputErrorMessage: '取消原因不能为空',
      })
      await cancelMarketingCampaign(campaign, shopId.value, value)
    }
    ElMessage.success('活动状态已更新')
    await load(campaigns.value?.page ?? 1)
  } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : '活动状态更新失败')
  } finally {
    busyId.value = undefined
  }
}

function cannotApprove(campaign: MarketingCampaignSummary) {
  return campaign.createdBy === currentAccountId.value
    || campaign.submittedBy === currentAccountId.value
}

function statusLabel(value: MarketingCampaignStatus) {
  return {
    DRAFT: '草稿', PENDING_APPROVAL: '待审批', APPROVED: '已批准',
    REJECTED: '已拒绝', RUNNING: '执行中', COMPLETED: '已完成', CANCELLED: '已取消',
  }[value]
}

function channelLabel(value: string) {
  return { IN_APP: '站内通知', SMS: '短信', EMAIL: '邮件', WECHAT: '微信' }[value] ?? value
}

function campaignRow(value: unknown) {
  return value as MarketingCampaignSummary
}
</script>

<template>
  <section class="marketing-page">
    <header class="page-heading marketing-heading">
      <div>
        <span class="eyebrow">COMPLIANT OUTREACH</span>
        <h1>营销治理</h1>
        <p>活动须经职责分离审批；受众只包含明确同意的会员，执行后保留不可变投递账本。</p>
      </div>
      <el-button v-if="canManage" type="primary" @click="showCreate = true">新建活动</el-button>
    </header>

    <div class="marketing-trust-strip" role="note">
      <div><strong>1</strong><span>会员本人授权</span></div>
      <div><strong>2</strong><span>异岗审批</span></div>
      <div><strong>3</strong><span>执行时冻结受众</span></div>
      <div><strong>4</strong><span>幂等投递与审计</span></div>
    </div>

    <section class="marketing-toolbar">
      <el-select v-model="shopId" placeholder="选择门店" @change="load(1)">
        <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
      </el-select>
      <el-select v-model="status" clearable placeholder="全部状态" @change="load(1)">
        <el-option
          v-for="option in statusOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <span class="channel-truth">可执行通道：站内通知 · 短信/邮件/微信未配置</span>
    </section>

    <section class="marketing-table-panel" v-loading="loading">
      <el-table :data="campaigns?.records ?? []" empty-text="暂无营销活动">
        <el-table-column label="活动" min-width="280">
          <template #default="scope">
            <div class="campaign-title">
              <strong>{{ scope.row.title }}</strong>
              <span>{{ scope.row.safeSummary }}</span>
              <small>{{ scope.row.campaignNo }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="通道" width="130">
          <template #default="scope">
            <el-tag :type="scope.row.channel === 'IN_APP' ? 'success' : 'info'" effect="plain">
              {{ channelLabel(scope.row.channel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="scope">
            <span class="campaign-status" :data-status="scope.row.status">
              {{ statusLabel(scope.row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="受众 / 送达" width="130">
          <template #default="scope">
            {{ scope.row.audienceCount ?? 0 }} / {{ scope.row.deliveredCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="300" fixed="right">
          <template #default="scope">
            <div class="campaign-actions">
              <el-button
                v-if="canManage && scope.row.status === 'DRAFT'"
                link type="primary" :loading="busyId === scope.row.id"
                @click="command(campaignRow(scope.row), 'submit')"
              >提交审批</el-button>
              <template v-if="canApprove && scope.row.status === 'PENDING_APPROVAL'">
                <el-tooltip
                  :disabled="!cannotApprove(campaignRow(scope.row))"
                  content="活动创建人和提交人不能审批本活动"
                >
                  <span><el-button
                    link type="success" :disabled="cannotApprove(campaignRow(scope.row))"
                    @click="command(campaignRow(scope.row), 'approve')"
                  >批准</el-button></span>
                </el-tooltip>
                <el-button link type="danger" :disabled="cannotApprove(campaignRow(scope.row))" @click="command(campaignRow(scope.row), 'reject')">拒绝</el-button>
              </template>
              <el-button
                v-if="canExecute && scope.row.status === 'APPROVED'"
                link type="primary" @click="command(campaignRow(scope.row), 'execute')"
              >冻结受众并执行</el-button>
              <el-button
                v-if="canManage && ['DRAFT', 'PENDING_APPROVAL', 'APPROVED'].includes(scope.row.status)"
                link type="danger" @click="command(campaignRow(scope.row), 'cancel')"
              >取消</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="(campaigns?.total ?? 0) > 30"
        layout="prev, pager, next" :total="campaigns?.total ?? 0" :page-size="30"
        @current-change="load"
      />
    </section>

    <el-dialog v-model="showCreate" title="新建营销活动" width="min(620px, 92vw)">
      <el-form label-position="top" @submit.prevent="createCampaign">
        <el-form-item label="活动标题" required>
          <el-input v-model="form.title" maxlength="120" show-word-limit />
        </el-form-item>
        <el-form-item label="站内安全摘要" required>
          <el-input v-model="form.safeSummary" type="textarea" :rows="3" maxlength="500" show-word-limit />
          <span class="field-help">不得填写手机号、健康档案、护理详情或支付信息。</span>
        </el-form-item>
        <div class="marketing-form-grid">
          <el-form-item label="触达通道" required>
            <el-select v-model="form.channel">
              <el-option label="站内通知（可执行）" value="IN_APP" />
              <el-option v-for="item in unavailableChannels" :key="item" :label="`${channelLabel(item)}（未配置）`" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="计划时间">
            <el-date-picker v-model="form.scheduledAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
          </el-form-item>
        </div>
        <el-form-item label="站内跳转路径">
          <el-input v-model="form.actionPath" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">返回</el-button>
        <el-button type="primary" :loading="saving" @click="createCampaign">保存草稿</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.marketing-page { display: grid; gap: 18px; }
.marketing-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; }
.marketing-heading h1 { margin: 4px 0 8px; }
.marketing-heading p { max-width: 760px; margin: 0; color: var(--oc-text-muted); }
.marketing-trust-strip { display: grid; grid-template-columns: repeat(4, 1fr); border: 1px solid var(--oc-border); border-radius: 12px; background: var(--oc-surface-1); }
.marketing-trust-strip div { display: flex; align-items: center; gap: 10px; padding: 16px 18px; }
.marketing-trust-strip div + div { border-left: 1px solid var(--oc-border); }
.marketing-trust-strip strong { display: grid; place-items: center; width: 26px; height: 26px; border-radius: 50%; background: var(--oc-accent-soft); color: var(--oc-accent-strong); }
.marketing-trust-strip span { color: var(--oc-text-soft); font-size: 13px; }
.marketing-toolbar { display: flex; align-items: center; gap: 12px; padding: 14px 16px; border: 1px solid var(--oc-border); border-radius: 12px; background: var(--oc-surface-1); }
.marketing-toolbar .el-select { width: 190px; }
.channel-truth { margin-left: auto; color: var(--oc-text-muted); font-size: 13px; }
.marketing-table-panel { overflow: hidden; border: 1px solid var(--oc-border); border-radius: 12px; background: var(--oc-surface-1); }
.marketing-table-panel .el-pagination { justify-content: flex-end; padding: 16px; }
.campaign-title { display: grid; gap: 4px; padding: 6px 0; }
.campaign-title strong { color: var(--oc-text); }
.campaign-title span { max-width: 480px; overflow: hidden; color: var(--oc-text-muted); text-overflow: ellipsis; white-space: nowrap; }
.campaign-title small { color: var(--oc-text-soft); }
.campaign-status { display: inline-flex; padding: 4px 9px; border-radius: 999px; background: var(--oc-surface-2); color: var(--oc-text-soft); font-size: 12px; }
.campaign-status[data-status='PENDING_APPROVAL'], .campaign-status[data-status='APPROVED'] { background: var(--oc-accent-soft); color: var(--oc-accent-strong); }
.campaign-status[data-status='COMPLETED'] { background: var(--oc-success-soft); color: var(--oc-success); }
.campaign-actions { display: flex; align-items: center; gap: 2px; min-height: 32px; }
.marketing-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.marketing-form-grid .el-select, .marketing-form-grid .el-date-editor { width: 100%; }
.field-help { margin-top: 7px; color: var(--oc-text-muted); font-size: 12px; }
@media (max-width: 900px) {
  .marketing-trust-strip { grid-template-columns: 1fr 1fr; }
  .marketing-trust-strip div:nth-child(3) { border-left: 0; border-top: 1px solid var(--oc-border); }
  .marketing-trust-strip div:nth-child(4) { border-top: 1px solid var(--oc-border); }
  .marketing-toolbar { align-items: stretch; flex-direction: column; }
  .marketing-toolbar .el-select { width: 100%; }
  .channel-truth { margin-left: 0; }
}
@media (max-width: 640px) {
  .marketing-heading { align-items: stretch; flex-direction: column; }
  .marketing-trust-strip, .marketing-form-grid { grid-template-columns: 1fr; }
  .marketing-trust-strip div + div { border-left: 0; border-top: 1px solid var(--oc-border); }
}
</style>
