import { useEffect, useMemo, useState } from 'react';
import { getVendorStats, money, SESSION } from '../api.js';

const DOW = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
const ymd = (d) => d.toLocaleDateString('sv-SE');
const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
const parse = (s) => new Date(`${s}T00:00:00`);

const RANGES = {
  today: { label: 'Hôm nay', get: (t) => [t, t] },
  week: { label: 'Tuần này', get: (t) => [addDays(t, -((t.getDay() + 6) % 7)), t] },        // từ thứ Hai
  month: { label: 'Tháng', get: (t) => [new Date(t.getFullYear(), t.getMonth(), 1), t] },
};

/** Hình 24 (UC9): thống kê điểm bán cho quản lý quầy. Số liệu lấy từ /api/manager/stats, không tự tính lại ở trình duyệt. */
export default function ManagerStats({ vendor }) {
  const [range, setRange] = useState('week');
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');

  useEffect(() => {
    const [f, t] = RANGES[range].get(new Date());
    let live = true;
    setData(null); setErr('');
    getVendorStats(vendor.id, ymd(f), ymd(t)).then((d) => { if (live) setData(d); }).catch((x) => { if (live) setErr(x.message); });
    return () => { live = false; };
  }, [vendor.id, range]);

  return (
    <>
      <div className="page-head">
        <h2 className="page-title">Thống kê điểm bán</h2>
        <div className="tabs" role="tablist" aria-label="Khoảng thời gian">
          {Object.entries(RANGES).map(([k, r]) => (
            <button key={k} role="tab" aria-selected={k === range} className={`chip ${k === range ? 'on' : ''}`} onClick={() => setRange(k)}>{r.label}</button>
          ))}
        </div>
      </div>
      {err && <p className="error" role="alert">{err}</p>}
      {!data && !err && <p className="muted">Đang tải thống kê...</p>}
      {data && <Body d={data} />}
    </>
  );
}

