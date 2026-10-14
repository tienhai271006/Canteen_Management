import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { authenticate } from '../../common/middleware/authenticate';
import { validate } from '../../common/middleware/validate';
import * as controller from './auth.controller';
import { changePasswordSchema, loginSchema, registerSchema, verifyEmailSchema } from './auth.schema';

const router = Router();

// Giới hạn dò mật khẩu / spam đăng ký (NFR04)
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: { code: 'TOO_MANY_REQUESTS', message: 'Thao tác quá nhiều lần, vui lòng thử lại sau ít phút' } },
});

router.post('/auth/register', authLimiter, validate({ body: registerSchema }), controller.register);
router.post('/auth/login', authLimiter, validate({ body: loginSchema }), controller.login);
router.post('/auth/logout', authenticate, controller.logout);
router.post('/auth/verify-email', authenticate, validate({ body: verifyEmailSchema }), controller.verifyEmail);
router.post('/auth/resend-otp', authenticate, controller.resendOtp);
router.post('/auth/change-password', authenticate, validate({ body: changePasswordSchema }), controller.changePassword);

export default router;
