<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ImageUploadField from '../components/ImageUploadField.vue'
import { resolveMediaUrl } from '../utils/media'
import { getStaffPublicProfiles, updateStaffPublicProfile, type StaffPublicProfile } from '../services/api'

const profiles = ref<StaffPublicProfile[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogOpen = ref(false)
const form = reactive<StaffPublicProfile>({ id: 0, name: '', jobRole: '', levelName: '', avatarUrl: '', bio: '', specialties: '' })

async function load() { loading.value = true; try { profiles.value = await getStaffPublicProfiles() } finally { loading.value = false } }
function edit(profile: StaffPublicProfile) { Object.assign(form, profile); dialogOpen.value = true }
async function save() { saving.value = true; try { await updateStaffPublicProfile(form); ElMessage.success('公开资料已更新'); dialogOpen.value = false; await load() } finally { saving.value = false } }
onMounted(load)
</script>

<template>
  <section class="profiles-page">
    <header><span class="environment-label">PUBLIC TEAM</span><h1>技师公开资料</h1><p>维护用户端可见的头像、职级、擅长项目与个人介绍；此处不会创建技师登录账号。</p></header>
    <div v-loading="loading" class="profile-grid">
      <article v-for="profile in profiles" :key="profile.id" class="profile-card">
        <img v-if="profile.avatarUrl" :src="resolveMediaUrl(profile.avatarUrl)" :alt="profile.name" loading="lazy" decoding="async" />
        <div v-else class="profile-avatar" aria-hidden="true">{{ profile.name.slice(0,1) }}</div>
        <div><h2>{{ profile.name }}</h2><p>{{ profile.levelName || profile.jobRole }}</p><small>{{ profile.specialties || '暂未配置擅长项目' }}</small></div>
        <p class="profile-bio">{{ profile.bio || '暂未填写个人介绍。' }}</p>
        <el-button @click="edit(profile)">编辑公开资料</el-button>
      </article>
      <el-empty v-if="!loading && !profiles.length" description="暂无在岗技师" />
    </div>

    <el-dialog v-model="dialogOpen" :title="`编辑 ${form.name}`" width="min(560px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="公开岗位"><el-input v-model="form.jobRole" maxlength="30" /></el-form-item>
        <el-form-item label="职级"><el-input v-model="form.levelName" maxlength="50" /></el-form-item>
        <el-form-item label="技师头像"><ImageUploadField v-model="form.avatarUrl" label="头像" help="建议使用清晰的正方形照片；支持 JPG、PNG、WebP，单张不超过 5 MB。" /></el-form-item>
        <el-form-item label="个人介绍"><el-input v-model="form.bio" type="textarea" :rows="5" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item label="擅长项目"><el-input :model-value="form.specialties" disabled /><small>擅长项目来自护理项目技能绑定，请在护理项目页面维护。</small></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogOpen=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!form.jobRole.trim()" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.profiles-page{display:grid;gap:22px}.profiles-page h1{margin:7px 0 6px;font-size:clamp(28px,4vw,42px)}.profiles-page header p,.profile-card p,.profile-card small{color:var(--text-muted)}.profile-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:16px;min-height:180px}.profile-card{display:grid;grid-template-columns:64px 1fr;gap:14px;padding:20px;border:1px solid var(--line);border-radius:10px;background:var(--surface)}.profile-card img,.profile-avatar{width:64px;height:64px;border-radius:50%;object-fit:cover}.profile-avatar{display:grid;place-items:center;background:var(--rose-soft);color:var(--rose);font-size:24px}.profile-card h2,.profile-card p{margin:0}.profile-bio,.profile-card button{grid-column:1/-1}.profile-card button{width:max-content}
</style>
