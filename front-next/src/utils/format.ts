const fallbackImages = [
  '/images/card-facial.webp',
  '/images/card-hydration.webp',
  '/images/card-body.webp',
  '/images/path-care.webp',
  '/images/path-packages.webp',
  '/images/path-services.webp',
  '/images/hero-facial.webp',
  '/images/hero-hydration.webp',
]

export function fallbackMediaUrl(seed = 0) {
  return fallbackImages[Math.abs(seed) % fallbackImages.length] ?? '/images/card-facial.webp'
}

export function mediaUrl(value: string | undefined | null, seed = 0) {
  const normalized = value?.trim()
  if (!normalized) return fallbackMediaUrl(seed)
  if (/^https?:\/\//i.test(normalized)) return normalized
  if (normalized.startsWith('/face-next/upload/')) return normalized
  if (normalized.startsWith('/face/upload/')) {
    return normalized.replace('/face/upload/', '/face-next/upload/')
  }
  if (normalized.startsWith('/upload/')) return `/face-next${normalized}`
  if (normalized.startsWith('upload/')) return `/face-next/${normalized}`
  if (normalized.startsWith('/images/')) return normalized
  if (/^[^/]+\.(avif|gif|jpe?g|png|webp)$/i.test(normalized)) {
    return `/face-next/upload/${normalized}`
  }
  return fallbackMediaUrl(seed)
}

export function handleMediaError(event: Event, seed = 0) {
  const image = event.currentTarget
  if (!(image instanceof HTMLImageElement)) return
  const fallback = fallbackMediaUrl(seed)
  if (image.getAttribute('src') !== fallback) image.src = fallback
}

export function money(value: number | string | undefined | null) {
  const number = Number(value ?? 0)
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    maximumFractionDigits: 0,
  }).format(number)
}

export function dateTime(value: string | undefined | null) {
  if (!value) return '—'
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

export const statusText: Record<string, string> = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  CHECKED_IN: '已到店',
  IN_SERVICE: '服务中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  NO_SHOW: '未到店',
}

export const statusClass: Record<string, string> = {
  PENDING: 'pending',
  CONFIRMED: 'confirmed',
  CHECKED_IN: 'checked',
  IN_SERVICE: 'serving',
  COMPLETED: 'completed',
  CANCELLED: 'muted',
  NO_SHOW: 'danger',
}
