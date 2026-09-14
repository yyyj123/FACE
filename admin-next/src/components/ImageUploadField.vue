<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Picture, Upload } from '@element-plus/icons-vue'
import { uploadAdminImage } from '../services/api'
import { resolveMediaUrl } from '../utils/media'

const props = withDefaults(defineProps<{
  modelValue?: string
  label?: string
  help?: string
}>(), {
  modelValue: '',
  label: '图片',
  help: '支持 JPG、PNG、WebP，单张不超过 5 MB。',
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const input = ref<HTMLInputElement>()
const uploading = ref(false)
const preview = computed(() => resolveMediaUrl(props.modelValue))

function choose() {
  input.value?.click()
}

async function selected(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    ElMessage.warning('请选择 JPG、PNG 或 WebP 图片')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5 MB')
    return
  }
  uploading.value = true
  try {
    const result = await uploadAdminImage(file)
    emit('update:modelValue', result.url)
    ElMessage.success('图片已上传，保存表单后生效')
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : '图片上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="image-upload-field">
    <div class="image-upload-field__preview">
      <img v-if="preview" :src="preview" :alt="`${label}预览`" />
      <div v-else class="image-upload-field__empty">
        <el-icon><Picture /></el-icon>
        <span>暂未上传{{ label }}</span>
      </div>
    </div>
    <div class="image-upload-field__actions">
      <input ref="input" class="visually-hidden" type="file" accept="image/jpeg,image/png,image/webp" @change="selected" />
      <el-button :icon="Upload" :loading="uploading" @click="choose">
        {{ preview ? `更换${label}` : `上传${label}` }}
      </el-button>
      <el-button v-if="preview" :icon="Delete" :disabled="uploading" @click="emit('update:modelValue', '')">
        移除
      </el-button>
    </div>
    <small>{{ help }}</small>
  </div>
</template>

<style scoped>
.image-upload-field{display:grid;gap:10px;width:100%}.image-upload-field__preview{overflow:hidden;width:100%;aspect-ratio:16/9;border:1px solid var(--line);border-radius:10px;background:var(--surface-muted,#f7f3f1)}.image-upload-field__preview img{display:block;width:100%;height:100%;object-fit:cover}.image-upload-field__empty{display:grid;place-items:center;align-content:center;gap:8px;width:100%;height:100%;color:var(--text-muted)}.image-upload-field__empty .el-icon{font-size:30px}.image-upload-field__actions{display:flex;flex-wrap:wrap;gap:8px}.image-upload-field small{color:var(--text-muted);line-height:1.6}.visually-hidden{position:absolute!important;width:1px!important;height:1px!important;padding:0!important;margin:-1px!important;overflow:hidden!important;clip:rect(0,0,0,0)!important;white-space:nowrap!important;border:0!important}
</style>
