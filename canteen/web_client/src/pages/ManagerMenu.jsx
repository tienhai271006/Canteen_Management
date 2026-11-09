import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  listMenus, getMenu, createMenu, publishMenu, unpublishMenu, setCutoff, addMenuItem, setItemAvailability, removeMenuItem,
  adjustQuota, addSlot, deleteSlot, setSlotCapacity, listDishes, createDish, listWaste, handleWaste, money, SESSION, CATEGORY,
} from '../api.js';

const SESSIONS = ['MORNING', 'LUNCH', 'AFTERNOON'];
const WEEKDAY = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
const hhmm = (t) => t?.slice(0, 5);
const todayStr = () => new Date().toLocaleDateString('sv-SE');
const defaultSession = () => { const h = new Date().getHours(); return h < 10 ? 'MORNING' : h < 15 ? 'LUNCH' : 'AFTERNOON'; };
const minutes = (t) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5));
const titleDate = (d) => `${WEEKDAY[new Date(`${d}T00:00:00`).getDay()]} ${d.slice(8, 10)}/${d.slice(5, 7)}`;

/** Hình 23: thực đơn, hạn mức và xử lý món không nhận (UC3.1 đến UC3.7). Chỉ quản lý quầy. */
export default function ManagerMenu({ vendor }) {
  const [date, setDate] = useState(todayStr());
  const [session, setSession] = useState(defaultSession());
  const [summary, setSummary] = useState(undefined);      // undefined = đang tải, null = chưa có thực đơn
  const [detail, setDetail] = useState(null);
  const [editId, setEditId] = useState(null);
  const [adding, setAdding] = useState(false);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setErr('');
    try {
      const menus = await listMenus(vendor.id, date, date);
      const m = menus.find((x) => x.mealSession === session) || null;
      setSummary(m);
      setDetail(m ? await getMenu(m.id) : null);
    } catch (x) { setErr(x.message); setSummary((cur) => cur ?? null); }
  }, [vendor.id, date, session]);
  useEffect(() => { setSummary(undefined); setEditId(null); setAdding(false); load(); }, [load]);

  // Mọi thao tác: gọi API, báo lỗi nguyên văn của server (kể cả "không giảm dưới số đã cam kết"), rồi tải lại số liệu thật.
  const run = useCallback(async (fn) => {
    setErr(''); setBusy(true);
    try { await fn(); await load(); return true; } catch (x) { setErr(x.message); return false; } finally { setBusy(false); }
  }, [load]);

  const menu = detail?.menu;
  const items = detail?.items || [];
  const slots = detail?.slots || [];
  const editItem = items.find((i) => i.id === editId) || null;

  const slotSummary = useMemo(() => {
    if (!slots.length) return 'Chưa có khung nhận';
    const sorted = [...slots].sort((a, b) => a.startTime.localeCompare(b.startTime));
    const lens = new Set(sorted.map((s) => minutes(s.endTime) - minutes(s.startTime)));
    return `${hhmm(sorted[0].startTime)} – ${hhmm(sorted[sorted.length - 1].endTime)}${lens.size === 1 ? `, bước ${[...lens][0]} phút` : ''}`;
  }, [slots]);

  return (
    <>
      <div className="board-head">
        <h2>Thực đơn và hạn mức · {titleDate(date)}</h2>
        <button className="btn primary add-btn" disabled={!menu} onClick={() => { setAdding((v) => !v); setEditId(null); }}>+ Thêm món</button>
      </div>

      <div className="menu-bar">
        <div className="tabs" role="tablist" aria-label="Buổi">
          {SESSIONS.map((s) => (
            <button key={s} role="tab" aria-selected={s === session} className={`chip ${s === session ? 'on' : ''}`} onClick={() => setSession(s)}>{SESSION[s]}</button>
          ))}
          <input className="date-in" type="date" aria-label="Ngày" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
        </div>
        {menu && <span className="muted">Đóng nhận đơn: {hhmm(menu.orderCutoffTime)} · Khung nhận: {slotSummary}</span>}
      </div>
      {err && <p className="note danger" role="alert">{err}</p>}

      {summary === undefined && <p className="muted">Đang tải thực đơn...</p>}
      {summary === null && <NewMenu vendor={vendor} date={date} session={session} busy={busy} run={run} />}

      {menu && (
        <>
          <section className="card flush" aria-label="Món trong thực đơn">
            <div className="menu-state">
              <span className={`pill ${menu.status === 'PUBLISHED' ? 'green' : 'grey'}`}>{menu.status === 'PUBLISHED' ? 'Đã đăng, khách thấy được' : 'Bản nháp, khách chưa thấy'}</span>
              {menu.status === 'PUBLISHED'
                ? <button className="link" disabled={busy} onClick={() => run(() => unpublishMenu(menu.id))}>Gỡ khỏi trang khách</button>
                : <button className="btn-sm" disabled={busy || items.length === 0 || slots.length === 0} onClick={() => run(() => publishMenu(menu.id))}
                  title={items.length === 0 || slots.length === 0 ? 'Cần ít nhất một món và một khung nhận' : ''}>Đăng thực đơn</button>}
            </div>
            {items.length === 0 ? <div className="empty">Thực đơn chưa có món. Bấm "+ Thêm món".</div> : (
              <div className="table-wrap"><table className="lines menu-table">
                <thead><tr><th>Món · UC3.1</th><th>Giá bán · UC3.2</th><th>Hạn mức món · UC3.6</th><th>Đã cam kết</th><th>Hiển thị · UC3.3</th><th>Thao tác</th></tr></thead>
                <tbody>
                  {items.map((i) => {
                    const [label, tone] = !i.dishActive ? ['Món đã ẩn', 'grey'] : !i.available ? ['Ngừng bán', 'grey'] : i.availableOnline <= 0 ? ['Hết suất', 'red'] : ['Đang bán', 'green'];
                    return (
                      <tr key={i.id} className={i.id === editId ? 'sel' : ''}>
                        <td><b>{i.dishName}</b></td>
                        <td>{money(i.price)}</td>
                        <td>{i.totalSlots > 0 ? `${i.totalSlots} suất` : '—'}{i.counterSlots > 0 && <span className="muted"> (quầy {i.counterSlots})</span>}</td>
                        <td>{i.reservedSlots}</td>
                        <td><span className={`pill ${tone}`}>{label}</span></td>
                        <td className="row-acts">
                          <button className="link" onClick={() => { setEditId(i.id); setAdding(false); }}>Sửa</button> ·{' '}
                          <button className="link" disabled={busy || !i.dishActive} onClick={() => run(() => setItemAvailability(menu.id, i.id, !i.available))}>{i.available ? 'Ẩn' : 'Bán lại'}</button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table></div>
            )}
          </section>

          <SlotsCard menu={menu} slots={slots} busy={busy} run={run} />

          {adding && <AddItem vendor={vendor} menu={menu} items={items} busy={busy} run={run} onDone={() => setAdding(false)} />}
        </>
      )}

      <div className="two-cards">
        {editItem && <QuotaEdit key={editItem.id} menu={menu} item={editItem} run={run} onDone={() => setEditId(null)} />}
        <WasteCard vendor={vendor} />
      </div>
    </>
  );
}

// ---------------------------------------------------------------- tạo thực đơn

function NewMenu({ vendor, date, session, busy, run }) {
  const [cutoff, setCutoff] = useState(session === 'MORNING' ? '07:00' : session === 'LUNCH' ? '11:15' : '16:00');
  useEffect(() => { setCutoff(session === 'MORNING' ? '07:00' : session === 'LUNCH' ? '11:15' : '16:00'); }, [session]);
  return (
    <section className="card">
      <h3 className="step-title">Chưa có thực đơn buổi {SESSION[session].toLowerCase()} ngày {date.slice(8, 10)}/{date.slice(5, 7)}</h3>
      <label className="field narrow-field"><span>Giờ đóng nhận đơn</span>
        <input type="time" value={cutoff} onChange={(e) => setCutoff(e.target.value)} /></label>
      <button className="btn primary" style={{ marginTop: '.75rem' }} disabled={busy || !cutoff}
        onClick={() => run(() => createMenu({ vendorId: vendor.id, menuDate: date, mealSession: session, orderCutoffTime: cutoff }))}>Tạo thực đơn</button>
    </section>
  );
}

// ---------------------------------------------------------------- khung nhận

function SlotsCard({ menu, slots, busy, run }) {
  const [start, setStart] = useState('');
  const [end, setEnd] = useState('');
  const [cut, setCut] = useState(hhmm(menu.orderCutoffTime));
  useEffect(() => { setCut(hhmm(menu.orderCutoffTime)); }, [menu.orderCutoffTime]);
  const sorted = [...slots].sort((a, b) => a.startTime.localeCompare(b.startTime));
  return (
    <section className="card" style={{ marginTop: '1.25rem' }} aria-labelledby="sl">
      <h3 id="sl" className="step-title">Khung nhận và giờ đóng nhận đơn · UC3.5</h3>
      <div className="slot-edit">
        <div>
          {sorted.length === 0 ? <p className="muted">Chưa có khung nhận.</p> : (
            <div className="table-wrap"><table className="lines slot-tbl">
              <thead><tr><th>Khung giờ</th><th>Hạn mức</th><th>Đang giữ</th><th>Đã dùng</th><th>Còn nhận</th><th /></tr></thead>
              <tbody>
                {sorted.map((s) => <SlotRow key={s.id} menu={menu} slot={s} busy={busy} run={run} />)}
              </tbody>
            </table></div>
          )}
          <form className="inline-row" onSubmit={(e) => { e.preventDefault(); run(() => addSlot(menu.id, { startTime: start, endTime: end })).then((ok) => { if (ok) { setStart(''); setEnd(''); } }); }}>
            <label className="field"><span>Từ</span><input type="time" value={start} onChange={(e) => setStart(e.target.value)} /></label>
            <label className="field"><span>Đến</span><input type="time" value={end} onChange={(e) => setEnd(e.target.value)} /></label>
            <button className="btn outline" disabled={busy || !start || !end}>Thêm khung</button>
          </form>
        </div>
        <form className="inline-row" onSubmit={(e) => { e.preventDefault(); run(() => setCutoff(menu.id, cut)); }}>
          <label className="field"><span>Đóng nhận đơn lúc</span><input type="time" value={cut} onChange={(e) => setCut(e.target.value)} /></label>
          <button className="btn outline" disabled={busy || !cut || cut === hhmm(menu.orderCutoffTime)}>Lưu giờ</button>
        </form>
      </div>
      <p className="muted">Còn nhận = hạn mức − đang giữ − đã dùng. Để trống hạn mức nếu không giới hạn khung. Khung đầy thì khách không chọn được.</p>
    </section>
  );
}


function SlotRow({ menu, slot, busy, run }) {
  const init = slot.capacity == null ? '' : String(slot.capacity);
  const [cap, setCap] = useState(init);
  useEffect(() => { setCap(init); }, [init]);
  const n = cap === '' ? null : Number(cap);
  const bad = n !== null && (!Number.isInteger(n) || n < 0);
  const low = n !== null && !bad && n < slot.held + slot.used;
  return (
    <tr>
      <td>{hhmm(slot.startTime)}–{hhmm(slot.endTime)} {slot.full && <span className="tone urgent">Đầy</span>}</td>
      <td>
        <input className="cap-input" type="number" min="0" aria-label={`Hạn mức khung ${hhmm(slot.startTime)}`} value={cap}
          placeholder="Không giới hạn" onChange={(e) => setCap(e.target.value)} />
        {low && <div className="error small">Thấp hơn số đã nhận ({slot.held + slot.used}).</div>}
      </td>
      <td>{slot.held}</td>
      <td>{slot.used}</td>
      <td>{slot.remaining == null ? '∞' : slot.remaining}</td>
      <td className="row-actions">
        <button className="btn outline" disabled={busy || bad || low || cap === init}
          onClick={() => run(() => setSlotCapacity(menu.id, slot.id, n))}>Lưu</button>
        <button className="btn ghost" aria-label={`Xóa khung ${hhmm(slot.startTime)}`} disabled={busy}
          onClick={() => run(() => deleteSlot(menu.id, slot.id))}>Xóa</button>
      </td>
    </tr>
  );
}

// ---------------------------------------------------------------- thêm món

function AddItem({ vendor, menu, items, busy, run, onDone }) {
  const [dishes, setDishes] = useState(null);
  const [dishId, setDishId] = useState('');
  const [total, setTotal] = useState('');
  const [counter, setCounter] = useState('0');
  const [newDish, setNewDish] = useState(false);
  const [nd, setNd] = useState({ name: '', category: 'MAIN', price: '' });
  const [err, setErr] = useState('');

  const loadDishes = useCallback(() => listDishes(vendor.id).then((r) => setDishes(r.content)).catch((x) => { setErr(x.message); setDishes([]); }), [vendor.id]);
  useEffect(() => { loadDishes(); }, [loadDishes]);
  const used = new Set(items.map((i) => i.dishId));
  const options = (dishes || []).filter((d) => !used.has(d.id));

  return (
    <section className="card" style={{ marginTop: '1.25rem' }} aria-labelledby="ad">
      <h3 id="ad" className="step-title">Thêm món vào thực đơn</h3>
      {err && <p className="error" role="alert">{err}</p>}
      <div className="two">
        <label className="field"><span>Món</span>
          <select value={dishId} onChange={(e) => setDishId(e.target.value)}>
            <option value="">{dishes === null ? 'Đang tải...' : options.length ? 'Chọn món' : 'Không còn món để thêm'}</option>
            {options.map((d) => <option key={d.id} value={d.id}>{d.name} · {money(d.price)}</option>)}
          </select></label>
        <label className="field"><span>Số suất cả buổi</span><input type="number" min="0" value={total} onChange={(e) => setTotal(e.target.value)} /></label>
      </div>
      <label className="field"><span>Trong đó dành cho khách tại quầy</span><input type="number" min="0" value={counter} onChange={(e) => setCounter(e.target.value)} /></label>
      <div className="oacts row">
        <button className="btn primary" disabled={busy || !dishId || total === ''}
          onClick={() => run(() => addMenuItem(menu.id, { dishId: Number(dishId), totalSlots: Number(total), counterSlots: Number(counter || 0) })).then((ok) => ok && onDone())}>Thêm vào thực đơn</button>
        <button className="link" onClick={() => setNewDish((v) => !v)}>{newDish ? 'Ẩn form món mới' : 'Chưa có món? Tạo món mới'}</button>
        <button className="link" onClick={onDone}>Đóng</button>
      </div>
      {newDish && (
        <form className="new-dish" onSubmit={async (e) => {
          e.preventDefault(); setErr('');
          try { const d = await createDish({ vendorId: vendor.id, name: nd.name.trim(), category: nd.category, price: Number(nd.price) }); await loadDishes(); setDishId(String(d.id)); setNd({ name: '', category: 'MAIN', price: '' }); setNewDish(false); }
          catch (x) { setErr(x.message); }
        }}>
          <label className="field"><span>Tên món</span><input value={nd.name} maxLength={120} onChange={(e) => setNd({ ...nd, name: e.target.value })} /></label>
          <div className="two">
            <label className="field"><span>Loại</span><select value={nd.category} onChange={(e) => setNd({ ...nd, category: e.target.value })}>
              {Object.entries(CATEGORY).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
            <label className="field"><span>Giá bán (đ)</span><input type="number" min="0" value={nd.price} onChange={(e) => setNd({ ...nd, price: e.target.value })} /></label>
          </div>
          <button className="btn outline" disabled={!nd.name.trim() || nd.price === ''}>Lưu món mới</button>
        </form>
      )}
    </section>
  );
}

// ---------------------------------------------------------------- sửa hạn mức

function QuotaEdit({ menu, item, run, onDone }) {
  const [total, setTotal] = useState(String(item.totalSlots));
  const [counter, setCounter] = useState(String(item.counterSlots));
  const [reason, setReason] = useState('');
  const committed = item.reservedSlots;
  const t = Number(total); const c = Number(counter);
  const tooLow = total !== '' && counter !== '' && t < committed + c;
  return (
    <section className="card" aria-labelledby="qe">
      <h3 id="qe" className="step-title">Sửa hạn mức · {item.dishName}</h3>
      <div className="two">
        <label className="field"><span>Số suất mới</span><input type="number" min="0" value={total} onChange={(e) => setTotal(e.target.value)} /></label>
        <label className="field"><span>Đã cam kết</span><input value={committed} disabled /></label>
      </div>
      <label className="field"><span>Dành cho khách tại quầy</span><input type="number" min="0" value={counter} onChange={(e) => setCounter(e.target.value)} /></label>
      <label className="field"><span>Lý do điều chỉnh (lưu lịch sử)</span><input value={reason} maxLength={255} onChange={(e) => setReason(e.target.value)} /></label>
      {tooLow && <p className="note danger" role="alert">Không thể giảm dưới {committed + c} suất: {committed} đã cam kết cho các đơn Pending/Confirmed và {c} dành cho khách tại quầy.</p>}
      <div className="oacts row">
        <button className="btn primary" disabled={tooLow || total === '' || counter === '' || !reason.trim()}
          onClick={() => run(() => adjustQuota(menu.id, item.id, { totalSlots: t, counterSlots: c, reason: reason.trim() })).then((ok) => ok && onDone())}>Lưu hạn mức</button>
        <button className="link" onClick={onDone}>Đóng</button>
        <button className="link danger-link" onClick={() => run(() => removeMenuItem(menu.id, item.id)).then((ok) => ok && onDone())}>Gỡ món khỏi thực đơn</button>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------- món không được nhận (UC3.7)

function WasteCard({ vendor }) {
  const [orders, setOrders] = useState(null);
  const [err, setErr] = useState('');
  const [busyKey, setBusyKey] = useState(null);
  const [qty, setQty] = useState({});
  const load = useCallback(() => listWaste(vendor.id).then((r) => { setOrders(r); setErr(''); }).catch((x) => { setErr(x.message); setOrders([]); }), [vendor.id]);
  useEffect(() => { load(); }, [load]);

  async function record(order, item, kind) {
    const n = Number(qty[item.orderItemId] ?? item.remaining);
    setBusyKey(`${item.orderItemId}${kind}`); setErr('');
    try {
      await handleWaste(order.orderId, { items: [{ orderItemId: item.orderItemId, resoldQuantity: kind === 'resold' ? n : 0, wastedQuantity: kind === 'wasted' ? n : 0 }] });
      await load();
    } catch (x) { setErr(x.message); } finally { setBusyKey(null); }
  }

  return (
    <section className="card" aria-labelledby="wc">
      <h3 id="wc" className="step-title">Xử lý món không được nhận · UC3.7</h3>
      {err && <p className="note danger" role="alert">{err}</p>}
      {orders === null ? <p className="muted">Đang tải...</p> : orders.length === 0 ? <p className="muted">Không có món nào đang chờ xử lý.</p> : orders.map((o) => (
        <div key={o.orderId} className="waste-order">
          {o.items.filter((i) => i.remaining > 0).map((i) => (
            <div key={i.orderItemId} className="waste-item">
              <div>Đơn #{o.orderCode} · {i.dishName} × {i.remaining} · NotCollected
                {(i.resold > 0 || i.wasted > 0) && <span className="muted"> (đã bán lại {i.resold}, hao hụt {i.wasted})</span>}</div>
              {i.remaining > 1 && (
                <label className="field narrow-field"><span>Số suất ghi nhận</span>
                  <input type="number" min="1" max={i.remaining} value={qty[i.orderItemId] ?? i.remaining} onChange={(e) => setQty({ ...qty, [i.orderItemId]: e.target.value })} /></label>
              )}
              <div className="two">
                <button className="btn outline" disabled={busyKey !== null} onClick={() => record(o, i, 'resold')}>Ghi bán lại</button>
                <button className="btn danger" disabled={busyKey !== null} onClick={() => record(o, i, 'wasted')}>Ghi hao hụt</button>
              </div>
            </div>
          ))}
        </div>
      ))}
      <p className="muted">Số lượng món đã làm không tự cộng lại vào hạn mức.</p>
    </section>
  );
}
