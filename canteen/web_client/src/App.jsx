import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, getToken, setToken, money, SESSION, STATUS } from './api.js';

export default function App() {
  const [user, setUser] = useState(null);
  const [authed, setAuthed] = useState(!!getToken());
  const logout = () => { setToken(null); setUser(null); setAuthed(false); };

  return (
    <>
      <header className="top">
        <h1>Canteen Phenikaa</h1>
        {authed && <button className="link" onClick={logout}>Đăng xuất {user ? `(${user.fullName})` : ''}</button>}
      </header>
      {authed
        ? <Shop onUser={setUser} onExpired={logout} />
        : <Auth onDone={(u) => { setUser(u); setAuthed(true); }} />}
    </>
  );
}

function Auth({ onDone }) {
  const [mode, setMode] = useState('login');
  const [f, setF] = useState({ fullName: '', email: '', password: '' });
  const [err, setErr] = useState('');
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setErr('');
    try {
      const body = mode === 'login' ? { email: f.email, password: f.password } : f;
      const r = await api(`/api/auth/${mode}`, { method: 'POST', body });
      setToken(r.accessToken);
      onDone(r.user);
    } catch (x) { setErr(x.message); }
  }

  return (
    <main className="auth">
      <h2>{mode === 'login' ? 'Đăng nhập để đặt món' : 'Tạo tài khoản'}</h2>
      <p className="muted">Đặt trước, đến quầy nhận món đúng khung giờ.</p>
      <form onSubmit={submit}>
        {mode === 'register' && <label>Họ tên<input value={f.fullName} onChange={set('fullName')} required /></label>}
        <label>Email<input type="email" value={f.email} onChange={set('email')} required /></label>
        <label>Mật khẩu<input type="password" value={f.password} onChange={set('password')} required minLength={8} /></label>
        {err && <p className="error" role="alert">{err}</p>}
        <button className="primary">{mode === 'login' ? 'Đăng nhập' : 'Đăng ký'}</button>
      </form>
      <button className="link" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setErr(''); }}>
        {mode === 'login' ? 'Chưa có tài khoản? Đăng ký' : 'Đã có tài khoản? Đăng nhập'}
      </button>
    </main>
  );
}

