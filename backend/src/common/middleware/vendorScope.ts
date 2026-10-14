import type { Request, RequestHandler } from 'express';
import type { VendorRole, VendorStatus } from '@prisma/client';
import { prisma } from '../../config/prisma';
import { AppError } from '../errors/AppError';
import { asyncHandler } from '../utils/asyncHandler';
import { requireUser } from './authenticate';

/** Quầy chưa bị từ chối: được phép cấu hình thực đơn (kể cả khi chờ duyệt hoặc tạm ngừng). */
export const OPERABLE_VENDOR_STATUSES: VendorStatus[] = ['PENDING_APPROVAL', 'ACTIVE', 'SUSPENDED'];

/**
 * Chặn truy cập dữ liệu quầy khác (BR03, NFR01).
 * Người dùng phải được gán vào quầy ở :vendorId với vai trò trong `roles`.
 * Quản trị viên KHÔNG tự động có quyền vận hành quầy.
 *
 * Trả 403 chung cho cả "quầy không tồn tại" và "không được gán" để không lộ thông tin.
 */
export function vendorScope(
  roles: VendorRole[] = ['MANAGER', 'STAFF'],
  opts: { allowedStatuses?: VendorStatus[] } = {},
): RequestHandler {
  return asyncHandler(async (req, _res, next) => {
    const user = requireUser(req);
    const vendorId = req.params.vendorId;
    if (!vendorId) throw AppError.badRequest('MISSING_VENDOR_ID', 'Thiếu mã quầy');

    const membership = await prisma.vendorStaff.findUnique({
      where: { vendorId_userId: { vendorId, userId: user.id } },
      select: { role: true, vendor: { select: { status: true } } },
    });

    if (!membership) {
      throw AppError.forbidden('VENDOR_ACCESS_DENIED', 'Bạn không được phân công tại quầy này');
    }
    if (!roles.includes(membership.role)) {
      throw AppError.forbidden('VENDOR_ROLE_NOT_ALLOWED', 'Vai trò tại quầy không đủ quyền cho thao tác này');
    }
    if (opts.allowedStatuses && !opts.allowedStatuses.includes(membership.vendor.status)) {
      throw AppError.conflict('VENDOR_STATUS_NOT_ALLOWED', 'Trạng thái hiện tại của quầy không cho phép thao tác này');
    }

    req.vendorMember = { vendorId, role: membership.role, vendorStatus: membership.vendor.status };
    next();
  });
}

export function requireMember(req: Request): Express.VendorMember {
  if (!req.vendorMember) throw AppError.forbidden('VENDOR_ACCESS_DENIED');
  return req.vendorMember;
}
