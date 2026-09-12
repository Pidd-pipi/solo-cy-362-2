import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";

// 独立于 vite.config.ts（那里的 dev proxy 与测试无关），测试在 Node + happy-dom 中运行
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: "happy-dom",
    include: ["tests/**/*.spec.ts"],
    globals: false,
  },
});
