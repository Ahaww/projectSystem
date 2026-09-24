// 内置 Mock：模拟后端接口与业务规则（先修课 / 满员 / 时间冲突 / 注册关闭）
// 后端就绪后此文件不再被调用（VITE_USE_MOCK=false）
import { ApiError } from './http';
import type { CloseResult, Offering, ProfessorRow, ReportCard, RosterRow, Schedule, ScheduleItem, StudentRow, TaughtOffering, User } from './types';

const wait = (ms = 250) => new Promise(r => setTimeout(r, ms));

// ---------- 数据 ----------
const OFFERINGS: Offering[] = [
  { id: 'CS-301', code: 'CS-301', title: '软件工程', dept: '计算机科学', professor: 'Morgan 博士', days: ['周一', '周三'], start: 540, end: 630, room: '科学楼 204', seatsTotal: 10, seatsTaken: 8, prerequisites: [] },
  { id: 'MATH-214', code: 'MATH-214', title: '离散数学', dept: '数学', professor: 'Lin 教授', days: ['周二', '周四'], start: 660, end: 750, room: 'B12 教室', seatsTotal: 10, seatsTaken: 10, prerequisites: [] },
  { id: 'BUS-110', code: 'BUS-110', title: '管理学原理', dept: '商学', professor: 'Patel 博士', days: ['周五'], start: 780, end: 930, room: '西楼 103', seatsTotal: 10, seatsTaken: 4, prerequisites: [] },
  { id: 'CS-401', code: 'CS-401', title: '高级数据库', dept: '计算机科学', professor: 'Chen 博士', days: ['周一', '周三'], start: 540, end: 630, room: '科学楼 301', seatsTotal: 10, seatsTaken: 2, prerequisites: ['CS-301'] },
  { id: 'PHYS-150', code: 'PHYS-150', title: '大学物理', dept: '自然科学', professor: 'Kim 教授', days: ['周一', '周四'], start: 780, end: 870, room: '实验楼 105', seatsTotal: 10, seatsTaken: 6, prerequisites: ['PHYS-201'] },
  { id: 'HIST-101', code: 'HIST-101', title: '世界历史', dept: '人文', professor: 'Lee 博士', days: ['周三'], start: 840, end: 930, room: '东楼 210', seatsTotal: 10, seatsTaken: 5, prerequisites: [] },
  { id: 'ART-120', code: 'ART-120', title: '艺术鉴赏', dept: '艺术', professor: 'Dai 博士', days: ['周二'], start: 930, end: 1020, room: '艺术楼 101', seatsTotal: 10, seatsTaken: 2, prerequisites: [] },
];

const COMPLETED = ['CS-101', 'MATH-110', 'ENG-105'];   // 学生已修课程（用于先修课校验）
const USERS: Record<string, { password: string; user: User }> = {
  'j.davis': { password: '123456', user: { id: 'S001', name: 'Jordan Davis', role: 'STUDENT' } },
  'morgan': { password: '123456', user: { id: 'P001', name: 'Alex Morgan', role: 'PROFESSOR' } },
  'wang': { password: '123456', user: { id: 'R001', name: '王丽', role: 'REGISTRAR' } },
};
// 演示别名：student / teacher / admin 也可登录
const LOGIN_ALIASES: Record<string, string> = { student: 'j.davis', teacher: 'morgan', admin: 'wang' };

let closed = false;                                     // 注册是否已关闭
const cancelledIds = new Set<string>();                 // 关闭注册时被取消的课程
const TERM = '2026 秋季';
let schedule: Schedule = { status: 'draft', items: [], submitTime: null };
const teachingIds = new Set<string>();
// 学号/工号自增序列：删除后再新增也不会与已有 ID 撞号（FR-29 唯一 ID）
let studentSeq = 5;
let professorSeq = 4;

