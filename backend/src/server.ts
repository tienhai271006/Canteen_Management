import app from './app';
import { env } from './config/env';
import { prisma } from './config/prisma';

const server = app.listen(env.PORT, () => {
  console.log(`🍜 Canteen API chạy tại http://localhost:${env.PORT}/api/v1  (${env.NODE_ENV})`);
});

// Các tác vụ nền (hết hạn Pending, thử lại email...) sẽ được khởi động ở đây:
// import { startJobs } from './jobs'; startJobs();

async function shutdown(signal: string) {
  console.log(`\n${signal} nhận được, đang tắt server...`);
  server.close(async () => {
    await prisma.$disconnect();
    process.exit(0);
  });
  setTimeout(() => process.exit(1), 10_000).unref();
}

process.on('SIGINT', () => void shutdown('SIGINT'));
process.on('SIGTERM', () => void shutdown('SIGTERM'));
