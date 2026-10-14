export {
  Role,
  UserStatus,
  VendorStatus,
  VendorType,
  VendorRole,
  DishStatus,
  SessionType,
  QuotaAdjustmentType,
} from '@prisma/client';

import type { SessionType } from '@prisma/client';

export const SESSION_TYPES = ['BREAKFAST', 'LUNCH', 'DINNER'] as const;

export const SESSION_LABEL_VI: Record<SessionType, string> = {
  BREAKFAST: 'Buổi sáng',
  LUNCH: 'Buổi trưa',
  DINNER: 'Buổi chiều',
};
