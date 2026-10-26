import { useEffect, useMemo, useState } from 'react';
import { getAreas, getVendors, searchMenuItems, addCartItem, money, SESSION, CATEGORY } from '../api.js';

const EMOJI = { MAIN: '🍛', SIDE: '🥗', DRINK: '🧋', SNACK: '🍟', DESSERT: '🍮' };
// Buổi mặc định theo giờ hiện tại, như minh họa đang chọn "Trưa" lúc giữa trưa.
const sessionNow = () => { const h = new Date().getHours(); return h < 10 ? 'MORNING' : h < 14 ? 'LUNCH' : 'AFTERNOON'; };
const hhmm = (t) => t?.slice(0, 5);
const key = (i) => i.menuItemId;

/** Hình 19: khu vực, điểm bán, thực đơn và chi tiết món (UC4.1 đến UC4.4). */
export default function Discover({ authed, onCartChange, goLogin }) {
  const [areas, setAreas] = useState([]);
  const [vendors, setVendors] = useState([]);
  const [areaId, setAreaId] = useState('');
  const [pickedVendors, setPickedVendors] = useState(null);   // null = tất cả điểm bán trong khu
  const [session, setSession] = useState(sessionNow());
  const [category, setCategory] = useState('');
  const [availableOnly, setAvailableOnly] = useState(true);
  const [q, setQ] = useState('');
  const [debouncedQ, setDebouncedQ] = useState('');
  const [items, setItems] = useState([]);
  const [state, setState] = useState('loading');               // loading | ok | error
  const [error, setError] = useState('');
  const [selectedId, setSelectedId] = useState(null);
  const [msg, setMsg] = useState(null);     // { ok, text } hiển thị ở khung chi tiết
  const [busy, setBusy] = useState(false);

  useEffect(() => { getAreas().then(setAreas).catch(() => {}); }, []);
  useEffect(() => {
    setPickedVendors(null);
    getVendors(areaId).then(setVendors).catch(() => setVendors([]));
  }, [areaId]);
  useEffect(() => { const t = setTimeout(() => setDebouncedQ(q.trim()), 300); return () => clearTimeout(t); }, [q]);

  useEffect(() => {
    let live = true;
    setState('loading');
    searchMenuItems({ areaId, session, category, availableOnly, q: debouncedQ })
      .then((r) => { if (live) { setItems(r.content); setState('ok'); } })
      .catch((x) => { if (live) { setError(x.message); setState('error'); } });
    return () => { live = false; };
  }, [areaId, session, category, availableOnly, debouncedQ]);

  // API chỉ lọc một vendorId, nên chọn nhiều điểm bán được lọc ở phía giao diện.
  const shown = useMemo(
    () => (pickedVendors ? items.filter((i) => pickedVendors.includes(i.vendorId)) : items), [items, pickedVendors]);
  const groups = useMemo(() => {
    const m = new Map();
    shown.forEach((i) => m.set(i.vendorId, [...(m.get(i.vendorId) || []), i]));
    return [...m.values()];
  }, [shown]);
  const selected = shown.find((i) => key(i) === selectedId) || null;

  async function add(item) {
    setSelectedId(key(item));
    if (!authed) { goLogin(); return; }
    setBusy(true); setMsg(null);
    try { await addCartItem(item.menuItemId, 1); setMsg({ ok: true, text: `Đã thêm ${item.dishName} vào giỏ.` }); onCartChange(); }
    catch (x) { setMsg({ ok: false, text: x.message }); } finally { setBusy(false); }
  }
  useEffect(() => setMsg(null), [selectedId]);

  const toggleVendor = (id) => {
    const cur = pickedVendors ?? vendors.map((v) => v.id);
    const next = cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id];
    setPickedVendors(next.length === vendors.length ? null : next);
  };
  const vendorOn = (id) => !pickedVendors || pickedVendors.includes(id);

  return (
    <div className="discover">
      <aside className="filters" aria-label="Bộ lọc">
        <input type="search" className="search" placeholder="🔍 Tìm món..." value={q} onChange={(e) => setQ(e.target.value)} aria-label="Tìm món" />

        <fieldset><legend>Khu vực (UC4.1)</legend>
          <label className="opt"><input type="radio" name="area" checked={!areaId} onChange={() => setAreaId('')} /> Tất cả</label>
          {areas.map((a) => (
            <label key={a.id} className="opt"><input type="radio" name="area" checked={areaId === a.id} onChange={() => setAreaId(a.id)} /> {a.name}</label>
          ))}
        </fieldset>

        <fieldset><legend>Điểm bán</legend>
          {vendors.length === 0 && <span className="muted">Chưa có điểm bán.</span>}
          {vendors.map((v) => (
            <label key={v.id} className="opt"><input type="checkbox" checked={vendorOn(v.id)} onChange={() => toggleVendor(v.id)} /> {v.name}</label>
          ))}
        </fieldset>

        <fieldset><legend>Buổi · Loại món (UC4.3)</legend>
          <div className="chips">
            {Object.entries(SESSION).map(([k, label]) => (
              <button key={k} className={`chip ${session === k ? 'on' : ''}`} aria-pressed={session === k} onClick={() => setSession(session === k ? '' : k)}>{label}</button>
            ))}
            {Object.entries(CATEGORY).map(([k, label]) => (
              <button key={k} className={`chip ${category === k ? 'on' : ''}`} aria-pressed={category === k} onClick={() => setCategory(category === k ? '' : k)}>{label}</button>
            ))}
          </div>
          <label className="opt"><input type="checkbox" checked={availableOnly} onChange={(e) => setAvailableOnly(e.target.checked)} /> Chỉ món còn suất</label>
        </fieldset>
      </aside>

      <main className="menu" aria-busy={state === 'loading'}>
        {state === 'error' && <p className="error" role="alert">{error}</p>}
        {state === 'loading' && <p className="muted">Đang tải thực đơn...</p>}
        {state === 'ok' && groups.length === 0 && (
          <p className="empty">Không có món phù hợp. Thử đổi buổi, khu vực hoặc bỏ lọc "Chỉ món còn suất".</p>
        )}
        {groups.map((list) => <VendorMenu key={list[0].vendorId} list={list} selectedId={selectedId} onSelect={setSelectedId} onAdd={add} />)}
      </main>

      <DetailPanel item={selected} authed={authed} msg={msg} busy={busy} onAdd={add} />
    </div>
  );
}

