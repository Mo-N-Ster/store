import nodemailer from 'nodemailer';
export interface SmtpConfig {
  host: string;
  port: number;
  secure: boolean;
  user: string;
  password: string;
  from: string;
}
export interface EmailAttachment {
  filename: string;
  content: Buffer;
  contentType?: string;
}
export async function sendEmail(
  config: SmtpConfig,
  to: string,
  subject: string,
  text: string,
  attachments: EmailAttachment[] = [],
) {
  if (!config.host || !to) throw new Error('SMTP_NOT_CONFIGURED');
  try {
  const transporter = nodemailer.createTransport({
    host: config.host,
    port: config.port,
    secure: config.secure,
    auth: config.user ? { user: config.user, pass: config.password } : undefined,
  });
  await transporter.sendMail({
    from: config.from || config.user,
    to,
    subject,
    text,
    attachments,
    // STORE only supplies in-memory PDF buffers. Keep message rendering unable
    // to dereference local paths or remote URLs even if future callers regress.
    disableFileAccess: true,
    disableUrlAccess: true,
  });
  } catch {
    // Never persist or log provider responses containing credentials or message data.
    throw new Error('SMTP_FAILED');
  }
}