/** 课表条目中所有两两组合的时间冲突校验（主主 / 主备 / 备备 全部校验） */
function checkTimeConflicts(items: ScheduleItem[]): { offeringId: string; code: string; message: string }[] {
  const errors: { offeringId: string; code: string; message: string }[] = [];
  const withOffer = items.map(i => ({ i, o: OFFERINGS.find(x => x.id === i.offeringId)! })).filter(x => x.o);
  for (let a = 0; a < withOffer.length; a++) for (let b = a + 1; b < withOffer.length; b++) {
    if (overlap(withOffer[a].o, withOffer[b].o)) {
      const ta = withOffer[a].i.type === 'primary' ? '主选' : '备选';
      const tb = withOffer[b].i.type === 'primary' ? '主选' : '备选';
      errors.push({
        offeringId: withOffer[b].o.id, code: 'conflict',
        message: `${withOffer[b].o.code}（${tb}）与 ${withOffer[a].o.code}（${ta}）上课时间冲突`,
      });
    }
  }
  return errors;
}

/** 为备选条目补全优先级（按当前顺序 1,2,...） */
function assignPriority(items: ScheduleItem[]): ScheduleItem[] {
  let p = 0;
  return items.map(i => {
    if (i.type === 'alternate') { p++; return { ...i, priority: p }; }
    const { priority: _drop, ...rest } = i; return rest as ScheduleItem;
  });
}

/** 重复选择校验：同一门课不能同时出现多条（DC-03） */
function checkDuplicates(items: ScheduleItem[]) {
  const seen = new Set<string>();
  for (const it of items) {
    if (seen.has(it.offeringId)) {
      const o = OFFERINGS.find(x => x.id === it.offeringId);
      return { offeringId: it.offeringId, code: 'conflict' as const, message: `${o?.code ?? it.offeringId} 不能重复选择` };
    }
    seen.add(it.offeringId);
  }
  return null;
}

/**
 * 名额对账：以旧课表中已注册（enrolled）条目为基准，
 * 释放"不再属于新课表主选"的名额；新增主选的名额占用由调用方处理。
 * 避免暂存降级 / 移除已注册课程后名额不回滚、重复提交导致人数虚高。
 */
function releaseDroppedSeats(oldItems: ScheduleItem[], newPrimaryIds: Set<string>) {
  for (const it of oldItems) {
    if (it.status === 'enrolled' && !newPrimaryIds.has(it.offeringId)) {
      const o = OFFERINGS.find(x => x.id === it.offeringId);
      if (o && o.seatsTaken > 0) o.seatsTaken--;
    }
  }
}

const ROSTERS: Record<string, RosterRow[]> = {
  'CS-210': [{ studentId: 'S001', name: 'Jordan Davis', grade: 'A' }, { studentId: 'S002', name: '李四', grade: null }, { studentId: 'S003', name: '王五', grade: 'C' }, { studentId: 'S004', name: '赵六', grade: null }, { studentId: 'S005', name: '孙七', grade: 'B' }],
  'MATH-110': [{ studentId: 'S001', name: 'Jordan Davis', grade: 'B' }, { studentId: 'S004', name: '赵六', grade: 'A' }, { studentId: 'S005', name: '孙七', grade: null }],
};

const STUDENTS: StudentRow[] = [
  { id: 'S001', name: 'Jordan Davis', dob: '2005-03-12', ssn: '110101200503120011', status: '在读', graduationDate: '2028-06-30' },
  { id: 'S002', name: '李四', dob: '2004-11-02', ssn: '110101200411020022', status: '在读', graduationDate: '2027-06-30' },
  { id: 'S003', name: '王五', dob: '2005-07-21', ssn: '110101200507210033', status: '休学', graduationDate: '2028-06-30' },
  { id: 'S004', name: '赵六', dob: '2005-09-30', ssn: '110101200509300044', status: '在读', graduationDate: '2028-06-30' },
  { id: 'S005', name: '孙七', dob: '2004-05-18', ssn: '110101200405180055', status: '在读', graduationDate: '2027-06-30' },
];
const PROFESSORS: ProfessorRow[] = [
  { id: 'P001', name: 'Alex Morgan', dob: '1980-01-15', ssn: '110101198001150044', status: '在职', dept: '计算机科学' },
  { id: 'P002', name: 'Lin 教授', dob: '1979-09-08', ssn: '110101197909080055', status: '在职', dept: '数学' },
  { id: 'P003', name: 'Chen 博士', dob: '1983-04-02', ssn: '110101198304020066', status: '在职', dept: '计算机科学' },
  { id: 'P004', name: 'Lee 博士', dob: '1985-12-25', ssn: '110101198512250077', status: '休假', dept: '人文' },
];

