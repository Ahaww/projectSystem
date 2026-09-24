// 统一 API 入口：USE_MOCK 时走内置 mock，否则按 docs/api-contract.md 请求真实后端
import { del, get, post, put, USE_MOCK } from './http';
import type {
  CloseResult, Offering, ProfessorRow, RegistrationStatus, ReportCard, RosterRow, Schedule,
  ScheduleItem, StudentRow, TaughtOffering, User,
} from './types';

export * from './types';

/** Mock 模块按需动态加载（单例），避免开发热替换下的模块实例不一致 */
let mockPromise: Promise<typeof import('./mockdb')> | null = null;
const mockdb = () => (mockPromise ??= import('./mockdb'));

export const api = {
  // ---- 认证 ----
  async login(username: string, password: string): Promise<{ token: string; user: User }> {
    if (USE_MOCK) return (await mockdb()).mock.login(username, password);
    return post('/auth/login', { username, password });
  },
  logout() { if (!USE_MOCK) post('/auth/logout').catch(() => {}); },

  // ---- 课程目录 ----
  async offerings(): Promise<Offering[]> {
    if (USE_MOCK) return (await mockdb()).mock.offerings();
    return get('/offerings');
  },
  /** 教务课程情况总览（含真实教授名；学生端 offerings 在关闭前会脱敏） */
  async adminOfferings(): Promise<Offering[]> {
    if (USE_MOCK) return (await mockdb()).mock.adminOfferings();
    return get('/registrar/offerings');
  },

  // ---- 课表（学生）----
  async getSchedule(): Promise<Schedule> {
    if (USE_MOCK) return (await mockdb()).mock.getSchedule();
    return get('/schedules/me');
  },
  async saveSchedule(items: ScheduleItem[]): Promise<Schedule> {
    if (USE_MOCK) return (await mockdb()).mock.saveSchedule(items);
    return put('/schedules/me', { items });
  },
  async submitSchedule(items: ScheduleItem[]): Promise<Schedule> {
    if (USE_MOCK) return (await mockdb()).mock.submitSchedule(items);
    return post('/schedules/me/submit', { items });
  },
  async deleteSchedule(): Promise<void> {
    if (USE_MOCK) return (await mockdb()).mock.deleteSchedule();
    return del('/schedules/me');
  },

  // ---- 成绩 ----
  async reportCard(): Promise<ReportCard> {
    if (USE_MOCK) return (await mockdb()).mock.reportCard();
    return get('/report-card/me');
  },
  async taughtOfferings(): Promise<TaughtOffering[]> {
    if (USE_MOCK) return (await mockdb()).mock.taughtOfferings();
    return get('/professor/offerings');
  },
  async roster(offeringId: string): Promise<RosterRow[]> {
    if (USE_MOCK) return (await mockdb()).mock.roster(offeringId);
    return get(`/professor/offerings/${offeringId}/roster`);
  },
  async saveGrades(offeringId: string, grades: { studentId: string; grade: string | null }[]): Promise<void> {
    if (USE_MOCK) return (await mockdb()).mock.saveGrades(offeringId, grades);
    return put(`/professor/offerings/${offeringId}/grades`, { grades });
  },

  // ---- 教授任教 ----
  async eligibleOfferings(): Promise<Offering[]> {
    if (USE_MOCK) return (await mockdb()).mock.eligible();
    return get('/professor/eligible');
  },
  async updateTeaching(offeringIds: string[]): Promise<void> {
    if (USE_MOCK) return (await mockdb()).mock.updateTeaching(offeringIds);
    return put('/professor/teaching', { offeringIds });
  },

  // ---- 教务 ----
  async students(): Promise<StudentRow[]> {
    if (USE_MOCK) return (await mockdb()).mock.students();
    return get('/registrar/students');
  },
  async addStudent(d: Omit<StudentRow, 'id'>): Promise<StudentRow> {
    if (USE_MOCK) return (await mockdb()).mock.addStudent(d);
    return post('/registrar/students', d);
  },
  async updateStudent(id: string, d: Omit<StudentRow, 'id'>): Promise<void> {
    if (USE_MOCK) return (await mockdb()).mock.updateStudent(id, d);
    return put(`/registrar/students/${id}`, d);
  },
  async deleteStudent(id: string): Promise<void> {
    if (USE_MOCK) return (await mockdb()).mock.deleteStudent(id);
    return del(`/registrar/students/${id}`);
  },
  async professors(): Promise<ProfessorRow[]> {
    if (USE_MOCK) return (await mockdb()).mock.professors();
    return get('/registrar/professors');
  },
  async addProfessor(d: Omit<ProfessorRow, 'id'>): Promise<ProfessorRow> {
    if (USE_MOCK) return (await mockdb()).mock.addProfessor(d);
    return post('/registrar/professors', d);
  },
  async updateProfessor(id: string, d: Omit<ProfessorRow, 'id'>): Promise<void> {
    if (USE_MOCK) return (await mockdb()).mock.updateProfessor(id, d);
    return put(`/registrar/professors/${id}`, d);
  },
  async deleteProfessor(id: string): Promise<void> {
    if (USE_MOCK) return (await mockdb()).mock.deleteProfessor(id);
    return del(`/registrar/professors/${id}`);
  },
  async closeRegistration(): Promise<CloseResult> {
    if (USE_MOCK) return (await mockdb()).mock.closeRegistration();
    return post('/registrar/close-registration');
  },
  async registrationStatus(): Promise<RegistrationStatus> {
    if (USE_MOCK) return (await mockdb()).mock.registrationStatus();
    return get('/registration/status');
  },
};
