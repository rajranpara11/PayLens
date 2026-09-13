export interface AuthUser {
  username: string;
  roles: string[];
}

export interface LoginRequest {
  username: string;
  password: string;
}
