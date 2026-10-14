import { asyncHandler } from '../../common/utils/asyncHandler';
import { requireUser } from '../../common/middleware/authenticate';
import * as vendorsService from './vendors.service';
import * as staffService from './vendorStaff.service';
import type { VendorStatus } from '@prisma/client';

const vendorId = (req: { params: Record<string, string> }) => req.params.vendorId as string;

export const register = asyncHandler(async (req, res) => {
  res.status(201).json({ data: await vendorsService.registerVendor(requireUser(req).id, req.body) });
});

export const listMine = asyncHandler(async (req, res) => {
  res.json({ data: await vendorsService.listMyVendors(requireUser(req).id) });
});

export const getOne = asyncHandler(async (req, res) => {
  res.json({ data: await vendorsService.getVendor(vendorId(req)) });
});

export const update = asyncHandler(async (req, res) => {
  res.json({ data: await vendorsService.updateVendor(vendorId(req), req.body) });
});

export const suspend = asyncHandler(async (req, res) => {
  res.json({ data: await vendorsService.suspendVendor(vendorId(req), req.body.reason) });
});

export const resume = asyncHandler(async (req, res) => {
  res.json({ data: await vendorsService.resumeVendor(vendorId(req)) });
});

// ── Admin ──
export const adminList = asyncHandler(async (req, res) => {
  const status = req.query.status as VendorStatus | undefined;
  res.json({ data: await vendorsService.listVendorsForAdmin(status) });
});

export const adminApprove = asyncHandler(async (req, res) => {
  res.json({ data: await vendorsService.approveVendor(requireUser(req).id, vendorId(req)) });
});

export const adminReject = asyncHandler(async (req, res) => {
  res.json({
    data: await vendorsService.rejectVendor(requireUser(req).id, vendorId(req), req.body.reason),
  });
});

// ── Nhân sự quầy ──
export const listStaff = asyncHandler(async (req, res) => {
  res.json({ data: await staffService.listStaff(vendorId(req)) });
});

export const addStaff = asyncHandler(async (req, res) => {
  res.status(201).json({ data: await staffService.addStaff(vendorId(req), req.body) });
});

export const updateStaff = asyncHandler(async (req, res) => {
  res.json({
    data: await staffService.updateStaffRole(vendorId(req), req.params.userId as string, req.body.role),
  });
});

export const removeStaff = asyncHandler(async (req, res) => {
  await staffService.removeStaff(vendorId(req), req.params.userId as string);
  res.status(204).end();
});
