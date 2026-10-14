import crypto from 'node:crypto';
import { JsonWebTokenError, sign, verify, type SignOptions } from 'jsonwebtoken';
import { env } from '../../config/env';
import { OTP_LENGTH } from '../../config/constants';

export interface JwtClaims {
  sub: string; // userId
  tv: number; // tokenVersion – khác với DB thì token bị thu hồi
}

export function signAccessToken(claims: JwtClaims): string {
  return sign(claims, env.JWT_SECRET, {
    expiresIn: env.JWT_EXPIRES_IN as SignOptions['expiresIn'],
  });
}

export function verifyAccessToken(token: string): JwtClaims {
  const payload = verify(token, env.JWT_SECRET);
  if (typeof payload === 'string' || !payload.sub || typeof payload.tv !== 'number') {
    throw new JsonWebTokenError('invalid claims');
  }
  return { sub: payload.sub, tv: payload.tv };
}

/** OTP số, sinh bằng CSPRNG. */
export const generateOtp = (): string =>
  crypto.randomInt(0, 10 ** OTP_LENGTH).toString().padStart(OTP_LENGTH, '0');

/** Chỉ lưu bản băm của OTP (NFR04). */
export const hashOtp = (userId: string, code: string): string =>
  crypto.createHmac('sha256', env.JWT_SECRET).update(`${userId}:${code}`).digest('hex');

export function safeEqual(a: string, b: string): boolean {
  const ba = Buffer.from(a);
  const bb = Buffer.from(b);
  return ba.length === bb.length && crypto.timingSafeEqual(ba, bb);
}
