import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { readFileSync } from 'fs'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia']
    }),
    Components({
      resolvers: [ElementPlusResolver()]
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: Number(process.env.VITE_PORT || 5173),
    https: process.env.VITE_LOCAL_HTTPS === '1' ? {
      key: readFileSync(resolve(__dirname, 'config/local-https/server.key')),
      cert: readFileSync(resolve(__dirname, 'config/local-https/server.crt'))
    } : undefined,
    proxy: {
      '/api': {
        target: process.env.ATTENDANCE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
