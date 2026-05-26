import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'https://polling-platform-production-180d.up.railway.app',
        changeOrigin: true,
      },
      '/ws': {
        target: 'https://polling-platform-production-180d.up.railway.app',
        ws: true,
        changeOrigin: true,
      },
    },
  },
})
