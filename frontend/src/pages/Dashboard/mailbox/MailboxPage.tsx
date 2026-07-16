import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../../types';
import { messageService } from '../../../services/messageService';
export function MailboxPage({ user, notify }: { user: User; notify: (x: string) => void }) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<any[]>([]);
  const [compose, setCompose] = useState(false);
  const load = () => messageService.list({ userId: user.id, role: user.role }).then(setRows);
  useEffect(() => {
    void load();
  }, []);
  const send = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = Object.fromEntries(new FormData(event.currentTarget));
    await messageService.send({
      senderId: user.id,
      recipientType: user.role === 'admin' ? 'all' : 'admin',
      ...form,
    });
    setCompose(false);
    await load();
    notify('Message envoyé');
  };
  return (
    <>
      <div className="titlebar">
        <div>
          <span className="eyebrow">Communication</span>
          <h1>{t('mailbox')}</h1>
        </div>
        <button onClick={() => setCompose(true)}>+ {t('newMessage')}</button>
      </div>
      {!rows.length && (
        <div className="empty-state">
          <span>✉</span>
          <p>Aucun message</p>
        </div>
      )}
      {rows.map((message) => (
        <article
          className={`message ${message.is_read ? '' : 'unread'}`}
          key={message.id}
          onClick={() => messageService.mark(message.id, true).then(load)}
        >
          <b>{message.subject}</b>
          <small>
            {message.sender} · {new Date(message.created_at).toLocaleString()}
          </small>
          <p>{message.content}</p>
          {user.role === 'admin' && (
            <button
              className="danger"
              onClick={(event) => {
                event.stopPropagation();
                messageService.remove(message.id).then(load);
              }}
            >
              🗑
            </button>
          )}
        </article>
      ))}
      {compose && (
        <div className="modal">
          <form className="form-modal" onSubmit={send}>
            <h2>{t('newMessage')}</h2>
            <input name="subject" placeholder={t('subject')} required />
            <textarea name="content" placeholder={t('content')} required />
            <button>{t('send')}</button>
            <button type="button" className="ghost" onClick={() => setCompose(false)}>
              {t('close')}
            </button>
          </form>
        </div>
      )}
    </>
  );
}
