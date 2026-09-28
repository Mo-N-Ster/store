import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../../types';
import { messageService } from '../../../services/messageService';
import { dashboardService } from '../../../services/dashboardService';
import { SubTabs } from '../../../components/UI/SubTabs';
import { ModalBackdrop } from '../../../components/UI/ModalBackdrop';
import { Button, IconButton } from '../../../design-system';
import { Plus, Trash2 } from 'lucide-react';
export function MailboxPage({
  user,
  notify,
  variant = 'management',
}: {
  user: User;
  notify: (x: string) => void;
  variant?: 'chat' | 'management';
}) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<any[]>([]);
  const [compose, setCompose] = useState(false);
  const [users, setUsers] = useState<User[]>([]);
  const [tab, setTab] = useState('mailbox');
  const [alerts, setAlerts] = useState<any[]>([]);
  const [emailLogs, setEmailLogs] = useState<any[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [sending, setSending] = useState(false);
  const load = () => messageService.list({ userId: user.id, role: user.role }).then(setRows);
  const loadArchives = () =>
    Promise.all([dashboardService.notifications(), dashboardService.emailReportLogs()]).then(
      ([notificationRows, logs]) => {
        setAlerts(notificationRows);
        setEmailLogs(logs);
      },
    );
  useEffect(() => {
    void load();
    if (variant === 'management') void loadArchives();
    void messageService
      .recipients()
      .then((items) => setUsers(items.filter((item: User) => item.active)));
    const timer = window.setInterval(load, 15000);
    return () => window.clearInterval(timer);
  }, []);
  const send = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (sending) return;
    setSending(true);
    const form = Object.fromEntries(new FormData(event.currentTarget));
    const destination = String(form.destination);
    const recipientId = destination.startsWith('user:') ? Number(destination.slice(5)) : undefined;
    try {
      await messageService.send({
        senderId: user.id,
        recipientType: recipientId ? 'user' : 'all',
        recipientId,
        subject: form.subject,
        content: form.content,
        requestId: crypto.randomUUID(),
      });
      setCompose(false);
      await load();
      notify(t('messageSent'));
    } catch {
      notify(t('messageSendFailed'));
    } finally {
      setSending(false);
    }
  };
  const archiveRows = tab === 'alerts' ? alerts : emailLogs;
  const deleteArchive = async () => {
    if (!selected.size) return;
    if (tab === 'alerts') await dashboardService.deleteNotifications([...selected]);
    else await dashboardService.deleteEmailReportLogs([...selected]);
    setSelected(new Set());
    await loadArchives();
  };
  return (
    <section className={variant === 'chat' ? 'quick-chat' : 'mailbox-management'}>
      <div className="titlebar">
        <div>
          <span className="eyebrow">{t('communication')}</span>
          <h1>{variant === 'chat' ? t('chat') : t('mailboxManagement')}</h1>
        </div>
        {variant === 'chat' && tab === 'mailbox' && <Button icon={Plus} onClick={() => setCompose(true)}>{t('newMessage')}</Button>}
      </div>
      {variant === 'management' && <SubTabs
        ariaLabel={t('mailboxSections')}
        active={tab}
        onChange={(value) => {
          setTab(value);
          setSelected(new Set());
        }}
        tabs={[
          { id: 'mailbox', label: t('chatManagement'), count: rows.length },
          { id: 'alerts', label: t('alertManagement'), count: alerts.length },
          ...(user.role !== 'employee'
            ? [{ id: 'emailReports', label: t('emailManagement'), count: emailLogs.length }]
            : []),
        ]}
      />}
      {tab === 'mailbox' && !rows.length && (
        <div className="empty-state">
          <span>✉</span>
          <p>{t('noMessages')}</p>
        </div>
      )}
      {tab === 'mailbox' && <div className="chat-conversation" role="log" aria-label={t('chat')}>
        {rows.map((message) => (
          <article
            className={`message ${message.sent ? 'message--sent' : 'message--received'} ${message.is_read ? '' : 'unread'}`}
            key={message.id}
            onClick={() =>
              !message.sent && messageService.mark(message.id, user.id, true).then(load)
            }
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
            {variant === 'management' && <IconButton
              className="danger"
              icon={Trash2}
              label={t('deleteMessage')}
              onClick={(event) => {
                event.stopPropagation();
                messageService.remove(message.id, user.id).then(load);
              }}
            />}
          </article>
        ))}
      </div>}
      {tab !== 'mailbox' && (
        <>
          <div className="archive-actions">
            <label className="select-all">
              <input
                type="checkbox"
                checked={archiveRows.length > 0 && selected.size === archiveRows.length}
                onChange={() =>
                  setSelected(
                    selected.size === archiveRows.length
                      ? new Set()
                      : new Set(archiveRows.map((row) => row.id)),
                  )
                }
              />
              {t('selectAll')}
            </label>
            <button className="danger" disabled={!selected.size} onClick={deleteArchive}>
              {t('deleteSelected')}
            </button>
            {tab === 'emailReports' && (
              <button
                className="secondary"
                onClick={async () => {
                  const result = await dashboardService.retryEmailQueue();
                  await loadArchives();
                  notify(String(t('emailRetryResult', result)));
                }}
              >
                {t('retryPendingEmails')}
              </button>
            )}
          </div>
          <div className="table-shell">
            <table>
              <thead>
                <tr>
                  <th></th>
                  <th>{t('date')}</th>
                  {tab === 'alerts' ? (
                    <>
                      <th>{t('product')}</th>
                      <th>{t('content')}</th>
                      <th>{t('stock')}</th>
                    </>
                  ) : (
                    <>
                      <th>{t('recipientEmail')}</th>
                      <th>{t('subject')}</th>
                      <th>{t('file')}</th>
                      <th>{t('status')}</th>
                      <th>{t('attempts')}</th>
                    </>
                  )}
                </tr>
              </thead>
              <tbody>
                {archiveRows.map((row) => (
                  <tr key={row.id}>
                    <td>
                      <input
                        type="checkbox"
                        checked={selected.has(row.id)}
                        onChange={() =>
                          setSelected((current) => {
                            const next = new Set(current);
                            if (next.has(row.id)) next.delete(row.id);
                            else next.add(row.id);
                            return next;
                          })
                        }
                      />
                    </td>
                    <td>{new Date(row.created_at).toLocaleString()}</td>
                    {tab === 'alerts' ? (
                      <>
                        <td>{row.productName}</td>
                        <td>{row.message}</td>
                        <td>{row.currentStock}</td>
                      </>
                    ) : (
                      <>
                        <td>{row.recipient}</td>
                        <td>{row.subject}</td>
                        <td>{row.filename}</td>
                        <td>
                          <span className={`status-pill ${row.status}`} title={row.last_error || ''}>
                            {t(row.status)}
                          </span>
                          {row.next_attempt_at && row.status === 'pending' && (
                            <small>{t('nextAttempt')}: {new Date(row.next_attempt_at).toLocaleString()}</small>
                          )}
                        </td>
                        <td>{row.attempts}</td>
                      </>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
      {compose && (
        <ModalBackdrop className="chat-compose-overlay" dismissible={!sending} onClose={() => setCompose(false)}>
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
            <label>{t('subject')}<input name="subject" placeholder={t('subject')} required /></label>
            <label>{t('content')}<textarea name="content" placeholder={t('content')} required /></label>
            <Button disabled={sending}>{sending ? t('sending') : t('send')}</Button>
            <Button type="button" disabled={sending} variant="ghost" onClick={() => setCompose(false)}>
              {t('close')}
            </Button>
          </form>
        </ModalBackdrop>
      )}
    </section>
  );
}