// ---------- 工具 ----------
const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v));
const overlap = (a: Offering, b: Offering) => a.days.some(d => b.days.includes(d)) && a.start < b.end && b.start < a.end;

// ---------- Mock 接口 ----------
export const mock = {
  async login(username: string, password: string) {
    await wait();
    const key = LOGIN_ALIASES[username.trim().toLowerCase()] ?? username.trim();
    const u = USERS[key];
    if (!u || u.password !== password) throw new ApiError(401, '用户名或密码错误');
    return { token: `mock-token-${key}`, user: clone(u.user) };
  },

  async offerings() {
    await wait();
    // 学生端：注册关闭前隐藏教授名（关闭后随最终课表公开）；取消标记照常返回
    return clone(OFFERINGS).map(o => ({
      ...o,
      professor: closed ? o.professor : '',
      ...(cancelledIds.has(o.id) ? { cancelled: true } : {}),
    }));
  },

  /** 教务端课程总览：始终返回真实任教教授与取消标记 */
  async adminOfferings() {
    await wait();
    return clone(OFFERINGS).map(o => ({
      ...o,
      ...(cancelledIds.has(o.id) ? { cancelled: true } : {}),
    }));
  },

  async getSchedule(): Promise<Schedule> { await wait(); return clone(schedule); },

  async saveSchedule(items: ScheduleItem[]): Promise<Schedule> {
    await wait();
    if (closed) throw new ApiError(409, '本学期注册已关闭，无法修改课表');
    const primaries = items.filter(i => i.type === 'primary');
    const alternates = items.filter(i => i.type === 'alternate');
    const errors: { offeringId: string; code: string; message: string }[] = [];
    const dup = checkDuplicates(items);
    if (dup) errors.push(dup);
    if (primaries.length > 4) errors.push({ offeringId: '', code: 'conflict', message: '主选课程不能超过 4 门' });
    if (alternates.length > 2) errors.push({ offeringId: '', code: 'conflict', message: '备选课程不能超过 2 门' });
    // 暂存也要校验所有两两时间冲突（主主 / 主备 / 备备）
    errors.push(...checkTimeConflicts(items));
    if (errors.length) throw new ApiError(422, '课表校验未通过，请修正后重新保存', errors);
    // 暂存后所有条目均为 selected：旧课表中已注册（enrolled）的名额必须先释放
    releaseDroppedSeats(schedule.items, new Set());
    const norm = assignPriority(clone(items));
    // 暂存条目全部为 Selected，不占用名额
    norm.forEach(i => { i.status = 'selected'; });
    schedule = { status: 'saved', items: norm, submitTime: null };
    return clone(schedule);
  },

  async submitSchedule(items: ScheduleItem[]): Promise<Schedule> {
    await wait();
    if (closed) throw new ApiError(409, '本学期注册已关闭，无法提交课表');
    const errors: { offeringId: string; code: string; message: string }[] = [];
    const primaries = items.filter(i => i.type === 'primary');
    const alternates = items.filter(i => i.type === 'alternate');
    const dup = checkDuplicates(items);
    if (dup) errors.push(dup);
    if (primaries.length > 4) errors.push({ offeringId: '', code: 'conflict', message: '主选课程不能超过 4 门' });
    if (alternates.length > 2) errors.push({ offeringId: '', code: 'conflict', message: '备选课程不能超过 2 门' });
    // 主选校验先修课与满员
    for (const it of primaries) {
      const o = OFFERINGS.find(x => x.id === it.offeringId)!;
      if (o.prerequisites.some(p => !COMPLETED.includes(p)))
        errors.push({ offeringId: o.id, code: 'prerequisite', message: `${o.code} 未满足先修课要求（${o.prerequisites.join('、')}）` });
      if (o.seatsTaken >= o.seatsTotal)
        errors.push({ offeringId: o.id, code: 'full', message: `${o.code} 名额已满` });
    }
    // 所有两两时间冲突（主主 / 主备 / 备备）
    errors.push(...checkTimeConflicts(items));
    if (errors.length) throw new ApiError(422, '课表校验未通过，请修正后重新提交', errors);
    // 名额对账：以服务端旧课表的 enrolled 集合为准，移除的释放、新增的占用
    const oldEnrolledIds = new Set(schedule.items.filter(i => i.status === 'enrolled').map(i => i.offeringId));
    const newPrimaryIds = new Set(primaries.map(i => i.offeringId));
    releaseDroppedSeats(schedule.items, newPrimaryIds);
    // 主选注册占用名额；备选永远保持 Selected，不注册
    for (const it of primaries) {
      if (!oldEnrolledIds.has(it.offeringId)) {
        const o = OFFERINGS.find(x => x.id === it.offeringId)!;
        o.seatsTaken++;
      }
      it.status = 'enrolled';
    }
    alternates.forEach(it => { it.status = 'selected'; });
    const norm = assignPriority(clone(items));
    schedule = { status: 'submitted', items: norm, submitTime: new Date().toISOString() };
    return clone(schedule);
  },

  async deleteSchedule() {
    await wait();
    if (closed) throw new ApiError(409, '本学期注册已关闭，无法删除课表');
    // 删除课表：已注册课程需把学生从开设名单移除，释放名额
    for (const it of schedule.items) {
      if (it.status === 'enrolled') {
        const o = OFFERINGS.find(x => x.id === it.offeringId);
        if (o && o.seatsTaken > 0) o.seatsTaken--;
      }
    }
    schedule = { status: 'draft', items: [], submitTime: null };
  },

  async reportCard(): Promise<ReportCard> {
    await wait();
    return { semesters: [
      { term: '2025 秋季', items: [
        { code: 'CS-210', title: '程序设计', credits: 3, grade: 'A' },
        { code: 'MATH-110', title: '微积分', credits: 4, grade: 'B' },
        { code: 'ENG-105', title: '学术英语', credits: 2, grade: 'A' },
      ] },
      { term: '2025 春季', items: [
        { code: 'CS-101', title: '程序设计基础', credits: 3, grade: 'A' },
        { code: 'PHYS-101', title: '物理基础', credits: 4, grade: 'B' },
        { code: 'BUS-101', title: '经济学导论', credits: 2, grade: 'C' },
      ] },
    ] };
  },

  async taughtOfferings(): Promise<TaughtOffering[]> {
    await wait();
    return [
      { id: 'CS-210', title: '程序设计（CS-210）', term: '2025 秋季', students: 3 },
      { id: 'MATH-110', title: '微积分（MATH-110）', term: '2025 秋季', students: 2 },
    ];
  },

  async roster(offeringId: string) {
    await wait();
    // UC05：注册未关闭时不展示选课名单
    if (!closed) throw new ApiError(409, '注册尚未关闭，暂不可查看选课名册');
    if (!ROSTERS[offeringId]) throw new ApiError(404, '课程不存在');
    return clone(ROSTERS[offeringId]);
  },

  async saveGrades(offeringId: string, grades: { studentId: string; grade: string | null }[]) {
    await wait();
    const list = ROSTERS[offeringId];
    if (!list) throw new ApiError(404, '课程不存在');
    for (const g of grades) { const s = list.find(x => x.studentId === g.studentId); if (s) s.grade = g.grade; }
  },

  async eligible() {
    await wait();
    return clone(OFFERINGS.filter(o => ['CS-301', 'CS-401', 'HIST-101', 'BUS-110'].includes(o.id)))
      .map(o => ({ ...o, teaching: teachingIds.has(o.id) }));
  },

  async updateTeaching(ids: string[]) {
    await wait();
    if (closed) throw new ApiError(409, '注册已关闭，不能再变更授课课程');
    const sel = ids.map(id => OFFERINGS.find(o => o.id === id)!).filter(Boolean);
    for (let a = 0; a < sel.length; a++) for (let b = a + 1; b < sel.length; b++) {
      if (overlap(sel[a], sel[b]))
        throw new ApiError(409, '所选课程存在时间冲突', [
          { offeringId: sel[a].id, code: 'conflict', message: `${sel[a].code} 与 ${sel[b].code} 时间冲突` },
          { offeringId: sel[b].id, code: 'conflict', message: `${sel[b].code} 与 ${sel[a].code} 时间冲突` },
        ]);
    }
    teachingIds.clear();
    ids.forEach(id => teachingIds.add(id));
  },

  async students() { await wait(); return clone(STUDENTS); },
  async addStudent(d: Omit<StudentRow, 'id'>) {
    await wait();
    const row: StudentRow = { ...d, id: 'S' + String(++studentSeq).padStart(3, '0') };
    STUDENTS.push(row);
    return clone(row);
  },
  async updateStudent(id: string, d: Omit<StudentRow, 'id'>) {
    await wait();
    const row = STUDENTS.find(s => s.id === id);
    if (!row) throw new ApiError(404, '学生不存在');
    Object.assign(row, d);
  },
  async deleteStudent(id: string) {
    await wait();
    const i = STUDENTS.findIndex(s => s.id === id);
    if (i < 0) throw new ApiError(404, '学生不存在');
    STUDENTS.splice(i, 1);
  },

  async professors() { await wait(); return clone(PROFESSORS); },
  async addProfessor(d: Omit<ProfessorRow, 'id'>) {
    await wait();
    const row: ProfessorRow = { ...d, id: 'P' + String(++professorSeq).padStart(3, '0') };
    PROFESSORS.push(row);
    return clone(row);
  },
  async updateProfessor(id: string, d: Omit<ProfessorRow, 'id'>) {
    await wait();
    const row = PROFESSORS.find(s => s.id === id);
    if (!row) throw new ApiError(404, '教师不存在');
    Object.assign(row, d);
  },
  async deleteProfessor(id: string) {
    await wait();
    const i = PROFESSORS.findIndex(s => s.id === id);
    if (i < 0) throw new ApiError(404, '教师不存在');
    PROFESSORS.splice(i, 1);
  },

  async registrationStatus() { await wait(); return { closed, term: TERM }; },

  async closeRegistration(): Promise<CloseResult> {
    await wait();
    if (closed) throw new ApiError(409, '注册已经关闭，不能重复执行');
    // ---- 1. 取消无教授任教 或 注册人数不足 3 人的课程开设 ----
    const cancelled = OFFERINGS.filter(o =>
      !teachingIds.has(o.id) || o.seatsTaken < 3
    ).map(o => o.id);
    cancelled.forEach(id => cancelledIds.add(id));
    // 从所有学生课表中移除被取消课程的条目
    if (cancelled.length) {
      schedule.items = schedule.items.filter(i => !cancelled.includes(i.offeringId));
    }
    // ---- 2. 课表配平：主选不足 4 门时按备选优先级依次尝试 ----
    // 仅已提交（Submitted）课表参与配平；按 submitTime 升序决定名额竞争先后
    const submittedSchedules = [schedule].filter(s => s.status === 'submitted' && s.submitTime)
      .sort((a, b) => (a.submitTime! < b.submitTime! ? -1 : 1));
    const leveled: string[] = [];   // 被配平升级为主选的课程
    for (const s of submittedSchedules) {
      const enrolledPrimaries = s.items.filter(i => i.type === 'primary' && i.status === 'enrolled');
      if (enrolledPrimaries.length >= 4) continue;
      const alternates = s.items.filter(i => i.type === 'alternate')
        .sort((a, b) => (a.priority ?? 99) - (b.priority ?? 99));
      for (const alt of alternates) {
        if (enrolledPrimaries.length >= 4) break;
        const o = OFFERINGS.find(x => x.id === alt.offeringId);
        if (!o || cancelled.includes(o.id)) continue;
        // 先修课校验
        if (o.prerequisites.some(p => !COMPLETED.includes(p))) continue;
        // 与已生效主选无时间冲突
        const conflict = enrolledPrimaries.some(p => {
          const po = OFFERINGS.find(x => x.id === p.offeringId)!;
          return overlap(o, po);
        });
        if (conflict) continue;
        // 名额未满
        if (o.seatsTaken >= o.seatsTotal) continue;
        // 配平成功：备选升级为主选并注册
        alt.type = 'primary';
        alt.status = 'enrolled';
        o.seatsTaken++;
        enrolledPrimaries.push(alt);
        leveled.push(o.code);
      }
    }
    // ---- 3. 全部课程开设置为 Closed ----
    closed = true;
    // ---- 4. 计算学费、生成收费事务（仅针对注册人数 >= 3 的确认课程）----
    const confirmed = OFFERINGS.filter(o => !cancelled.includes(o.id) && o.seatsTaken >= 3);
    const billed = confirmed.reduce((n, o) => n + o.seatsTaken, 0);
    return { cancelled, billed, leveled };
  },
};
