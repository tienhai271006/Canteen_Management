import type { SessionType } from '@prisma/client';
import { prisma } from '../../config/prisma';
import { AppError } from '../../common/errors/AppError';
import type { UpsertSessionInput } from './menu.schema';

const sessionInclude = {
  slots: { where: { isActive: true }, orderBy: { startTime: 'asc' } },
} as const;

/** UC3.5 Xem cấu hình ca của quầy. */
export async function listSessions(vendorId: string) {
  return prisma.serviceSession.findMany({
    where: { vendorId },
    include: sessionInclude,
    orderBy: { session: 'asc' }, // thứ tự enum: sáng, trưa, chiều
  });
}

/**
 * UC3.5 Thiết lập ca sáng/trưa/chiều: giờ đóng nhận đơn và các khung nhận khả dụng.
 * Khung nhận không xóa cứng (đơn cũ có thể tham chiếu) mà được tắt bằng isActive.
 */
export async function upsertSession(vendorId: string, session: SessionType, input: UpsertSessionInput) {
  const vendor = await prisma.vendor.findUnique({
    where: { id: vendorId },
    select: { openTime: true, closeTime: true },
  });
  if (!vendor) throw AppError.notFound('VENDOR_NOT_FOUND', 'Không tìm thấy quầy');

  if (input.startTime < vendor.openTime || input.endTime > vendor.closeTime) {
    throw AppError.badRequest(
      'OUTSIDE_OPENING_HOURS',
      `Ca phải nằm trong giờ hoạt động của quầy (${vendor.openTime}–${vendor.closeTime})`,
    );
  }

  return prisma.$transaction(async (tx) => {
    const saved = await tx.serviceSession.upsert({
      where: { vendorId_session: { vendorId, session } },
      create: {
        vendorId,
        session,
        startTime: input.startTime,
        endTime: input.endTime,
        orderCutoff: input.orderCutoff,
        prepMinutes: input.prepMinutes,
        isActive: input.isActive,
      },
      update: {
        startTime: input.startTime,
        endTime: input.endTime,
        orderCutoff: input.orderCutoff,
        prepMinutes: input.prepMinutes,
        isActive: input.isActive,
      },
    });

    const existing = await tx.pickupSlot.findMany({ where: { sessionId: saved.id } });
    const wanted = new Map(input.slots.map((s) => [s.startTime, s]));
    const existingByStart = new Map(existing.map((s) => [s.startTime, s]));

    for (const slot of existing) {
      const next = wanted.get(slot.startTime);
      if (next) {
        await tx.pickupSlot.update({
          where: { id: slot.id },
          data: { endTime: next.endTime, isActive: true },
        });
      } else if (slot.isActive) {
        await tx.pickupSlot.update({ where: { id: slot.id }, data: { isActive: false } });
      }
    }
    for (const [startTime, slot] of wanted) {
      if (!existingByStart.has(startTime)) {
        await tx.pickupSlot.create({
          data: { sessionId: saved.id, startTime, endTime: slot.endTime },
        });
      }
    }

    return tx.serviceSession.findUniqueOrThrow({ where: { id: saved.id }, include: sessionInclude });
  });
}
