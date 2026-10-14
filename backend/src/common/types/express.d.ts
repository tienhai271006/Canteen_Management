import type { Role, VendorRole, VendorStatus } from '@prisma/client';

declare global {
  namespace Express {
    interface AuthUser {
      id: string;
      role: Role;
      emailVerified: boolean;
    }
    interface VendorMember {
      vendorId: string;
      role: VendorRole;
      vendorStatus: VendorStatus;
    }
    interface Request {
      user?: AuthUser;
      vendorMember?: VendorMember;
    }
  }
}

export {};
