/// <reference types="vite/client" />
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_MODEL_PREVIEW_BASE_URL?: string
  /** HTTPS URL of the deployed consumer H5 used by the miniapp web-view shell. */
  readonly VITE_CONSUMER_WEB_URL?: string
}
interface ImportMeta { readonly env: ImportMetaEnv }