function Body({ d }) {
  const st = d.orders.byStatus;
  const cancelled = (st.CANCELLED || 0) + (st.EXPIRED || 0);
  const w = d.waste;
  const rv = d.revenue;

  // Số đơn theo ngày: gộp các buổi, điền 0 cho ngày không có đơn để cột liền nhau.
  const days = useMemo(() => {
    const by = {};
    d.orders.breakdown.forEach((r) => { by[r.date] = (by[r.date] || 0) + r.orders; });
    const out = [];
    for (let x = parse(d.from); x <= parse(d.to); x = addDays(x, 1)) out.push({ key: ymd(x), n: by[ymd(x)] || 0, date: x });
    return out;
  }, [d]);
  const max = Math.max(1, ...days.map((x) => x.n));
  const long = days.length > 8;

  const peak = useMemo(() => {
    const by = {};
    d.orders.breakdown.forEach((r) => { by[r.session] = (by[r.session] || 0) + r.orders; });
    const top = Object.entries(by).sort((a, b) => b[1] - a[1])[0];
    return top && d.orders.total ? { session: top[0], pct: Math.round((top[1] * 100) / d.orders.total) } : null;
  }, [d]);

  return (
    <>
      <div className="stat-cards">
        <section className="card stat">
          <p className="stat-lbl">Tổng đơn (UC9.1)</p>
          <p className="stat-big">{d.orders.total}</p>
          <p className="stat-sub ok-text">Completed {st.COMPLETED || 0} · Hủy {cancelled} · NotCollected {st.NOT_COLLECTED || 0}</p>
        </section>
        <section className="card stat">
          <p className="stat-lbl">Thực thu – Completed &amp; Paid (UC9.2)</p>
          <p className="stat-big">{money(rv.netRevenue)}</p>
          <p className="stat-sub">Trước giảm {money(rv.grossAmount)} · {rv.completedPaidOrders} đơn</p>
        </section>
        <section className="card stat">
          <p className="stat-lbl">Chi phí ưu đãi đơn đầu · {rv.discountedOrders} đơn áp dụng</p>
          <p className="stat-big">{money(rv.discountAmount)}</p>
          <div className="split">
            <span className="split-pill sp-plat">Nền tảng <b>{money(rv.funding.platformAmount)}</b></span>
            <span className="split-pill sp-shop">Quầy <b>{money(rv.funding.vendorAmount)}</b></span>
          </div>
        </section>
        <section className="card stat">
          <p className="stat-lbl">Không được nhận (UC9.4)</p>
          <p className="stat-big">{w.notCollectedPortions} suất</p>
          <p className="stat-sub">Bán lại {w.resold} · <b>Hao hụt thực tế {w.wasted}</b>{w.unprocessed > 0 && <> · Chưa xử lý {w.unprocessed}</>}</p>
        </section>
      </div>

      <Reimbursement f={rv.funding} />

      <div className="two-cards" style={{ marginTop: '1.25rem' }}>
        <section className="card" aria-labelledby="bd">
          <h3 id="bd" className="step-title">Số đơn theo ngày</h3>
          {d.orders.total === 0 ? <p className="muted">Chưa có đơn trong khoảng này.</p> : (
            <div className="bars" role="img" aria-label="Biểu đồ số đơn theo ngày">
              {days.map((x) => (
                <div key={x.key} className="bar-col" title={`${x.key}: ${x.n} đơn`}>
                  <span className="bar-n">{x.n}</span>
                  <div className="bar" style={{ height: `${(x.n / max) * 100}%` }} />
                  <span className="bar-x">{long ? x.date.getDate() : DOW[x.date.getDay()]}</span>
                </div>
              ))}
            </div>
          )}
          {peak && <p className="muted">Cao điểm: buổi {SESSION[peak.session]?.toLowerCase()} ({peak.pct}% số đơn).</p>}
        </section>

        <section className="card" aria-labelledby="dish">
          <h3 id="dish" className="step-title">Thống kê món (UC9.3)</h3>
          {d.dishes.length === 0 ? <p className="muted">Chưa có món nào được đặt.</p> : (
            <div className="table-wrap"><table className="lines dish-stats">
              <thead><tr><th>Món</th><th>Suất đặt</th><th>Suất bán thực tế</th><th>Suất hủy</th><th>Không nhận</th></tr></thead>
              <tbody>
                {d.dishes.map((x) => (
                  <tr key={x.dishId}><td>{x.dishName}</td><td>{x.ordered}</td><td>{x.sold}</td><td>{x.cancelled}</td><td>{x.notCollected}</td></tr>
                ))}
              </tbody>
            </table></div>
          )}
        </section>
      </div>

      <p className="note-box">
        Đặt = tất cả đơn tạo; bán thực tế = Completed; hủy gồm cả hủy phía khách và phía quầy (Cancelled, Expired).
        “Không được nhận” gồm bán lại và hao hụt thực tế, tách riêng để đọc đúng nhu cầu. Suất đang xử lý chưa tính vào các cột còn lại.
      </p>
    </>
  );
}

/** Khoản bù từ nền tảng: nền tảng chịu f.platformPercent% mỗi khoản giảm; đã nhận là các lần chuyển khoản admin đã ghi. */
function Reimbursement({ f }) {
  const total = f.platformAmount;
  const got = Math.min(f.reimbursed, total);
  const pct = total > 0 ? (got * 100) / total : 0;
  return (
    <section className="card reimb" aria-labelledby="rb" style={{ marginTop: '1.25rem' }}>
      <h3 id="rb" className="step-title">Khoản bù từ nền tảng · {money(total)} <span className="muted">({f.platformPercent}% chi phí ưu đãi)</span></h3>
      <div className="meter" role="img" aria-label={`Đã nhận ${money(got)} trên ${money(total)}`}>
        <div className="meter-got" style={{ width: `${pct}%` }} />
      </div>
      <div className="meter-legend">
        <span className="got">■ Đã nhận {money(f.reimbursed)}</span>
        <span className="due">■ Chưa nhận {money(f.outstanding)}</span>
      </div>
    </section>
  );
}
