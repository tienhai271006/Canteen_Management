import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  adminListIncidents, adminDecideIncident, adminRevokeIncident, adminViolations, adminRestrict,
  adminRevokeRestriction, adminListFeedback, adminResolveFeedback,
} from '../api.js';

const THRESHOLD = 3;   // mức chuyển xem xét hạn chế (IncidentProperties.escalateAt, mặc định 3 lần trong 30 ngày)
const code = (i) => `#SV-${String(i.id).padStart(4, '0')}`;
const dm = (iso) => (iso ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}` : '');
const dmHm = (iso) => (iso ? `${dm(iso)} ${iso.slice(11, 16)}` : '');
const STATUS = {
  PENDING_REVIEW: ['warn', 'Chờ xem xét'], CONFIRMED: ['info', 'Đã xác nhận'], EXEMPTED: ['grey', 'Đã miễn'], REVOKED: ['grey', 'Đã hủy kết luận'],
};

/** Hình 25: xem xét sự việc không nhận món (UC10.3) và hạn chế đặt trước (UC10.5). */
export default function AdminIncidents() {
  const [list, setList] = useState(null);
  const [feedbacks, setFeedbacks] = useState([]);
  const [selId, setSelId] = useState(null);
  const [sum, setSum] = useState(null);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [note, setNote] = useState('');
  const [reply, setReply] = useState('');
  const [days, setDays] = useState('7');
  const [why, setWhy] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [i, f] = await Promise.all([adminListIncidents(), adminListFeedback(['OPEN'])]);
      setList(i.content); setFeedbacks(f.content); setErr('');
      setSelId((cur) => cur ?? (i.content.find((x) => x.status === 'PENDING_REVIEW') || i.content[0])?.id ?? null);
    } catch (x) { setErr(x.message); setList((c) => c ?? []); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const sel = list?.find((i) => i.id === selId) || list?.find((i) => i.status === 'PENDING_REVIEW') || list?.[0] || null;

  const loadSum = useCallback(async (userId) => {
    if (!userId) { setSum(null); return; }
    try { setSum(await adminViolations(userId)); } catch (x) { setErr(x.message); }
  }, []);
  useEffect(() => { loadSum(sel?.userId); setNote(''); setReply(''); setMsg(''); }, [sel?.id, sel?.userId, loadSum]);

  // Số thứ tự "lần k" của vi phạm đã xác nhận theo từng người dùng, theo thời gian xảy ra.
  const nth = useMemo(() => {
    const m = new Map(); const c = {};
    [...(list || [])].filter((i) => i.status === 'CONFIRMED').sort((a, b) => a.occurredAt.localeCompare(b.occurredAt))
      .forEach((i) => { c[i.userId] = (c[i.userId] || 0) + 1; m.set(i.id, c[i.userId]); });
    return m;
  }, [list]);

  const pending = (list || []).filter((i) => i.status === 'PENDING_REVIEW').length;
  const confirmed = (list || []).filter((i) => i.status === 'CONFIRMED').length;
  const openFb = feedbacks.filter((f) => f.incidentId === sel?.id);

  async function run(fn, ok) {
    setBusy(true); setErr(''); setMsg('');
    try { await fn(); setMsg(ok); await load(); await loadSum(sel?.userId); return true; }
    catch (x) { setErr(x.message); return false; }
    finally { setBusy(false); }
  }
  const decide = (d) => run(() => adminDecideIncident(sel.id, d, note.trim()), d === 'CONFIRM' ? 'Đã xác nhận vi phạm.' : 'Đã miễn sự việc.').then((ok) => ok && setNote(''));

  // Điều kiện mở quyết định hạn chế: đủ ngưỡng bằng các sự việc ĐÃ xác nhận, không còn sự việc chờ xem xét hoặc phản hồi đang tranh chấp (BR26, BR35).
  const userPending = (list || []).filter((i) => i.userId === sel?.userId && i.status === 'PENDING_REVIEW');
  const valid = sum?.validCount ?? 0;
  let blocked = '';
  if (sum?.activeRestriction) blocked = 'Tài khoản đang bị hạn chế đặt trước.';
  else if (userPending.length) blocked = `Chưa thể ra quyết định hạn chế: sự việc ${userPending.map(code).join(', ')} đang chờ xem xét nên chưa được cộng vào ngưỡng (hiện ${valid}/${THRESHOLD}).`;
  else if (sum?.disputedOpen) blocked = 'Chưa thể ra quyết định hạn chế: còn phản hồi của khách chưa được xử lý.';
  else if (valid < THRESHOLD) blocked = `Chưa đủ ngưỡng: mới có ${valid}/${THRESHOLD} vi phạm đã xác nhận trong ${sum?.windowDays ?? 30} ngày.`;
  const r = sum?.activeRestriction;
  const daysOk = /^\d+$/.test(days) && +days >= 1 && +days <= 30;

  return (
    <>
      <div className="board-head">
        <h2>Xem xét sự việc không nhận món</h2>
        <span className="row-pills"><span className="pill warn">Chờ xem xét {pending}</span> <span className="pill info">Đã xác nhận {confirmed}</span></span>
      </div>
      {err && <p className="error" role="alert">{err}</p>}

      <div className="card flush table-wrap">
        <table className="lines adm">
          <thead><tr><th>Sự việc</th><th>Người dùng</th><th>Đơn · Quầy</th><th>Ngày</th><th>Tính vào ngưỡng 30 ngày</th><th>Trạng thái</th></tr></thead>
          <tbody>
            {list === null && <tr><td colSpan="6" className="muted">Đang tải...</td></tr>}
            {list?.length === 0 && <tr><td colSpan="6" className="muted">Chưa có sự việc nào. Sự việc được tạo khi quầy xác nhận đơn Ready không được nhận (UC7.8).</td></tr>}
            {list?.map((i) => (
              <tr key={i.id} className={sel?.id === i.id ? 'sel' : ''} onClick={() => setSelId(i.id)}>
                <td><button className="row-btn" onClick={() => setSelId(i.id)} aria-pressed={sel?.id === i.id}>{code(i)}</button></td>
                <td>{i.customerName}</td>
                <td>#{i.orderCode} · {i.vendorName}</td>
                <td>{dm(i.occurredAt)}</td>
                <td>{i.status === 'CONFIRMED' ? `Có · lần ${nth.get(i.id)}`
                  : i.status === 'PENDING_REVIEW' ? <b className="warn-t">Chưa tính · đang chờ</b>
                  : i.status === 'EXEMPTED' ? 'Không · được miễn' : 'Không · đã hủy kết luận'}</td>
                <td><span className={`pill ${STATUS[i.status][0]}`}>{STATUS[i.status][1]}</span>{i.disputed && <span className="pill bad" title="Có phản hồi chờ xử lý"> Có phản hồi</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {sel && sum && (
        <p className="muted">Ngưỡng hiện tại của {sum.customerName}: <b>{valid} vi phạm đã xác nhận</b> / {sum.windowDays} ngày
          {valid < THRESHOLD ? ` (chưa đến mức chuyển xem xét hạn chế, từ ${THRESHOLD} lần).` : ' (đã đến mức chuyển xem xét hạn chế).'}</p>
      )}

      {sel && (
        <div className="adm-split">
          <section className="card">
            <h3 className="step-title">Hồ sơ {code(sel)} · UC10.3</h3>
            <p>Đơn <b>#{sel.orderCode}</b> tại {sel.vendorName} không được nhận món (<b>NO_SHOW</b>) lúc {dmHm(sel.occurredAt)}. Quầy đã xác nhận không nhận{sel.status === 'PENDING_REVIEW' ? ', chờ quản trị kết luận' : ''}.</p>
            {sum?.recentIncidents?.length > 1 && (
              <p className="muted">Lịch sử: {sum.recentIncidents.filter((i) => i.id !== sel.id).map((i) => `${dm(i.occurredAt)} ${STATUS[i.status][1].toLowerCase()} (${code(i)})`).join(' · ')}
                {sel.status === 'PENDING_REVIEW' && ` · ${dm(sel.occurredAt)} sự việc mới, đang chờ xem xét`}.</p>
            )}
            {sel.decisionNote && sel.decidedAt && <p className="muted">Kết luận trước đó: {sel.decisionNote} ({dmHm(sel.decidedAt)})</p>}

            {openFb.map((f) => (
              <div key={f.id} className="note warn">
                <b>Phản hồi của khách:</b> “{f.content}”
                <label className="field"><span>Trả lời khách (bắt buộc)</span>
                  <input value={reply} onChange={(e) => setReply(e.target.value)} maxLength={1000} /></label>
                <div className="adm-row">
                  <button className="btn outline" disabled={busy || !reply.trim()} onClick={() => run(() => adminResolveFeedback(f.id, reply.trim(), 'KEEP'), 'Đã trả lời, giữ nguyên kết luận.')}>Giữ kết luận</button>
                  <button className="btn outline" disabled={busy || !reply.trim()} onClick={() => run(() => adminResolveFeedback(f.id, reply.trim(), 'EXEMPT'), 'Đã chấp nhận phản hồi, sự việc được miễn.')}>Chấp nhận, miễn</button>
                </div>
              </div>
            ))}

            {sel.status === 'PENDING_REVIEW' ? (
              <>
                <label className="field"><span>Căn cứ kết luận (bắt buộc)</span>
                  <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} /></label>
                <div className="adm-row">
                  <button className="btn danger" disabled={busy || !note.trim()} onClick={() => decide('CONFIRM')}>Xác nhận vi phạm</button>
                  <button className="btn outline" disabled={busy || !note.trim()} onClick={() => decide('EXEMPT')}>Miễn (có căn cứ)</button>
                </div>
                <p className="muted">Chỉ khi sự việc được kết luận Đã xác nhận mới cộng vào ngưỡng.</p>
              </>
            ) : sel.status === 'CONFIRMED' ? (
              <>
                <label className="field"><span>Lý do hủy kết luận</span><input value={note} onChange={(e) => setNote(e.target.value)} maxLength={255} /></label>
                <button className="btn outline" disabled={busy || !note.trim()} onClick={() => run(() => adminRevokeIncident(sel.id, note.trim()), 'Đã hủy kết luận, sự việc không còn tính vào ngưỡng.').then((ok) => ok && setNote(''))}>Hủy kết luận (ghi nhận sai)</button>
              </>
            ) : <p className="muted">Sự việc đã được xử lý.</p>}
            {msg && <p className="ok" role="status">{msg}</p>}
          </section>

          <section className="card">
            <h3 className="step-title">Hạn chế đặt trước · UC10.5</h3>
            {r ? (
              <>
                <div className="note warn">Đang hạn chế từ {dmHm(r.startsAt)} đến <b>{dmHm(r.endsAt)}</b>. Lý do: {r.reason}</div>
                <label className="field"><span>Lý do gỡ hạn chế</span><input value={why} onChange={(e) => setWhy(e.target.value)} maxLength={255} /></label>
                <button className="btn outline block" disabled={busy || !why.trim()} onClick={() => run(() => adminRevokeRestriction(r.id, why.trim()), 'Đã gỡ hạn chế.').then((ok) => ok && setWhy(''))}>Gỡ hạn chế</button>
              </>
            ) : (
              <>
                <label className={`field ${blocked ? 'locked-field' : ''}`}><span>Thời hạn hạn chế (ngày, tối đa 30)</span>
                  <input inputMode="numeric" value={days} disabled={!!blocked} onChange={(e) => setDays(e.target.value)} /></label>
                {!blocked && <label className="field"><span>Lý do (bắt buộc)</span><input value={why} onChange={(e) => setWhy(e.target.value)} maxLength={255} /></label>}
                {blocked && <div className="note danger" role="alert">{blocked}</div>}
                <div className="note info">Sau khi sự việc được xác nhận và đạt ngưỡng, quyết định hạn chế mới được mở. Thông báo trong ứng dụng và email trường đã xác minh gửi sau khi lưu (UC10.4).</div>
                <button className="btn primary block" disabled={busy || !!blocked || !why.trim() || !daysOk}
                  onClick={() => run(() => adminRestrict(sel.userId, why.trim(), Number(days)), 'Đã lưu quyết định hạn chế.').then((ok) => ok && setWhy(''))}>
                  {blocked ? 'Lưu quyết định (chưa khả dụng)' : 'Lưu quyết định'}</button>
              </>
            )}
          </section>
        </div>
      )}
    </>
  );
}
