import { defineStore } from 'pinia';

/** 当前开放注册的学期（选课/课表操作仅对该学期开放） */
export const CURRENT_TERM = '2026 秋季';
/** 学生可切换查看的学期（历史学期为只读） */
export const TERMS = ['2026 秋季', '2025 秋季', '2025 春季'];

export const useTerm = defineStore('term', {
  state: () => ({
    selected: CURRENT_TERM,
  }),
  getters: {
    /** 是否为已结束的历史学期（只读，不能再选课/改课表） */
    isHistorical: s => s.selected !== CURRENT_TERM,
  },
  actions: {
    select(t: string) { this.selected = t; },
  },
});
