// Gọi API. Lỗi server có dạng { code, message } (ErrorResponse) nên ném lại nguyên văn để hiển thị.
let token = localStorage.getItem('token');
let refreshToken = localStorage.getItem('refreshToken');
export const getToken = () => token;

/** Lưu hoặc xóa cặp token. setTokens(null) khi đăng xuất. */
export function setTokens(t) {
  token = t?.accessToken || null;
  refreshToken = t?.refreshToken || null;
  token ? localStorage.setItem('token', token) : localStorage.removeItem('token');
  refreshToken ? localStorage.setItem('refreshToken', refreshToken) : localStorage.removeItem('refreshToken');
}
export const getRefreshToken = () => refreshToken;

async function refreshAccess() {
  if (!refreshToken) return false;
  try {
    const res = await fetch('/api/auth/refresh', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return false;
    setTokens(await res.json());
    return true;
  } catch { return false; }
}

export async function api(path, { method = 'GET', body, headers = {} } = {}, retried = false) {
  const isForm = body instanceof FormData;
  let res;
  try {
    res = await fetch(path, {
      method,
      headers: {
        ...(body && !isForm ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: body ? (isForm ? body : JSON.stringify(body)) : undefined,
    });
  } catch {
    throw netError();
  }
  // Access token hết hạn sau 15 phút: thử đổi bằng refresh token một lần rồi gọi lại.
  if (res.status === 401 && !retried && token && !path.startsWith('/api/auth/') && await refreshAccess()) {
    return api(path, { method, body, headers }, true);
  }
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }   // proxy lỗi trả về chữ thường, không phải JSON
  if (!res.ok) {
    // Vite trả 500/502/504 không có JSON khi không nối được server Spring Boot.
    if (data === null && res.status >= 500) throw netError(res.status);
    // Server đôi khi trả chữ thường (ví dụ "Invalid CORS request"), hiển thị luôn để dễ tìm lỗi.
    const raw = data === null && text ? ` ${text.slice(0, 120)}` : '';
    const err = new Error(data?.message || `Có lỗi xảy ra (mã ${res.status}).${raw}`);
    err.code = data?.code;
    err.status = res.status;
    throw err;
  }
  return data;
}

function netError(status) {
  const e = new Error('Không kết nối được máy chủ. Hãy kiểm tra server Spring Boot đang chạy ở cổng 8080 và MySQL (docker compose up -d) đã bật.');
  e.status = status || 0;
  return e;
}

// ---- Tài khoản và xác thực (UC1) ----
export const register = (b) => api('/api/auth/register', { method: 'POST', body: b });
export const login = (b) => api('/api/auth/login', { method: 'POST', body: b });
export const logoutApi = () => refreshToken
  ? api('/api/auth/logout', { method: 'POST', body: { refreshToken } }).catch(() => {})
  : Promise.resolve();
export const verifyEmail = (code) => api('/api/auth/verify-email', { method: 'POST', body: { code } });
export const resendEmailCode = () => api('/api/auth/verify-email/resend', { method: 'POST' });
export const getProfile = () => api('/api/me/profile');
export const updateProfile = (b) => api('/api/me/profile', { method: 'PUT', body: b });
export const getAccountStatus = () => api('/api/me/account-status');
/**
 * OCR thẻ sinh viên (UC1.3). Server chưa có endpoint này nên đang kỳ vọng:
 * POST /api/me/student-card/ocr (multipart, field `file`) trả
 * { studentCode, fullName, faculty, confidence: { studentCode, fullName, faculty } } với confidence 0..1.
 * Khi server trả 404 giao diện tự chuyển sang nhập tay.
 */
export const scanStudentCard = (file) => {
  const f = new FormData();
  f.append('file', file);
  return api('/api/me/student-card/ocr', { method: 'POST', body: f });
};

// ---- Khám phá và tìm kiếm (UC4), công khai không cần đăng nhập ----
export const getAreas = () => api('/api/public/areas');
export const getVendors = (areaId) => api(`/api/public/vendors${areaId ? `?areaId=${areaId}` : ''}`);
export function searchMenuItems(p) {
  const qs = new URLSearchParams();
  Object.entries({ size: 100, ...p }).forEach(([k, v]) => { if (v !== '' && v != null && v !== false) qs.set(k, v); });
  return api(`/api/public/menu-items?${qs}`);
}
// ---- Giỏ hàng (UC5.1), cần đăng nhập ----
export const getCart = () => api('/api/cart');
export const addCartItem = (menuItemId, quantity = 1) => api('/api/cart/items', { method: 'POST', body: { menuItemId, quantity } });

export const updateCartItem = (id, quantity) => api(`/api/cart/items/${id}`, { method: 'PUT', body: { quantity } });
export const removeCartItem = (id) => api(`/api/cart/items/${id}`, { method: 'DELETE' });
export const clearCart = () => api('/api/cart', { method: 'DELETE' });
// ---- Đặt món (UC5.3). Idempotency-Key giúp bấm đặt hai lần vẫn chỉ ra một đơn. ----
// useVoucher chưa được server đọc (ưu đãi Pha 8 chưa có), server bỏ qua trường lạ nên gửi trước vẫn an toàn.
export const placeOrder = ({ pickupSlotId, useVoucher }, key) =>
  api('/api/orders', { method: 'POST', body: { pickupSlotId, useVoucher }, headers: { 'Idempotency-Key': key } });
export const getOrder = (id) => api(`/api/orders/${id}`);

export const money = (n) => `${Number(n).toLocaleString('vi-VN')}đ`;
export const CATEGORY = { MAIN: 'Món chính', SIDE: 'Món phụ', DRINK: 'Đồ uống', SNACK: 'Ăn vặt', DESSERT: 'Tráng miệng' };
export const SESSION = { MORNING: 'Sáng', LUNCH: 'Trưa', AFTERNOON: 'Chiều' };
export const STATUS = {
  PENDING: 'Chờ quầy xác nhận', CONFIRMED: 'Quầy đã xác nhận', PREPARING: 'Đang chuẩn bị',
  READY: 'Sẵn sàng nhận', COMPLETED: 'Đã nhận', CANCELLED: 'Đã hủy', EXPIRED: 'Hết hạn xác nhận',
  NOT_COLLECTED: 'Không đến nhận',
};
