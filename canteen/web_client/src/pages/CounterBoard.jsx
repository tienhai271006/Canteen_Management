import { useCallback, useEffect, useRef, useState } from 'react';
import {
  listCounterOrders, listOverdueOrders, counterAction, rejectOrder, completeOrder, markNotCollected,
  listCounterCancelRequests, decideCancel, listCounterLateArrivals, decideLate, money,
} from '../api.js';

const COLUMNS = [['PENDING', 'Pending', 'warn'], ['CONFIRMED', 'Confirmed', 'info'], ['PREPARING', 'Preparing', 'orange'], ['READY', 'Ready', 'ok']];
const hhmm = (t) => t?.slice(0, 5);
const hm = (iso) => iso?.slice(11, 16);
const clock = (d) => d.toTimeString().slice(0, 5);
const secondsLeft = (iso, now) => (iso ? Math.max(0, Math.floor((new Date(iso) - now) / 1000)) : 0);
const mmss = (s) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
const lineText = (o) => o.items.map((i) => `${i.dishName} × ${i.quantity}`).join(', ');

/** Hình 22: bảng đơn theo trạng thái cho quầy (UC7.1 đến UC7.8). */
export default function CounterBoard({ vendor, focusRequests }) {
  const [orders, setOrders] = useState(null);
  const [overdue, setOverdue] = useState(new Set());
  const [cancels, setCancels] = useState([]);
  const [lates, setLates] = useState([]);
  const [updated, setUpdated] = useState(null);
  const [err, setErr] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [now, setNow] = useState(() => new Date());
  const panelRef = useRef(null);

  const load = useCallback(async () => {
    try {
      const [o, od, c, l] = await Promise.all([
        listCounterOrders(vendor.id), listOverdueOrders(vendor.id),
        listCounterCancelRequests(vendor.id), listCounterLateArrivals(vendor.id),
      ]);
      setOrders(o); setOverdue(new Set(od.content.map((x) => x.id)));
      setCancels(c.content); setLates(l.content); setUpdated(new Date());
    } catch (x) { setErr(x.message); setOrders((cur) => cur ?? { content: [], totalElements: 0 }); }
  }, [vendor.id]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    const t = setInterval(() => { setNow(new Date()); }, 1000);
    const p = setInterval(() => { if (!document.hidden) load(); }, 15000);
    return () => { clearInterval(t); clearInterval(p); };
  }, [load]);
  useEffect(() => { if (focusRequests) panelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }, [focusRequests]);

  // Thao tác: gọi API, báo lỗi nguyên văn của server, rồi tải lại để thấy trạng thái thật.
  const act = useCallback(async (orderId, fn) => {
    setErr(''); setBusyId(orderId);
    try { await fn(); await load(); return true; } catch (x) { setErr(x.message); await load(); return false; } finally { setBusyId(null); }
  }, [load]);

  const list = orders?.content || [];
  const byStatus = (s) => list.filter((o) => o.status === s);
  const cancelOf = (orderId) => cancels.find((c) => c.orderId === orderId);
  const open = vendor.status === 'ACTIVE';

  return (
    <>
      <div className="board-head">
        <h2>Đơn hàng đang xử lý</h2>
        <span className="muted">
          {updated ? `Cập nhật ${clock(updated)}` : 'Đang tải...'} · {open ? 'Quầy đang nhận đơn' : 'Quầy đang tạm ngừng nhận đơn mới'}
          {' '}<button className="link" onClick={load}>Tải lại</button>
        </span>
      </div>
      {err && <p className="error" role="alert">{err}</p>}
      {orders && orders.totalElements > list.length && <p className="note warn">Đang hiện {list.length}/{orders.totalElements} đơn cũ nhất. Xử lý bớt rồi tải lại để thấy phần còn lại.</p>}

      <div className="columns">
        {COLUMNS.map(([status, label, tone]) => {
          const items = byStatus(status);
          return (
            <section key={status} className="column" aria-label={`${label}: ${items.length} đơn`}>
              <h3><span>{label}</span><span className={`count ${tone}`}>{items.length}</span></h3>
              {items.length === 0 && <p className="muted col-empty">Không có đơn</p>}
              {items.map((o) => (
                <OrderCard key={o.id} order={o} now={now} busy={busyId === o.id} overdue={overdue.has(o.id)}
                  cancelReq={cancelOf(o.id)} act={act} jumpToRequests={() => panelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
              ))}
            </section>
          );
        })}
      </div>

      <section ref={panelRef} className="requests" aria-label="Yêu cầu cần xử lý" tabIndex={-1}>
        {cancels.length === 0 && lates.length === 0
          ? <p className="muted">Không có yêu cầu hủy hoặc báo đến muộn nào đang chờ.</p>
          : <>
            {cancels.map((c) => <CancelRow key={`c${c.id}`} req={c} busy={busyId === `c${c.id}`} act={act} />)}
            {lates.map((l) => <LateRow key={`l${l.id}`} req={l} busy={busyId === `l${l.id}`} act={act} />)}
          </>}
      </section>
    </>
  );
}

