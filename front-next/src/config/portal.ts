import type { ClientRole } from '../types/domain'

export type PortalMode = 'customer' | 'technician'

export function resolvePortalMode(value?: string): PortalMode {
  return value?.trim().toLowerCase() === 'technician' ? 'technician' : 'customer'
}

export const PORTAL_MODE = resolvePortalMode(import.meta.env.VITE_PORTAL_MODE)
export const IS_TECHNICIAN_PORTAL = PORTAL_MODE === 'technician'
export const EXPECTED_PORTAL_ROLE: ClientRole = IS_TECHNICIAN_PORTAL ? 'BEAUTICIAN' : 'MEMBER'
export const PORTAL_SESSION_KEY = IS_TECHNICIAN_PORTAL
  ? 'face-technician-session-v1'
  : 'face-client-session-v2'

