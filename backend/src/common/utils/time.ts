import { TIMEZONE } from '../../config/constants';

// Việt Nam không có giờ mùa hè nên offset cố định +07:00 (NFR08).
const dateFmt = new Intl.DateTimeFormat('en-CA', {
  timeZone: TIMEZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});
const timeFmt = new Intl.DateTimeFormat('en-GB', {
  timeZone: TIMEZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

export const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;
export const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

/** 'YYYY-MM-DD' theo giờ Việt Nam. */
export const formatDateVN = (d: Date = new Date()): string => dateFmt.format(d);

/** 'HH:mm' theo giờ Việt Nam. */
export const formatTimeVN = (d: Date = new Date()): string => timeFmt.format(d);

export const todayVN = (): string => formatDateVN();

export function isValidDateString(s: string): boolean {
  if (!DATE_RE.test(s)) return false;
  const d = new Date(`${s}T00:00:00.000Z`);
  return !Number.isNaN(d.getTime()) && d.toISOString().slice(0, 10) === s;
}

/** 'YYYY-MM-DD' → Date (00:00 UTC) để lưu vào cột @db.Date. */
export function parseDateOnly(s: string): Date {
  if (!isValidDateString(s)) throw new Error(`Ngày không hợp lệ: ${s}`);
  return new Date(`${s}T00:00:00.000Z`);
}

/** Giá trị cột @db.Date → 'YYYY-MM-DD'. */
export const toDateString = (d: Date): string => d.toISOString().slice(0, 10);

export function addDays(dateStr: string, n: number): string {
  const d = parseDateOnly(dateStr);
  d.setUTCDate(d.getUTCDate() + n);
  return toDateString(d);
}

/** Ghép ngày + giờ theo giờ Việt Nam thành một thời điểm tuyệt đối. */
export const vnDateTime = (dateStr: string, time: string): Date =>
  new Date(`${dateStr}T${time}:00+07:00`);

export const addMinutes = (d: Date, minutes: number): Date => new Date(d.getTime() + minutes * 60_000);

/** Đã qua giờ đóng nhận đơn của ca trong ngày `dateStr` chưa (BR02). */
export const isPastCutoff = (dateStr: string, cutoff: string, now: Date = new Date()): boolean =>
  now.getTime() >= vnDateTime(dateStr, cutoff).getTime();

/** Hạn nhận = mốc muộn hơn giữa cuối khung nhận và lúc báo Ready, cộng thời gian chờ (BR22). */
export function computePickupDeadline(slotEnd: Date, readyAt: Date, waitMinutes: number): Date {
  const base = slotEnd.getTime() > readyAt.getTime() ? slotEnd : readyAt;
  return addMinutes(base, waitMinutes);
}

/** Mốc bắt đầu của cửa sổ trượt N ngày gần nhất (BR26). */
export const windowStart = (days: number, now: Date = new Date()): Date =>
  new Date(now.getTime() - days * 86_400_000);
