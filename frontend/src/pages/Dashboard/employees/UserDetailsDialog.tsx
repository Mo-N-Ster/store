import { useTranslation } from 'react-i18next';
import { Modal } from '../../../design-system';
import type { User } from '../../../types';

export function UserDetailsDialog({ user, onClose }: { user: User; onClose: () => void }) {
  const { t } = useTranslation();
  const fullName = `${user.firstName || user.first_name || ''} ${user.lastName || user.last_name || ''}`.trim();
  const fields = [[t('fullName'), fullName || '—'], [t('usernameOnly'), user.username], [t('email'), user.email || '—'], [t('phone'), user.phone || '—'], [t('accountRole'), t(user.role)], [t('accountStatus'), user.active ? t('active') : t('inactive')], [t('hireDate'), user.hireDate ? new Date(user.hireDate).toLocaleDateString() : '—']];
  return <Modal title={fullName || user.username} description={t('employeeAccountDistinction')} closeLabel={t('close')} onClose={onClose}>{user.photo && <img className="employee-avatar" src={user.photo} alt={t('employeePhoto')} />}<span className={`ops-status ops-status--${user.active ? 'active' : 'inactive'}`}>{t(user.active ? 'active' : 'inactive')}</span><dl className="ops-detail-list">{fields.map(([label, value]) => <div key={String(label)}><dt>{label}</dt><dd>{value}</dd></div>)}</dl></Modal>;
}
