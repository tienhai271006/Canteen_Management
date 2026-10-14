import { asyncHandler } from '../../common/utils/asyncHandler';
import { requireUser } from '../../common/middleware/authenticate';
import * as authService from './auth.service';
import * as verification from './emailVerification.service';
import { toPublicUser } from '../users/users.service';

export const register = asyncHandler(async (req, res) => {
  res.status(201).json({ data: await authService.register(req.body) });
});

export const login = asyncHandler(async (req, res) => {
  res.json({ data: await authService.login(req.body) });
});

export const logout = asyncHandler(async (req, res) => {
  await authService.logout(requireUser(req).id);
  res.json({ data: { message: 'Đã đăng xuất' } });
});

export const verifyEmail = asyncHandler(async (req, res) => {
  const user = await verification.verifyCode(requireUser(req).id, req.body.code);
  res.json({ data: { user: toPublicUser(user) } });
});

export const resendOtp = asyncHandler(async (req, res) => {
  const { delivered, expiresAt } = await verification.resendVerificationCode(requireUser(req).id);
  res.json({ data: { delivered, expiresAt } });
});

export const changePassword = asyncHandler(async (req, res) => {
  res.json({ data: await authService.changePassword(requireUser(req).id, req.body) });
});
