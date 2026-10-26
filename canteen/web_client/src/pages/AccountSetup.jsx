import { useEffect, useRef, useState } from 'react';
import {
  register, verifyEmail, resendEmailCode, getAccountStatus, updateProfile, scanStudentCard, setTokens,
} from '../api.js';
import OtpInput from '../components/OtpInput.jsx';

const PASS_RULE = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;
const COOLDOWN = 60;          // khớp app.auth.otp-resend-cooldown = 1 phút
const LOW_CONFIDENCE = 0.8;   // dưới ngưỡng này ô được đánh dấu "cần kiểm tra"

/** Hình 18: thiết lập tài khoản trường gồm 3 bước (UC1.1 đến UC1.4). */
export default function AccountSetup({ profile, onRegistered, onProfileChange }) {
  const [cooldown, setCooldown] = useState(0);
  const [status, setStatus] = useState(null);

  // Trạng thái email trường (schoolEmail, emailVerified) lấy từ account-status, cập nhật lại sau khi xác minh.
  useEffect(() => {
    if (!profile) { setStatus(null); return; }
    getAccountStatus().then(setStatus).catch(() => {});
  }, [profile?.id, profile?.emailVerified]);

  useEffect(() => {
    if (cooldown <= 0) return undefined;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  return (
    <main className="page">
      <div className="page-head">
        <h2>Thiết lập tài khoản trường</h2>
        <span className="muted">Xác minh email trường để sử dụng ưu đãi một lần</span>
      </div>
      <div className="steps3">
        <RegisterCard profile={profile} onRegistered={(t) => { setCooldown(COOLDOWN); onRegistered(t); }} />
        <VerifyCard
          profile={profile} status={status} cooldown={cooldown} setCooldown={setCooldown}
          onVerified={() => onProfileChange()}
        />
        <StudentCardStep profile={profile} onSaved={onProfileChange} />
      </div>
    </main>
  );
}

function StepTitle({ n, title, tag, tagKind, done }) {
  return (
    <h3 className="step-title">
      <span className={`num ${done ? 'done' : ''}`} aria-hidden="true">{done ? '✓' : n}</span>
      {title}
      {tag && <span className={`tag ${tagKind}`}>{tag}</span>}
    </h3>
  );
}

/* ---------- 1. Đăng ký (UC1.1) ---------- */
function RegisterCard({ profile, onRegistered }) {
  const [f, setF] = useState({ fullName: '', email: '', password: '', confirm: '' });
  const [errs, setErrs] = useState({});
  const [serverErr, setServerErr] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => { setF({ ...f, [k]: e.target.value }); setErrs({ ...errs, [k]: '' }); };

  function validate() {
    const e = {};
    if (!f.fullName.trim()) e.fullName = 'Nhập họ và tên.';
    if (!/^\S+@\S+\.\S+$/.test(f.email.trim())) e.email = 'Email chưa đúng định dạng.';
    if (!PASS_RULE.test(f.password)) e.password = 'Mật khẩu dài 8 đến 72 ký tự, có cả chữ và số.';
    if (f.confirm !== f.password) e.confirm = 'Mật khẩu nhập lại chưa khớp.';
    setErrs(e);
    return Object.keys(e).length === 0;
  }

  async function submit(ev) {
    ev.preventDefault();
    setServerErr('');
    if (!validate()) return;
    setBusy(true);
    try {
      const r = await register({ fullName: f.fullName.trim(), email: f.email.trim(), password: f.password });
      setTokens(r);
      onRegistered(r);
    } catch (x) { setServerErr(x.message); } finally { setBusy(false); }
  }

  const created = !!profile;
  return (
    <section className="card" aria-labelledby="s1">
      <StepTitle n={1} title={<span id="s1">Đăng ký · UC1.1</span>} done={created} />
      <form onSubmit={submit} noValidate>
        <Field label="Họ và tên" error={errs.fullName}>
          <input value={created ? profile.fullName : f.fullName} onChange={set('fullName')} disabled={created} autoComplete="name" />
        </Field>
        <Field label="Email (ưu tiên email trường)" error={errs.email}>
          <input type="email" value={created ? profile.email : f.email} onChange={set('email')} disabled={created} autoComplete="email" />
        </Field>
        {!created && (
          <div className="two">
            <Field label="Mật khẩu" error={errs.password}>
              <input type="password" value={f.password} onChange={set('password')} autoComplete="new-password" />
            </Field>
            <Field label="Nhập lại" error={errs.confirm}>
              <input type="password" value={f.confirm} onChange={set('confirm')} autoComplete="new-password" />
            </Field>
          </div>
        )}
        {errs.password && <p className="error" role="alert">{errs.password}</p>}
        {errs.confirm && <p className="error" role="alert">{errs.confirm}</p>}
        {serverErr && <p className="error" role="alert">{serverErr}</p>}
        {!created && <button className="btn primary block" disabled={busy}>{busy ? 'Đang tạo...' : 'Tạo tài khoản'}</button>}
        {created && <p className="ok">✔ Đã tạo tài khoản</p>}
      </form>
    </section>
  );
}

function Field({ label, error, children }) {
  return <label className={`field ${error ? 'bad' : ''}`}><span>{label}</span>{children}</label>;
}

/* ---------- 2. Xác minh email (UC1.2) ---------- */
function VerifyCard({ profile, status, cooldown, setCooldown, onVerified }) {
  const [code, setCode] = useState('');
  const [err, setErr] = useState('');
  const [info, setInfo] = useState('');
  const [busy, setBusy] = useState(false);
  const locked = !profile;
  const verified = profile?.emailVerified;
  const notSchool = status && !status.schoolEmail;

  async function submit(c = code) {
    if (c.length !== 6) { setErr('Nhập đủ 6 chữ số.'); return; }
    setErr(''); setBusy(true);
    try { await verifyEmail(c); setCode(''); onVerified(); }
    catch (x) { setErr(x.message); } finally { setBusy(false); }
  }

  async function resend() {
    setErr(''); setInfo('');
    try { await resendEmailCode(); setCooldown(COOLDOWN); setCode(''); setInfo('Đã gửi mã mới tới email của bạn.'); }
    catch (x) { setErr(x.message); }
  }

  return (
    <section className={`card ${locked ? 'locked' : ''}`} aria-labelledby="s2" aria-disabled={locked}>
      <StepTitle n={2} title={<span id="s2">Xác minh email · UC1.2</span>} tag="Bắt buộc" tagKind="warn" done={verified} />
      {locked && <p className="muted">Tạo tài khoản ở bước 1 để nhận mã xác minh.</p>}
      {!locked && verified && <p className="note success">Email trường {profile.email} đã được xác minh. Bạn có thể dùng ưu đãi đơn đầu.</p>}
      {!locked && !verified && notSchool && (
        <p className="note info">Email này không thuộc miền của trường nên không thể xác minh và không có ưu đãi dành cho tài khoản trường. Bạn vẫn đặt món được bình thường.</p>
      )}
      {!locked && !verified && !notSchool && (
        <>
          <p className="muted">Mã 6 chữ số đã gửi tới email trường, hiệu lực 10 phút.</p>
          <OtpInput value={code} onChange={(v) => { setCode(v); setErr(''); }} onComplete={submit} disabled={busy} />
          <p className="center muted" aria-live="polite">
            {cooldown > 0
              ? `Gửi lại sau ${cooldown} giây`
              : <button type="button" className="link" onClick={resend}>Gửi lại mã</button>}
          </p>
          {err && <p className="error" role="alert">{err}</p>}
          {info && <p className="ok" role="status">{info}</p>}
          <button className="btn primary block" disabled={busy || code.length < 6} onClick={() => submit()}>Xác minh</button>
          <button className="btn outline block" disabled title="Máy chủ hiện chỉ hỗ trợ xác minh bằng mã OTP">Xác minh bằng liên kết</button>
        </>
      )}
      <p className="note warn">Nhập tay hoặc OCR không thay thế bước này.</p>
    </section>
  );
}

/* ---------- 3. Thẻ sinh viên, tùy chọn (UC1.3, UC1.4) ---------- */
function StudentCardStep({ profile, onSaved }) {
  const [skipped, setSkipped] = useState(false);
  const [preview, setPreview] = useState('');
  const [scan, setScan] = useState({ state: 'idle', msg: '' });    // idle | scanning | done | manual
  const [form, setForm] = useState({ studentCode: '', fullName: '', faculty: '' });
  const [conf, setConf] = useState({});
  const [manual, setManual] = useState(false);
  const [err, setErr] = useState('');
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const fileRef = useRef(null);
  const locked = !profile;

  useEffect(() => {
    if (!profile) return;
    setForm((f) => ({ ...f, fullName: f.fullName || profile.fullName, studentCode: f.studentCode || profile.studentCode || '' }));
  }, [profile?.id]);
  useEffect(() => () => preview && URL.revokeObjectURL(preview), [preview]);

  async function pick(e) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setErr('');
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) { setErr('Chỉ nhận ảnh JPEG, PNG hoặc WebP.'); return; }
    if (file.size > 3 * 1024 * 1024) { setErr('Ảnh tối đa 3 MB.'); return; }
    setPreview(URL.createObjectURL(file));
    setScan({ state: 'scanning', msg: 'Đang nhận dạng thẻ...' });
    try {
      const r = await scanStudentCard(file);
      setForm({ studentCode: r.studentCode || '', fullName: r.fullName || form.fullName, faculty: r.faculty || '' });
      setConf(r.confidence || {});
      const found = ['studentCode', 'fullName', 'faculty'].filter((k) => r[k]).length;
      setScan({ state: 'done', msg: `Đã nhận dạng ${found}/3 trường. Kiểm tra và sửa trước khi lưu.` });
      setManual(true);
    } catch (x) {
      // Server chưa có OCR (404) hoặc nhận dạng lỗi: không chặn người dùng, chuyển sang nhập tay.
      setScan({ state: 'manual', msg: 'Chưa nhận dạng tự động được. Bạn hãy nhập thông tin bằng tay.' });
      setManual(true);
    }
  }

  async function save() {
    setErr('');
    if (!form.fullName.trim()) { setErr('Nhập họ và tên.'); return; }
    setBusy(true);
    try {
      // Server hiện chỉ lưu họ tên, mã sinh viên và số điện thoại; khoa/ngành chưa có cột trong hồ sơ.
      await updateProfile({ fullName: form.fullName.trim(), studentCode: form.studentCode.trim(), phone: profile.phone || '' });
      setSaved(true);
      onSaved();
    } catch (x) { setErr(x.message); } finally { setBusy(false); }
  }

  const flag = (k) => (scan.state === 'done' && (conf[k] ?? 1) < LOW_CONFIDENCE ? 'warn' : '');
  const set = (k) => (e) => { setForm({ ...form, [k]: e.target.value }); setSaved(false); };

  return (
    <section className={`card dashed ${locked ? 'locked' : ''}`} aria-labelledby="s3" aria-disabled={locked}>
      <StepTitle n={3} title={<span id="s3">Thẻ sinh viên · UC1.3, UC1.4</span>} tag="Tùy chọn" tagKind="grey" />
      {skipped ? (
        <>
          <p className="note info">Bạn đã bỏ qua bước này, có thể bổ sung sau.</p>
          <button className="link" onClick={() => setSkipped(false)}>Bổ sung ngay</button>
        </>
      ) : (
        <>
          <p className="note info">Bước này không bắt buộc, bạn có thể bỏ qua và bổ sung sau.</p>
          <input ref={fileRef} type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={pick} />
          <button className="dropzone" disabled={locked || scan.state === 'scanning'} onClick={() => fileRef.current.click()}>
            {preview ? <img src={preview} alt="Ảnh thẻ sinh viên đã tải lên" /> : '📷 Tải ảnh thẻ lên (tùy chọn)'}
          </button>
          {scan.msg && <p className={`note ${scan.state === 'done' ? 'success' : scan.state === 'manual' ? 'warn' : 'info'}`} aria-live="polite">{scan.msg}</p>}
          {(manual || saved) && (
            <div>
              <div className="two">
                <Field label="Mã sinh viên"><input className={flag('studentCode')} value={form.studentCode} onChange={set('studentCode')} /></Field>
                <Field label="Họ và tên"><input className={flag('fullName')} value={form.fullName} onChange={set('fullName')} /></Field>
              </div>
              <Field label={flag('faculty') ? 'Khoa / Ngành (cần kiểm tra)' : 'Khoa / Ngành'}>
                <input className={flag('faculty')} value={form.faculty} onChange={set('faculty')} />
              </Field>
            </div>
          )}
          {err && <p className="error" role="alert">{err}</p>}
          {saved && <p className="ok" role="status">✔ Đã lưu hồ sơ</p>}
          <div className="two">
            <button className="btn primary" disabled={locked || busy || !manual} onClick={save}>Xác nhận và lưu</button>
            <button className="btn outline" disabled={locked} onClick={() => { setManual(true); setScan({ state: 'idle', msg: '' }); }}>Nhập tay</button>
          </div>
          <p className="center"><button className="link" disabled={locked} onClick={() => setSkipped(true)}>Bỏ qua bước này</button></p>
        </>
      )}
    </section>
  );
}
