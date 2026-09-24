import { defineConfig } from 'vite'; import vue from '@vitejs/plugin-vue';

// 后端开发时将请求代理到 http://localhost:8000（可按后端实际端口修改）
export default defineConfig({
  plugins: [vue()],
  server: { proxy: { '/api': 'http://localhost:8000' } },
});
