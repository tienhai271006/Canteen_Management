import { prisma } from '../../config/prisma';
import { AppError } from '../../common/errors/AppError';
import { comparePassword, DUMMY_HASH, hashPassword } from '../../common/utils/hash';
import { signAccessToken } from '../../common/utils/token';
import { normalizeEmail, toPublicUser } from '../users/users.service';
import { sendVerificationCode } from './emailVerification.service';
import type { ChangePasswordInput, LoginInput, RegisterInput } from './auth.schema';

const issueToken = (user: { id: string; tokenVersion: number }) =>
  signAccessToken({ sub: user.id, tv: user.tokenVersion });

/** UC1.1 Đăng ký tài khoản bằng email duy nhất (ưu tiên email trường). */
export async function register(input: RegisterInput) {
  const email = normalizeEmail(input.email);

  const existing = await prisma.user.findUnique({ where: { email }, select: { id: true } });
  if (existing) throw AppError.conflict('EMAIL_TAKEN', 'Email đã được sử dụng');

  const user = await prisma.user.create({
    data: {
      email,
      passwordHash: await hashPassword(input.password),
      fullName: input.fullName,
      studentCode: input.studentCode,
      className: input.className,
      phone: input.phone,
      role: 'CUSTOMER',
    },
  });

  // Gửi OTP nhưng không để lỗi gửi mail làm hỏng việc đăng ký (NFR05)
  let verification: { delivered: boolean; expiresAt?: Date } = { delivered: false };
  try {
    verification = await sendVerificationCode(user.id, email);
  } catch (err) {
    console.error('[auth] không gửi được OTP khi đăng ký:', err);
  }

  return { token: issueToken(user), user: toPublicUser(user), verification };
}

/** UC1.5 Đăng nhập: xác thực, kiểm tra trạng thái và trả về phạm vi quầy được phân công. */
export async function login(input: LoginInput) {
  const email = normalizeEmail(input.email);
  const user = await prisma.user.findUnique({
    where: { email },
    include: {
      vendorStaff: {
        include: { vendor: { select: { id: true, name: true, status: true } } },
      },
    },
  });

  // Luôn so sánh hash (kể cả khi không có user) để tránh lộ email tồn tại qua thời gian phản hồi
  const passwordOk = await comparePassword(input.password, user?.passwordHash ?? DUMMY_HASH);
  if (!user || !passwordOk) {
    throw AppError.unauthorized('Email hoặc mật khẩu không đúng', 'INVALID_CREDENTIALS');
  }
  if (user.status === 'LOCKED') {
    throw AppError.forbidden('ACCOUNT_LOCKED', 'Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên');
  }

  return {
    token: issueToken(user),
    user: toPublicUser(user),
    vendors: user.vendorStaff.map((m) => ({
      vendorId: m.vendor.id,
      name: m.vendor.name,
      status: m.vendor.status,
      role: m.role,
    })),
  };
}

/** UC1.6 Đăng xuất: tăng tokenVersion để mọi token đang lưu hành của tài khoản mất hiệu lực. */
export async function logout(userId: string) {
  await prisma.user.update({ where: { id: userId }, data: { tokenVersion: { increment: 1 } } });
}

/** UC1.7 Đổi mật khẩu: kiểm tra mật khẩu hiện tại, thu hồi các phiên cũ, cấp token mới. */
export async function changePassword(userId: string, input: ChangePasswordInput) {
  const user = await prisma.user.findUnique({ where: { id: userId } });
  if (!user) throw AppError.notFound('USER_NOT_FOUND', 'Không tìm thấy tài khoản');

  if (!(await comparePassword(input.currentPassword, user.passwordHash))) {
    throw AppError.badRequest('WRONG_CURRENT_PASSWORD', 'Mật khẩu hiện tại không đúng');
  }

  const updated = await prisma.user.update({
    where: { id: userId },
    data: { passwordHash: await hashPassword(input.newPassword), tokenVersion: { increment: 1 } },
  });
  return { token: issueToken(updated) };
}
