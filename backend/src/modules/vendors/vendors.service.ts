import type { VendorStatus } from '@prisma/client';
import { prisma } from '../../config/prisma';
import { AppError, isPrismaCode } from '../../common/errors/AppError';
import type { CreateVendorInput, UpdateVendorInput } from './vendors.schema';

const vendorInclude = { area: { select: { id: true, name: true } } } as const;

/** UC2.1 Người dùng đăng ký quầy → trạng thái chờ duyệt, người đăng ký thành quản lý quầy. */
export async function registerVendor(userId: string, input: CreateVendorInput) {
  const area = await prisma.area.findUnique({ where: { id: input.areaId }, select: { id: true } });
  if (!area) throw AppError.notFound('AREA_NOT_FOUND', 'Không tìm thấy khu vực');

  try {
    return await prisma.$transaction(async (tx) => {
      const vendor = await tx.vendor.create({
        data: {
          ...input,
          status: 'PENDING_APPROVAL',
          staff: { create: { userId, role: 'MANAGER' } },
        },
        include: vendorInclude,
      });
      await tx.user.updateMany({ where: { id: userId, role: 'CUSTOMER' }, data: { role: 'VENDOR' } });
      return vendor;
    });
  } catch (err) {
    if (isPrismaCode(err, 'P2002')) {
      throw AppError.conflict('VENDOR_NAME_TAKEN', 'Khu vực này đã có quầy trùng tên');
    }
    throw err;
  }
}

/** Danh sách quầy mà người dùng được phân công. */
export async function listMyVendors(userId: string) {
  const memberships = await prisma.vendorStaff.findMany({
    where: { userId },
    include: { vendor: { include: vendorInclude } },
    orderBy: { createdAt: 'asc' },
  });
  return memberships.map((m) => ({ ...m.vendor, myRole: m.role }));
}

export async function getVendor(vendorId: string) {
  const vendor = await prisma.vendor.findUnique({ where: { id: vendorId }, include: vendorInclude });
  if (!vendor) throw AppError.notFound('VENDOR_NOT_FOUND', 'Không tìm thấy quầy');
  return vendor;
}

/** Quản trị viên xem danh sách quầy (mặc định ưu tiên quầy chờ duyệt). */
export async function listVendorsForAdmin(status?: VendorStatus) {
  return prisma.vendor.findMany({
    where: { status },
    include: {
      ...vendorInclude,
      staff: {
        where: { role: 'MANAGER' },
        include: { user: { select: { id: true, fullName: true, email: true } } },
      },
    },
    orderBy: [{ status: 'asc' }, { createdAt: 'desc' }],
  });
}

/** UC2.2 Duyệt: chỉ quầy PENDING_APPROVAL mới duyệt được; chỉ quầy ACTIVE mới nhận đơn (BR02). */
export async function approveVendor(adminId: string, vendorId: string) {
  const result = await prisma.vendor.updateMany({
    where: { id: vendorId, status: 'PENDING_APPROVAL' },
    data: { status: 'ACTIVE', approvedAt: new Date(), approvedById: adminId, rejectionReason: null },
  });
  if (result.count === 0) await explainTransitionFailure(vendorId, 'duyệt');
  return getVendor(vendorId);
}

export async function rejectVendor(adminId: string, vendorId: string, reason: string) {
  const result = await prisma.vendor.updateMany({
    where: { id: vendorId, status: 'PENDING_APPROVAL' },
    data: { status: 'REJECTED', rejectionReason: reason, approvedById: adminId },
  });
  if (result.count === 0) await explainTransitionFailure(vendorId, 'từ chối');
  return getVendor(vendorId);
}

/** UC2.3 Cập nhật thông tin, địa điểm nhận món (trong phạm vi quầy mình – đã chặn bởi vendorScope). */
export async function updateVendor(vendorId: string, input: UpdateVendorInput) {
  const current = await getVendor(vendorId);
  if (current.status === 'REJECTED') {
    throw AppError.conflict('VENDOR_REJECTED', 'Quầy đã bị từ chối, không thể cập nhật');
  }

  const openTime = input.openTime ?? current.openTime;
  const closeTime = input.closeTime ?? current.closeTime;
  if (openTime >= closeTime) {
    throw AppError.badRequest('INVALID_OPENING_HOURS', 'Giờ mở cửa phải trước giờ đóng cửa');
  }

  try {
    return await prisma.vendor.update({ where: { id: vendorId }, data: input, include: vendorInclude });
  } catch (err) {
    if (isPrismaCode(err, 'P2002')) {
      throw AppError.conflict('VENDOR_NAME_TAKEN', 'Khu vực này đã có quầy trùng tên');
    }
    throw err;
  }
}

/**
 * UC2.4 Tạm ngừng: chỉ ngừng nhận đơn MỚI (BR02 kiểm tra status khi đặt đơn).
 * Các đơn đã nhận vẫn được xử lý tiếp hoặc hủy có lý do – module orders không chặn theo status quầy.
 */
export async function suspendVendor(vendorId: string, reason: string) {
  const result = await prisma.vendor.updateMany({
    where: { id: vendorId, status: 'ACTIVE' },
    data: { status: 'SUSPENDED', suspendedReason: reason, suspendedAt: new Date() },
  });
  if (result.count === 0) await explainTransitionFailure(vendorId, 'tạm ngừng');
  return getVendor(vendorId);
}

export async function resumeVendor(vendorId: string) {
  const result = await prisma.vendor.updateMany({
    where: { id: vendorId, status: 'SUSPENDED' },
    data: { status: 'ACTIVE', suspendedReason: null, suspendedAt: null },
  });
  if (result.count === 0) await explainTransitionFailure(vendorId, 'mở lại');
  return getVendor(vendorId);
}

async function explainTransitionFailure(vendorId: string, action: string): Promise<never> {
  const vendor = await prisma.vendor.findUnique({ where: { id: vendorId }, select: { status: true } });
  if (!vendor) throw AppError.notFound('VENDOR_NOT_FOUND', 'Không tìm thấy quầy');
  throw AppError.conflict(
    'VENDOR_INVALID_STATE',
    `Không thể ${action} quầy đang ở trạng thái ${vendor.status}`,
    { currentStatus: vendor.status },
  );
}
