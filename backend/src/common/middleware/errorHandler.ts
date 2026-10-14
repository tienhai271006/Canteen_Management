import type { ErrorRequestHandler, RequestHandler, Response } from 'express';
import { Prisma } from '@prisma/client';
import { JsonWebTokenError, TokenExpiredError } from 'jsonwebtoken';
import { ZodError } from 'zod';
import { AppError } from '../errors/AppError';
import { env } from '../../config/env';

const send = (res: Response, status: number, code: string, message: string, details?: unknown) => {
  res.status(status).json({
    error: { code, message, ...(details !== undefined && { details }) },
  });
};

export const notFoundHandler: RequestHandler = (req, res) => {
  send(res, 404, 'NOT_FOUND', `Không tìm thấy ${req.method} ${req.originalUrl}`);
};

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export const errorHandler: ErrorRequestHandler = (err, _req, res, _next) => {
  if (err instanceof AppError) return send(res, err.statusCode, err.code, err.message, err.details);

  if (err instanceof ZodError) {
    return send(res, 400, 'VALIDATION_ERROR', 'Dữ liệu gửi lên không hợp lệ', err.flatten());
  }

  if (err instanceof TokenExpiredError) {
    return send(res, 401, 'TOKEN_EXPIRED', 'Phiên đăng nhập đã hết hạn');
  }
  if (err instanceof JsonWebTokenError) {
    return send(res, 401, 'INVALID_TOKEN', 'Token không hợp lệ');
  }

  if (err instanceof Prisma.PrismaClientKnownRequestError) {
    switch (err.code) {
      case 'P2002':
        return send(res, 409, 'DUPLICATE_RECORD', 'Dữ liệu bị trùng với bản ghi đã có', err.meta);
      case 'P2003':
        return send(res, 409, 'REFERENCED_RECORD', 'Bản ghi đang được dữ liệu khác tham chiếu nên không thể thực hiện');
      case 'P2025':
        return send(res, 404, 'NOT_FOUND', 'Không tìm thấy dữ liệu');
    }
  }

  if (err instanceof SyntaxError && 'body' in err) {
    return send(res, 400, 'INVALID_JSON', 'Nội dung JSON không hợp lệ');
  }

  console.error('[error]', err);
  send(
    res,
    500,
    'INTERNAL_ERROR',
    'Có lỗi hệ thống, vui lòng thử lại sau',
    env.NODE_ENV === 'development' ? String(err?.stack ?? err) : undefined,
  );
};
