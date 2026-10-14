import { z } from 'zod';
import { PRICE_LIMITS } from '../../config/constants';
import { isValidDateString } from '../../common/utils/time';
import { timeSchema } from '../vendors/vendors.schema';

export const sessionTypeSchema = z.enum(['BREAKFAST', 'LUNCH', 'DINNER']);
export const dateSchema = z.string().refine(isValidDateString, 'Ngày phải có dạng YYYY-MM-DD');

const priceSchema = z
  .number()
  .int('Giá phải là số nguyên (VND)')
  .min(PRICE_LIMITS.MIN, `Giá tối thiểu ${PRICE_LIMITS.MIN}đ`)
  .max(PRICE_LIMITS.MAX, `Giá tối đa ${PRICE_LIMITS.MAX}đ`);

const quantitySchema = z.number().int().min(0).max(5000);
const positiveQuantitySchema = z.number().int().min(1).max(5000);
const nonEmpty = (v: object) => Object.keys(v).length > 0;

// ───────── UC3.1–3.3 Món ăn ─────────
export const createDishSchema = z
  .object({
    name: z.string().trim().min(2).max(120),
    description: z.string().trim().max(1000).optional(),
    imageUrl: z.string().trim().url().max(500).optional(),
    category: z.string().trim().min(2).max(50),
    price: priceSchema,
  })
  .strict();

export const updateDishSchema = z
  .object({
    name: z.string().trim().min(2).max(120).optional(),
    description: z.string().trim().max(1000).nullable().optional(),
    imageUrl: z.string().trim().url().max(500).nullable().optional(),
    category: z.string().trim().min(2).max(50).optional(),
  })
  .strict()
  .refine(nonEmpty, { message: 'Cần ít nhất một trường để cập nhật' });

export const updatePriceSchema = z.object({ price: priceSchema }).strict();

export const dishStatusSchema = z
  .object({ status: z.enum(['ACTIVE', 'HIDDEN', 'DISCONTINUED']) })
  .strict();

export const listDishesQuerySchema = z.object({
  status: z.enum(['ACTIVE', 'HIDDEN', 'DISCONTINUED']).optional(),
});

// ───────── UC3.5 Ca & khung nhận ─────────
export const sessionParamSchema = z.object({ session: sessionTypeSchema }).passthrough();

export const upsertSessionSchema = z
  .object({
    startTime: timeSchema,
    endTime: timeSchema,
    orderCutoff: timeSchema,
    prepMinutes: z.number().int().min(0).max(180).default(15),
    isActive: z.boolean().default(true),
    slots: z.array(z.object({ startTime: timeSchema, endTime: timeSchema }).strict()).max(30),
  })
  .strict()
  .superRefine((v, ctx) => {
    if (v.startTime >= v.endTime) {
      ctx.addIssue({ code: 'custom', path: ['endTime'], message: 'Giờ kết thúc ca phải sau giờ bắt đầu' });
    }
    if (v.orderCutoff >= v.endTime) {
      ctx.addIssue({ code: 'custom', path: ['orderCutoff'], message: 'Giờ đóng nhận đơn phải trước giờ kết thúc ca' });
    }
    const seen = new Set<string>();
    v.slots.forEach((s, i) => {
      if (s.startTime >= s.endTime) {
        ctx.addIssue({ code: 'custom', path: ['slots', i, 'endTime'], message: 'Khung nhận có giờ kết thúc phải sau giờ bắt đầu' });
      }
      if (s.startTime < v.startTime || s.endTime > v.endTime) {
        ctx.addIssue({ code: 'custom', path: ['slots', i], message: 'Khung nhận phải nằm trong giờ của ca' });
      }
      if (seen.has(s.startTime)) {
        ctx.addIssue({ code: 'custom', path: ['slots', i, 'startTime'], message: 'Hai khung nhận trùng giờ bắt đầu' });
      }
      seen.add(s.startTime);
    });
  });

// ───────── UC3.4 Thực đơn theo ngày ─────────
export const menuQuerySchema = z.object({
  date: dateSchema.optional(),
  session: sessionTypeSchema.optional(),
});

export const addMenuItemSchema = z
  .object({
    date: dateSchema,
    session: sessionTypeSchema,
    dishId: z.string().min(1),
    onlineQuota: quantitySchema,
    walkInQuota: quantitySchema.default(0),
  })
  .strict();

// ───────── UC3.6–3.7 Hạn mức ─────────
export const setQuotaSchema = z
  .object({
    onlineTotal: quantitySchema.optional(),
    walkInTotal: quantitySchema.optional(),
    reason: z.string().trim().max(300).optional(),
  })
  .strict()
  .refine((v) => v.onlineTotal !== undefined || v.walkInTotal !== undefined, {
    message: 'Cần onlineTotal hoặc walkInTotal',
  });

export const transferQuotaSchema = z
  .object({
    direction: z.enum(['ONLINE_TO_WALKIN', 'WALKIN_TO_ONLINE']),
    quantity: positiveQuantitySchema,
    reason: z.string().trim().min(3, 'Vui lòng nêu lý do điều chuyển').max(300),
  })
  .strict();

export const leftoverSchema = z
  .object({
    outcome: z.enum(['RESOLD', 'WASTED']),
    quantity: positiveQuantitySchema,
    reason: z.string().trim().max(300).optional(),
  })
  .strict();

export type CreateDishInput = z.infer<typeof createDishSchema>;
export type UpdateDishInput = z.infer<typeof updateDishSchema>;
export type UpsertSessionInput = z.infer<typeof upsertSessionSchema>;
export type AddMenuItemInput = z.infer<typeof addMenuItemSchema>;
export type SetQuotaInput = z.infer<typeof setQuotaSchema>;
export type TransferQuotaInput = z.infer<typeof transferQuotaSchema>;
export type LeftoverInput = z.infer<typeof leftoverSchema>;
