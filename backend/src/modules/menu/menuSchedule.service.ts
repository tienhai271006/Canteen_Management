import type { SessionType } from '@prisma/client';
import { prisma } from '../../config/prisma';
import { AppError, isPrismaCode } from '../../common/errors/AppError';
import { parseDateOnly, todayVN, toDateString } from '../../common/utils/time';
import type { AddMenuItemInput } from './menu.schema';
import { withAvailability } from './quota.service';

const itemInclude = {
  dish: { select: { id: true, name: true, price: true, status: true, imageUrl: true, category: true } },
  quota: true,
} as const;

function present(item: {
  id: string;
  date: Date;
  session: SessionType;
  dish: unknown;
  quota: Parameters<typeof withAvailability>[0] | null;
}) {
  return {
    id: item.id,
    date: toDateString(item.date),
    session: item.session,
    dish: item.dish,
    quota: item.quota ? withAvailability(item.quota) : null,
  };
}

/** UC3.4 Xem thực đơn của quầy (mặc định từ hôm nay trở đi), kèm số suất còn lại. */
export async function listMenu(vendorId: string, q: { date?: string; session?: SessionType }) {
  const items = await prisma.menuItem.findMany({
    where: {
      vendorId,
      session: q.session,
      date: q.date ? parseDateOnly(q.date) : { gte: parseDateOnly(todayVN()) },
    },
    include: itemInclude,
    orderBy: [{ date: 'asc' }, { session: 'asc' }, { createdAt: 'asc' }],
    take: 500,
  });
  return items.map(present);
}

/** UC3.4 + UC3.6 Thêm món vào thực đơn một ngày/ca, kèm hạn mức ban đầu. */
export async function addMenuItem(vendorId: string, input: AddMenuItemInput) {
  if (input.date < todayVN()) {
    throw AppError.badRequest('PAST_DATE', 'Không thể lập thực đơn cho ngày đã qua');
  }

  const dish = await prisma.dish.findFirst({ where: { id: input.dishId, vendorId } });
  if (!dish) throw AppError.notFound('DISH_NOT_FOUND', 'Không tìm thấy món trong quầy này');
  if (dish.status !== 'ACTIVE') {
    throw AppError.conflict('DISH_NOT_AVAILABLE', 'Món đang ẩn hoặc đã ngừng bán nên không thể thêm vào thực đơn');
  }

  const session = await prisma.serviceSession.findUnique({
    where: { vendorId_session: { vendorId, session: input.session } },
    select: { isActive: true },
  });
  if (!session?.isActive) {
    throw AppError.conflict(
      'SESSION_NOT_CONFIGURED',
      'Quầy chưa thiết lập (hoặc đã tắt) ca này. Hãy cấu hình ca và khung nhận trước',
    );
  }

  try {
    const item = await prisma.menuItem.create({
      data: {
        vendorId,
        dishId: input.dishId,
        date: parseDateOnly(input.date),
        session: input.session,
        quota: { create: { onlineTotal: input.onlineQuota, walkInTotal: input.walkInQuota } },
      },
      include: itemInclude,
    });
    return present(item);
  } catch (err) {
    if (isPrismaCode(err, 'P2002')) {
      throw AppError.conflict('ALREADY_IN_MENU', 'Món này đã có trong thực đơn của ngày/ca đó');
    }
    throw err;
  }
}

/**
 * Gỡ món khỏi thực đơn. Chỉ được khi chưa có suất nào được giữ hoặc bán, để không làm mất lịch sử đơn.
 * Nếu đã có đơn: hạ hạn mức về đúng số đã cam kết hoặc ngừng bán món.
 * (Xóa quota kèm lịch sử điều chỉnh của nó, vốn chỉ phát sinh khi món chưa có đơn.)
 */
export async function removeMenuItem(vendorId: string, menuItemId: string) {
  const item = await prisma.menuItem.findFirst({ where: { id: menuItemId, vendorId }, select: { id: true } });
  if (!item) throw AppError.notFound('MENU_ITEM_NOT_FOUND', 'Không tìm thấy món trong thực đơn');

  await prisma.$transaction(async (tx) => {
    // Điều kiện reserved=0, sold=0 nằm ngay trong câu DELETE nên an toàn khi có đơn đặt đồng thời
    const removed = await tx.quota.deleteMany({ where: { menuItemId, reserved: 0, sold: 0 } });
    if (removed.count === 0) {
      throw AppError.conflict(
        'MENU_ITEM_HAS_ORDERS',
        'Món đã có suất được đặt nên không thể gỡ. Hãy hạ hạn mức về số đã cam kết hoặc ngừng bán món',
      );
    }
    await tx.menuItem.delete({ where: { id: menuItemId } });
  });
}
