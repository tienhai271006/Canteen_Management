import { Router } from 'express';
import { authenticate } from '../../common/middleware/authenticate';
import { authorize } from '../../common/middleware/authorize';
import { validate } from '../../common/middleware/validate';
import { vendorScope } from '../../common/middleware/vendorScope';
import * as controller from './vendors.controller';
import {
  addStaffSchema,
  adminVendorQuerySchema,
  createVendorSchema,
  reasonSchema,
  updateStaffSchema,
  updateVendorSchema,
} from './vendors.schema';

const router = Router();

const anyMember = vendorScope(['MANAGER', 'STAFF']);
const managerOnly = vendorScope(['MANAGER']);

// ── UC2.1 Đăng ký quầy (khách hàng hoặc người đã thuộc quầy khác; admin thì không) ──
router.post(
  '/vendors',
  authenticate,
  authorize('CUSTOMER', 'VENDOR'),
  validate({ body: createVendorSchema }),
  controller.register,
);

// Phải đặt trước '/vendors/:vendorId' để "mine" không bị hiểu là vendorId
router.get('/vendors/mine', authenticate, controller.listMine);

router.get('/vendors/:vendorId', authenticate, anyMember, controller.getOne);

// ── UC2.3 / UC2.4 ──
router.patch('/vendors/:vendorId', authenticate, managerOnly, validate({ body: updateVendorSchema }), controller.update);
router.post('/vendors/:vendorId/suspend', authenticate, managerOnly, validate({ body: reasonSchema }), controller.suspend);
router.post('/vendors/:vendorId/resume', authenticate, managerOnly, controller.resume);

// ── UC2.5 Nhân sự quầy ──
router.get('/vendors/:vendorId/staff', authenticate, anyMember, controller.listStaff);
router.post('/vendors/:vendorId/staff', authenticate, managerOnly, validate({ body: addStaffSchema }), controller.addStaff);
router.patch(
  '/vendors/:vendorId/staff/:userId',
  authenticate,
  managerOnly,
  validate({ body: updateStaffSchema }),
  controller.updateStaff,
);
router.delete('/vendors/:vendorId/staff/:userId', authenticate, managerOnly, controller.removeStaff);

// ── UC2.2 Quản trị viên duyệt quầy ──
router.get(
  '/admin/vendors',
  authenticate,
  authorize('ADMIN'),
  validate({ query: adminVendorQuerySchema }),
  controller.adminList,
);
router.post('/admin/vendors/:vendorId/approve', authenticate, authorize('ADMIN'), controller.adminApprove);
router.post(
  '/admin/vendors/:vendorId/reject',
  authenticate,
  authorize('ADMIN'),
  validate({ body: reasonSchema }),
  controller.adminReject,
);

export default router;
