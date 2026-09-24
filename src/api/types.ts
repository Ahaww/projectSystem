export type Role = 'STUDENT' | 'PROFESSOR' | 'REGISTRAR';

export interface User { id: string; name: string; role: Role }

export interface Offering {
  id: string;
  code: string;
  title: string;
  dept: string;
  professor: string;
  days: string[];      // ['周一','周三']
  start: number;       // 距 00:00 分钟数
  end: number;
  room: string;
  seatsTotal: number;
  seatsTaken: number;
  prerequisites: string[];
  teaching?: boolean;  // 教授端：当前是否已选任教
  cancelled?: boolean; // 关闭注册后：该课程是否已被取消
}

export type ItemType = 'primary' | 'alternate';
export type ItemStatus = 'selected' | 'enrolled';
export interface ScheduleItem {
  offeringId: string;
  type: ItemType;
  status: ItemStatus;
  priority?: number;   // 备选优先级（1 = 最高，配平时按此顺序尝试）
}
export interface Schedule {
  status: 'draft' | 'saved' | 'submitted';
  items: ScheduleItem[];
  submitTime?: string | null;   // 服务端时间戳，Draft/Saved 为 null
}

export interface SubmitError { offeringId: string; code: 'full' | 'prerequisite' | 'conflict'; message: string }

export interface RegistrationStatus { closed: boolean; term: string }

export interface ReportCard {
  semesters: { term: string; items: { code: string; title: string; credits: number; grade: string | null }[] }[];
}
export interface TaughtOffering { id: string; title: string; term: string; students: number }
export interface RosterRow { studentId: string; name: string; grade: string | null }
export interface StudentRow { id: string; name: string; dob: string; ssn: string; status: string; graduationDate: string }
export interface ProfessorRow { id: string; name: string; dob: string; ssn: string; status: string; dept: string }
export interface CloseResult { cancelled: string[]; billed: number; leveled?: string[] }
