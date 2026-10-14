import type { Quota, QuotaAdjustmentType } from '@prisma/client';
import { prisma } from '../../config/prisma';
import { AppError } from '../../common/errors/AppError';
import { todayVN, toDateString } from '../../common/utils/time';
import type { LeftoverInput, SetQuotaInput, TransferQuotaInput } from './menu.schema';

/**
 * UC3.6–3.7 Hạn mức đặt trước.
 *
 * Số còn đặt được = onlineTotal - reserved - sold   (reserved/sold do module orders cập nhật).
 * Mọi thay đổi ở đây dùng UPDATE có điều kiện trong một câu lệnh SQL để an toàn khi
 * đơn hàng đặt đồng thời: không bao giờ hạ hạn mức xuống dưới số đã cam kết (BR12, NFR02).
 * Module orders sẽ dùng cùng nguyên tắc:
 *   UPDATE "Quota" SET "reserved" = "reserved" + :qty
 *   WHERE "id" = :id AND "onlineTotal" - "reserved" - "sold" >= :qty
 */

export const availableOnline = (q: Pick<Quota, 'onlineTotal' | 'reserved' | 'sold'>) =>
  q.onlineTotal - q.reserved - q.sold;

export const withAvailability = (q: Quota) => ({ ...q, availableOnline: availableOnline(q) });

async function getOwnedQuota(vendorId: string, menuItemId: string) {
  const quota = await prisma.quota.findFirst({
    where: { menuItemId, menuItem: { vendorId } },
    include: { menuItem: { select: { date: true } } },
  });
  if (!quota) throw AppError.notFound('QUOTA_NOT_FOUND', 'Không tìm thấy hạn mức của món này trong quầy');
  return quota;
}

async function freshQuota(quotaId: string) {
  return withAvailability(await prisma.quota.findUniqueOrThrow({ where: { id: quotaId } }));
}

function log(
  tx: Pick<typeof prisma, 'quotaAdjustment'>,
  quotaId: string,
  actorId: string,
  type: QuotaAdjustmentType,
  quantity: number,
  reason?: string,
) {
  return tx.quotaAdjustment.create({ data: { quotaId, actorId, type, quantity, reason } });
}

/** UC3.6 Đặt lại tổng số suất; không cho hạ dưới số đã cam kết. */
export async function setQuota(vendorId: string, menuItemId: string, actorId: string, input: SetQuotaInput) {
  const quota = await getOwnedQuota(vendorId, menuItemId);

  await prisma.$transaction(async (tx) => {
    if (input.onlineTotal !== undefined) {
      const v = input.onlineTotal;
      const updated = await tx.$executeRaw`
        UPDATE "Quota" SET "onlineTotal" = ${v}::int
        WHERE "id" = ${quota.id} AND ("reserved" + "sold") <= ${v}::int`;
      if (updated === 0) {
        const now = await tx.quota.findUniqueOrThrow({ where: { id: quota.id } });
        throw AppError.conflict(
          'QUOTA_BELOW_COMMITTED',
          `Không thể đặt dưới số suất đã cam kết (${now.reserved + now.sold})`,
          { committed: now.reserved + now.sold, reserved: now.reserved, sold: now.sold },
        );
      }
      await log(tx, quota.id, actorId, 'SET_ONLINE', v - quota.onlineTotal, input.reason);
    }

    if (input.walkInTotal !== undefined) {
      await tx.quota.update({ where: { id: quota.id }, data: { walkInTotal: input.walkInTotal } });
      await log(tx, quota.id, actorId, 'SET_WALKIN', input.walkInTotal - quota.walkInTotal, input.reason);
    }
  });

  return freshQuota(quota.id);
}

/** BR33 Điều chuyển giữa phần online và phần tại quầy, bảo đảm các suất đã cam kết và có ghi nhận. */
export async function transferQuota(
  vendorId: string,
  menuItemId: string,
  actorId: string,
  input: TransferQuotaInput,
) {
  const quota = await getOwnedQuota(vendorId, menuItemId);
  const qty = input.quantity;
  const toWalkIn = input.direction === 'ONLINE_TO_WALKIN';

  await prisma.$transaction(async (tx) => {
    const updated = toWalkIn
      ? await tx.$executeRaw`
          UPDATE "Quota"
          SET "onlineTotal" = "onlineTotal" - ${qty}::int, "walkInTotal" = "walkInTotal" + ${qty}::int
          WHERE "id" = ${quota.id} AND ("onlineTotal" - ${qty}::int) >= ("reserved" + "sold")`
      : await tx.$executeRaw`
          UPDATE "Quota"
          SET "onlineTotal" = "onlineTotal" + ${qty}::int, "walkInTotal" = "walkInTotal" - ${qty}::int
          WHERE "id" = ${quota.id} AND "walkInTotal" >= ${qty}::int`;

    if (updated === 0) {
      throw AppError.conflict(
        'QUOTA_TRANSFER_NOT_ALLOWED',
        toWalkIn
          ? 'Phần online còn lại không đủ để chuyển (các suất đã cam kết được giữ nguyên)'
          : 'Phần tại quầy không đủ suất để chuyển sang online',
      );
    }
    await log(tx, quota.id, actorId, toWalkIn ? 'TRANSFER_TO_WALKIN' : 'TRANSFER_TO_ONLINE', qty, input.reason);
  });

  return freshQuota(quota.id);
}

/**
 * UC3.7 Món đã làm nhưng không được nhận: quầy kiểm tra thực tế rồi GHI bán lại hoặc hao hụt.
 * Tuyệt đối không tự cộng lại vào số suất đặt được (BR15).
 * Sau này notCollected.service của module orders sẽ gợi ý nhắc quầy ghi nhận cho đơn tương ứng.
 */
export async function recordLeftover(
  vendorId: string,
  menuItemId: string,
  actorId: string,
  input: LeftoverInput,
) {
  const quota = await getOwnedQuota(vendorId, menuItemId);
  if (toDateString(quota.menuItem.date) > todayVN()) {
    throw AppError.conflict('MENU_DATE_NOT_REACHED', 'Chưa đến ngày bán của món này nên chưa thể ghi nhận món dư');
  }

  const resold = input.outcome === 'RESOLD';
  await prisma.$transaction(async (tx) => {
    await tx.quota.update({
      where: { id: quota.id },
      data: resold ? { resold: { increment: input.quantity } } : { wasted: { increment: input.quantity } },
    });
    await log(tx, quota.id, actorId, resold ? 'RESOLD' : 'WASTED', input.quantity, input.reason);
  });

  return freshQuota(quota.id);
}

/** NFR03 Lịch sử điều chỉnh: ai, khi nào, vì sao. */
export async function listAdjustments(vendorId: string, menuItemId: string) {
  const quota = await getOwnedQuota(vendorId, menuItemId);
  return prisma.quotaAdjustment.findMany({
    where: { quotaId: quota.id },
    include: { actor: { select: { id: true, fullName: true } } },
    orderBy: { createdAt: 'desc' },
    take: 100,
  });
}
