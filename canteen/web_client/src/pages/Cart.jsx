import { useCallback, useEffect, useRef, useState } from 'react';
import {
  getCart, updateCartItem, removeCartItem, clearCart, searchMenuItems, getAccountStatus, placeOrder, getOrder,
  money, SESSION, STATUS,
} from '../api.js';

// Điều khoản ưu đãi đơn đầu theo chính sách đã chốt (mục 1.1.1); số tiền thật do server tính trong estimatedDiscount.
const PROMO = { percent: 50, max: 30000, min: 25000 };
const hhmm = (t) => t?.slice(0, 5);
const nowHHmm = () => new Date().toTimeString().slice(0, 5);
const daysLeft = (iso) => Math.max(0, Math.ceil((new Date(iso) - Date.now()) / 86400000));

/** Hình 20: giỏ hàng, khung nhận và đặt món (UC5.1 đến UC5.5). */
export default function Cart({ onCartChange, goDiscover }) {
  const [cart, setCart] = useState(null);
  const [place, setPlace] = useState(null);          // { slots, location } của thực đơn trong giỏ
  const [promo, setPromo] = useState(undefined);     // undefined = đang tải, null = không có ưu đãi
  const [slotId, setSlotId] = useState(null);
  const [useVoucher, setUseVoucher] = useState(true);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const [order, setOrder] = useState(null);
  const keyRef = useRef(crypto.randomUUID());        // một khóa cho mỗi lần đặt

  const load = useCallback(async () => {
    try {
      const c = await getCart();
      setCart(c);
      onCartChange();
      if (c.items.length) {
        // Giỏ không kèm khung nhận, lấy từ thực đơn cùng quầy, ngày, buổi.
        const r = await searchMenuItems({ date: c.menuDate, session: c.mealSession, vendorId: c.vendorId });
        const m = r.content.find((i) => i.menuId === c.menuId) || r.content[0];
        setPlace(m ? { slots: m.pickupSlots || [], location: `${m.areaName} · ${m.pickupLocation}` } : { slots: [], location: '' });
      }
    } catch (x) { setErr(x.message); }
  }, [onCartChange]);

  useEffect(() => { load(); getAccountStatus().then((s) => setPromo(s.promotion || null)).catch(() => setPromo(null)); }, [load]);

  const act = (fn) => async (...a) => { setErr(''); try { await fn(...a); await load(); } catch (x) { setErr(x.message); } };
  const setQty = act((id, q) => (q < 1 ? removeCartItem(id) : updateCartItem(id, q)));
  const remove = act((id) => removeCartItem(id));
  const clear = act(() => clearCart());

  async function submit() {
    setErr(''); setBusy(true);
    try {
      const o = await placeOrder({ pickupSlotId: slotId, useVoucher: voucherOk && useVoucher }, keyRef.current);
      keyRef.current = crypto.randomUUID();
      setOrder(o); setSlotId(null); onCartChange();
    } catch (x) { setErr(x.message); } finally { setBusy(false); }
  }

  if (order) return <Confirmation order={order} onChange={setOrder} goDiscover={() => { setOrder(null); load(); goDiscover(); }} />;
  if (!cart) return <main className="page"><p className="muted">{err || 'Đang tải giỏ hàng...'}</p></main>;
  if (cart.items.length === 0) return (
    <main className="page narrow">
      <section className="card center"><h2>Giỏ hàng trống</h2>
        <p className="muted">Chọn món ở trang Khám phá. Giỏ hàng chỉ lưu lựa chọn, chưa giữ suất.</p>
        <button className="btn primary" onClick={goDiscover}>Đi tới Khám phá</button></section>
    </main>
  );

  // Ưu đãi: có quyền AVAILABLE còn hạn thì mới có lựa chọn; số tiền giảm do server ước tính.
  const entitled = promo?.status === 'AVAILABLE' && new Date(promo.expiresAt) > new Date();
  const voucherOk = entitled && cart.estimatedDiscount > 0;
  const discount = voucherOk && useVoucher ? cart.estimatedDiscount : 0;
  const total = cart.subtotal - discount;
  const blocked = cart.items.some((l) => l.problem);
  const now = nowHHmm();
  const isToday = cart.menuDate === new Date().toLocaleDateString('sv-SE');
  const slotState = (s) => (s.full || s.available === false ? 'full' : isToday && hhmm(s.endTime) <= now ? 'past' : 'ok');
  const fullOnes = (place?.slots || []).filter((s) => slotState(s) === 'full');

  return (
    <main className="page">
      <h2 className="page-title">Giỏ hàng và đặt món · {cart.vendorName}</h2>
      <div className="cart-grid">
        <div>
          <section className="card" aria-labelledby="c1">
            <h3 id="c1" className="step-title">Món đã chọn · UC5.1</h3>
            <div className="table-wrap"><table className="lines">
              <thead><tr><th>Món</th><th>Đơn giá</th><th>Số lượng</th><th>Thành tiền</th><th><span className="sr">Xóa</span></th></tr></thead>
              <tbody>
                {cart.items.map((l) => (
                  <tr key={l.menuItemId}>
                    <td>{l.dishName}{l.problem && <div className="error">{l.problem === 'QUOTA_EXCEEDED' ? `Chỉ còn ${l.availableOnline} suất` : 'Món này không đặt được nữa'}</div>}</td>
                    <td>{money(l.unitPrice)}</td>
                    <td><div className="qty">
                      <button aria-label={`Giảm ${l.dishName}`} onClick={() => setQty(l.menuItemId, l.quantity - 1)}>−</button>
                      <span aria-live="polite">{l.quantity}</span>
                      <button aria-label={`Tăng ${l.dishName}`} onClick={() => setQty(l.menuItemId, l.quantity + 1)}>+</button>
                    </div></td>
                    <td>{money(l.lineTotal)}</td>
                    <td><button className="link muted-link" onClick={() => remove(l.menuItemId)}>Xóa</button></td>
                  </tr>
                ))}
              </tbody>
            </table></div>
            <p className="note warn">Giỏ chỉ lưu lựa chọn, chưa giữ suất. Mỗi đơn chỉ gồm món của một quầy.</p>
            <button className="link" onClick={clear}>Xóa toàn bộ giỏ hàng</button>
          </section>

          <section className="card" aria-labelledby="c2" style={{ marginTop: '1.25rem' }}>
            <h3 id="c2" className="step-title">Khung nhận · Buổi {SESSION[cart.mealSession]?.toLowerCase()} · UC5.2</h3>
            {(place?.slots || []).length === 0
              ? <p className="muted">Thực đơn này chưa có khung nhận khả dụng.</p>
              : <div className="slots" role="radiogroup" aria-label="Khung nhận">
                {place.slots.map((s) => {
                  const st = slotState(s);
                  return (
                    <span key={s.id} className="slot-wrap">
                      <button role="radio" aria-checked={slotId === s.id} disabled={st !== 'ok'}
                        className={`slot ${slotId === s.id ? 'on' : ''} ${st !== 'ok' ? 'off' : ''}`} onClick={() => setSlotId(s.id)}>
                        {hhmm(s.startTime)}
                      </button>
                      {st === 'full' && <span className="pill red">Đã đầy</span>}
                      {st === 'past' && <span className="pill grey">Đã qua</span>}
                    </span>
                  );
                })}
              </div>}
            <p className="muted">
              {fullOnes.length > 0 && <>Khung <b>{fullOnes.map((s) => hhmm(s.startTime)).join(' và ')}</b> đã đầy, không thể chọn. </>}
              Nhận và thanh toán tại {place?.location || cart.vendorName}.
            </p>
          </section>
        </div>

        <section className="card" aria-labelledby="c3">
          <h3 id="c3" className="step-title">Ưu đãi đơn đầu · UC5.5</h3>
          {voucherOk ? (
            <div role="radiogroup" aria-label="Dùng ưu đãi đơn đầu">
              <label className={`choice ${useVoucher ? 'on' : ''}`}>
                <input type="radio" name="v" checked={useVoucher} onChange={() => setUseVoucher(true)} />
                <span><b>Dùng voucher</b> — giảm {PROMO.percent}%, tối đa {money(PROMO.max)}</span>
              </label>
              <label className={`choice ${!useVoucher ? 'on' : ''}`}>
                <input type="radio" name="v" checked={!useVoucher} onChange={() => setUseVoucher(false)} />
                <span>Không dùng voucher (giữ lại cho đơn sau)</span>
              </label>
              <p className="note danger">Nếu bạn hủy đơn vì lý do cá nhân, voucher sẽ bị mất và không được hoàn lại.</p>
            </div>
          ) : promo === undefined ? <p className="muted">Đang kiểm tra ưu đãi...</p>
            : entitled ? <p className="note warn">Đơn cần từ {money(PROMO.min)} để dùng ưu đãi. Hiện tạm tính {money(cart.subtotal)}.</p>
            : <p className="note info">Bạn chưa có ưu đãi đơn đầu khả dụng. Xác minh email trường để nhận ưu đãi một lần.</p>}

          <dl className="sum">
            <dt>Tạm tính</dt><dd>{money(cart.subtotal)}</dd>
            {discount > 0 && <><dt className="green">Ưu đãi đơn đầu ({PROMO.percent}%, tối đa {money(PROMO.max)})</dt><dd className="green"><b>−{money(discount)}</b></dd></>}
          </dl>
          <div className="total"><span>Phải trả tại quầy</span><b>{money(total)}</b></div>
          {voucherOk && <p className="note success">Đủ điều kiện ưu đãi: đơn từ {money(PROMO.min)}, còn hạn {daysLeft(promo.expiresAt)} ngày.</p>}

          {err && <p className="error" role="alert">{err}</p>}
          <button className="btn accent block" disabled={!slotId || blocked || busy} onClick={submit}>{busy ? 'Đang đặt...' : 'Đặt món · UC5.3'}</button>
          {!slotId && <p className="muted center">Chọn khung nhận để đặt món.</p>}
          <p className="note info">Đơn Pending giữ suất 5 phút để quầy xác nhận (UC5.4). Quá hạn: <b>Hết hạn xác nhận — Expired</b>, trả lại suất và ưu đãi.</p>
        </section>
      </div>
    </main>
  );
}

