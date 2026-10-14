import type { DishStatus, SessionType } from '@prisma/client';
import { asyncHandler } from '../../common/utils/asyncHandler';
import { requireUser } from '../../common/middleware/authenticate';
import * as dishes from './dishes.service';
import * as sessions from './sessions.service';
import * as schedule from './menuSchedule.service';
import * as quota from './quota.service';

const vendorId = (req: { params: Record<string, string> }) => req.params.vendorId as string;
const param = (req: { params: Record<string, string> }, name: string) => req.params[name] as string;

// ── Món ăn ──
export const listDishes = asyncHandler(async (req, res) => {
  res.json({ data: await dishes.listDishes(vendorId(req), req.query.status as DishStatus | undefined) });
});
export const createDish = asyncHandler(async (req, res) => {
  res.status(201).json({ data: await dishes.createDish(vendorId(req), req.body) });
});
export const getDish = asyncHandler(async (req, res) => {
  res.json({ data: await dishes.getDish(vendorId(req), param(req, 'dishId')) });
});
export const updateDish = asyncHandler(async (req, res) => {
  res.json({ data: await dishes.updateDish(vendorId(req), param(req, 'dishId'), req.body) });
});
export const updateDishPrice = asyncHandler(async (req, res) => {
  res.json({ data: await dishes.updateDishPrice(vendorId(req), param(req, 'dishId'), req.body.price) });
});
export const setDishStatus = asyncHandler(async (req, res) => {
  res.json({ data: await dishes.setDishStatus(vendorId(req), param(req, 'dishId'), req.body.status) });
});

// ── Ca & khung nhận ──
export const listSessions = asyncHandler(async (req, res) => {
  res.json({ data: await sessions.listSessions(vendorId(req)) });
});
export const upsertSession = asyncHandler(async (req, res) => {
  res.json({
    data: await sessions.upsertSession(vendorId(req), param(req, 'session') as SessionType, req.body),
  });
});

// ── Thực đơn ──
export const listMenu = asyncHandler(async (req, res) => {
  const { date, session } = req.query as { date?: string; session?: SessionType };
  res.json({ data: await schedule.listMenu(vendorId(req), { date, session }) });
});
export const addMenuItem = asyncHandler(async (req, res) => {
  res.status(201).json({ data: await schedule.addMenuItem(vendorId(req), req.body) });
});
export const removeMenuItem = asyncHandler(async (req, res) => {
  await schedule.removeMenuItem(vendorId(req), param(req, 'menuItemId'));
  res.status(204).end();
});

// ── Hạn mức ──
export const setQuota = asyncHandler(async (req, res) => {
  res.json({
    data: await quota.setQuota(vendorId(req), param(req, 'menuItemId'), requireUser(req).id, req.body),
  });
});
export const transferQuota = asyncHandler(async (req, res) => {
  res.json({
    data: await quota.transferQuota(vendorId(req), param(req, 'menuItemId'), requireUser(req).id, req.body),
  });
});
export const recordLeftover = asyncHandler(async (req, res) => {
  res.json({
    data: await quota.recordLeftover(vendorId(req), param(req, 'menuItemId'), requireUser(req).id, req.body),
  });
});
export const listAdjustments = asyncHandler(async (req, res) => {
  res.json({ data: await quota.listAdjustments(vendorId(req), param(req, 'menuItemId')) });
});
