import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  listMyOrders, getOrderHistory, cancelPendingOrder, listCancelRequests, requestCancel, withdrawCancelRequest,
  listLateArrivals, requestLateArrival, withdrawLateArrival, getAccountStatus, listMyIncidents, sendFeedback, money,
} from '../api.js';

const ACTIVE = ['PENDING', 'CONFIRMED', 'PREPARING', 'READY'];
const DONE = ['COMPLETED', 'CANCELLED', 'EXPIRED', 'NOT_COLLECTED'];
// Nhãn tiếng Anh đúng như mẫu giao diện; title/aria-label giữ nghĩa tiếng Việt.
const PILL = {
  PENDING: ['Pending', 'warn', 'Chờ quầy xác nhận'], CONFIRMED: ['Confirmed', 'info', 'Quầy đã xác nhận'],
  PREPARING: ['Preparing', 'info', 'Đang chuẩn bị'], READY: ['Ready', 'ok', 'Sẵn sàng nhận'],
  COMPLETED: ['Completed', 'grey', 'Đã nhận'], CANCELLED: ['Cancelled', 'grey', 'Đã hủy'],
  EXPIRED: ['Expired', 'grey', 'Hết hạn xác nhận'], NOT_COLLECTED: ['NotCollected', 'bad', 'Không đến nhận'],
};
const STEPS = ['PENDING', 'CONFIRMED', 'PREPARING', 'READY'];
const PROMO = { AVAILABLE: ['Khả dụng', 'ok'], HELD: ['Đang giữ', 'warn'], USED: ['Đã dùng', 'grey'], EXPIRED: ['Hết hạn', 'grey'], UNDER_REVIEW: ['Chờ xem xét', 'warn'] };
const REQ_STATUS = { PENDING: 'đang chờ quầy xử lý', APPROVED: 'quầy đã duyệt', REJECTED: 'quầy đã từ chối', WITHDRAWN: 'bạn đã rút' };
const ACTOR = { CUSTOMER: 'Bạn', STAFF: 'Quầy', MANAGER: 'Quầy', ADMIN: 'Quản trị', SYSTEM: 'Hệ thống' };

const hhmm = (t) => t?.slice(0, 5);
const dm = (d) => `${d.slice(8, 10)}/${d.slice(5, 7)}`;
const hm = (iso) => iso?.slice(11, 16);
const sameDay = (iso) => iso === new Date().toLocaleDateString('sv-SE');   // yyyy-MM-dd theo giờ máy
const slotText = (o) => (sameDay(o.menuDate) ? hhmm(o.pickupStart) : `${dm(o.menuDate)} ${hhmm(o.pickupStart)}`);
// Lấy giờ máy khách làm giờ địa phương, đủ dùng vì người dùng ở Việt Nam; server là nơi kiểm tra cuối.
const toLocalInput = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);

function Pill({ status }) {
  const [text, tone, vi] = PILL[status] || [status, 'grey', status];
  return <span className={`pill ${tone}`} title={vi} aria-label={vi}>{text}</span>;
}

