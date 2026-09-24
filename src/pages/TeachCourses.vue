<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { api, type Offering } from '../api';

const rows = ref<Offering[]>([]);
const loading = ref(true);
const saving = ref(false);
const closed = ref(false);
const loadError = ref('');
const banner = ref<{ type: 'ok' | 'error'; text: string } | null>(null);
const errRows = ref<Record<string, string>>({});

const fmtTime = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;

onMounted(async () => {
  try {
    rows.value = await api.eligibleOfferings();
    closed.value = (await api.registrationStatus()).closed;
  } catch (e: any) {
    loadError.value = e?.message || '课程目录系统暂不可用，请稍后再试。';
  } finally {
    loading.value = false;
  }
});

/** 课程 o 是否与已勾选的其他课程时间冲突 */
function hasConflict(o: Offering): boolean {
  return rows.value.some(x => x.id !== o.id && x.teaching &&
    o.days.some(d => x.days.includes(d)) && o.start < x.end && x.start < o.end);
}
function conflictMsg(o: Offering): string | null {
  for (const x of rows.value) {
    if (x.id === o.id || !x.teaching) continue;
    if (o.days.some(d => x.days.includes(d)) && o.start < x.end && x.start < o.end) {
      return `${o.code} 与 ${x.code} 时间冲突`;
    }
  }
  return null;
}

function toggle(o: Offering) {
  if (closed.value) return;
  banner.value = null;
  errRows.value = {};
  o.teaching = !o.teaching;
}

async function save() {
  saving.value = true;
  banner.value = null;
  errRows.value = {};
  try {
    await api.updateTeaching(rows.value.filter(o => o.teaching).map(o => o.id));
    banner.value = { type: 'ok', text: `任教课程已更新，共选择 ${rows.value.filter(o => o.teaching).length} 门。` };
  } catch (e: any) {
    banner.value = { type: 'error', text: e.message || '保存失败' };
    const m: Record<string, string> = {};
    (e.errors ?? []).forEach((er: any) => { if (er.offeringId) m[er.offeringId] = er.message; });
    errRows.value = m;
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="content">
    <section class="welcome">
      <div>
        <span class="eyebrow">教师门户 · 2026 秋季</span>
        <h2>选择任教课程</h2>
        <p class="muted">
          <template v-if="closed">注册已关闭，无法再变更任教课程。</template>
          <template v-else>勾选你计划任教的开课课程；时间冲突的课程无法同时选择。</template>
        </p>
      </div>
      <button class="primary" :disabled="saving || loading || closed" @click="save">{{ saving ? '保存中…' : '保存任教选择' }}</button>
    </section>

    <p v-if="loadError" class="banner error">{{ loadError }}</p>
    <p v-if="closed" class="banner error">注册已关闭，授课课程已锁定。</p>
    <p v-if="banner" class="banner" :class="banner.type">{{ banner.text }}</p>
    <p v-if="loading" class="muted">课程加载中…</p>

    <div v-else-if="!rows.length" class="empty-box">
      <b>当前没有你可以任教的课程。</b>
      <small>本学期课程目录中暂无符合你任教资格的开课，请联系注册办公室。</small>
    </div>

    <div v-else class="teach-list">
      <label v-for="o in rows" :key="o.id" class="teach-row" :class="{ 'row-error': errRows[o.id], picked: o.teaching, conflict: hasConflict(o) && !o.teaching }">
        <input type="checkbox" :checked="o.teaching" :disabled="closed || hasConflict(o)" @change="toggle(o)" />
        <div class="teach-main">
          <b>{{ o.code }} · {{ o.title }}
            <span v-if="hasConflict(o) && !o.teaching" class="pill conflict">课程冲突</span>
          </b>
          <small>{{ o.dept }} · {{ o.room }}</small>
          <em v-if="errRows[o.id]" class="teach-err">{{ errRows[o.id] }}</em>
          <em v-else-if="hasConflict(o) && !o.teaching" class="teach-err">{{ conflictMsg(o) }}</em>
        </div>
        <div class="teach-time">
          <b>{{ o.days.join(' · ') }}</b>
          <small>{{ fmtTime(o.start) }} - {{ fmtTime(o.end) }}</small>
        </div>
      </label>
    </div>
  </div>
</template>

<style scoped>
.welcome { align-items: center; }
.banner { padding: 12px 16px; border-radius: 8px; font-size: 13px; font-weight: 700; margin-bottom: 14px; }
.banner.ok { background: #e5f3e8; color: #408254; }
.banner.error { background: #f8e9df; color: #ac663a; }
.teach-list { background: #fff; border: 1px solid #e1e7e7; border-radius: 8px; overflow: hidden; }
.empty-box { background: #fff; border: 1px dashed #cbd8d8; border-radius: 8px; padding: 48px 20px; text-align: center; display: flex; flex-direction: column; gap: 10px; }
.empty-box small { color: #829093; font-size: 14px; }
.teach-row { display: flex; align-items: center; gap: 14px; padding: 16px 20px; border-bottom: 1px solid #edf0f0; cursor: pointer; }
.teach-row:last-child { border-bottom: 0; }
.teach-row:hover { background: #f6f8f8; }
.teach-row.picked { background: #f3f7ee; }
.teach-row input { width: 17px; height: 17px; accent-color: #173a38; }
.teach-main { flex: 1; display: flex; flex-direction: column; gap: 3px; }
.teach-main small { color: #829093; }
.teach-err { font-style: normal; color: #ac663a; font-size: 14px; font-weight: 700; }
.teach-time { text-align: right; display: flex; flex-direction: column; gap: 3px; }
.teach-time small { color: #829093; }
.teach-row.row-error { background: #fdf3ec; }
.teach-row.conflict { opacity: 0.75; }
.teach-row.conflict input { cursor: not-allowed; }
.pill.conflict { background: #f8e9df; color: #ac663a; }
</style>