/** Sau khi đặt: đơn Pending giữ suất, đếm ngược hạn quầy xác nhận và tự cập nhật trạng thái (UC5.4). */
function Confirmation({ order, onChange, goDiscover }) {
  const [left, setLeft] = useState(0);
  useEffect(() => {
    if (order.status !== 'PENDING') return undefined;
    const t = setInterval(async () => { try { onChange(await getOrder(order.id)); } catch { /* giữ trạng thái cũ */ } }, 8000);
    return () => clearInterval(t);
  }, [order.id, order.status, onChange]);
  useEffect(() => {
    const tick = () => setLeft(Math.max(0, Math.floor((new Date(order.pendingExpiresAt) - Date.now()) / 1000)));
    tick();
    const t = setInterval(tick, 1000);
    return () => clearInterval(t);
  }, [order.pendingExpiresAt]);

  const pending = order.status === 'PENDING';
  return (
    <main className="page narrow wide-ok">
      <section className="card" aria-live="polite">
        <h2>Đơn {order.orderCode}: {STATUS[order.status]}</h2>
        <p>{order.vendorName} · nhận lúc {hhmm(order.pickupStart)} đến {hhmm(order.pickupEnd)} tại {order.pickupLocation}</p>
        <ul className="plain">{order.items.map((i) => <li key={i.menuItemId}>{i.quantity} × {i.dishName} <span className="muted">{money(i.lineTotal)}</span></li>)}</ul>
        <dl className="sum">
          <dt>Tạm tính</dt><dd>{money(order.subtotalAmount)}</dd>
          {order.discountAmount > 0 && <><dt className="green">Ưu đãi đơn đầu</dt><dd className="green">−{money(order.discountAmount)}</dd></>}
        </dl>
        <div className="total"><span>Trả tại quầy khi nhận món</span><b>{money(order.totalAmount)}</b></div>
        {pending && <p className="note info">Suất đang được giữ. Quầy cần xác nhận trong {String(Math.floor(left / 60)).padStart(2, '0')}:{String(left % 60).padStart(2, '0')}, quá hạn đơn tự hết hạn và trả lại suất, ưu đãi.</p>}
        {order.status === 'EXPIRED' && <p className="note warn">Quầy chưa xác nhận kịp nên đơn đã hết hạn. Suất và ưu đãi đã được trả lại, bạn có thể đặt lại.</p>}
        <button className="btn primary block" onClick={goDiscover}>Tiếp tục khám phá</button>
      </section>
    </main>
  );
}
