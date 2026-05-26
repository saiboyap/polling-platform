export type UserRole = 'USER' | 'ADMIN';

export interface User {
  username: string;
  role: UserRole;
}

export interface AuthResponse {
  token: string;
  username: string;
  role: string;
  tokenType: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}
