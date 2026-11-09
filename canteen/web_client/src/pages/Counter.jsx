import { useEffect, useState } from 'react';
import { listCounterVendors } from '../api.js';
import CounterBoard from './CounterBoard.jsx';
import ManagerMenu from './ManagerMenu.jsx';
import ManagerStats from './ManagerStats.jsx';

// Các mục của thanh bên theo mẫu Hình 22. Mục chưa có màn hình thì khóa và ghi rõ, không để bấm vào trang trống.
const MENU = [
  { key: 'orders', label: 'Đơn hàng' },
  { key: 'menu', label: 'Thực đơn & hạn mức', managerOnly: true },
  { key: 'requests', label: 'Yêu cầu hủy / hỗ trợ' },
  { key: 'stats', label: 'Thống kê', managerOnly: true },
  { key: 'staff', label: 'Nhân sự quầy', soon: true },
];

/** Giao diện quầy (Hình 22): thanh bên và bảng xử lý đơn cho nhân viên và quản lý quầy. */
export default function Counter({ profile, onLogout }) {
  const [vendors, setVendors] = useState(null);
  const [vendorId, setVendorId] = useState(null);
  const [err, setErr] = useState('');
  const [focus, setFocus] = useState(0);              // đổi giá trị để cuộn tới khung yêu cầu
  const [view, setView] = useState('orders');
  const isManager = profile.role === 'VENDOR_MANAGER';

  useEffect(() => {
    listCounterVendors()
      .then((v) => { setVendors(v); setVendorId(v[0]?.id ?? null); })
      .catch((x) => { setErr(x.message); setVendors([]); });
  }, []);

  const vendor = vendors?.find((v) => v.id === vendorId) || null;

  return (
    <div className="counter">
      <aside className="side" aria-label="Menu quầy">
        <h1 className="side-brand">🍽 Canteen · Quầy</h1>
        {vendors && vendors.length > 1
          ? <select className="side-vendor" aria-label="Chọn quầy" value={vendorId ?? ''} onChange={(e) => setVendorId(Number(e.target.value))}>
            {vendors.map((v) => <option key={v.id} value={v.id}>{v.name}</option>)}
          </select>
          : <p className="side-sub">{vendor ? `Quầy ${vendor.name}` : ' '}</p>}
        <nav>
          {MENU.map((m) => {
            const locked = m.soon || (m.managerOnly && !isManager);
            const current = m.key === view;
            if (locked) {
              return <span key={m.key} className="side-item soon" aria-disabled="true"
                title={m.managerOnly ? 'Chỉ quản lý quầy dùng được' : 'Màn hình này làm ở bước sau'}>{m.label}</span>;
            }
            return (
              <button key={m.key} className={`side-item ${current ? 'on' : ''}`} aria-current={current ? 'page' : undefined}
                onClick={() => {
                  if (m.key === 'requests') { setView('orders'); setFocus((n) => n + 1); } else { setView(m.key); window.scrollTo({ top: 0, behavior: 'smooth' }); }
                }}>{m.label}</button>
            );
          })}
        </nav>
        <div className="side-foot">
          <span>{profile.fullName}</span>
          <button className="link light" onClick={onLogout}>Đăng xuất</button>
        </div>
      </aside>

      <main className="board-wrap">
        {vendors === null && <p className="muted">Đang tải quầy...</p>}
        {err && <p className="error" role="alert">{err}</p>}
        {vendors && vendors.length === 0 && !err && (
          <section className="card"><h2>Chưa được gán vào quầy nào</h2>
            <p className="muted">Hãy nhờ quản lý quầy hoặc quản trị viên thêm bạn vào quầy, rồi tải lại trang.</p></section>
        )}
        {vendor && view === 'orders' && <CounterBoard key={vendor.id} vendor={vendor} focusRequests={focus} />}
        {vendor && view === 'menu' && isManager && <ManagerMenu key={vendor.id} vendor={vendor} />}
        {vendor && view === 'stats' && isManager && <ManagerStats key={vendor.id} vendor={vendor} />}
      </main>
    </div>
  );
}
