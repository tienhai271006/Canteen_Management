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

// ---- Đơn của tôi (UC6), cần đăng nhập ----
export const listMyOrders = (statuses, page = 0, size = 50) =>
  api(`/api/orders?status=${statuses.join(',')}&page=${page}&size=${size}`);
export const getOrderHistory = (id) => api(`/api/orders/${id}/history`);
export const cancelPendingOrder = (id) => api(`/api/orders/${id}/cancel`, { method: 'POST' });
export const listCancelRequests = (id) => api(`/api/orders/${id}/cancel-requests`);
export const requestCancel = (id, reason) => api(`/api/orders/${id}/cancel-requests`, { method: 'POST', body: { reason } });
export const withdrawCancelRequest = (id, reqId) => api(`/api/orders/${id}/cancel-requests/${reqId}`, { method: 'DELETE' });
export const listLateArrivals = (id) => api(`/api/orders/${id}/late-arrivals`);
// proposedArrivalAt là giờ địa phương dạng yyyy-MM-ddTHH:mm:ss
export const requestLateArrival = (id, proposedArrivalAt, reason) =>
  api(`/api/orders/${id}/late-arrivals`, { method: 'POST', body: { proposedArrivalAt, reason } });
export const withdrawLateArrival = (id, reqId) => api(`/api/orders/${id}/late-arrivals/${reqId}`, { method: 'DELETE' });
// ---- Sự việc và phản hồi của chính khách (UC1.9, UC6.5) ----
export const listMyIncidents = () => api('/api/me/incidents?size=50');
export const sendFeedback = (incidentId, content) =>
  api(`/api/me/incidents/${incidentId}/feedback`, { method: 'POST', body: { content } });

// ---- Quầy xử lý đơn (UC7), cần đăng nhập bằng tài khoản nhân viên hoặc quản lý quầy ----
export const listCounterVendors = () => api('/api/counter/my-vendors');
export const listCounterOrders = (vendorId) => api(`/api/counter/orders?vendorId=${vendorId}&size=100`);
export const listOverdueOrders = (vendorId) => api(`/api/counter/orders/overdue?vendorId=${vendorId}&size=100`);
/** action: confirm | prepare | ready (không cần body) */
export const counterAction = (orderId, action) => api(`/api/counter/orders/${orderId}/${action}`, { method: 'POST' });
export const rejectOrder = (orderId, reason) => api(`/api/counter/orders/${orderId}/reject`, { method: 'POST', body: { reason } });
export const completeOrder = (orderId, body) => api(`/api/counter/orders/${orderId}/complete`, { method: 'POST', body });
export const markNotCollected = (orderId, body) => api(`/api/counter/orders/${orderId}/not-collected`, { method: 'POST', body });
export const listCounterCancelRequests = (vendorId) => api(`/api/counter/cancel-requests?vendorId=${vendorId}&status=PENDING&size=50`);
export const decideCancel = (id, verb, body) => api(`/api/counter/cancel-requests/${id}/${verb}`, { method: 'POST', body });
export const listCounterLateArrivals = (vendorId) => api(`/api/counter/late-arrivals?vendorId=${vendorId}&status=PENDING&size=50`);
export const decideLate = (id, verb, body) => api(`/api/counter/late-arrivals/${id}/${verb}`, { method: 'POST', body });

// ---- Thực đơn và hạn mức (UC3), chỉ quản lý quầy ----
export const listMenus = (vendorId, from, to) => api(`/api/manager/menus?vendorId=${vendorId}&from=${from}&to=${to}`);
export const getMenu = (id) => api(`/api/manager/menus/${id}`);
export const createMenu = (b) => api('/api/manager/menus', { method: 'POST', body: b });
export const publishMenu = (id) => api(`/api/manager/menus/${id}/publish`, { method: 'POST' });
export const unpublishMenu = (id) => api(`/api/manager/menus/${id}/unpublish`, { method: 'POST' });
export const setCutoff = (id, orderCutoffTime) => api(`/api/manager/menus/${id}/cutoff`, { method: 'PUT', body: { orderCutoffTime } });
export const addMenuItem = (menuId, b) => api(`/api/manager/menus/${menuId}/items`, { method: 'POST', body: b });
export const setItemAvailability = (menuId, itemId, available) =>
  api(`/api/manager/menus/${menuId}/items/${itemId}/availability`, { method: 'PATCH', body: { available } });
