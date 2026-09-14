<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAdminAccount,
  deactivateAdminAccount,
  getAdminAccounts,
  resetAdminPassword,
  type AdminAccountSummary,
} from '../services/api'

const accounts = ref<AdminAccountSummary[]>([])
const loading = ref(false)
const dialogOpen = ref(false)
const saving = ref(false)
const form = reactive({ username: '', display_name: '', password: '', role: 'ADMIN' as 'ADMIN' | 'SUPER_ADMIN' })

async function load() {
  loading.value = true
  try { accounts.value = await getAdminAccounts() }
  finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, { username: '', display_name: '', password: '', role: 'ADMIN' })
  dialogOpen.value = true
}

async function create() {
  saving.value = true
  try {
    await createAdminAccount(form)
    ElMessage.success('管理员账号已创建')
    dialogOpen.value = false
    await load()
  } finally { saving.value = false }
}

async function deactivate(row: unknown) {
  const account = row as AdminAccountSummary
  await ElMessageBox.confirm(`确认停用 ${account.username}？其现有会话将立即失效。`, '停用管理员', { type: 'warning' })
  await deactivateAdminAccount(account)
  ElMessage.success('账号已停用')
  await load()
}

async function resetPassword(row: unknown) {
  const account = row as AdminAccountSummary
  const result = await ElMessageBox.prompt(`为 ${account.username} 设置至少 8 位的新密码`, '重置密码', {
    inputType: 'password',
    inputValidator: (value) => value.length >= 8 || '密码至少 8 位',
  })
  await resetAdminPassword(account, result.value)
  ElMessage.success('密码已重置，原会话已失效')
  await load()
}

onMounted(load)
</script>

<template>
  <section class="settings-page">
    <header class="settings-heading">
      <div><span class="environment-label">IDENTITY & ACCESS</span><h1>系统设置</h1><p>仅超级管理员可创建、停用或重置运营平台账号。</p></div>
      <el-button type="primary" @click="openCreate">新建管理员</el-button>
    </header>
    <div class="account-table-wrap">
      <el-table v-loading="loading" :data="accounts" style="width:100%">
        <el-table-column prop="username" label="账号" min-width="150" />
        <el-table-column prop="displayName" label="显示名称" min-width="140" />
        <el-table-column label="角色" min-width="130"><template #default="scope"><el-tag>{{ scope.row.role === 'SUPER_ADMIN' ? '超级管理员' : '管理员' }}</el-tag></template></el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="lastLoginAt" label="最近登录" min-width="180" />
        <el-table-column label="操作" min-width="210" fixed="right"><template #default="scope"><el-button size="small" :disabled="scope.row.status !== 'ACTIVE'" @click="resetPassword(scope.row)">重置密码</el-button><el-button size="small" type="danger" plain :disabled="scope.row.status !== 'ACTIVE'" @click="deactivate(scope.row)">停用</el-button></template></el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogOpen" title="新建管理员" width="min(520px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="账号"><el-input v-model="form.username" maxlength="80" autocomplete="off" /></el-form-item>
        <el-form-item label="显示名称"><el-input v-model="form.display_name" maxlength="80" /></el-form-item>
        <el-form-item label="初始密码"><el-input v-model="form.password" type="password" minlength="8" maxlength="200" show-password autocomplete="new-password" /></el-form-item>
        <el-form-item label="角色"><el-radio-group v-model="form.role"><el-radio-button value="ADMIN">管理员</el-radio-button><el-radio-button value="SUPER_ADMIN">超级管理员</el-radio-button></el-radio-group><p class="role-note">管理员负责内容与基础资料；超级管理员还可管理账号和系统配置。</p></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogOpen=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!form.username.trim() || !form.display_name.trim() || form.password.length < 8" @click="create">创建</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.settings-page { display:grid; gap:22px; }.settings-heading{display:flex;align-items:end;justify-content:space-between;gap:20px}.settings-heading h1{margin:7px 0 6px;font-size:clamp(28px,4vw,42px)}.settings-heading p,.role-note{color:var(--text-muted)}.account-table-wrap{overflow:hidden;border:1px solid var(--line);border-radius:10px;background:var(--surface)}.role-note{margin:10px 0 0;font-size:12px;line-height:1.6}@media(max-width:640px){.settings-heading{align-items:stretch;flex-direction:column}.account-table-wrap{overflow-x:auto}}
</style>