// ---------------------------------------------------------------- thẻ đơn

function OrderCard({ order: o, now, busy, overdue, cancelReq, act, jumpToRequests }) {
  const [mode, setMode] = useState(null);                 // 'reject' | 'complete' | 'nocollect'
  const [reason, setReason] = useState('');
  const [code, setCode] = useState('');
  const [amount, setAmount] = useState('');
  const [handed, setHanded] = useState(false);
  const [verified, setVerified] = useState(false);
  const left = o.status === 'PENDING' ? secondsLeft(o.pendingExpiresAt, now) : null;
  const cls = ['ocard', cancelReq ? 'asks' : '', overdue ? 'late' : ''].join(' ');

  const close = () => { setMode(null); setReason(''); setCode(''); setAmount(''); setHanded(false); setVerified(false); };
  const run = (fn) => act(o.id, fn).then((ok) => { if (ok) close(); });

  return (
    <article className={cls}>
      <div className="ohead">
        <b>#{o.orderCode}</b>
        {o.status === 'PENDING' && <span className={`tone warn ${left <= 60 ? 'urgent' : ''}`}>{left > 0 ? `còn ${mmss(left)}` : 'đã quá hạn'}</span>}
        {cancelReq && <span className="pill red">Xin hủy</span>}
        {overdue && <span className="pill red">Quá hạn</span>}
        {o.status === 'READY' && !overdue && o.pickupDeadline && <span className="tone ok">hạn nhận {hm(o.pickupDeadline)}</span>}
      </div>
      <div>{lineText(o)}</div>
      <div className="muted">
        {o.status === 'READY' ? `${o.paymentStatus === 'PAID' ? 'Đã thanh toán' : 'Chưa thanh toán'} · ${money(o.totalAmount)}` : `${hhmm(o.pickupStart)} · ${money(o.totalAmount)}`}
      </div>

      {!mode && (
        <div className="oacts">
          {o.status === 'PENDING' && <>
            <button className="btn primary" disabled={busy || left === 0} onClick={() => run(() => counterAction(o.id, 'confirm'))}>Tiếp nhận · UC7.1</button>
            <button className="btn danger" disabled={busy} onClick={() => setMode('reject')}>Từ chối · UC7.5</button>
          </>}
          {o.status === 'CONFIRMED' && (
            cancelReq
              ? <button className="btn outline" onClick={jumpToRequests}>Duyệt yêu cầu · UC7.6</button>
              : <button className="btn primary" disabled={busy} onClick={() => run(() => counterAction(o.id, 'prepare'))}>Bắt đầu chuẩn bị · UC7.2</button>
          )}
          {o.status === 'PREPARING' && <button className="btn accent" disabled={busy} onClick={() => run(() => counterAction(o.id, 'ready'))}>Báo sẵn sàng · UC7.3</button>}
          {o.status === 'READY' && (overdue
            ? <button className="btn danger" disabled={busy} onClick={() => setMode('nocollect')}>Xác nhận không nhận · UC7.8</button>
            : <button className="btn primary" disabled={busy} onClick={() => setMode('complete')}>Bàn giao, ghi thu tiền · UC7.4</button>)}
        </div>
      )}

      {mode === 'reject' && (
        <form className="inline" onSubmit={(e) => { e.preventDefault(); run(() => rejectOrder(o.id, reason.trim())); }}>
          <label className="field"><span>Lý do từ chối (khách sẽ thấy)</span>
            <input value={reason} maxLength={255} onChange={(e) => setReason(e.target.value)} autoFocus /></label>
          <div className="oacts"><button className="btn danger" disabled={busy || !reason.trim()}>Từ chối đơn</button>
            <button type="button" className="link" onClick={close}>Thôi</button></div>
        </form>
      )}

      {mode === 'complete' && (
        <form className="inline" onSubmit={(e) => { e.preventDefault(); run(() => completeOrder(o.id, { orderCode: code.trim(), amountReceived: Number(amount), handedOver: handed })); }}>
          <label className="field"><span>Mã đơn khách đọc</span>
            <input value={code} onChange={(e) => setCode(e.target.value)} autoFocus autoComplete="off" /></label>
          <label className="field"><span>Số tiền đã thu (đ), phải thu {money(o.totalAmount)}</span>
            <input type="number" min="0" inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value)} /></label>
          <label className="opt"><input type="checkbox" checked={handed} onChange={(e) => setHanded(e.target.checked)} /> Đã giao món cho khách</label>
          <div className="oacts"><button className="btn primary" disabled={busy || !code.trim() || amount === '' || !handed}>Hoàn tất bàn giao</button>
            <button type="button" className="link" onClick={close}>Thôi</button></div>
        </form>
      )}

      {mode === 'nocollect' && (
        <form className="inline" onSubmit={(e) => { e.preventDefault(); run(() => markNotCollected(o.id, { verifiedAtCounter: true, note: reason.trim() || null })); }}>
          <label className="opt"><input type="checkbox" checked={verified} onChange={(e) => setVerified(e.target.checked)} autoFocus />
            Tôi đã kiểm tra thực tế tại quầy: khách không có mặt và đã thử báo lại.</label>
          <label className="field"><span>Ghi chú (không bắt buộc)</span>
            <input value={reason} maxLength={255} onChange={(e) => setReason(e.target.value)} /></label>
          <div className="oacts"><button className="btn danger" disabled={busy || !verified}>Xác nhận không nhận</button>
            <button type="button" className="link" onClick={close}>Thôi</button></div>
        </form>
      )}
    </article>
  );
}

