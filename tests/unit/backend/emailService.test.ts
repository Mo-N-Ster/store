import { beforeEach, describe, expect, it, vi } from 'vitest';

const { sendMail, createTransport } = vi.hoisted(() => {
  const sendMail = vi.fn().mockResolvedValue({ messageId: 'isolated-test-message' });
  return { sendMail, createTransport: vi.fn(() => ({ sendMail })) };
});

vi.mock('nodemailer', () => ({
  default: { createTransport },
}));

import { sendEmail } from '../../../backend/src/services/emailService';

describe('SMTP service security regression', () => {
  it('redacts provider errors before logs or queue persistence', async () => {
    sendMail.mockRejectedValueOnce(new Error('password=test-secret provider response'));
    await expect(sendEmail({ host: 'smtp.test', port: 587, secure: false, user: 'u', password: 'test-secret', from: 'a@b.test' }, 'a@b.test', 'subject', 'body')).rejects.toThrow(/^SMTP_FAILED$/);
  });
  beforeEach(() => {
    createTransport.mockClear();
    sendMail.mockClear();
  });

  it('uses the configured SMTP transport and only in-memory attachments', async () => {
    const attachment = Buffer.from('isolated invoice');

    await sendEmail(
      {
        host: 'smtp.invalid.test',
        port: 465,
        secure: true,
        user: 'store-test',
        password: 'not-a-real-secret',
        from: 'store@example.invalid',
      },
      'recipient@example.invalid',
      'Facture test',
      'Contenu test',
      [{ filename: 'invoice.pdf', content: attachment, contentType: 'application/pdf' }],
    );

    expect(createTransport).toHaveBeenCalledWith({
      host: 'smtp.invalid.test',
      port: 465,
      secure: true,
      auth: { user: 'store-test', pass: 'not-a-real-secret' },
    });
    expect(sendMail).toHaveBeenCalledWith(
      expect.objectContaining({
        to: 'recipient@example.invalid',
        attachments: [
          expect.objectContaining({ filename: 'invoice.pdf', content: attachment }),
        ],
        disableFileAccess: true,
        disableUrlAccess: true,
      }),
    );
  });

  it('fails closed before transport creation when SMTP is not configured', async () => {
    await expect(
      sendEmail(
        { host: '', port: 587, secure: false, user: '', password: '', from: '' },
        'recipient@example.invalid',
        'Test',
        'Test',
      ),
    ).rejects.toThrow('SMTP_NOT_CONFIGURED');
    expect(createTransport).not.toHaveBeenCalled();
  });
});
