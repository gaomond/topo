/// <reference types="vitest/config" />
import { fileURLToPath } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    {
      name: "license-text-charset",
      configurePreviewServer(server) {
        // previewの静的配信はtxtの文字コードを指定しないため、日本語環境での誤判定を防ぐ。
        server.middlewares.use((request, response, next) => {
          if (request.url?.split("?")[0] === `${server.config.base}THIRD_PARTY_LICENSES.txt`) {
            response.setHeader("Content-Type", "text/plain; charset=utf-8");
          }
          next();
        });
      },
    },
  ],
  build: { license: { fileName: "THIRD_PARTY_LICENSES.txt" } },
  // src を指す "@" エイリアス。tests/ ツリーから src を参照するときに深い相対パス
  // （../../../src/...）を避け、機能ベース構成でも import を安定させる。
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
    css: true,
  },
});