function Shop({ onUser, onExpired }) {
  const [items, setItems] = useState([]);
  const [cart, setCart] = useState(null);
  const [order, setOrder] = useState(null);
  const [msg, setMsg] = useState('');

  const guard = useCallback((fn) => async (...a) => {
    setMsg('');
    try { return await fn(...a); }
    catch (x) { if (x.status === 401) onExpired(); else setMsg(x.message); }
  }, [onExpired]);

  const load = useMemo(() => guard(async () => {
    const [menu, c] = await Promise.all([api('/api/public/menu-items?size=100'), api('/api/cart')]);
    setItems(menu.content); setCart(c);
  }), [guard]);

  useEffect(() => { load(); api('/api/me/profile').then(onUser).catch(() => {}); }, [load, onUser]);

  const add = guard(async (id) => setCart(await api('/api/cart/items', { method: 'POST', body: { menuItemId: id, quantity: 1 } })));
  const setQty = guard(async (id, q) => setCart(q < 1
    ? await api(`/api/cart/items/${id}`, { method: 'DELETE' })
    : await api(`/api/cart/items/${id}`, { method: 'PUT', body: { quantity: q } })));
  const clear = guard(async () => { await api('/api/cart', { method: 'DELETE' }); load(); });
  const place = guard(async (slotId, key) => {
    const o = await api('/api/orders', { method: 'POST', body: { pickupSlotId: slotId }, headers: { 'Idempotency-Key': key } });
    setOrder(o); load();
  });

  const byVendor = useMemo(() => {
    const m = new Map();
    items.forEach((i) => m.set(i.vendorName, [...(m.get(i.vendorName) || []), i]));
    return [...m];
  }, [items]);
  const qtyOf = (id) => cart?.items.find((l) => l.menuItemId === id)?.quantity || 0;

  return (
    <div className="shop">
      <main>
        {msg && <p className="error" role="alert">{msg}</p>}
        {order && <OrderStatus order={order} onChange={setOrder} />}
        <h2>Thực đơn hôm nay</h2>
        {byVendor.length === 0 && <p className="muted">Chưa có món nào đang mở bán. Quay lại sau khi quầy công bố thực đơn.</p>}
        {byVendor.map(([vendor, list]) => (
          <section key={vendor}>
            <h3>{vendor}<span className="muted"> · {list[0].areaName}, {list[0].pickupLocation}</span></h3>
            <ul className="dishes">
              {list.map((d) => (
                <li key={d.menuItemId}>
                  <div className="info">
                    <b>{d.dishName}</b>
                    <span className="muted">{SESSION[d.mealSession]}, đóng nhận đơn {d.orderCutoffTime.slice(0, 5)}</span>
                    <span className="left" title={`Còn ${d.remaining} suất`}><i style={{ width: `${Math.min(100, d.remaining * 4)}%` }} /></span>
                    <span className="muted">{d.remaining > 0 ? `Còn ${d.remaining} suất` : 'Hết suất'}</span>
                  </div>
                  <div className="buy">
                    <span className="price">{money(d.price)}</span>
                    <button className="primary" disabled={!d.orderable} onClick={() => add(d.menuItemId)}>
                      {qtyOf(d.menuItemId) ? `Thêm nữa (${qtyOf(d.menuItemId)})` : 'Thêm vào giỏ'}
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </main>
      <Cart cart={cart} items={items} setQty={setQty} clear={clear} place={place} />
    </div>
  );
}

function Cart({ cart, items, setQty, clear, place }) {
  const [slot, setSlot] = useState('');
  const [key, setKey] = useState(() => crypto.randomUUID());   // một khóa cho mỗi lần đặt, bấm lại không tạo đơn thứ hai
  const slots = items.find((i) => i.menuId === cart?.menuId)?.pickupSlots || [];
  const empty = !cart || cart.items.length === 0;
  const blocked = cart?.items.some((l) => l.problem);

  return (
    <aside className="cart">
      <h2>Giỏ hàng</h2>
      {empty ? <p className="muted">Chưa có món nào. Chọn món ở bên trái.</p> : (
        <>
          <p className="muted">{cart.vendorName}, buổi {SESSION[cart.mealSession]} ngày {cart.menuDate}</p>
          <ul>
            {cart.items.map((l) => (
              <li key={l.menuItemId}>
                <div><b>{l.dishName}</b><span className="muted">{money(l.unitPrice)}</span>
                  {l.problem && <span className="error">{l.problem === 'QUOTA_EXCEEDED' ? `Chỉ còn ${l.availableOnline} suất` : 'Món này không đặt được nữa'}</span>}
                </div>
                <div className="qty">
                  <button aria-label="Giảm" onClick={() => setQty(l.menuItemId, l.quantity - 1)}>−</button>
                  <span>{l.quantity}</span>
                  <button aria-label="Tăng" onClick={() => setQty(l.menuItemId, l.quantity + 1)}>+</button>
                </div>
              </li>
            ))}
          </ul>
          <label>Khung giờ nhận
            <select value={slot} onChange={(e) => setSlot(e.target.value)}>
              <option value="">Chọn khung giờ</option>
              {slots.map((s) => <option key={s.id} value={s.id}>{s.startTime.slice(0, 5)} đến {s.endTime.slice(0, 5)}</option>)}
            </select>
          </label>
          <dl>
            <dt>Tạm tính</dt><dd>{money(cart.subtotal)}</dd>
            {cart.estimatedDiscount > 0 && <><dt>Ưu đãi đơn đầu</dt><dd>−{money(cart.estimatedDiscount)}</dd></>}
            <dt>Thanh toán tại quầy</dt><dd><b>{money(cart.estimatedTotal)}</b></dd>
          </dl>
          <button className="primary wide" disabled={!slot || blocked}
            onClick={async () => { await place(Number(slot), key); setKey(crypto.randomUUID()); setSlot(''); }}>
            Đặt đơn
          </button>
          <button className="link" onClick={clear}>Xóa giỏ hàng</button>
        </>
      )}
    </aside>
  );
}

function OrderStatus({ order, onChange }) {
  useEffect(() => {
    if (order.status !== 'PENDING') return undefined;
    const t = setInterval(async () => {
      try { onChange(await api(`/api/orders/${order.id}`)); } catch { /* giữ trạng thái cũ */ }
    }, 8000);
    return () => clearInterval(t);
  }, [order.id, order.status, onChange]);

  return (
    <section className="order" aria-live="polite">
      <h3>Đơn {order.orderCode}: {STATUS[order.status]}</h3>
      <p>{order.vendorName}, nhận lúc {order.pickupStart.slice(0, 5)} đến {order.pickupEnd.slice(0, 5)} tại {order.pickupLocation}</p>
      <p>{order.items.map((i) => `${i.quantity} × ${i.dishName}`).join(', ')}. Trả {money(order.totalAmount)} khi nhận món.</p>
      {order.status === 'PENDING' && <p className="muted">Quầy cần xác nhận trước {order.pendingExpiresAt.slice(11, 16)}, quá giờ đơn sẽ tự hết hạn.</p>}
    </section>
  );
}
