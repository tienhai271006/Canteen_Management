// Gọi API. Lỗi server có dạng { code, message } (ErrorResponse) nên ném lại nguyên văn để hiển thị.
let token = localStorage.getItem('token');
export const getToken = () => token;
export const setToken = (t) => {
  token = t;
  t ? localStorage.setItem('token', t) : localStorage.removeItem('token');
};

export async function api(path, { method = 'GET', body, headers = {} } = {}) {
  const res = await fetch(path, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const err = new Error(data?.message || 'Có lỗi xảy ra, vui lòng thử lại.');
    err.code = data?.code;
    err.status = res.status;
    throw err;
  }
  return data;
}

export const money = (n) => `${Number(n).toLocaleString('vi-VN')}đ`;
export const SESSION = { MORNING: 'Sáng', LUNCH: 'Trưa', AFTERNOON: 'Chiều' };
export const STATUS = {
  PENDING: 'Chờ quầy xác nhận', CONFIRMED: 'Quầy đã xác nhận', PREPARING: 'Đang chuẩn bị',
  READY: 'Sẵn sàng nhận', COMPLETED: 'Đã nhận', CANCELLED: 'Đã hủy', EXPIRED: 'Hết hạn xác nhận',
  NOT_COLLECTED: 'Không đến nhận',
};
