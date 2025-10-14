import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173, // 프론트 서버 포트 (기본값 그대로)
    proxy: {
      // 백엔드 API 프록시 설정
      '/api': {
        target: 'http://localhost:8080', // 백엔드 서버 주소
        changeOrigin: true, // Origin 헤더를 백엔드에 맞게 변경
        secure: false,      // https 아닐 때 false
        rewrite: (path) => path.replace(/^\/api/, ''), // 필요 시 /api 제거
      },
    },
  },
})