function VendorMenu({ list, selectedId, onSelect, onAdd }) {
  const f = list[0];
  const open = list.some((i) => i.orderable);
  return (
    <section className="vendor-menu">
      <div className="vendor-head">
        <div>
          <h2>{f.vendorName} · Thực đơn buổi {SESSION[f.mealSession].toLowerCase()}</h2>
          <p className="muted">{f.areaName} · {f.pickupLocation} · {open ? 'Đang nhận đơn' : 'Đã đóng nhận đơn'} đến {hhmm(f.orderCutoffTime)} (UC4.2)</p>
        </div>
        <span className={`pill ${open ? 'green' : 'grey'}`}>{open ? 'Đang nhận đơn' : 'Đã đóng'}</span>
      </div>
      <ul className="grid">
        {list.map((d) => <DishCard key={key(d)} d={d} on={selectedId === key(d)} onSelect={() => onSelect(key(d))} onAdd={() => onAdd(d)} />)}
      </ul>
    </section>
  );
}

function DishCard({ d, on, onSelect, onAdd }) {
  const out = d.remaining <= 0;
  return (
    <li className={`dish ${on ? 'on' : ''} ${out ? 'out' : ''}`}>
      <button className="dish-open" onClick={onSelect} aria-pressed={on} aria-label={`Xem chi tiết ${d.dishName}`}>
        <div className="thumb">{d.imageUrl ? <img src={d.imageUrl} alt="" loading="lazy" /> : <span aria-hidden="true">{EMOJI[d.category] || '🍽'}</span>}</div>
        <b>{d.dishName}</b>
        <span className="muted">{out ? 'Hết suất' : `Còn ${d.remaining} suất`}</span>
      </button>
      <div className="dish-foot">
        <span className="price">{money(d.price)}</span>
        {out ? <span className="pill grey">Hết</span>
          : <button className="btn-sm" onClick={onAdd} disabled={!d.orderable} title={d.orderable ? '' : 'Quầy đã đóng nhận đơn'}>+ Thêm</button>}
      </div>
    </li>
  );
}

function DetailPanel({ item: d, authed, msg, busy, onAdd }) {
  return (
    <aside className="detail" aria-live="polite">
      <h3>Chi tiết món · UC4.4</h3>
      {!d ? <p className="muted">Chọn một món để xem mô tả, giá và nơi nhận.</p> : (
        <>
          <p className="muted dish-name">{d.dishName}</p>
          <p>{d.dishDescription || 'Chưa có mô tả.'}</p>
          <dl>
            <dt>Giá</dt><dd><b>{money(d.price)}</b></dd>
            <dt>Buổi</dt><dd><b>{SESSION[d.mealSession]}</b></dd>
            <dt>Nhận tại</dt><dd><b>{d.areaName} · {d.pickupLocation}</b></dd>
            <dt>Tình trạng</dt><dd><span className={`pill ${d.remaining > 0 ? 'green' : 'grey'}`}>{d.remaining > 0 ? `Còn ${d.remaining} suất` : 'Hết suất'}</span></dd>
            {d.pickupSlots?.length > 0 && <><dt>Khung nhận</dt><dd>{d.pickupSlots.map((s) => `${hhmm(s.startTime)}–${hhmm(s.endTime)}`).join(', ')}</dd></>}
          </dl>
          <p className="note info">Thêm vào giỏ không giữ suất.</p>
          {msg && <p className={msg.ok ? 'ok' : 'error'} role={msg.ok ? 'status' : 'alert'}>{msg.text}</p>}
          <button className="btn accent block" disabled={!d.orderable || busy} onClick={() => onAdd(d)}>
            {!d.orderable ? (d.remaining <= 0 ? 'Hết suất' : 'Đã đóng nhận đơn') : authed ? 'Thêm vào giỏ' : 'Đăng nhập để thêm vào giỏ'}
          </button>
        </>
      )}
    </aside>
  );
}
