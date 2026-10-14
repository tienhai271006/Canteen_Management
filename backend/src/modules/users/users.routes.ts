import { Router } from 'express';
import { authenticate } from '../../common/middleware/authenticate';
import { validate } from '../../common/middleware/validate';
import * as controller from './users.controller';
import { updateProfileSchema } from './users.schema';

const router = Router();

router.get('/users/me', authenticate, controller.getMe);
router.patch('/users/me', authenticate, validate({ body: updateProfileSchema }), controller.updateMe);
router.get('/users/me/status', authenticate, controller.getMyStatus);

export default router;
