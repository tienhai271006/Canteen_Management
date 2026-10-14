import { randomInt } from 'node:crypto';
import { formatDateVN } from './time';

// Bỏ các ký tự dễ nhầm: 0/O, 1/I
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

/** Mã đơn dạng CT-241014-K7M2Q. Khi lưu DB, cột code là unique nên nếu trùng thì sinh lại. */
export function generateOrderCode(now: Date = new Date()): string {
  const yymmdd = formatDateVN(now).slice(2).replace(/-/g, '');
  let suffix = '';
  for (let i = 0; i < 5; i++) suffix += ALPHABET[randomInt(0, ALPHABET.length)];
  return `CT-${yymmdd}-${suffix}`;
}