// ---------------------------------------------------------------- yêu cầu hủy và báo đến muộn

/** Duyệt hoặc từ chối đều bắt buộc có lý do (BR19), nên bấm nút là mở ô lý do chứ không gửi ngay. */
function DecisionForm({ verb, busy, withDeadline, defaultDeadline, onSubmit, onCancel }) {
  const [reason, setReason] = useState('');
  const [phone, setPhone] = useState(false);
  const [deadline, setDeadline] = useState(defaultDeadline || '');
  const approve = verb === 'approve';
  return (
    <form className="decide" onSubmit={(e) => { e.preventDefault(); onSubmit({ reason: reason.trim(), channel: phone ? 'PHONE' : 'APP', deadline }); }}>
      {withDeadline && approve && (
        <label className="field"><span>Hạn nhận mới</span>
          <input type="datetime-local" value={deadline} onChange={(e) => setDeadline(e.target.value)} /></label>
      )}
      <label className="field"><span>{approve ? 'Lý do đồng ý' : 'Lý do từ chối'}</span>
        <input value={reason} maxLength={255} onChange={(e) => setReason(e.target.value)} autoFocus /></label>
      <label className="opt"><input type="checkbox" checked={phone} onChange={(e) => setPhone(e.target.checked)} /> Đã thỏa thuận qua điện thoại</label>
      <div className="oacts">
        <button className={`btn ${approve ? 'ok' : 'danger'}`} disabled={busy || !reason.trim() || (withDeadline && approve && !deadline)}>{approve ? 'Xác nhận đồng ý' : 'Xác nhận từ chối'}</button>
        <button type="button" className="link" onClick={onCancel}>Thôi</button>
      </div>
    </form>
  );
}

function CancelRow({ req, busy, act }) {
  const [verb, setVerb] = useState(null);
  return (
    <div className="req" id={`req-cancel-${req.id}`}>
      <div className="req-line">
        <span><b>Yêu cầu hủy #{req.orderCode}</b> <span className="muted">· Lý do: "{req.reason}"</span></span>
        {!verb && <span className="req-btns">
          <button className="btn ok" onClick={() => setVerb('approve')}>Đồng ý</button>
          <button className="btn danger" onClick={() => setVerb('reject')}>Từ chối có lý do</button>
        </span>}
      </div>
      {verb && <DecisionForm verb={verb} busy={busy} onCancel={() => setVerb(null)}
        onSubmit={(d) => act(`c${req.id}`, () => decideCancel(req.id, verb, { reason: d.reason, channel: d.channel }))} />}
    </div>
  );
}

function LateRow({ req, busy, act }) {
  const [verb, setVerb] = useState(null);
  return (
    <div className="req" id={`req-late-${req.id}`}>
      <div className="req-line">
        <span><b>Báo đến muộn #{req.orderCode}</b> <span className="muted">· dự kiến {hm(req.proposedArrivalAt)} · Lý do: "{req.reason}"</span></span>
        {!verb && <span className="req-btns">
          <button className="btn ok" onClick={() => setVerb('approve')}>Đồng ý gia hạn</button>
          <button className="btn danger" onClick={() => setVerb('reject')}>Từ chối có lý do</button>
        </span>}
      </div>
      {verb && <DecisionForm verb={verb} busy={busy} withDeadline defaultDeadline={req.proposedArrivalAt?.slice(0, 16)} onCancel={() => setVerb(null)}
        onSubmit={(d) => act(`l${req.id}`, () => (verb === 'approve'
          ? decideLate(req.id, 'approve', { newDeadline: `${d.deadline}:00`, reason: d.reason, channel: d.channel })
          : decideLate(req.id, 'reject', { reason: d.reason, channel: d.channel })))} />}
    </div>
  );
}
