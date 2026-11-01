import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 静态资源与源码同构打包；nginx 侧见 nginx.conf（/api/ 反代 alarm-server）
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://192.168.31.77:30085',
        changeOrigin: true
      }
    }
  }
})
