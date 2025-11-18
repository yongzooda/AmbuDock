// vite.config.ts

import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0', // ⭐ 모든 IP에서 접속 허용 (핸드폰에서 접근 가능해짐)
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // rewrite 없음: 백엔드가 /api/* 를 그대로 받음
      },
    },
  },
});
