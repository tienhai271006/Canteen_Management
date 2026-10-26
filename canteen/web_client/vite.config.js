import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Proxy /api sang server Spring Boot. Khi dev, trình duyệt và proxy cùng một nguồn nên bỏ header Origin,
// nếu không server trả "Invalid CORS request" mỗi khi mở trang bằng 127.0.0.1, IP mạng LAN hoặc cổng khác 5173.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        configure: (proxy) => proxy.on('proxyReq', (req) => req.removeHeader('origin')),
      },
    },
  },
});
