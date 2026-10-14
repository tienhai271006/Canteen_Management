import { z } from 'zod';

const phoneSchema = z
  .string()
  .trim()
  .regex(/^(0|\+84)\d{9,10}$/, 'Số điện thoại không hợp lệ');

export const updateProfileSchema = z
  .object({
    fullName: z.string().trim().min(2).max(100).optional(),
    studentCode: z.string().trim().min(1).max(20).nullable().optional(),
    className: z.string().trim().min(1).max(50).nullable().optional(),
    phone: phoneSchema.nullable().optional(),
    // Đổi email → phải xác minh lại bằng OTP; email cũ vẫn dùng được cho tới khi xác minh xong
    email: z.string().trim().toLowerCase().email().max(255).optional(),
  })
  .strict()
  .refine((v) => Object.keys(v).length > 0, { message: 'Cần ít nhất một trường để cập nhật' });

export type UpdateProfileInput = z.infer<typeof updateProfileSchema>;
