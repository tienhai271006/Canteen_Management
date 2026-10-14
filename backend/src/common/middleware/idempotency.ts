import type { Prisma } from '@prisma/client';
import { prisma } from '../../config/prisma';
import { AppError, isPrismaCode } from '../errors/AppError';
import { asyncHandler } from '../utils/asyncHandler';

/**
 * Chống xử lý lặp (BR36, NFR02): client gửi header `Idempotency-Key`.
 *  - Lần đầu: xử lý bình thường, lưu kết quả nếu thành công (2xx).
 *  - Gửi lại cùng key: trả lại đúng kết quả cũ, không chạy lại nghiệp vụ.
 *  - Đang xử lý dở: trả 409 để client chờ.
 * Kết quả lỗi (non-2xx) không được lưu để client có thể thử lại bằng cùng key.
 */
export const idempotency = (opts: { required?: boolean } = {}) =>
  asyncHandler(async (req, res, next) => {
    const key = req.header('Idempotency-Key');
    if (!key) {
      if (opts.required) {
        throw AppError.badRequest('MISSING_IDEMPOTENCY_KEY', 'Thiếu header Idempotency-Key');
      }
      return next();
    }
    if (key.length > 100) {
      throw AppError.badRequest('INVALID_IDEMPOTENCY_KEY', 'Idempotency-Key quá dài (tối đa 100 ký tự)');
    }

    const userId = req.user?.id ?? 'anonymous';
    const scope = `${req.method} ${req.originalUrl.split('?')[0]}`;

    try {
      await prisma.idempotencyKey.create({ data: { userId, key, scope } });
    } catch (err) {
      if (!isPrismaCode(err, 'P2002')) throw err;
      const existing = await prisma.idempotencyKey.findUnique({
        where: { userId_key: { userId, key } },
      });
      if (existing && existing.scope !== scope) {
        throw AppError.conflict('IDEMPOTENCY_KEY_REUSED', 'Idempotency-Key này đã được dùng cho một yêu cầu khác');
      }
      if (existing?.responseStatus != null) {
        return void res.status(existing.responseStatus).json(existing.responseBody);
      }
      throw AppError.conflict('REQUEST_IN_PROGRESS', 'Yêu cầu trước đó vẫn đang được xử lý');
    }

    const originalJson = res.json.bind(res);
    res.json = (body: unknown) => {
      const where = { userId_key: { userId, key } };
      const save =
        res.statusCode >= 200 && res.statusCode < 300
          ? prisma.idempotencyKey.update({
              where,
              data: { responseStatus: res.statusCode, responseBody: body as Prisma.InputJsonValue },
            })
          : prisma.idempotencyKey.delete({ where });
      void save.catch((e) => console.error('[idempotency] không lưu được kết quả:', e));
      return originalJson(body);
    };
    next();
  });
