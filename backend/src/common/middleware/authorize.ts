import type { RequestHandler } from 'express';
import type { Role } from '@prisma/client';
import { AppError } from '../errors/AppError';

/** Chặn theo vai trò toàn hệ thống. Dùng sau authenticate. */
export const authorize =
  (...roles: Role[]): RequestHandler =>
  (req, _res, next) => {
    if (!req.user) return next(AppError.unauthorized());
    if (!roles.includes(req.user.role)) {
      return next(AppError.forbidden('ROLE_NOT_ALLOWED', 'Vai trò của bạn không được phép thực hiện thao tác này'));
    }
    next();
  };
