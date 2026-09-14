export function resolveMediaUrl(value?: string | null) {
  const normalized = value?.trim()
  if (!normalized) return ''
  if (/^(https?:)?\/\//i.test(normalized) || normalized.startsWith('data:') || normalized.startsWith('blob:')) {
    return normalized
  }
  if (normalized.startsWith('/face-next/') || normalized.startsWith('/images/')) return normalized
  if (normalized.startsWith('/upload/')) return `/face-next${normalized}`
  if (normalized.startsWith('upload/')) return `/face-next/${normalized}`
  return normalized.startsWith('/') ? normalized : `/${normalized}`
}
