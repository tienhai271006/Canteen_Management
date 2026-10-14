import { asyncHandler } from '../../common/utils/asyncHandler';
import { requireUser } from '../../common/middleware/authenticate';
import * as usersService from './users.service';

export const getMe = asyncHandler(async (req, res) => {
  res.json({ data: await usersService.getProfile(requireUser(req).id) });
});

export const updateMe = asyncHandler(async (req, res) => {
  res.json({ data: await usersService.updateProfile(requireUser(req).id, req.body) });
});

export const getMyStatus = asyncHandler(async (req, res) => {
  res.json({ data: await usersService.getAccountStatus(requireUser(req).id) });
});
