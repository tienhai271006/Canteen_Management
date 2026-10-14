import { Router } from 'express';
import authRoutes from '../modules/auth/auth.routes';
import usersRoutes from '../modules/users/users.routes';
import vendorsRoutes from '../modules/vendors/vendors.routes';
import menuRoutes from '../modules/menu/menu.routes';

const router = Router();

router.get('/health', (_req, res) => {
  res.json({ data: { status: 'ok', time: new Date().toISOString() } });
});

router.use(authRoutes); // UC1
router.use(usersRoutes); // UC1
router.use(vendorsRoutes); // UC2
router.use(menuRoutes); // UC3

// Các module tiếp theo sẽ được gắn tại đây:
// catalog (UC4), cart (UC5.1), orders (UC5–7), promotions (UC8),
// violations (UC10), notifications, reports (UC9)

export default router;
