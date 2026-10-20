import client from './client';
import type { Account, ChangePasswordPayload, PageResponse } from './types';

export function searchAccounts(params: {
  q?: string;
  page?: number;
  size?: number;
} = {}): Promise<PageResponse<Account>> {
  return client
    .get<PageResponse<Account>>('/api/v1/accounts', { params })
    .then((r) => r.data);
}

/** Tự đổi mật khẩu của chính mình (backend xác thực mật khẩu hiện tại trước). */
export function changeOwnPassword(data: ChangePasswordPayload): Promise<void> {
  return client.post('/api/v1/accounts/change-password', data).then((r) => r.data);
}

/** Admin đặt lại mật khẩu cho tài khoản. */
export function resetAccountPassword(username: string, newPassword: string): Promise<void> {
  return client
    .patch(`/api/v1/accounts/${encodeURIComponent(username)}/password`, { newPassword })
    .then((r) => r.data);
}

/** Admin bật/tắt tài khoản đăng nhập. */
export function setAccountEnabled(username: string, enabled: boolean): Promise<void> {
  return client
    .patch(`/api/v1/accounts/${encodeURIComponent(username)}/enabled`, { enabled })
    .then((r) => r.data);
}

/** Admin sửa roles của tài khoản (backend validate role hợp lệ + không tự hạ mình). */
export function updateAccountRoles(username: string, roles: string[]): Promise<void> {
  return client
    .patch(`/api/v1/accounts/${encodeURIComponent(username)}/roles`, { roles })
    .then((r) => r.data);
}
