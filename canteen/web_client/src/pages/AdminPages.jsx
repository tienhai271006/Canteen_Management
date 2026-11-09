import { useCallback, useEffect, useState } from 'react';
import {
  adminListVendors, adminVendorAction, adminListCampaigns, adminSaveCampaign, adminCampaignToggle,
  listNotifications, markNotificationRead, markAllNotificationsRead, money,
} from '../api.js';

const dmHm = (iso) => (iso ? `${iso.slice(8, 10)}/${iso.slice(5, 7)} ${iso.slice(11, 16)}` : '');
const VSTATUS = { PENDING: ['warn', 'Chờ duyệt'], ACTIVE: ['ok', 'Đang nhận đơn'], SUSPENDED: ['bad', 'Tạm ngừng'] };

/** Điểm bán: duyệt, tạm ngừng, mở lại (UC10.1, UC10.2). */
export function AdminVendors() {
  const [list, setList] = useState(null);
  const [err, setErr] = useState('');
  const [susp, setSusp] = useState(null);     // { id, reason }
  const load = useCallback(() => adminListVendors().then((r) => { setList(r.content); setErr(''); }).catch((x) => { setErr(x.message); setList([]); }), []);
  useEffect(() => { load(); }, [load]);
  const act = async (id, verb, body) => { try { await adminVendorAction(id, verb, body); setSusp(null); await load(); } catch (x) { setErr(x.message); } };

  return (
    <>
      <div className="board-head"><h2>Điểm bán</h2></div>
      {err && <p className="error" role="alert">{err}</p>}
      <div className="card flush table-wrap">
        <table className="lines adm">
          <thead><tr><th>Quầy</th><th>Khu vực</th><th>Nhận món tại</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
          <tbody>
            {list === null && <tr><td colSpan="5" className="muted">Đang tải...</td></tr>}
            {list?.length === 0 && <tr><td colSpan="5" className="muted">Chưa có quầy nào.</td></tr>}
            {list?.map((v) => (
              <tr key={v.id}>
                <td><b>{v.name}</b></td><td>{v.areaName}</td><td>{v.pickupLocation || '—'}</td>
                <td><span className={`pill ${VSTATUS[v.status][0]}`}>{VSTATUS[v.status][1]}</span>{v.statusReason && <div className="muted">{v.statusReason}</div>}</td>
                <td>
                  {v.status === 'PENDING' && <button className="btn outline sm" onClick={() => act(v.id, 'approve')}>Duyệt</button>}
                  {v.status === 'SUSPENDED' && <button className="btn outline sm" onClick={() => act(v.id, 'reactivate')}>Mở lại</button>}
                  {v.status === 'ACTIVE' && (susp?.id === v.id
                    ? <span className="adm-inline"><input aria-label="Lý do tạm ngừng" placeholder="Lý do (bắt buộc)" value={susp.reason} onChange={(e) => setSusp({ id: v.id, reason: e.target.value })} />
                      <button className="btn danger sm" disabled={!susp.reason.trim()} onClick={() => act(v.id, 'suspend', { reason: susp.reason.trim() })}>Xác nhận</button></span>
                    : <button className="btn outline sm" onClick={() => setSusp({ id: v.id, reason: '' })}>Tạm ngừng</button>)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="muted">Tạm ngừng chỉ chặn đơn mới, đơn đã đặt vẫn được xử lý.</p>
    </>
  );
}

const toLocal = (iso) => (iso || '').slice(0, 16);
const blank = () => {
  const d = new Date(Date.now() - new Date().getTimezoneOffset() * 60000);
  const e = new Date(d.getTime() + 90 * 864e5);
  return { id: null, name: '', discountPercent: '50', maxDiscountAmount: '30000', minOrderAmount: '25000', entitlementValidDays: '7', startsAt: d.toISOString().slice(0, 16), endsAt: e.toISOString().slice(0, 16) };
};

/** Chiến dịch ưu đãi đơn đầu (UC8.1). */
export function AdminCampaigns() {
  const [list, setList] = useState(null);
  const [f, setF] = useState(null);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const load = useCallback(() => adminListCampaigns().then((r) => { setList(r.content); setErr(''); }).catch((x) => { setErr(x.message); setList([]); }), []);
  useEffect(() => { load(); }, [load]);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const edit = (c) => setF({ id: c.id, name: c.name, discountPercent: String(c.discountPercent), maxDiscountAmount: String(c.maxDiscountAmount), minOrderAmount: String(c.minOrderAmount), entitlementValidDays: String(c.entitlementValidDays), startsAt: toLocal(c.startsAt), endsAt: toLocal(c.endsAt) });

  async function save(e) {
    e.preventDefault(); setErr(''); setMsg('');
    try {
      await adminSaveCampaign(f.id, {
        name: f.name.trim(), discountPercent: +f.discountPercent, maxDiscountAmount: +f.maxDiscountAmount, minOrderAmount: +f.minOrderAmount,
        entitlementValidDays: +f.entitlementValidDays, startsAt: `${f.startsAt}:00`, endsAt: `${f.endsAt}:00`,
      });
      setMsg('Đã lưu chiến dịch.'); setF(null); await load();
    } catch (x) { setErr(x.message); }
  }
  const toggle = async (c) => { try { await adminCampaignToggle(c.id, !c.active); await load(); } catch (x) { setErr(x.message); } };

  return (
    <>
      <div className="board-head"><h2>Chiến dịch ưu đãi</h2><button className="btn primary" onClick={() => { setF(blank()); setMsg(''); }}>+ Tạo chiến dịch</button></div>
      {err && <p className="error" role="alert">{err}</p>}
      {msg && <p className="ok" role="status">{msg}</p>}
      <div className="card flush table-wrap">
        <table className="lines adm">
          <thead><tr><th>Tên</th><th>Mức giảm</th><th>Đơn tối thiểu</th><th>Hạn dùng</th><th>Thời gian</th><th>Trạng thái</th><th /></tr></thead>
          <tbody>
            {list === null && <tr><td colSpan="7" className="muted">Đang tải...</td></tr>}
            {list?.length === 0 && <tr><td colSpan="7" className="muted">Chưa có chiến dịch. Tạo chiến dịch mặc định: 50%, tối đa 30.000đ, đơn từ 25.000đ, hạn 7 ngày.</td></tr>}
            {list?.map((c) => (
              <tr key={c.id}>
                <td><b>{c.name}</b></td><td>{c.discountPercent}%, tối đa {money(c.maxDiscountAmount)}</td><td>{money(c.minOrderAmount)}</td>
                <td>{c.entitlementValidDays} ngày</td><td>{dmHm(c.startsAt)} → {dmHm(c.endsAt)}</td>
                <td><span className={`pill ${c.active ? 'ok' : 'grey'}`}>{c.active ? 'Đang chạy' : 'Tắt'}</span></td>
                <td className="adm-row"><button className="btn outline sm" onClick={() => edit(c)}>Sửa</button><button className="btn outline sm" onClick={() => toggle(c)}>{c.active ? 'Tắt' : 'Bật'}</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {f && (
        <form className="card" onSubmit={save}>
          <h3 className="step-title">{f.id ? 'Sửa chiến dịch' : 'Tạo chiến dịch'}</h3>
          <label className="field"><span>Tên chiến dịch</span><input required maxLength={120} value={f.name} onChange={set('name')} /></label>
          <div className="adm-grid">
            <label className="field"><span>Giảm (%)</span><input required inputMode="numeric" value={f.discountPercent} onChange={set('discountPercent')} /></label>
            <label className="field"><span>Giảm tối đa (đ)</span><input required inputMode="numeric" value={f.maxDiscountAmount} onChange={set('maxDiscountAmount')} /></label>
            <label className="field"><span>Đơn tối thiểu (đ)</span><input required inputMode="numeric" value={f.minOrderAmount} onChange={set('minOrderAmount')} /></label>
            <label className="field"><span>Hạn dùng quyền (ngày)</span><input required inputMode="numeric" value={f.entitlementValidDays} onChange={set('entitlementValidDays')} /></label>
            <label className="field"><span>Bắt đầu</span><input required type="datetime-local" value={f.startsAt} onChange={set('startsAt')} /></label>
            <label className="field"><span>Kết thúc</span><input required type="datetime-local" value={f.endsAt} onChange={set('endsAt')} /></label>
          </div>
          <div className="adm-row"><button className="btn primary">Lưu</button><button type="button" className="btn outline" onClick={() => setF(null)}>Hủy</button></div>
        </form>
      )}
    </>
  );
}

/** Thông báo trong ứng dụng của quản trị viên (INCIDENT_ESCALATED, FEEDBACK_RECEIVED...). Server chưa có nhật ký gửi email cho admin. */
export function AdminNotifications() {
  const [list, setList] = useState(null);
  const [err, setErr] = useState('');
  const load = useCallback(() => listNotifications().then((r) => { setList(r.content); setErr(''); }).catch((x) => { setErr(x.message); setList([]); }), []);
  useEffect(() => { load(); }, [load]);
  const read = async (n) => { if (!n.readAt) { try { await markNotificationRead(n.id); await load(); } catch (x) { setErr(x.message); } } };
  const unread = (list || []).filter((n) => !n.readAt).length;
  return (
    <>
      <div className="board-head"><h2>Thông báo email</h2>
        <button className="btn outline" disabled={!unread} onClick={() => markAllNotificationsRead().then(load).catch((x) => setErr(x.message))}>Đánh dấu đã đọc ({unread})</button></div>
      <div className="note info">Đang hiển thị thông báo trong ứng dụng gửi tới tài khoản quản trị. Server chưa có API xem nhật ký gửi email; trạng thái email không đồng nghĩa người dùng đã đọc.</div>
      {err && <p className="error" role="alert">{err}</p>}
      <div className="card flush">
        <ul className="plain notif">
          {list === null && <li className="muted">Đang tải...</li>}
          {list?.length === 0 && <li className="muted">Chưa có thông báo.</li>}
          {list?.map((n) => (
            <li key={n.id} className={n.readAt ? '' : 'unread'}>
              <button className="row-btn" onClick={() => read(n)}><b>{n.title}</b><span className="muted"> · {dmHm(n.createdAt)}</span><br /><span>{n.body}</span></button>
              {!n.readAt && <span className="pill warn">Mới</span>}
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
