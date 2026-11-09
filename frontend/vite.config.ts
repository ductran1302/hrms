import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
  },
  preview: {
    host: true,
    port: 4173,
    strictPort: true,
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'antd-core': ['antd', '@ant-design/icons'],
          'antd-pro': ['@ant-design/pro-components'],
          'utils-vendor': ['dayjs', 'axios', 'i18next', 'react-i18next'],
        },
      },
    },
    chunkSizeWarningLimit: 1600,
  },
});
