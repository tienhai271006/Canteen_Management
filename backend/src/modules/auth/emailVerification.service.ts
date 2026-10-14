import { env } from '../../config/env';
import { OTP_RESEND_COOLDOWN_SECONDS } from '../../config/constants';
import { prisma } from '../../config/prisma';
import { sendMail } from '../../config/mailer';
import { AppError, isPrismaCode } from '../../common/errors/AppError';
import { generateOtp, hashOtp, safeEqual } from '../../common/utils/token';

/**
 * UC1.2 – Xác minh quyền truy cập email bằng OTP (BR08, NFR04):
 * OTP có hạn, dùng một lần, giới hạn số lần thử sai, chỉ lưu bản băm.
 * Nhập tay hoặc OCR email KHÔNG thay thế bước này.
 */
export async function sendVerificationCode(userId: string, email: string) {
  const last = await prisma.emailVerification.findFirst({
    where: { userId },
    orderBy: { createdAt: 'desc' },
    select: { createdAt: true },
  });
  if (last) {
    const waitMs = OTP_RESEND_COOLDOWN_SECONDS * 1000 - (Date.now() - last.createdAt.getTime());
    if (waitMs > 0) {
      throw AppError.tooMany(
        'OTP_RESEND_TOO_SOON',
        `Vui lòng chờ ${Math.ceil(waitMs / 1000)} giây trước khi yêu cầu mã mới`,
      );
    }
  }

  const code = generateOtp();
  const expiresAt = new Date(Date.now() + env.OTP_TTL_MINUTES * 60_000);

  await prisma.$transaction([
    // Vô hiệu hóa các mã cũ chưa dùng
    prisma.emailVerification.updateMany({
      where: { userId, usedAt: null },
      data: { usedAt: new Date() },
    }),
    prisma.emailVerification.create({
      data: { userId, email, codeHash: hashOtp(userId, code), expiresAt },
    }),
  ]);

  const delivered = await sendMail({
    to: email,
    subject: 'Mã xác minh email - Canteen',
    text:
      `Mã xác minh email của bạn là: ${code}\n` +
      `Mã có hiệu lực trong ${env.OTP_TTL_MINUTES} phút và chỉ dùng được một lần.\n` +
      `Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email.`,
  });

  return { delivered, expiresAt };
}

/** Gửi lại mã cho email hiện tại, hoặc email mới đang chờ xác minh nếu có. */
export async function resendVerificationCode(userId: string) {
  const user = await prisma.user.findUnique({ where: { id: userId } });
  if (!user) throw AppError.notFound('USER_NOT_FOUND', 'Không tìm thấy tài khoản');

  const pending = await prisma.emailVerification.findFirst({
    where: { userId, email: { not: user.email } },
    orderBy: { createdAt: 'desc' },
    select: { email: true, usedAt: true, expiresAt: true },
  });
  const target = pending && pending.usedAt === null && pending.expiresAt > new Date() ? pending.email : user.email;

  if (target === user.email && user.emailVerifiedAt) {
    throw AppError.conflict('EMAIL_ALREADY_VERIFIED', 'Email đã được xác minh');
  }
  return sendVerificationCode(userId, target);
}

export async function verifyCode(userId: string, code: string) {
  const record = await prisma.emailVerification.findFirst({
    where: { userId, usedAt: null },
    orderBy: { createdAt: 'desc' },
  });

  if (!record) {
    throw AppError.badRequest('NO_PENDING_VERIFICATION', 'Không có mã xác minh nào đang chờ. Hãy yêu cầu gửi mã mới');
  }
  if (record.expiresAt <= new Date()) {
    throw AppError.badRequest('CODE_EXPIRED', 'Mã xác minh đã hết hạn. Hãy yêu cầu gửi mã mới');
  }
  if (record.attempts >= env.OTP_MAX_ATTEMPTS) {
    throw AppError.tooMany('TOO_MANY_ATTEMPTS', 'Bạn đã nhập sai quá nhiều lần. Hãy yêu cầu gửi mã mới');
  }

  if (!safeEqual(record.codeHash, hashOtp(userId, code))) {
    const updated = await prisma.emailVerification.update({
      where: { id: record.id },
      data: { attempts: { increment: 1 } },
      select: { attempts: true },
    });
    throw AppError.badRequest('INVALID_CODE', 'Mã xác minh không đúng', {
      attemptsLeft: Math.max(0, env.OTP_MAX_ATTEMPTS - updated.attempts),
    });
  }

  try {
    return await prisma.$transaction(async (tx) => {
      // updateMany có điều kiện usedAt=null để chỉ một request thắng (dùng một lần)
      const claimed = await tx.emailVerification.updateMany({
        where: { id: record.id, usedAt: null },
        data: { usedAt: new Date() },
      });
      if (claimed.count === 0) {
        throw AppError.badRequest('CODE_ALREADY_USED', 'Mã xác minh đã được sử dụng');
      }
      // Đổi email (nếu xác minh email mới) và đánh dấu đã xác minh
      return tx.user.update({
        where: { id: userId },
        data: { email: record.email, emailVerifiedAt: new Date() },
      });
    });
  } catch (err) {
    if (isPrismaCode(err, 'P2002')) {
      throw AppError.conflict('EMAIL_TAKEN', 'Email này vừa được tài khoản khác sử dụng');
    }
    throw err;
  }
}
