/// <reference types="vite/client" />
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_MODEL_PREVIEW_BASE_URL?: string
  /** HTTPS URL of the deployed consumer H5 used by the miniapp web-view shell. */
  readonly VITE_CONSUMER_WEB_URL?: string
}
interface ImportMeta { readonly env: ImportMetaEnv }

interface WechatVirtualPaymentOptions {
  mode: 'short_series_coin'
  signData: string
  paySig: string
  signature: string
  success?: (result: { errMsg?: string }) => void
  fail?: (error: { errMsg?: string; errCode?: number; message?: string }) => void
}

interface WechatClipboardOptions {
  data: string
  showToast?: boolean
  success?: (result: { errMsg?: string }) => void
  fail?: (error: { errMsg?: string; errCode?: number; message?: string }) => void
  complete?: (result: { errMsg?: string }) => void
}

declare const wx: {
  requestVirtualPayment(options: WechatVirtualPaymentOptions): void
  setClipboardData?(options: WechatClipboardOptions): void
  canIUse?(schema: string): boolean
}
