/**
 * Dữ liệu mẫu cho demo. Chạy lại nhiều lần vẫn an toàn (upsert / findFirst).
 *   npm run db:seed
 * Mật khẩu tất cả tài khoản mẫu: Canteen@123
 */
import { PrismaClient, type SessionType, type VendorType } from '@prisma/client';
import { hashPassword } from '../src/common/utils/hash';
import { addDays, parseDateOnly, todayVN } from '../src/common/utils/time';

const prisma = new PrismaClient();
const PASSWORD = 'Canteen@123';

const SESSION_CONFIG: Record<SessionType, { start: string; end: string; cutoff: string; slots: [string, string][] }> = {
  BREAKFAST: {
    start: '06:30',
    end: '09:00',
    cutoff: '08:00',
    slots: [['06:30', '07:00'], ['07:00', '07:30'], ['07:30', '08:00'], ['08:00', '08:30'], ['08:30', '09:00']],
  },
  LUNCH: {
    start: '10:30',
    end: '13:30',
    cutoff: '12:30',
    slots: [['11:00', '11:30'], ['11:30', '12:00'], ['12:00', '12:30'], ['12:30', '13:00'], ['13:00', '13:30']],
  },
  DINNER: {
    start: '16:00',
    end: '18:00',
    cutoff: '17:00',
    slots: [['16:00', '16:30'], ['16:30', '17:00'], ['17:00', '17:30'], ['17:30', '18:00']],
  },
};

async function upsertUser(email: string, fullName: string, role: 'ADMIN' | 'CUSTOMER' | 'VENDOR', extra = {}) {
  return prisma.user.upsert({
    where: { email },
    update: {},
    create: {
      email,
      fullName,
      role,
      passwordHash: await hashPassword(PASSWORD),
      emailVerifiedAt: new Date(),
      ...extra,
    },
  });
}