/** Hình 21: theo dõi đơn, yêu cầu hủy và phản hồi (UC6.1 đến UC6.5, UC1.9). */
export default function Orders({ goDiscover }) {
  const [tab, setTab] = useState('active');
  const [orders, setOrders] = useState(null);
  const [selId, setSelId] = useState(null);
  const [err, setErr] = useState('');

  const load = useCallback(async (keep) => {
    setErr('');
    try {
      const r = await listMyOrders(tab === 'active' ? ACTIVE : DONE);
      setOrders(r.content);
      setSelId((cur) => (keep && r.content.some((o) => o.id === cur) ? cur : r.content[0]?.id ?? null));
    } catch (x) { setErr(x.message); setOrders([]); }
  }, [tab]);
  useEffect(() => { setOrders(null); load(false); }, [load]);

  // Đơn Pending và Ready đổi trạng thái do phía quầy, nên làm mới nhẹ nhàng mỗi 20 giây.
  useEffect(() => {
    const t = setInterval(() => { if (!document.hidden) load(true); }, 20000);
    return () => clearInterval(t);
  }, [load]);

  const selected = orders?.find((o) => o.id === selId) || null;

  return (
    <main className="page">
      <h1 className="page-title">Đơn của tôi</h1>
      <div className="orders-grid">
        <div>
          <div className="tabs" role="tablist" aria-label="Nhóm đơn">
            <button role="tab" aria-selected={tab === 'active'} className={`chip ${tab === 'active' ? 'on' : ''}`} onClick={() => setTab('active')}>Đang xử lý</button>
            <button role="tab" aria-selected={tab === 'history'} className={`chip ${tab === 'history' ? 'on' : ''}`} onClick={() => setTab('history')}>Lịch sử</button>
          </div>
          {err && <p className="error" role="alert">{err}</p>}
          <section className="card flush" aria-label="Danh sách đơn">
            {orders === null ? <p className="muted pad">Đang tải đơn...</p>
              : orders.length === 0 ? (
                <div className="empty">
                  {tab === 'active' ? 'Bạn chưa có đơn nào đang xử lý.' : 'Chưa có đơn nào trong lịch sử.'}
                  {tab === 'active' && <div><button className="link" onClick={goDiscover}>Đi tới Khám phá</button></div>}
                </div>
              ) : (
                <div className="table-wrap"><table className="lines orders">
                  <thead><tr><th>Mã đơn · UC6.1</th><th>Quầy</th><th>Khung nhận</th><th>Phải trả</th><th>Trạng thái</th></tr></thead>
                  <tbody>
                    {orders.map((o) => (
                      <tr key={o.id} className={o.id === selId ? 'sel' : ''} onClick={() => setSelId(o.id)}>
                        <td><button className="row-btn" aria-pressed={o.id === selId} onClick={() => setSelId(o.id)}>#{o.orderCode}</button></td>
                        <td>{o.vendorName}</td>
                        <td>{slotText(o)}</td>
                        <td>{money(o.totalAmount)}</td>
                        <td><Pill status={o.status} />{o.status === 'READY' && o.pickupDeadline && <span className="deadline"> hạn {hm(o.pickupDeadline)}</span>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table></div>
              )}
          </section>
          <AccountCard />
        </div>

        {selected ? <Detail key={selected.id} order={selected} onChanged={() => load(true)} />
          : <section className="card"><p className="muted">Chọn một đơn để xem chi tiết.</p></section>}
      </div>
    </main>
  );
}

// ---------------------------------------------------------------- chi tiết đơn

function Detail({ order: o, onChanged }) {
  const [history, setHistory] = useState([]);
  const [cancels, setCancels] = useState([]);
  const [lates, setLates] = useState([]);
  const [reason, setReason] = useState('');
  const [lateOpen, setLateOpen] = useState(false);
  const [lateAt, setLateAt] = useState('');
  const [lateReason, setLateReason] = useState('');
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [okMsg, setOkMsg] = useState('');

  const loadExtra = useCallback(async () => {
    try {
      const [h, c, l] = await Promise.all([getOrderHistory(o.id), listCancelRequests(o.id), listLateArrivals(o.id)]);
      setHistory(h); setCancels(c); setLates(l);
    } catch (x) { setErr(x.message); }
  }, [o.id]);
  useEffect(() => { loadExtra(); }, [loadExtra, o.status]);

  async function run(fn, done) {
    setErr(''); setOkMsg(''); setBusy(true);
    try { await fn(); if (done) setOkMsg(done); await loadExtra(); onChanged(); } catch (x) { setErr(x.message); } finally { setBusy(false); }
  }

  const pendingCancel = cancels.find((c) => c.status === 'PENDING');
  const pendingLate = lates.find((l) => l.status === 'PENDING');
  const lastCancel = cancels.find((c) => c.status !== 'PENDING');
  const lastLate = lates.find((l) => l.status !== 'PENDING');
  const stepIdx = STEPS.indexOf(o.status);
  const canLate = ['CONFIRMED', 'PREPARING', 'READY'].includes(o.status);

  const lines = useMemo(() => o.items.map((i) => `${i.dishName} × ${i.quantity}`).join(', '), [o.items]);
  const minLate = toLocalInput(new Date(Date.now() + 60000));

  return (
    <section className="card detail-card" aria-labelledby="od">
      <div className="detail-head">
        <h2 id="od">Chi tiết #{o.orderCode}</h2>
        <Pill status={o.status} />
      </div>

      {stepIdx >= 0 || o.status === 'COMPLETED' ? (
        <ol className="stepper" aria-label="Tiến độ đơn">
          {STEPS.map((s, i) => {
            const state = o.status === 'COMPLETED' || i < stepIdx ? 'done' : i === stepIdx ? 'cur' : 'todo';
            return <li key={s} className={state} aria-current={state === 'cur' ? 'step' : undefined}><i /> <span>{PILL[s][0]}</span></li>;
          })}
        </ol>
      ) : <p className="note grey">{PILL[o.status]?.[2]}{o.closedReason ? `. ${o.closedReason}` : ''}</p>}

      <p className="muted">{lines} · {money(o.totalAmount)}{o.discountAmount > 0 && ` (đã giảm ${money(o.discountAmount)})`} · {o.paymentStatus === 'PAID' ? 'Đã thanh toán' : 'Chưa thanh toán'}</p>
      <p className="muted">Nhận tại {o.pickupLocation || o.vendorName}, {slotText(o)}–{hhmm(o.pickupEnd)}{o.status === 'READY' && o.pickupDeadline ? `. Hạn nhận ${hm(o.pickupDeadline)}.` : '.'}</p>

      {o.status === 'PENDING' && <PendingBlock order={o} busy={busy} confirmCancel={confirmCancel} setConfirmCancel={setConfirmCancel}
        onCancel={() => run(() => cancelPendingOrder(o.id))} />}

      {o.status === 'CONFIRMED' && !pendingCancel && (
        <>
          <p className="note warn">Quầy đã xác nhận nên không tự hủy được. Gửi yêu cầu để quầy phê duyệt (UC6.3).</p>
          <label className="field"><span>Lý do xin hủy</span>
            <input value={reason} maxLength={255} onChange={(e) => setReason(e.target.value)} placeholder="Ví dụ: có lịch đột xuất" />
          </label>
          <button className="btn danger block" disabled={busy || !reason.trim()}
            onClick={() => run(() => requestCancel(o.id, reason.trim()).then(() => setReason('')), 'Đã gửi yêu cầu hủy, chờ quầy xử lý.')}>Gửi yêu cầu hủy</button>
        </>
      )}

      {pendingCancel && (
        <div className="note info">
          Bạn đã gửi yêu cầu hủy lúc {hm(pendingCancel.createdAt)} ({REQ_STATUS.PENDING}). Lý do: {pendingCancel.reason}
          <div><button className="link" disabled={busy} onClick={() => run(() => withdrawCancelRequest(o.id, pendingCancel.id), 'Đã rút yêu cầu hủy.')}>Rút yêu cầu</button></div>
        </div>
      )}
      {!pendingCancel && lastCancel && ['CONFIRMED', 'PREPARING', 'READY'].includes(o.status) && (
        <p className="note grey">Yêu cầu hủy gần nhất: {REQ_STATUS[lastCancel.status]}{lastCancel.handleReason ? `. ${lastCancel.handleReason}` : ''}</p>
      )}
      {['PREPARING', 'READY'].includes(o.status) && <p className="note warn">Quầy đang làm món nên không hủy được. Nếu không kịp đến, hãy báo đến muộn.</p>}

      {pendingLate && (
        <div className="note info">
          Đã báo đến muộn (dự kiến {hm(pendingLate.proposedArrivalAt)}, {REQ_STATUS.PENDING}). Lý do: {pendingLate.reason}
          <div><button className="link" disabled={busy} onClick={() => run(() => withdrawLateArrival(o.id, pendingLate.id), 'Đã rút báo đến muộn.')}>Rút yêu cầu</button></div>
        </div>
      )}
      {!pendingLate && lastLate && canLate && (
        <p className="note grey">Báo đến muộn gần nhất: {REQ_STATUS[lastLate.status]}
          {lastLate.status === 'APPROVED' && lastLate.approvedDeadline ? `, hạn nhận mới ${hm(lastLate.approvedDeadline)}` : ''}
          {lastLate.handleReason ? `. ${lastLate.handleReason}` : ''}</p>
      )}

      {canLate && !pendingLate && (
        <>
          <button className="btn outline block" aria-expanded={lateOpen} onClick={() => setLateOpen((v) => !v)}>Báo đến muộn / việc đột xuất · UC6.4</button>
          {lateOpen && (
            <div className="late-form">
              <label className="field"><span>Giờ dự kiến đến</span>
                <input type="datetime-local" min={minLate} value={lateAt} onChange={(e) => setLateAt(e.target.value)} />
              </label>
              <label className="field"><span>Lý do</span>
                <input value={lateReason} maxLength={255} onChange={(e) => setLateReason(e.target.value)} />
              </label>
              <button className="btn primary block" disabled={busy || !lateAt || !lateReason.trim()}
                onClick={() => run(() => requestLateArrival(o.id, `${lateAt}:00`, lateReason.trim())
                  .then(() => { setLateOpen(false); setLateAt(''); setLateReason(''); }), 'Đã gửi báo đến muộn, chờ quầy xử lý.')}>Gửi báo đến muộn</button>
            </div>
          )}
        </>
      )}

      {err && <p className="error" role="alert">{err}</p>}
      {okMsg && <p className="ok" role="status">{okMsg}</p>}

      {history.length > 0 && (
        <details className="history">
          <summary>Tiến độ chi tiết</summary>
          <ul className="plain">
            {history.map((h, i) => (
              <li key={i}><span>{PILL[h.toStatus]?.[2] || h.toStatus}{h.reason ? ` · ${h.reason}` : ''}</span>
                <span className="muted">{ACTOR[h.actorType] || ''} · {h.createdAt.slice(8, 10)}/{h.createdAt.slice(5, 7)} {hm(h.createdAt)}</span></li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}

function PendingBlock({ order: o, busy, confirmCancel, setConfirmCancel, onCancel }) {
  const [left, setLeft] = useState(() => secondsLeft(o.pendingExpiresAt));
  useEffect(() => {
    const t = setInterval(() => setLeft(secondsLeft(o.pendingExpiresAt)), 1000);
    return () => clearInterval(t);
  }, [o.pendingExpiresAt]);
  return (
    <>
      <p className="note info">
        Đang chờ quầy xác nhận{o.pendingExpiresAt && (left > 0
          ? <>: còn <b>{Math.floor(left / 60)}:{String(left % 60).padStart(2, '0')}</b>, quá hạn đơn sẽ tự hết hạn và trả lại suất.</>
          : <>. Đã quá hạn, hệ thống sắp chuyển đơn sang Hết hạn.</>)}
      </p>
      <button className="btn danger block" disabled={busy} onClick={() => (confirmCancel ? onCancel() : setConfirmCancel(true))}>
        {confirmCancel ? 'Bấm lần nữa để hủy đơn' : 'Hủy đơn · UC6.2'}
      </button>
      {confirmCancel && <button className="link" onClick={() => setConfirmCancel(false)}>Giữ đơn</button>}
    </>
  );
}
const secondsLeft = (iso) => (iso ? Math.max(0, Math.floor((new Date(iso) - Date.now()) / 1000)) : 0);

// ---------------------------------------------------------------- tài khoản và phản hồi

function AccountCard() {
  const [acc, setAcc] = useState(undefined);        // undefined = đang tải, null = lỗi
  const [incidents, setIncidents] = useState([]);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [okMsg, setOkMsg] = useState('');

  const load = useCallback(async () => {
    try {
      const [a, inc] = await Promise.all([getAccountStatus(), listMyIncidents().catch(() => ({ content: [] }))]);
      setAcc(a); setIncidents(inc.content);
    } catch { setAcc(null); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const active = incidents.filter((i) => ['PENDING_REVIEW', 'CONFIRMED'].includes(i.status));
  const target = active.find((i) => !i.disputed) || null;
  const latest = active[0] || null;
  const n = acc?.violationsLast30Days ?? 0;
  const level = n >= 3 ? 'chuyển quản trị viên xem xét' : n === 2 ? 'mức 2: cảnh báo' : 'mức 1: nhắc nhở';

  async function submit(e) {
    e.preventDefault();
    setErr(''); setOkMsg(''); setBusy(true);
    try { await sendFeedback(target.id, text.trim()); setText(''); setOkMsg('Đã gửi phản hồi. Quầy sẽ xem xét.'); await load(); }
    catch (x) { setErr(x.message); } finally { setBusy(false); }
  }

  if (acc === undefined) return <section className="card"><p className="muted">Đang tải tài khoản...</p></section>;
  if (acc === null) return <section className="card"><h3 className="step-title">Tài khoản · UC1.9, UC6.5</h3><p className="note warn">Chưa tải được thông tin tài khoản. Hãy thử lại sau.</p></section>;

  const [pt, pc] = acc.promotion ? (PROMO[acc.promotion.status] || [acc.promotion.status, 'grey']) : ['Không có', 'grey'];
  const verified = acc.emailVerified && acc.schoolEmail;
  return (
    <section className="card account" aria-labelledby="ac">
      <h3 id="ac" className="step-title">Tài khoản · UC1.9, UC6.5</h3>
      <div className="acc-row">
        <span>Email trường <span className={`pill ${verified ? 'ok' : 'warn'}`}>{verified ? 'Đã xác minh' : 'Chưa xác minh'}</span></span>
        <span>Ưu đãi <span className={`pill ${pc}`}>{pt}</span></span>
        {acc.historyAvailable
          ? <span>Hạn chế đặt trước <span className={`pill ${acc.restriction ? 'red' : 'ok'}`}>{acc.restriction ? `Đến ${acc.restriction.endsAt.slice(8, 10)}/${acc.restriction.endsAt.slice(5, 7)}` : 'Không'}</span></span>
          : <span>Hạn chế đặt trước <span className="pill grey">Chưa tải được</span></span>}
      </div>
      {acc.historyAvailable
        ? <p className="muted">Cảnh báo 30 ngày: {n}{latest && n > 0 ? ` · ${dm(latest.occurredAt.slice(0, 10))} đơn #${latest.orderCode} không nhận món (${level}).` : '.'}</p>
        : <p className="note warn">Chưa tải được lịch sử vi phạm, nên chưa thể khẳng định bạn không có cảnh báo.</p>}
      {acc.restriction && <p className="note danger">Bạn đang bị hạn chế đặt trước đến {acc.restriction.endsAt.slice(8, 10)}/{acc.restriction.endsAt.slice(5, 7)}: {acc.restriction.reason}. Vẫn xem được đơn và gửi phản hồi.</p>}

      {target ? (
        <form className="feedback" onSubmit={submit}>
          <input value={text} maxLength={1000} onChange={(e) => setText(e.target.value)}
            placeholder={`Phản hồi cho đơn #${target.orderCode}...`} aria-label={`Phản hồi cho đơn ${target.orderCode}`} />
          <button className="btn outline" disabled={busy || !text.trim()}>Gửi phản hồi</button>
        </form>
      ) : latest ? <p className="muted">Phản hồi cho đơn #{latest.orderCode} đang chờ xử lý, bạn không cần gửi thêm.</p>
        : <p className="muted">Khi có đơn không nhận món bị ghi nhận, bạn gửi phản hồi tại đây.</p>}
      {err && <p className="error" role="alert">{err}</p>}
      {okMsg && <p className="ok" role="status">{okMsg}</p>}
    </section>
  );
}
