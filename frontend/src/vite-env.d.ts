/// <reference types="vite/client" />

// バックエンド API のベース URL（別オリジン前提）。未設定時は同一オリジン相対にフォールバックする。
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
