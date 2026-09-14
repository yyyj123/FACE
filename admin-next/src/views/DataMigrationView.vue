<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  downloadLegacyImportIssues,
  downloadLegacyImportTemplate,
  executeLegacyImport,
  getLegacyImportBatch,
  getLegacyImportBatches,
  preflightLegacyImport,
  type LegacyImportBatch,
} from '../services/api'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const shopId = ref(0)
const batches = ref<LegacyImportBatch[]>([])
const selected = ref<LegacyImportBatch>()
const file = ref<File>()
const loading = ref(false)
const isSuperAdmin = computed(() => auth.context?.roles.includes('SUPER_ADMIN'))
const canExecute = computed(() => isSuperAdmin.value && selected.value && !['COMPLETED', 'RUNNING'].includes(selected.value.status))

onMounted(async () => {
  if (!auth.context) await auth.refreshContext()
  shopId.value = auth.shops[0]?.id ?? auth.context?.homeShopId ?? 0
  await load()
})

async function load() {
  if (!shopId.value) return
  loading.value = true
  try {
    const result = await getLegacyImportBatches(shopId.value)
    batches.value = result.records
    if (selected.value) selected.value = await getLegacyImportBatch(selected.value.id)
  } finally { loading.value = false }
}

function chooseFile(event: Event) {
  const input = event.target as HTMLInputElement
  file.value = input.files?.[0]
}

async function download(blobPromise: Promise<Blob>, fileName: string) {
  const blob = await blobPromise
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = fileName
  link.click()
  URL.revokeObjectURL(link.href)
}

async function preflight() {
  if (!file.value) return ElMessage.warning('请先选择使用系统模板填写的 .xlsx 文件')
  loading.value = true
  try {
    selected.value = await preflightLegacyImport(shopId.value, file.value)
    ElMessage.success('预检完成；冲突与错误不会覆盖现有资料')
    await load()
  } finally { loading.value = false }
}

async function openBatch(batch: LegacyImportBatch) {
  selected.value = await getLegacyImportBatch(batch.id)
}

async function execute() {
  if (!selected.value) return
  await ElMessageBox.confirm(
    `正式执行批次 ${selected.value.batchNo}？只会导入预检通过的记录，历史卡保持独立。`,
    '确认正式导入',
    { confirmButtonText: '确认导入', cancelButtonText: '返回', type: 'warning' },
  )
  loading.value = true
  try {
    selected.value = await executeLegacyImport(selected.value.id, shopId.value)
    ElMessage.success('正式导入完成；重复执行不会重复建会员或发卡')
    await load()
  } finally { loading.value = false }
}

const statusText: Record<string, string> = {
  PREFLIGHTED: '预检通过', BLOCKED: '有冲突/错误', RUNNING: '导入中', COMPLETED: '已完成', FAILED: '失败',
  READY_NEW: '待新建', READY_MATCHED: '已匹配', CONFLICT: '冲突', ERROR: '错误', IMPORTED: '已导入', SKIPPED: '已存在',
}
</script>

<template>
  <section class="migration-page">
    <header class="migration-heading">
      <div>
        <span class="environment-label">SC7 数据迁移</span>
        <h1>老客户与历史卡导入</h1>
        <p>先下载模板并预检；系统已有不同资料不会被覆盖，正式导入仅超级管理员可执行。</p>
      </div>
      <el-select v-model="shopId" aria-label="选择导入门店" @change="load">
        <el-option v-for="shop in auth.shops" :key="shop.id" :label="shop.name" :value="shop.id" />
      </el-select>
    </header>

    <section class="migration-flow" aria-label="导入步骤">
      <button type="button" @click="download(downloadLegacyImportTemplate(), 'FACE-SC7-历史数据导入模板.xlsx')">
        <strong>1</strong><span><b>下载 Excel 模板</b><small>五张业务工作表，保持表头不变</small></span>
      </button>
      <label>
        <strong>2</strong><span><b>{{ file?.name || '选择 .xlsx 文件' }}</b><small>最大 10MB、10000 行</small></span>
        <input type="file" accept=".xlsx" @change="chooseFile">
      </label>
      <el-button type="primary" :loading="loading" :disabled="!file" @click="preflight">3&nbsp;&nbsp;上传并预检</el-button>
    </section>

    <el-alert
      v-if="!isSuperAdmin"
      title="当前是普通管理员：可以上传和预检，但不能正式导入。"
      type="info"
      show-icon
      :closable="false"
    />

    <div class="migration-grid">
      <section class="migration-panel">
        <div class="panel-title"><div><h2>历史批次</h2><p>{{ batches.length }} 个可追溯批次</p></div><el-button :loading="loading" @click="load">刷新</el-button></div>
        <button
          v-for="batch in batches"
          :key="batch.id"
          :class="['batch-row', { active: selected?.id === batch.id }]"
          type="button"
          @click="openBatch(batch)"
        >
          <span><strong>{{ batch.batchNo }}</strong><small>{{ batch.fileName }}</small></span>
          <span><b>{{ statusText[batch.status] || batch.status }}</b><small>{{ batch.totalRows }} 行</small></span>
        </button>
        <el-empty v-if="!batches.length" description="上传首个文件后，预检批次会显示在这里" />
      </section>

      <section class="migration-panel detail-panel">
        <template v-if="selected">
          <div class="panel-title">
            <div><h2>{{ selected.batchNo }}</h2><p>{{ selected.fileName }}</p></div>
            <span class="batch-state">{{ statusText[selected.status] || selected.status }}</span>
          </div>
          <div class="batch-metrics">
            <div><span>总行数</span><strong>{{ selected.totalRows }}</strong></div>
            <div><span>可执行</span><strong>{{ selected.readyRows }}</strong></div>
            <div><span>冲突</span><strong>{{ selected.conflictRows }}</strong></div>
            <div><span>错误</span><strong>{{ selected.errorRows }}</strong></div>
          </div>
          <div class="detail-actions">
            <el-button v-if="selected.conflictRows || selected.errorRows" @click="download(downloadLegacyImportIssues(selected.id), `导入问题-${selected.batchNo}.csv`)">下载错误报告</el-button>
            <el-button type="primary" :disabled="!canExecute" :loading="loading" @click="execute">正式导入通过记录</el-button>
          </div>
          <div class="issue-list">
            <article v-for="row in selected.rows" :key="row.id">
              <span><strong>{{ row.sheetName }} · 第 {{ row.rowNumber }} 行</strong><small>{{ row.maskedSubject || row.sourceRecordNo || '未标识记录' }}</small></span>
              <span><b>{{ statusText[row.status] || row.status }}</b><small>{{ row.issueMessage || row.matchType || '等待执行' }}</small></span>
            </article>
          </div>
        </template>
        <el-empty v-else description="选择一个批次查看预检和导入结果" />
      </section>
    </div>
  </section>
