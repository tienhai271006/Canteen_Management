import type { User } from '@prisma/client';
import { env } from '../../config/env';
import { prisma } from '../../config/prisma';
import { AppError } from '../../common/errors/AppError';
import { sendVerificationCode } from '../auth/emailVerification.service';
import type { UpdateProfileInput } from './users.schema';

export const normalizeEmail = (email: string) => email.trim().toLowerCase();

export const isSchoolEmail = (email: string) =>
  email.toLowerCase().endsWith(`@${env.SCHOOL_EMAIL_DOMAIN.toLowerCase()}`);

/** Chỉ trả các trường an toàn ra ngoài (không có passwordHash, tokenVersion). */
export function toPublicUser(user: User) {
  return {
    id: user.id,
    email: user.email,
    fullName: user.fullName,
    studentCode: user.studentCode,
    className: user.className,
    phone: user.phone,
    role: user.role,
    status: user.status,
    emailVerified: user.emailVerifiedAt !== null,
    isSchoolEmail: isSchoolEmail(user.email),
    createdAt: user.createdAt,
  };
}

async function findUserOrThrow(userId: string): Promise<User> {
  const user = await prisma.user.findUnique({ where: { id: userId } });
  if (!user) throw AppError.notFound('USER_NOT_FOUND', 'Không tìm thấy tài khoản');
  return user;
}

/** Email mới đang chờ xác minh (nếu có). */
async function findPendingEmail(user: User): Promise<string | null> {
  const pending = await prisma.emailVerification.findFirst({
    where: { userId: user.id, usedAt: null, expiresAt: { gt: new Date() }, email: { not: user.email } },
    orderBy: { createdAt: 'desc' },
    select: { email: true },
  });
  return pending?.email ?? null;
}

/** UC1.8 (xem) */
export async function getProfile(userId: string) {
  const user = await findUserOrThrow(userId);
  return { ...toPublicUser(user), pendingEmail: await findPendingEmail(user) };
}

/**
 * UC1.8 Quản lý hồ sơ.
 * Đổi email: chưa đổi ngay, chỉ gửi OTP tới email mới; verifyCode mới thực sự cập nhật email.
 * Ưu đãi đã dùng gắn với tài khoản/email trường (BR06) nên đổi email không làm sinh ưu đãi mới –
 * phần này được kiểm tra ở module promotions.
 */
export async function updateProfile(userId: string, input: UpdateProfileInput) {
  const { email, ...fields } = input;
  const user = await findUserOrThrow(userId);

  const updated = Object.keys(fields).length
    ? await prisma.user.update({ where: { id: userId }, data: fields })
    : user;

  let emailChange: { pendingEmail: string; delivered: boolean } | null = null;
  if (email && normalizeEmail(email) !== user.email) {
    const newEmail = normalizeEmail(email);
    const taken = await prisma.user.findUnique({ where: { email: newEmail }, select: { id: true } });
    if (taken) throw AppError.conflict('EMAIL_TAKEN', 'Email đã được sử dụng');
    const result = await sendVerificationCode(userId, newEmail);
    emailChange = { pendingEmail: newEmail, delivered: result.delivered };
  }

  return { user: toPublicUser(updated), emailChange };
}

/**
 * UC1.9 Xem trạng thái tài khoản.
 * Các khối firstOrderBenefit / violations / restriction sẽ được điền thật khi làm
 * module promotions và violations; hiện trả giá trị mặc định để frontend có sẵn cấu trúc.
 */
export async function getAccountStatus(userId: string) {
  const user = await findUserOrThrow(userId);
  return {
    email: {
      address: user.email,
      verified: user.emailVerifiedAt !== null,
      verifiedAt: user.emailVerifiedAt,
      isSchoolEmail: isSchoolEmail(user.email),
      pendingEmail: await findPendingEmail(user),
    },
    accountStatus: user.status,
    firstOrderBenefit: null as null | { status: string; expiresAt: Date },
    violations: { countInWindow: 0, level: 'NONE' as 'NONE' | 'REMINDER' | 'WARNING' | 'REVIEW' },
    restriction: null as null | { startsAt: Date; endsAt: Date },
  };
}
