import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Proxy /api sang server Spring Boot nên trình duyệt không bị chặn CORS khi dev.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': 'http://localhost:8080' } },
});
