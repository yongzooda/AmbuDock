// vite.config.ts

import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',        // 🔹 노트북 외부(핸드폰/터널)에서 접속 가능
    port: 5173,
    // 🔹 Cloudflare quick tunnel 도메인 허용
    //    (매번 서브도메인이 바뀌니까 전체 trycloudflare 하위 도메인 허용)
    allowedHosts: ['.trycloudflare.com'],
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // rewrite 없음: 백엔드가 /api/* 를 그대로 받음
      },
    },
  },
});
