import { useState } from 'react';
import { login, setTokens } from '../api.js';

/** UC1.5 Đăng nhập. Đăng ký nằm ở trang Thiết lập tài khoản. */
export default function Login({ onDone, goRegister }) {
  const [f, setF] = useState({ email: '', password: '' });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setErr(''); setBusy(true);
    try { const r = await login(f); setTokens(r); onDone(r); }
    catch (x) { setErr(x.message); } finally { setBusy(false); }
  }

  return (
    <main className="page narrow">
      <section className="card">
        <h2>Đăng nhập để đặt món</h2>
        <p className="muted">Đặt trước, đến quầy nhận món đúng khung giờ.</p>
        <form onSubmit={submit}>
          <label className="field"><span>Email</span>
            <input type="email" value={f.email} onChange={set('email')} required autoComplete="username" /></label>
          <label className="field"><span>Mật khẩu</span>
            <input type="password" value={f.password} onChange={set('password')} required autoComplete="current-password" /></label>
          {err && <p className="error" role="alert">{err}</p>}
          <button className="btn primary block" disabled={busy}>{busy ? 'Đang đăng nhập...' : 'Đăng nhập'}</button>
        </form>
        <p className="center"><button className="link" onClick={goRegister}>Chưa có tài khoản? Đăng ký</button></p>
      </section>
    </main>
  );
}
