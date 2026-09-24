import { defineStore } from 'pinia';
import { api } from '../api';
import type { User } from '../api/types';
import { useRegistration } from './registration';

function storedUser(): User | null {
  try { return JSON.parse(sessionStorage.getItem('user') || 'null') as User | null; }
  catch { return null; }
}

export const useAuth = defineStore('auth', {
  state: () => ({
    user: storedUser(),
    token: sessionStorage.getItem('token'),
  }),
  getters: {
    role: s => s.user?.role,
  },
  actions: {
    async login(username: string, password: string) {
      const r = await api.login(username, password);
      this.user = r.user;
      this.token = r.token;
      sessionStorage.setItem('token', r.token);
      sessionStorage.setItem('user', JSON.stringify(r.user));
    },
    logout() {
      api.logout();
      // 重置学生课表 store，避免同一会话切换账号后沿用上一个用户的 closed/课表缓存
      useRegistration().$reset();
      this.user = null;
      this.token = null;
      sessionStorage.clear();
    },
  },
});
