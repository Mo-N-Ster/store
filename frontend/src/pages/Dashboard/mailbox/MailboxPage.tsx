import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { User } from '../../../types';
import { messageService } from '../../../services/messageService';
import { employeeService } from '../../../services/employeeService';
import { dashboardService } from '../../../services/dashboardService';
import { SubTabs } from '../../../components/UI/SubTabs';
export function MailboxPage({ user, notify }: { user: User; notify: (x: string) => void }) {
  const { t } = useTranslation();
  const [rows, setRows] = useState<any[]>([]);
  const [compose, setCompose] = useState(false);
  const [users, setUsers] = useState<User[]>([]);
  const [tab, setTab] = useState('mailbox');
  const [alerts, setAlerts] = useState<any[]>([]);
  const [emailLogs, setEmailLogs] = useState<any[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
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
    void loadArchives();
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
  const archiveRows = tab === 'alerts' ? alerts : emailLogs;
  const deleteArchive = async () => {
    if (!selected.size) return;
    if (tab === 'alerts') await dashboardService.deleteNotifications([...selected]);
    else await dashboardService.deleteEmailReportLogs([...selected]);
    setSelected(new Set());
    await loadArchives();
  };
  return (
    <>
      <div className="titlebar">
        <div>
          <span className="eyebrow">{t('communication')}</span>
          <h1>{t('mailbox')}</h1>
        </div>
        {tab === 'mailbox' && <button onClick={() => setCompose(true)}>+ {t('newMessage')}</button>}
      </div>
      <SubTabs
        ariaLabel={t('mailboxSections')}
        active={tab}
        onChange={(value) => {
          setTab(value);
          setSelected(new Set());
        }}
        tabs={[
          { id: 'mailbox', label: t('mailingBox'), count: rows.length },
          { id: 'alerts', label: t('alerts'), count: alerts.length },
          ...(user.role !== 'employee'
            ? [{ id: 'emailReports', label: t('emailReports'), count: emailLogs.length }]
            : []),
        ]}
      />
      {tab === 'mailbox' && !rows.length && (
        <div className="empty-state">
          <span>✉</span>
          <p>{t('noMessages')}</p>
        </div>
      )}
      {tab === 'mailbox' &&
        rows.map((message) => (
          <article
            className={`message ${message.is_read ? '' : 'unread'}`}
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
            <button
              className="danger"
              onClick={(event) => {
                event.stopPropagation();
                messageService.remove(message.id, user.id).then(load);
              }}
            >
              🗑
            </button>
          </article>
        ))}
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
                        <td>{t(row.status)}</td>
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
