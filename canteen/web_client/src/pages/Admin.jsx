import { useState } from 'react';
import AdminIncidents from './AdminIncidents.jsx';
import { AdminVendors, AdminCampaigns, AdminNotifications } from './AdminPages.jsx';

// Thanh bên theo Hình 25. "Người dùng" chưa có API quản trị nên khóa và ghi rõ.
const MENU = [
  { key: 'vendors', label: 'Điểm bán' },
  { key: 'campaigns', label: 'Chiến dịch ưu đãi' },
  { key: 'incidents', label: 'Sự việc & hạn chế' },
  { key: 'notifications', label: 'Thông báo email' },
  { key: 'users', label: 'Người dùng', soon: true },
];

/** Giao diện quản trị (Hình 25): UC10.3 xem xét sự việc, UC10.5 hạn chế đặt trước, UC8.1 chiến dịch, quầy. */
export default function Admin({ profile, onLogout }) {
  const [view, setView] = useState('incidents');
  return (
    <div className="counter">
      <aside className="side" aria-label="Menu quản trị">
        <h1 className="side-brand">🍽 Canteen · Quản trị</h1>
        <p className="side-sub">{' '}</p>
        <nav>
          {MENU.map((m) => (m.soon
            ? <span key={m.key} className="side-item soon" aria-disabled="true" title="Server chưa có API quản lý người dùng">{m.label}</span>
            : <button key={m.key} className={`side-item ${m.key === view ? 'on' : ''}`} aria-current={m.key === view ? 'page' : undefined}
                onClick={() => { setView(m.key); window.scrollTo({ top: 0 }); }}>{m.label}</button>))}
        </nav>
        <div className="side-foot"><span>{profile.fullName}</span><button className="link light" onClick={onLogout}>Đăng xuất</button></div>
      </aside>
      <main className="board-wrap">
        {view === 'vendors' && <AdminVendors />}
        {view === 'campaigns' && <AdminCampaigns />}
        {view === 'incidents' && <AdminIncidents />}
        {view === 'notifications' && <AdminNotifications />}
      </main>
    </div>
  );
}
