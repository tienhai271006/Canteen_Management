import nodemailer from 'nodemailer';
import { env } from './env';

const transporter = env.SMTP_HOST
  ? nodemailer.createTransport({
      host: env.SMTP_HOST,
      port: env.SMTP_PORT,
      secure: env.SMTP_PORT === 465,
      auth: env.SMTP_USER ? { user: env.SMTP_USER, pass: env.SMTP_PASS } : undefined,
    })
  : null;

export interface MailInput {
  to: string;
  subject: string;
  text: string;
  html?: string;
}

/**
 * Gửi email. Trả về true/false thay vì ném lỗi: lỗi email không được làm hỏng
 * nghiệp vụ chính (NFR05). Khi chưa cấu hình SMTP, nội dung được in ra console.
 */
export async function sendMail(input: MailInput): Promise<boolean> {
  if (!transporter) {
    console.log(`\n[mail:dev] To: ${input.to}\n[mail:dev] Subject: ${input.subject}\n${input.text}\n`);
    return true;
  }
  try {
    await transporter.sendMail({ from: env.MAIL_FROM, ...input });
    return true;
  } catch (err) {
    console.error('[mail] gửi thất bại:', err);
    return false;
  }
}
