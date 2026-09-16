import { defineStore } from 'pinia';
import { api } from '../api';
import type { Offering, Schedule, ScheduleItem, SubmitError } from '../api/types';

export const useRegistration = defineStore('registration', {
  state: () => ({
    schedule: { status: 'draft', items: [], submitTime: null } as Schedule,
    loaded: false,
    saving: false,
    submitting: false,
    closed: false,
  }),
  getters: {
    primaries: s => s.schedule.items.filter(i => i.type === 'primary'),
    alternates: s => s.schedule.items.filter(i => i.type === 'alternate'),
    typeOf: s => (id: string) => s.schedule.items.find(i => i.offeringId === id)?.type,
    editable: s => !s.closed && s.schedule.status !== 'submitted',
  },
  actions: {
    async load() {
      if (this.loaded) return;
      this.schedule = await api.getSchedule();
      this.closed = (await api.registrationStatus()).closed;
      this.loaded = true;
    },
    /** 选中/取消/切换主选与备选（本地修改，需保存或提交后持久化） */
    toggle(offering: Offering, type: 'primary' | 'alternate') {
      if (!this.editable) return;
      const cur = this.schedule.items.find(i => i.offeringId === offering.id);
      const items = cur && cur.type === type
        ? this.schedule.items.filter(i => i.offeringId !== offering.id)
        : [...this.schedule.items.filter(i => i.offeringId !== offering.id),
           { offeringId: offering.id, type, status: cur?.status ?? 'selected' } as ScheduleItem];
      this.schedule = { ...this.schedule, items };
    },
    remove(id: string) {
      if (!this.editable) return;
      this.schedule = { ...this.schedule, items: this.schedule.items.filter(i => i.offeringId !== id) };
    },
    async save() {
      this.saving = true;
      try { this.schedule = await api.saveSchedule(this.schedule.items); }
      finally { this.saving = false; }
    },
    async submit(): Promise<{ ok: boolean; errors: SubmitError[]; message?: string }> {
      this.submitting = true;
      try {
        this.schedule = await api.submitSchedule(this.schedule.items);
        return { ok: true, errors: [] };
      } catch (e: any) {
        return { ok: false, errors: e.errors ?? [], message: e.message };
      } finally { this.submitting = false; }
    },
    async reset() {
      await api.deleteSchedule();
      this.schedule = { status: 'draft', items: [] };
    },
  },
});
