import type { Request } from 'express';
import { prisma } from '../../config/prisma';
import { AppError } from '../errors/AppError';
import { asyncHandler } from '../utils/asyncHandler';
import { verifyAccessToken } from '../utils/token';

/** Đọc JWT, đối chiếu DB (trạng thái tài khoản, tokenVersion) rồi gắn req.user. */
export const authenticate = asyncHandler(async (req, _res, next) => {
  const header = req.headers.authorization;
  if (!header?.startsWith('Bearer ')) throw AppError.unauthorized();

  const claims = verifyAccessToken(header.slice(7));
  const user = await prisma.user.findUnique({
    where: { id: claims.sub },
    select: { id: true, role: true, status: true, tokenVersion: true, emailVerifiedAt: true },
  });

  if (!user || user.tokenVersion !== claims.tv) {
    throw AppError.unauthorized('Phiên đăng nhập không còn hiệu lực');
  }
  if (user.status === 'LOCKED') {
    throw AppError.forbidden('ACCOUNT_LOCKED', 'Tài khoản đã bị khóa');
  }

  req.user = { id: user.id, role: user.role, emailVerified: user.emailVerifiedAt !== null };
  next();
});

/** Lấy req.user trong controller/service (đã chắc chắn qua authenticate). */
export function requireUser(req: Request): Express.AuthUser {
  if (!req.user) throw AppError.unauthorized();
  return req.user;
}