async function main() {
  // ── Người dùng ──
  const admin = await upsertUser('admin@phenikaa-uni.edu.vn', 'Quản trị viên', 'ADMIN');
  const manager1 = await upsertUser('quanly.d6@phenikaa-uni.edu.vn', 'Cô Hạnh (Canteen D6)', 'VENDOR');
  const staff1 = await upsertUser('nhanvien.d6@phenikaa-uni.edu.vn', 'Anh Nam (Canteen D6)', 'VENDOR');
  const manager2 = await upsertUser('quanly.basao@phenikaa-uni.edu.vn', 'Chú Tuấn (Ba Sao)', 'VENDOR');
  const manager3 = await upsertUser('quanly.lan@phenikaa-uni.edu.vn', 'Cô Lan (Lán cổng sau)', 'VENDOR');
  await upsertUser('sv.demo@phenikaa-uni.edu.vn', 'Sinh viên Demo', 'CUSTOMER', {
    studentCode: '24107856',
    className: 'K18-CNTT',
  });

  // ── Khu vực ──
  const d6 = await prisma.area.upsert({ where: { name: 'Canteen D6' }, update: {}, create: { name: 'Canteen D6' } });
  const baSao = await prisma.area.upsert({ where: { name: 'Nhà ăn Ba Sao' }, update: {}, create: { name: 'Nhà ăn Ba Sao' } });
  const lan = await prisma.area.upsert({ where: { name: 'Lán quanh trường' }, update: {}, create: { name: 'Lán quanh trường' } });

  // ── Quầy + món ──
  type DishSeed = { name: string; category: string; price: number; sessions: SessionType[] };
  const vendorSeeds: {
    areaId: string;
    name: string;
    type: VendorType;
    pickupLocation: string;
    managerId: string;
    staffId?: string;
    dishes: DishSeed[];
  }[] = [
    {
      areaId: d6.id,
      name: 'Quầy cơm D6',
      type: 'CANTEEN_STALL',
      pickupLocation: 'Tầng 1 tòa D6, quầy số 1',
      managerId: manager1.id,
      staffId: staff1.id,
      dishes: [
        { name: 'Cơm gà xối mỡ', category: 'Cơm', price: 35000, sessions: ['LUNCH', 'DINNER'] },
        { name: 'Cơm sườn nướng', category: 'Cơm', price: 38000, sessions: ['LUNCH', 'DINNER'] },
        { name: 'Cơm chay thập cẩm', category: 'Cơm', price: 30000, sessions: ['LUNCH'] },
        { name: 'Trà đá', category: 'Đồ uống', price: 3000, sessions: ['LUNCH', 'DINNER'] },
      ],
    },
    {
      areaId: baSao.id,
      name: 'Quầy bánh mì Ba Sao',
      type: 'CANTEEN_STALL',
      pickupLocation: 'Nhà ăn Ba Sao, quầy bánh mì',
      managerId: manager2.id,
      dishes: [
        { name: 'Bánh mì trứng', category: 'Bánh mì', price: 15000, sessions: ['BREAKFAST'] },
        { name: 'Bánh mì pate thịt', category: 'Bánh mì', price: 20000, sessions: ['BREAKFAST', 'DINNER'] },
        { name: 'Sữa đậu nành', category: 'Đồ uống', price: 10000, sessions: ['BREAKFAST'] },
      ],
    },
    {
      areaId: lan.id,
      name: 'Lán cô Lan',
      type: 'FOOD_SHED',
      pickupLocation: 'Lán cổng sau, bàn đầu tiên',
      managerId: manager3.id,
      dishes: [
        { name: 'Xôi xéo', category: 'Xôi', price: 15000, sessions: ['BREAKFAST'] },
        { name: 'Bún chả', category: 'Bún/Phở', price: 40000, sessions: ['LUNCH'] },
        { name: 'Phở bò', category: 'Bún/Phở', price: 40000, sessions: ['BREAKFAST', 'LUNCH'] },
      ],
    },
  ];

  const today = todayVN();
  const dates = [today, addDays(today, 1), addDays(today, 2)];

  for (const v of vendorSeeds) {
    const vendor = await prisma.vendor.upsert({
      where: { areaId_name: { areaId: v.areaId, name: v.name } },
      update: {},
      create: {
        areaId: v.areaId,
        name: v.name,
        type: v.type,
        pickupLocation: v.pickupLocation,
        openTime: '06:00',
        closeTime: '18:30',
        status: 'ACTIVE',
        approvedAt: new Date(),
        approvedById: admin.id,
      },
    });

    await prisma.vendorStaff.upsert({
      where: { vendorId_userId: { vendorId: vendor.id, userId: v.managerId } },
      update: {},
      create: { vendorId: vendor.id, userId: v.managerId, role: 'MANAGER' },
    });
    if (v.staffId) {
      await prisma.vendorStaff.upsert({
        where: { vendorId_userId: { vendorId: vendor.id, userId: v.staffId } },
        update: {},
        create: { vendorId: vendor.id, userId: v.staffId, role: 'STAFF' },
      });
    }

    // Ca + khung nhận
    for (const session of Object.keys(SESSION_CONFIG) as SessionType[]) {
      const cfg = SESSION_CONFIG[session];
      const s = await prisma.serviceSession.upsert({
        where: { vendorId_session: { vendorId: vendor.id, session } },
        update: {},
        create: {
          vendorId: vendor.id,
          session,
          startTime: cfg.start,
          endTime: cfg.end,
          orderCutoff: cfg.cutoff,
          prepMinutes: 15,
        },
      });
      for (const [startTime, endTime] of cfg.slots) {
        await prisma.pickupSlot.upsert({
          where: { sessionId_startTime: { sessionId: s.id, startTime } },
          update: {},
          create: { sessionId: s.id, startTime, endTime },
        });
      }
    }

    // Món + thực đơn 3 ngày tới + hạn mức
    for (const d of v.dishes) {
      const dish =
        (await prisma.dish.findFirst({ where: { vendorId: vendor.id, name: d.name } })) ??
        (await prisma.dish.create({
          data: { vendorId: vendor.id, name: d.name, category: d.category, price: d.price },
        }));

      for (const date of dates) {
        for (const session of d.sessions) {
          await prisma.menuItem.upsert({
            where: { dishId_date_session: { dishId: dish.id, date: parseDateOnly(date), session } },
            update: {},
            create: {
              vendorId: vendor.id,
              dishId: dish.id,
              date: parseDateOnly(date),
              session,
              quota: { create: { onlineTotal: 30, walkInTotal: 10 } },
            },
          });
        }
      }
    }
  }

  console.log('✅ Seed xong. Tài khoản mẫu (mật khẩu: %s):', PASSWORD);
  console.log('   admin@phenikaa-uni.edu.vn          – Quản trị viên');
  console.log('   quanly.d6@phenikaa-uni.edu.vn      – Quản lý Quầy cơm D6');
  console.log('   nhanvien.d6@phenikaa-uni.edu.vn    – Nhân viên Quầy cơm D6');
  console.log('   quanly.basao@phenikaa-uni.edu.vn   – Quản lý Quầy bánh mì Ba Sao');
  console.log('   quanly.lan@phenikaa-uni.edu.vn     – Quản lý Lán cô Lan');
  console.log('   sv.demo@phenikaa-uni.edu.vn        – Khách hàng');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
