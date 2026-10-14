import { Router } from 'express';
import { authenticate } from '../../common/middleware/authenticate';
import { validate } from '../../common/middleware/validate';
import { OPERABLE_VENDOR_STATUSES, vendorScope } from '../../common/middleware/vendorScope';
import * as controller from './menu.controller';
import {
  addMenuItemSchema,
  createDishSchema,
  dishStatusSchema,
  leftoverSchema,
  listDishesQuerySchema,
  menuQuerySchema,
  sessionParamSchema,
  setQuotaSchema,
  transferQuotaSchema,
  updateDishSchema,
  updatePriceSchema,
  upsertSessionSchema,
} from './menu.schema';

const router = Router();

// Đọc: quản lý và nhân viên của quầy. Ghi: chỉ quản lý, và quầy chưa bị từ chối (BR03, NFR01).
const read = [authenticate, vendorScope(['MANAGER', 'STAFF'])];
const write = [authenticate, vendorScope(['MANAGER'], { allowedStatuses: OPERABLE_VENDOR_STATUSES })];

// ── UC3.1–3.3 Món ăn ──
router.get('/vendors/:vendorId/dishes', ...read, validate({ query: listDishesQuerySchema }), controller.listDishes);
router.post('/vendors/:vendorId/dishes', ...write, validate({ body: createDishSchema }), controller.createDish);
router.get('/vendors/:vendorId/dishes/:dishId', ...read, controller.getDish);
router.patch('/vendors/:vendorId/dishes/:dishId', ...write, validate({ body: updateDishSchema }), controller.updateDish);
router.patch('/vendors/:vendorId/dishes/:dishId/price', ...write, validate({ body: updatePriceSchema }), controller.updateDishPrice);
router.patch('/vendors/:vendorId/dishes/:dishId/status', ...write, validate({ body: dishStatusSchema }), controller.setDishStatus);

// ── UC3.5 Ca & khung nhận ──
router.get('/vendors/:vendorId/sessions', ...read, controller.listSessions);
router.put(
  '/vendors/:vendorId/sessions/:session',
  ...write,
  validate({ params: sessionParamSchema, body: upsertSessionSchema }),
  controller.upsertSession,
);

// ── UC3.4 Thực đơn theo ngày/ca ──
router.get('/vendors/:vendorId/menus', ...read, validate({ query: menuQuerySchema }), controller.listMenu);
router.post('/vendors/:vendorId/menus/items', ...write, validate({ body: addMenuItemSchema }), controller.addMenuItem);
router.delete('/vendors/:vendorId/menus/items/:menuItemId', ...write, controller.removeMenuItem);

// ── UC3.6–3.7 Hạn mức ──
router.patch('/vendors/:vendorId/quotas/:menuItemId', ...write, validate({ body: setQuotaSchema }), controller.setQuota);
router.post(
  '/vendors/:vendorId/quotas/:menuItemId/transfer',
  ...write,
  validate({ body: transferQuotaSchema }),
  controller.transferQuota,
);
router.post(
  '/vendors/:vendorId/quotas/:menuItemId/leftover',
  ...write,
  validate({ body: leftoverSchema }),
  controller.recordLeftover,
);
router.get('/vendors/:vendorId/quotas/:menuItemId/adjustments', ...read, controller.listAdjustments);

export default router;
