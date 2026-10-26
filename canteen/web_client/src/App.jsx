import { useCallback, useEffect, useState } from 'react';
import { getToken, setTokens, getProfile, logoutApi, getCart } from './api.js';
import Cart from './pages/Cart.jsx';
import Login from './pages/Login.jsx';
import AccountSetup from './pages/AccountSetup.jsx';
import Discover from './pages/Discover.jsx';

// Điều hướng bằng hash (#/kham-pha, #/tai-khoan, #/dang-nhap, #/dang-ky) để không cần thêm thư viện.
const routeOf = () => window.location.hash.replace('#/', '') || 'kham-pha';
const go = (r) => { window.location.hash = `/${r}`; };

export default function App() {
  const [route, setRoute] = useState(routeOf());
  const [authed, setAuthed] = useState(!!getToken());
  const [profile, setProfile] = useState(null);
  const [cartCount, setCartCount] = useState(0);

  useEffect(() => {
    const on = () => setRoute(routeOf());
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
  }, []);

  const loadProfile = useCallback(async () => {
    try { setProfile(await getProfile()); } catch (x) { if (x.status === 401) expire(); }
  }, []);
  useEffect(() => { if (authed) loadProfile(); }, [authed, loadProfile]);
  const loadCart = useCallback(async () => {
    try { const c = await getCart(); setCartCount(c.items.reduce((s, l) => s + l.quantity, 0)); } catch { /* giữ số cũ */ }
  }, []);
  useEffect(() => { if (authed) loadCart(); else setCartCount(0); }, [authed, loadCart]);

  function expire() { setTokens(null); setProfile(null); setAuthed(false); setCartCount(0); }
  async function logout() { await logoutApi(); expire(); go('dang-nhap'); }

  // Chưa đăng nhập chỉ vào được trang đăng nhập và thiết lập tài khoản (đăng ký).
  const page = !authed && !['dang-ky', 'dang-nhap', 'kham-pha'].includes(route) ? 'dang-nhap' : route;

  return (
    <>
      <header className="nav">
        <a className="brand" href="#/kham-pha">🍽 Canteen Phenikaa</a>
        <nav aria-label="Chính">
          <a href="#/kham-pha" aria-current={page === 'kham-pha' ? 'page' : undefined}>Khám phá</a>
          {authed && <a href="#/gio-hang" aria-current={page === 'gio-hang' ? 'page' : undefined}>Giỏ hàng ({cartCount})</a>}
          {authed && <a href="#/tai-khoan" aria-current={page === 'tai-khoan' ? 'page' : undefined}>Tài khoản</a>}
        </nav>
        <div className="who">
          {authed
            ? <>{profile?.fullName} <button className="link" onClick={logout}>Đăng xuất</button></>
            : <a href="#/dang-nhap">Đăng nhập</a>}
        </div>
      </header>

      {page === 'dang-nhap' && <Login onDone={() => { setAuthed(true); go('kham-pha'); }} goRegister={() => go('dang-ky')} />}
      {(page === 'dang-ky' || page === 'tai-khoan') && (
        <AccountSetup
          profile={authed ? profile : null}
          onRegistered={() => { setAuthed(true); loadProfile(); go('tai-khoan'); }}
          onProfileChange={loadProfile}
        />
      )}
      {page === 'kham-pha' && <Discover authed={authed} onCartChange={loadCart} goLogin={() => go('dang-nhap')} />}
      {authed && page === 'gio-hang' && <Cart onCartChange={loadCart} goDiscover={() => go('kham-pha')} />}
    </>
  );
}
