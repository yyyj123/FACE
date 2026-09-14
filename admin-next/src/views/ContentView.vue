<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ImageUploadField from '../components/ImageUploadField.vue'
import {
  changeContentStatus,
  createContentEntry,
  getContentEntries,
  updateContentEntry,
  type ContentDraftPayload,
  type ContentEntry,
} from '../services/api'

const entries = ref<ContentEntry[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogOpen = ref(false)
const editing = ref<ContentEntry | null>(null)
const scheduleAt = ref('')
const form = reactive<ContentDraftPayload>({
  content_type: 'BANNER', title: '', summary: '', body: '', image_url: '',
  target_type: 'NONE', target_value: '', sort_order: 0,
})

const typeLabels: Record<string, string> = {
  BANNER: '首页轮播', FEATURED_SERVICE: '精选项目', FEATURED_PACKAGE: '精选套餐',
  ACTIVITY: '活动', POINTS_MALL: '积分商城', ANNOUNCEMENT: '公告',
  SHOP_INTRO: '门店介绍', CONTACT: '联系方式',
}
const statusLabels: Record<string, string> = {
  DRAFT: '草稿', SCHEDULED: '待发布', PUBLISHED: '已发布', OFFLINE: '已下线',
}

async function load() {
  loading.value = true
  try { entries.value = await getContentEntries() }
  finally { loading.value = false }
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    content_type: 'BANNER', title: '', summary: '', body: '', image_url: '',
    target_type: 'NONE', target_value: '', sort_order: 0,
  })
  dialogOpen.value = true
}

function openEdit(entry: ContentEntry) {
  editing.value = entry
  Object.assign(form, {
    content_type: entry.contentType, title: entry.title, summary: entry.summary || '',
    body: entry.body || '', image_url: entry.imageUrl || '', target_type: entry.targetType || 'NONE',
    target_value: entry.targetValue || '', sort_order: entry.sortOrder,
  })
  dialogOpen.value = true
}

async function save() {
  saving.value = true
  try {
    if (editing.value) await updateContentEntry(editing.value, form)
    else await createContentEntry(form)
    ElMessage.success(editing.value ? '内容已更新' : '草稿已创建')
    dialogOpen.value = false
    await load()
  } finally { saving.value = false }
}

async function setStatus(entry: ContentEntry, status: ContentEntry['status']) {
  const scheduled = status === 'SCHEDULED'
    ? scheduleAt.value && new Date(scheduleAt.value).toISOString()
    : undefined
  if (status === 'SCHEDULED' && !scheduled) {
    ElMessage.warning('请先选择定时发布时间')
    return
  }
  await changeContentStatus(entry, status, scheduled || undefined)
  ElMessage.success(`内容已变更为${statusLabels[status]}`)
  await load()
}

onMounted(load)
</script>

<template>
  <section class="sc2-page">
    <header class="sc2-heading">
      <div><span class="environment-label">HOME CONTENT</span><h1>内容运营</h1><p>统一维护用户端首页内容，草稿经发布后才会对访客可见。</p></div>
      <el-button type="primary" @click="openCreate">新建内容</el-button>
    </header>

    <div class="schedule-control">
      <label for="schedule-at">定时发布时间</label>
      <input id="schedule-at" v-model="scheduleAt" type="datetime-local" />
      <small>用于列表中的“定时”操作；时间必须晚于当前时间。</small>
    </div>

    <div v-loading="loading" class="content-grid">
      <article v-for="entry in entries" :key="entry.id" class="content-card">
        <div class="content-card__meta">
          <span>{{ typeLabels[entry.contentType] || entry.contentType }}</span>
          <el-tag size="small" :type="entry.status === 'PUBLISHED' ? 'success' : entry.status === 'OFFLINE' ? 'info' : 'warning'">{{ statusLabels[entry.status] }}</el-tag>
        </div>
        <h2>{{ entry.title }}</h2>
        <p>{{ entry.summary || entry.body || '暂无摘要' }}</p>
        <small>排序 {{ entry.sortOrder }} · 版本 {{ entry.version }}</small>
        <div class="content-card__actions">
          <el-button v-if="['DRAFT','OFFLINE'].includes(entry.status)" size="small" @click="openEdit(entry)">编辑</el-button>
          <el-button v-if="entry.status !== 'PUBLISHED'" size="small" type="primary" @click="setStatus(entry, 'PUBLISHED')">立即发布</el-button>
          <el-button v-if="['DRAFT','OFFLINE'].includes(entry.status)" size="small" @click="setStatus(entry, 'SCHEDULED')">定时</el-button>
          <el-button v-if="['PUBLISHED','SCHEDULED'].includes(entry.status)" size="small" type="danger" plain @click="setStatus(entry, 'OFFLINE')">下线</el-button>
          <el-button v-if="['SCHEDULED','OFFLINE'].includes(entry.status)" size="small" @click="setStatus(entry, 'DRAFT')">转为草稿</el-button>
        </div>
      </article>
      <el-empty v-if="!loading && !entries.length" description="暂无内容，先创建一条草稿" />
    </div>

    <el-dialog v-model="dialogOpen" :title="editing ? '编辑内容' : '新建内容'" width="min(640px, 92vw)">
      <el-form label-position="top" @submit.prevent="save">
        <div class="form-pair">
          <el-form-item label="内容类型"><el-select v-model="form.content_type"><el-option v-for="(label, value) in typeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="form.sort_order" :min="0" /></el-form-item>
        </div>
        <el-form-item label="标题"><el-input v-model="form.title" maxlength="120" show-word-limit /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="form.summary" maxlength="500" /></el-form-item>
        <el-form-item label="正文"><el-input v-model="form.body" type="textarea" :rows="4" maxlength="5000" /></el-form-item>
        <el-form-item label="展示图片"><ImageUploadField v-model="form.image_url" label="展示图片" /></el-form-item>
        <div class="form-pair">
          <el-form-item label="跳转类型"><el-select v-model="form.target_type"><el-option label="无" value="NONE" /><el-option label="护理项目" value="SERVICE" /><el-option label="链接" value="URL" /></el-select></el-form-item>
          <el-form-item label="跳转值"><el-input v-model="form.target_value" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!form.title.trim()" @click="save">保存草稿</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.sc2-page { display: grid; gap: 22px; }
.sc2-heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; }
.sc2-heading h1 { margin: 7px 0 6px; font-size: clamp(28px,4vw,42px); }
.sc2-heading p,.content-card p { color: var(--text-muted); }
.schedule-control { display: flex; align-items: center; gap: 12px; padding: 14px 16px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface); }
.schedule-control input { min-height: 40px; padding: 0 10px; border: 1px solid var(--line); border-radius: 8px; background: transparent; color: var(--text); }
.schedule-control small { color: var(--text-muted); }
.content-grid { display: grid; grid-template-columns: repeat(auto-fill,minmax(290px,1fr)); gap: 16px; min-height: 180px; }
.content-card { display: grid; gap: 12px; padding: 20px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface); }
.content-card h2,.content-card p { margin: 0; }
.content-card__meta,.content-card__actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }
.content-card__actions { justify-content: flex-start; padding-top: 4px; }
.form-pair { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width:640px) { .sc2-heading,.schedule-control { align-items: stretch; flex-direction: column; } .form-pair { grid-template-columns: 1fr; gap: 0; } }
</style>
