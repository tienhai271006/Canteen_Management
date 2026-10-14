import { Prisma } from '@prisma/client';

/** Lỗi nghiệp vụ có mã máy đọc được (code) và thông báo tiếng Việt (message). */
export class AppError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly code: string,
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
    Object.setPrototypeOf(this, AppError.prototype);
  }

  static badRequest(code: string, message: string, details?: unknown) {
    return new AppError(400, code, message, details);
  }
  static validation(details: unknown) {
    return new AppError(400, 'VALIDATION_ERROR', 'Dữ liệu gửi lên không hợp lệ', details);
  }
  static unauthorized(message = 'Chưa đăng nhập hoặc phiên đã hết hạn', code = 'UNAUTHORIZED') {
    return new AppError(401, code, message);
  }
  static forbidden(code = 'FORBIDDEN', message = 'Bạn không có quyền thực hiện thao tác này') {
    return new AppError(403, code, message);
  }
  static notFound(code = 'NOT_FOUND', message = 'Không tìm thấy dữ liệu') {
    return new AppError(404, code, message);
  }
  static conflict(code: string, message: string, details?: unknown) {
    return new AppError(409, code, message, details);
  }
  static tooMany(code: string, message: string) {
    return new AppError(429, code, message);
  }
}

/** Kiểm tra lỗi Prisma theo mã (P2002 = trùng unique, P2003 = vi phạm khóa ngoại...). */
export function isPrismaCode(err: unknown, code: string): boolean {
  return err instanceof Prisma.PrismaClientKnownRequestError && err.code === code;
}