</template>

<style scoped>
.migration-page { display: grid; gap: 20px; }
.migration-heading,.panel-title,.detail-actions { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.migration-heading h1 { margin: 4px 0 7px; font-size: 28px; }
.migration-heading p,.panel-title p { margin: 0; color: var(--oc-text-muted); }
.migration-heading .el-select { width: 220px; }
.migration-flow { display: grid; grid-template-columns: 1fr 1fr auto; gap: 12px; }
.migration-flow button,.migration-flow label { display: flex; min-height: 76px; align-items: center; gap: 12px; padding: 14px 16px; border: 1px solid var(--oc-border); border-radius: var(--oc-radius-md); background: var(--oc-surface-1); color: inherit; cursor: pointer; text-align: left; }
.migration-flow strong { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 8px; background: var(--oc-surface-2); color: var(--oc-accent); }
.migration-flow span { display: grid; gap: 3px; }
.migration-flow small { color: var(--oc-text-soft); }
.migration-flow input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.migration-grid { display: grid; grid-template-columns: minmax(280px, .75fr) minmax(0, 1.6fr); gap: 18px; }
.migration-panel { min-height: 430px; overflow: hidden; border: 1px solid var(--oc-border); border-radius: var(--oc-radius-md); background: var(--oc-surface-1); }
.panel-title { padding: 18px 20px; border-bottom: 1px solid var(--oc-border); }
.panel-title h2 { margin: 0 0 3px; font-size: 18px; }
.batch-row { display: grid; grid-template-columns: minmax(0,1fr) auto; gap: 14px; width: 100%; padding: 15px 20px; border: 0; border-bottom: 1px solid var(--oc-border); background: transparent; color: inherit; cursor: pointer; text-align: left; }
.batch-row:hover,.batch-row.active { background: var(--oc-surface-2); }
.batch-row > span,.issue-list article > span { display: grid; gap: 3px; min-width: 0; }
.batch-row > span:last-child,.issue-list article > span:last-child { text-align: right; }
.batch-row small,.issue-list small { overflow: hidden; color: var(--oc-text-soft); text-overflow: ellipsis; white-space: nowrap; }
.batch-state { color: var(--oc-accent-strong); font-weight: 700; }
.batch-metrics { display: grid; grid-template-columns: repeat(4,1fr); border-bottom: 1px solid var(--oc-border); }
.batch-metrics div { display: grid; gap: 3px; padding: 16px 20px; }
.batch-metrics div + div { border-left: 1px solid var(--oc-border); }
.batch-metrics span { color: var(--oc-text-soft); font-size: 12px; }
.batch-metrics strong { font-size: 22px; }
.detail-actions { justify-content: flex-end; padding: 14px 20px; border-bottom: 1px solid var(--oc-border); }
.issue-list article { display: grid; grid-template-columns: minmax(0,1fr) minmax(140px,.7fr); gap: 16px; padding: 14px 20px; border-bottom: 1px solid var(--oc-border); }
@media (max-width: 900px) { .migration-page { display: none; } }
</style>
