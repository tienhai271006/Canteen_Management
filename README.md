# Canteen_Management

# Cấu trúc thư mục
backend/
├── package.json
├── tsconfig.json
├── Dockerfile
├── .env.example
├── .eslintrc.cjs
├── prisma/
│   ├── schema.prisma
│   ├── seed.ts
│   └── migrations/
└── src/
    ├── server.ts
    ├── app.ts
    ├── config/
    │   ├── env.ts
    │   ├── prisma.ts
    │   ├── mailer.ts
    │   └── constants.ts
    ├── common/
    │   ├── middleware/
    │   │   ├── authenticate.ts
    │   │   ├── authorize.ts
    │   │   ├── vendorScope.ts
    │   │   ├── validate.ts
    │   │   ├── idempotency.ts
    │   │   └── errorHandler.ts
    │   ├── errors/
    │   │   └── AppError.ts
    │   ├── utils/
    │   │   ├── time.ts
    │   │   ├── hash.ts
    │   │   ├── token.ts
    │   │   ├── orderCode.ts
    │   │   └── asyncHandler.ts
    │   └── types/
    │       ├── express.d.ts
    │       └── enums.ts
    ├── modules/
    │   ├── auth/
    │   │   ├── auth.routes.ts
    │   │   ├── auth.controller.ts
    │   │   ├── auth.service.ts
    │   │   ├── auth.schema.ts
    │   │   └── emailVerification.service.ts
    │   ├── users/
    │   │   ├── users.routes.ts
    │   │   ├── users.controller.ts
    │   │   ├── users.service.ts
    │   │   └── users.schema.ts
    │   ├── vendors/
    │   │   ├── vendors.routes.ts
    │   │   ├── vendors.controller.ts
    │   │   ├── vendors.service.ts
    │   │   ├── vendors.schema.ts
    │   │   └── vendorStaff.service.ts
    │   ├── menu/
    │   │   ├── menu.routes.ts
    │   │   ├── menu.controller.ts
    │   │   ├── dishes.service.ts
    │   │   ├── menuSchedule.service.ts
    │   │   ├── sessions.service.ts
    │   │   ├── quota.service.ts
    │   │   └── menu.schema.ts
    │   ├── catalog/
    │   │   ├── catalog.routes.ts
    │   │   ├── catalog.controller.ts
    │   │   ├── catalog.service.ts
    │   │   └── catalog.schema.ts
    │   ├── cart/
    │   │   ├── cart.routes.ts
    │   │   ├── cart.controller.ts
    │   │   ├── cart.service.ts
    │   │   └── cart.schema.ts
    │   ├── orders/
    │   │   ├── orders.routes.ts
    │   │   ├── orders.controller.ts
    │   │   ├── orders.schema.ts
    │   │   ├── orderStateMachine.ts
    │   │   ├── placeOrder.service.ts
    │   │   ├── pricing.service.ts
    │   │   ├── orderQuery.service.ts
    │   │   ├── customerCancel.service.ts
    │   │   ├── vendorProcess.service.ts
    │   │   ├── cancelRequest.service.ts
    │   │   ├── supportRequest.service.ts
    │   │   └── notCollected.service.ts
    │   ├── promotions/
    │   │   ├── promotions.routes.ts
    │   │   ├── promotions.controller.ts
    │   │   ├── promotions.service.ts
    │   │   ├── firstOrderVoucher.service.ts
    │   │   └── promotions.schema.ts
    │   ├── violations/
    │   │   ├── violations.routes.ts
    │   │   ├── violations.controller.ts
    │   │   ├── violations.service.ts
    │   │   ├── thresholdPolicy.service.ts
    │   │   ├── restriction.service.ts
    │   │   ├── appeal.service.ts
    │   │   └── violations.schema.ts
    │   ├── notifications/
    │   │   ├── notifications.routes.ts
    │   │   ├── notifications.controller.ts
    │   │   ├── notifications.service.ts
    │   │   ├── emailOutbox.service.ts
    │   │   └── templates/
    │   │       ├── otp.ts
    │   │       ├── orderReady.ts
    │   │       ├── violationReminder.ts
    │   │       └── violationWarning.ts
    │   └── reports/
    │       ├── reports.routes.ts
    │       ├── reports.controller.ts
    │       ├── reports.service.ts
    │       └── reports.schema.ts
    ├── jobs/
    │   ├── index.ts
    │   ├── expirePending.job.ts
    │   ├── retryEmail.job.ts
    │   ├── expireRestriction.job.ts
    │   └── expireVoucher.job.ts
    ├── routes/
    │   └── index.ts
    └── services/
        └── ocr.service.ts
tests/
├── unit/
│   ├── orderStateMachine.test.ts
│   ├── pricing.test.ts
│   └── thresholdPolicy.test.ts
└── integration/
    ├── placeOrder.concurrent.test.ts
    ├── expirePending.test.ts
    └── authorization.test.ts
frontend/