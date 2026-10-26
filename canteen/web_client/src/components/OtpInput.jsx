import { useRef } from 'react';

/** Sáu ô nhập mã OTP: tự nhảy ô, hỗ trợ dán cả chuỗi và phím Backspace. value là chuỗi tối đa 6 chữ số. */
export default function OtpInput({ value, onChange, disabled, onComplete }) {
  const refs = useRef([]);
  const digits = Array.from({ length: 6 }, (_, i) => value[i] || '');

  const commit = (next) => {
    const v = next.replace(/\D/g, '').slice(0, 6);
    onChange(v);
    if (v.length === 6) onComplete?.(v);
  };

  const onKey = (i) => (e) => {
    if (e.key === 'Backspace' && !digits[i] && i > 0) {
      refs.current[i - 1].focus();
      commit(value.slice(0, i - 1));
    } else if (e.key === 'ArrowLeft' && i > 0) refs.current[i - 1].focus();
    else if (e.key === 'ArrowRight' && i < 5) refs.current[i + 1].focus();
  };

  const onInput = (i) => (e) => {
    const d = e.target.value.replace(/\D/g, '');
    if (!d) { commit(value.slice(0, i) + value.slice(i + 1)); return; }
    const next = value.slice(0, i) + d + value.slice(i + 1);
    commit(next);
    refs.current[Math.min(i + d.length, 5)]?.focus();
  };

  const onPaste = (e) => {
    e.preventDefault();
    const d = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    commit(d);
    refs.current[Math.min(d.length, 5)]?.focus();
  };

  return (
    <div className="otp" role="group" aria-label="Mã xác minh 6 chữ số">
      {digits.map((d, i) => (
        <input
          key={i} ref={(el) => (refs.current[i] = el)} value={d} disabled={disabled}
          inputMode="numeric" autoComplete={i === 0 ? 'one-time-code' : 'off'} maxLength={6}
          aria-label={`Chữ số thứ ${i + 1}`} onChange={onInput(i)} onKeyDown={onKey(i)} onPaste={onPaste}
          onFocus={(e) => e.target.select()}
        />
      ))}
    </div>
  );
}