export const removeMenuItem = (menuId, itemId) => api(`/api/manager/menus/${menuId}/items/${itemId}`, { method: 'DELETE' });
export const adjustQuota = (menuId, itemId, b) => api(`/api/manager/menus/${menuId}/items/${itemId}/quota`, { method: 'PUT', body: b });
export const addSlot = (menuId, b) => api(`/api/manager/menus/${menuId}/slots`, { method: 'POST', body: b });
export const setSlotCapacity = (menuId, slotId, capacity) => api(`/api/manager/menus/${menuId}/slots/${slotId}/capacity`, { method: 'PUT', body: { capacity } });
export const deleteSlot = (menuId, slotId) => api(`/api/manager/menus/${menuId}/slots/${slotId}`, { method: 'DELETE' });
export const listDishes = (vendorId) => api(`/api/manager/dishes?vendorId=${vendorId}&includeHidden=false&size=100`);
export const createDish = (b) => api('/api/manager/dishes', { method: 'POST', body: b });
// ---- Món không được nhận (UC3.7), nhân viên hoặc quản lý quầy ----
export const listWaste = (vendorId) => api(`/api/counter/waste?vendorId=${vendorId}&size=20`);
export const handleWaste = (orderId, b) => api(`/api/counter/orders/${orderId}/waste`, { method: 'POST', body: b });

export const money = (n) => `${Number(n).toLocaleString('vi-VN')}đ`;
export const CATEGORY = { MAIN: 'Món chính', SIDE: 'Món phụ', DRINK: 'Đồ uống', SNACK: 'Ăn vặt', DESSERT: 'Tráng miệng' };
export const SESSION = { MORNING: 'Sáng', LUNCH: 'Trưa', AFTERNOON: 'Chiều' };
export const STATUS = {
  PENDING: 'Chờ quầy xác nhận', CONFIRMED: 'Quầy đã xác nhận', PREPARING: 'Đang chuẩn bị',
  READY: 'Sẵn sàng nhận', COMPLETED: 'Đã nhận', CANCELLED: 'Đã hủy', EXPIRED: 'Hết hạn xác nhận',
  NOT_COLLECTED: 'Không đến nhận',
};

// UC9: thống kê quầy (quản lý)
export const getVendorStats = (vendorId, from, to) => api(`/api/manager/stats?vendorId=${vendorId}&from=${from}&to=${to}`);

// ---- Quản trị (UC10, UC8.1), chỉ vai trò ADMIN ----
const qs = (o) => { const p = new URLSearchParams(); Object.entries(o).forEach(([k, v]) => { if (v !== '' && v != null) p.set(k, v); }); return p.toString(); };
/** status: mảng PENDING_REVIEW | CONFIRMED | EXEMPTED | REVOKED, bỏ trống là tất cả */
export const adminListIncidents = (status = [], size = 100) => api(`/api/admin/incidents?${qs({ status: status.join(','), size })}`);
/** decision: CONFIRM | EXEMPT; note bắt buộc */
export const adminDecideIncident = (id, decision, note) => api(`/api/admin/incidents/${id}/decision`, { method: 'POST', body: { decision, note } });
export const adminRevokeIncident = (id, reason) => api(`/api/admin/incidents/${id}/revoke`, { method: 'POST', body: { reason } });
export const adminViolations = (userId) => api(`/api/admin/users/${userId}/violations`);
export const adminRestrict = (userId, reason, days) => api('/api/admin/restrictions', { method: 'POST', body: { userId, reason, days } });
export const adminRevokeRestriction = (id, reason) => api(`/api/admin/restrictions/${id}/revoke`, { method: 'POST', body: { reason } });
export const adminListFeedback = (status = ['OPEN']) => api(`/api/admin/feedback?${qs({ status: status.join(','), size: 100 })}`);
/** action: KEEP (giữ kết luận) | EXEMPT (chấp nhận phản hồi, miễn) */
export const adminResolveFeedback = (id, response, action) => api(`/api/admin/feedback/${id}/resolve`, { method: 'POST', body: { response, action } });
export const adminListVendors = (status = '') => api(`/api/admin/vendors?${qs({ status, size: 100 })}`);
export const adminVendorAction = (id, verb, body) => api(`/api/admin/vendors/${id}/${verb}`, { method: 'POST', body });
export const adminListCampaigns = () => api('/api/admin/campaigns?size=50');
export const adminSaveCampaign = (id, b) => api(id ? `/api/admin/campaigns/${id}` : '/api/admin/campaigns', { method: id ? 'PUT' : 'POST', body: b });
export const adminCampaignToggle = (id, on) => api(`/api/admin/campaigns/${id}/${on ? 'activate' : 'deactivate'}`, { method: 'POST' });
export const listNotifications = () => api('/api/notifications?size=50');
export const markNotificationRead = (id) => api(`/api/notifications/${id}/read`, { method: 'POST' });
export const markAllNotificationsRead = () => api('/api/notifications/read-all', { method: 'POST' });
