import { z } from 'zod';
import { TIME_RE } from '../../common/utils/time';

export const timeSchema = z.string().regex(TIME_RE, 'Giờ phải có dạng HH:mm (vd 07:30)');

const phoneSchema = z
  .string()
  .trim()
  .regex(/^(0|\+84)\d{9,10}$/, 'Số điện thoại không hợp lệ');

const vendorTypeSchema = z.enum(['CANTEEN_STALL', 'FOOD_SHED']);
const vendorRoleSchema = z.enum(['MANAGER', 'STAFF']);
const vendorStatusSchema = z.enum(['PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'SUSPENDED']);

/** UC2.1 Đăng ký điểm bán */
export const createVendorSchema = z
  .object({
    name: z.string().trim().min(2).max(100),
    areaId: z.string().min(1),
    type: vendorTypeSchema,
    description: z.string().trim().max(1000).optional(),
    pickupLocation: z.string().trim().min(2).max(200),
    openTime: timeSchema,
    closeTime: timeSchema,
    contactPhone: phoneSchema.optional(),
  })
  .strict()
  .refine((v) => v.openTime < v.closeTime, {
    message: 'Giờ mở cửa phải trước giờ đóng cửa',
    path: ['closeTime'],
  });

/** UC2.3 Cập nhật điểm bán */
export const updateVendorSchema = z
  .object({
    name: z.string().trim().min(2).max(100).optional(),
    type: vendorTypeSchema.optional(),
    description: z.string().trim().max(1000).nullable().optional(),
    pickupLocation: z.string().trim().min(2).max(200).optional(),
    openTime: timeSchema.optional(),
    closeTime: timeSchema.optional(),
    contactPhone: phoneSchema.nullable().optional(),
  })
  .strict()
  .refine((v) => Object.keys(v).length > 0, { message: 'Cần ít nhất một trường để cập nhật' });

export const reasonSchema = z
  .object({ reason: z.string().trim().min(3, 'Vui lòng nêu lý do').max(500) })
  .strict();

export const adminVendorQuerySchema = z.object({ status: vendorStatusSchema.optional() });

/** UC2.5 Quản lý nhân sự quầy */
export const addStaffSchema = z
  .object({
    email: z.string().trim().toLowerCase().email(),
    role: vendorRoleSchema.default('STAFF'),
  })
  .strict();

export const updateStaffSchema = z.object({ role: vendorRoleSchema }).strict();

export type CreateVendorInput = z.infer<typeof createVendorSchema>;
export type UpdateVendorInput = z.infer<typeof updateVendorSchema>;
export type AddStaffInput = z.infer<typeof addStaffSchema>;
