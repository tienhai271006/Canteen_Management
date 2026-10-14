import type { DishStatus } from '@prisma/client';
import { prisma } from '../../config/prisma';
import { AppError } from '../../common/errors/AppError';
import type { CreateDishInput, UpdateDishInput } from './menu.schema';

/**
 * UC3.1–3.3 Quản lý món.
 * Không xóa cứng món: ẩn / ngừng bán để giữ lịch sử đơn (BR04, UC3.3).
 * Đổi giá chỉ ảnh hưởng đơn mới; đơn đã tạo lưu snapshot giá riêng (BR04).
 */

export async function createDish(vendorId: string, input: CreateDishInput) {
  return prisma.dish.create({ data: { vendorId, ...input } });
}

export async function listDishes(vendorId: string, status?: DishStatus) {
  return prisma.dish.findMany({
    where: { vendorId, status },
    orderBy: [{ category: 'asc' }, { name: 'asc' }],
  });
}

export async function getDish(vendorId: string, dishId: string) {
  const dish = await prisma.dish.findFirst({ where: { id: dishId, vendorId } });
  if (!dish) throw AppError.notFound('DISH_NOT_FOUND', 'Không tìm thấy món trong quầy này');
  return dish;
}

export async function updateDish(vendorId: string, dishId: string, input: UpdateDishInput) {
  await getDish(vendorId, dishId);
  return prisma.dish.update({ where: { id: dishId }, data: input });
}

/** UC3.2 */
export async function updateDishPrice(vendorId: string, dishId: string, price: number) {
  await getDish(vendorId, dishId);
  return prisma.dish.update({ where: { id: dishId }, data: { price } });
}

/** UC3.3 Món không ACTIVE sẽ không hiện ở catalog và không thể thêm vào thực đơn mới. */
export async function setDishStatus(vendorId: string, dishId: string, status: DishStatus) {
  await getDish(vendorId, dishId);
  return prisma.dish.update({ where: { id: dishId }, data: { status } });
}
