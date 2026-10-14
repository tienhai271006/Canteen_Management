import type { VendorRole } from '@prisma/client';
import { prisma } from '../../config/prisma';
import { AppError } from '../../common/errors/AppError';
import type { AddStaffInput } from './vendors.schema';

/** UC2.5 Quản lý nhân sự quầy. Quyền chỉ có hiệu lực trong từng quầy, không cấp quyền ADMIN. */

export async function listStaff(vendorId: string) {
  return prisma.vendorStaff.findMany({
    where: { vendorId },
    include: { user: { select: { id: true, fullName: true, email: true, status: true } } },
    orderBy: [{ role: 'asc' }, { createdAt: 'asc' }],
  });
}

export async function addStaff(vendorId: string, input: AddStaffInput) {
  const user = await prisma.user.findUnique({ where: { email: input.email } });
  if (!user) throw AppError.notFound('USER_NOT_FOUND', 'Không tìm thấy tài khoản với email này');
  if (user.role === 'ADMIN') {
    throw AppError.badRequest('CANNOT_ASSIGN_ADMIN', 'Không thể gán tài khoản quản trị viên làm nhân sự quầy');
  }
  if (user.status === 'LOCKED') {
    throw AppError.conflict('USER_LOCKED', 'Tài khoản này đang bị khóa');
  }

  const already = await prisma.vendorStaff.findUnique({
    where: { vendorId_userId: { vendorId, userId: user.id } },
    select: { id: true },
  });
  if (already) throw AppError.conflict('ALREADY_MEMBER', 'Người này đã thuộc quầy');

  return prisma.$transaction(async (tx) => {
    const member = await tx.vendorStaff.create({
      data: { vendorId, userId: user.id, role: input.role },
      include: { user: { select: { id: true, fullName: true, email: true } } },
    });
    await tx.user.updateMany({ where: { id: user.id, role: 'CUSTOMER' }, data: { role: 'VENDOR' } });
    return member;
  });
}

async function assertNotLastManager(vendorId: string, targetUserId: string) {
  const target = await prisma.vendorStaff.findUnique({
    where: { vendorId_userId: { vendorId, userId: targetUserId } },
    select: { role: true },
  });
  if (!target) throw AppError.notFound('MEMBER_NOT_FOUND', 'Người này không thuộc quầy');

  if (target.role === 'MANAGER') {
    const managers = await prisma.vendorStaff.count({ where: { vendorId, role: 'MANAGER' } });
    if (managers <= 1) {
      throw AppError.conflict('LAST_MANAGER', 'Quầy phải còn ít nhất một quản lý');
    }
  }
}

export async function updateStaffRole(vendorId: string, userId: string, role: VendorRole) {
  if (role === 'STAFF') await assertNotLastManager(vendorId, userId);
  else {
    const exists = await prisma.vendorStaff.findUnique({
      where: { vendorId_userId: { vendorId, userId } },
      select: { id: true },
    });
    if (!exists) throw AppError.notFound('MEMBER_NOT_FOUND', 'Người này không thuộc quầy');
  }

  return prisma.vendorStaff.update({
    where: { vendorId_userId: { vendorId, userId } },
    data: { role },
    include: { user: { select: { id: true, fullName: true, email: true } } },
  });
}

export async function removeStaff(vendorId: string, userId: string) {
  await assertNotLastManager(vendorId, userId);

  await prisma.$transaction(async (tx) => {
    await tx.vendorStaff.delete({ where: { vendorId_userId: { vendorId, userId } } });
    // Không còn thuộc quầy nào thì trở lại vai trò khách hàng
    const remaining = await tx.vendorStaff.count({ where: { userId } });
    if (remaining === 0) {
      await tx.user.updateMany({ where: { id: userId, role: 'VENDOR' }, data: { role: 'CUSTOMER' } });
    }
  });
}
