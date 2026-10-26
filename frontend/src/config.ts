export const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8181';
export const KEYCLOAK_REALM = 'hrms';
export const KEYCLOAK_CLIENT_ID = 'hrms-spa';
// Dev: gateway chạy trong hrms-dev (SERVER_PORT=8185) — host 8185 (VS Code forwardPorts)
export const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8185';
