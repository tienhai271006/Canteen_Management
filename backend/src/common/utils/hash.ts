import { compare, hash, hashSync } from 'bcryptjs';

const COST = 10;

export const hashPassword = (plain: string) => hash(plain, COST);
export const comparePassword = (plain: string, hashed: string) => compare(plain, hashed);

/** Dùng khi không tìm thấy user để thời gian phản hồi login không lộ email có tồn tại hay không. */
export const DUMMY_HASH = hashSync('dummy-password-for-timing', COST);
