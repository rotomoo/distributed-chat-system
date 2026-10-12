import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 빌드 산출물을 api-gateway 의 static 으로 떨군다 — 게이트웨이(:8080)가 화면과 API 를 같은 origin 으로 내보내야
// 세션 쿠키(SESSION, userId)가 웹소켓 핸드셰이크까지 그대로 실린다.
// 개발 중에는 :5173 에서 돌리고 /v1 과 /websocket 만 게이트웨이로 넘긴다.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../infra/distributed-chat-system-api-gateway/src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/v1': 'http://localhost:8080',
      '/websocket': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
