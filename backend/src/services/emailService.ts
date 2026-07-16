import nodemailer from 'nodemailer';
export interface SmtpConfig {
  host: string;
  port: number;
  secure: boolean;
  user: string;
  password: string;
  from: string;
}
export async function sendEmail(config: SmtpConfig, to: string, subject: string, text: string) {
  if (!config.host || !to) throw new Error('SMTP_NOT_CONFIGURED');
  const transporter = nodemailer.createTransport({
    host: config.host,
    port: config.port,
    secure: config.secure,
    auth: config.user ? { user: config.user, pass: config.password } : undefined,
  });
  await transporter.sendMail({ from: config.from || config.user, to, subject, text });
}
