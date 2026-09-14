/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_PORTAL_MODE?: 'customer' | 'technician'
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
