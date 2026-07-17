import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../../types';
import { messageService } from '../../../services/messageService';
import { employeeService } from '../../../services/employeeService';
export function MailboxPage({ user, notify }: { user: User; notify: (x: string) => void }) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<any[]>([]);
  const [compose, setCompose] = useState(false);
  const [users, setUsers] = useState<User[]>([]);
  const load = () => messageService.list({ userId: user.id, role: user.role }).then(setRows);
  useEffect(() => {
    void load();
    void employeeService
      .list()
      .then((items) => setUsers(items.filter((item: User) => item.active)));
    const timer = window.setInterval(load, 15000);
    return () => window.clearInterval(timer);
  }, []);
  const send = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = Object.fromEntries(new FormData(event.currentTarget));
    const destination = String(form.destination);
    const recipientId = destination.startsWith('user:') ? Number(destination.slice(5)) : undefined;
    await messageService.send({
      senderId: user.id,
      recipientType: recipientId ? 'user' : 'all',
      recipientId,
      subject: form.subject,
      content: form.content,
    });
    setCompose(false);
    await load();
    notify(t('messageSent'));
  };
  return (
    <>
      <div className="titlebar">
        <div>
          <span className="eyebrow">{t('communication')}</span>
          <h1>{t('mailbox')}</h1>
        </div>
        <button onClick={() => setCompose(true)}>+ {t('newMessage')}</button>
      </div>
      {!rows.length && (
        <div className="empty-state">
          <span>✉</span>
          <p>{t('noMessages')}</p>
        </div>
      )}
      {rows.map((message) => (
        <article
          className={`message ${message.is_read ? '' : 'unread'}`}
          key={message.id}
          onClick={() => !message.sent && messageService.mark(message.id, user.id, true).then(load)}
        >
          <b>{message.subject}</b>
          <small>
            {message.sent
              ? t('sentTo', { recipient: message.recipient || t('everyone') })
              : t('receivedFrom', { sender: message.sender })}
            {' · '}
            {new Date(message.created_at).toLocaleString()}
          </small>
          <p>{message.content}</p>
          {Boolean(message.sent) && (
            <button
              className="danger"
              onClick={(event) => {
                event.stopPropagation();
                messageService.remove(message.id, user.id).then(load);
              }}
            >
              🗑
            </button>
          )}
        </article>
      ))}
      {compose && (
        <div className="modal" onMouseDown={() => setCompose(false)}>
          <form
            className="form-modal"
            onSubmit={send}
            onMouseDown={(event) => event.stopPropagation()}
          >
            <h2>{t('newMessage')}</h2>
            <label>
              {t('recipient')}
              <select name="destination" required defaultValue="all">
                <option value="all">{t('everyone')}</option>
                {users
                  .filter((recipient) => recipient.id !== user.id)
                  .map((recipient) => (
                    <option key={recipient.id} value={`user:${recipient.id}`}>
                      {recipient.firstName || recipient.first_name}{' '}
                      {recipient.lastName || recipient.last_name} — {recipient.username}
                    </option>
                  ))}
              </select>
            </label>
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
