<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { api, type RosterRow, type TaughtOffering } from '../api';
import { CURRENT_TERM } from '../stores/term';

const list = ref<TaughtOffering[]>([]);
const cur = ref('');
const roster = ref<RosterRow[]>([]);
const loading = ref(true);
const saving = ref(false);
const closed = ref(false);
const rosterError = ref('');
const banner = ref<{ type: 'ok' | 'error'; text: string } | null>(null);

const GRADES = ['A', 'B', 'C', 'D', 'F', 'I'];
const selected = computed(() => list.value.find(o => o.id === cur.value));
/** 仅当学期且注册未关时拦截名册；历史学期可直接录入。 */
const rosterBlocked = computed(() => !closed.value && selected.value?.term === CURRENT_TERM);

onMounted(async () => {
  list.value = await api.taughtOfferings();
  closed.value = (await api.registrationStatus()).closed;
  if (list.value.length) await select(list.value[0].id);
  loading.value = false;
});

async function select(id: string) {
  banner.value = null;
  rosterError.value = '';
  cur.value = id;
  const offering = list.value.find(o => o.id === id);
  if (!closed.value && offering?.term === CURRENT_TERM) { roster.value = []; return; }
  try { roster.value = await api.roster(id); }
  catch (e: any) { rosterError.value = e.message || '无法加载名册'; }
}

async function save() {
  saving.value = true;
  banner.value = null;
  try {
    await api.saveGrades(cur.value, roster.value.map(r => ({ studentId: r.studentId, grade: r.grade })));
    banner.value = { type: 'ok', text: '成绩已保存。' };
  } catch (e: any) {
    banner.value = { type: 'error', text: e.message || '保存失败' };
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="content">
    <section class="welcome">
      <div>
        <span class="eyebrow">教师门户 · 2025 秋季</span>
        <h2>成绩录入</h2>
        <p class="muted">为上学期的课程录入成绩（A / B / C / D / F / I），可留空稍后填写。</p>
      </div>
    </section>

    <p v-if="loading" class="muted">课程加载中…</p>
    <p v-else-if="!list.length" class="muted empty">上学期没有你任教的课程。</p>

    <template v-else>
      <div class="grade-tabs">
        <button v-for="o in list" :key="o.id" :class="{ selected: cur === o.id }" @click="select(o.id)">
          {{ o.title }}<small>{{ o.students }} 名学生</small>
        </button>
      </div>

      <p v-if="banner" class="banner" :class="banner.type">{{ banner.text }}</p>

      <div v-if="rosterBlocked" class="roster-hint">
        <b>注册尚未关闭</b>
        <p>按学校规定，注册关闭前不展示当学期选课学生名单。历史学期名册仍可查看并录入成绩。</p>
      </div>
      <p v-else-if="rosterError" class="banner error">{{ rosterError }}</p>
      <div v-else class="roster-card">
        <table>
          <thead><tr><th>学号</th><th>学生</th><th>成绩</th></tr></thead>
          <tbody>
            <tr v-for="r in roster" :key="r.studentId">
              <td>{{ r.studentId }}</td>
              <td>{{ r.name }}</td>
              <td>
                <select v-model="r.grade">
                  <option :value="null">暂不填写</option>
                  <option v-for="g in GRADES" :key="g" :value="g">{{ g }}</option>
                </select>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="roster-foot">
          <button class="primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存成绩' }}</button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.empty { padding: 30px; background: #fff; border: 1px solid #e1e7e7; border-radius: 8px; }
.roster-hint { background: #fff8ec; border: 1px solid #f0d9a8; border-radius: 8px; padding: 18px 22px; color: #8a5a1a; }
.roster-hint b { font-size: 15px; }
.roster-hint p { margin: 6px 0 0; font-size: 13px; line-height: 1.6; }
.grade-tabs { display: flex; gap: 10px; margin-bottom: 18px; }
.grade-tabs button { display: flex; flex-direction: column; gap: 2px; padding: 10px 16px; border: 1px solid #cbd8d8; background: #fff; border-radius: 8px; font-weight: 700; color: #315154; }
.grade-tabs button.selected { background: #173a38; color: #eaf2df; border-color: #173a38; }
.grade-tabs small { font-weight: 400; font-size: 13px; opacity: .75; }
.banner { padding: 12px 16px; border-radius: 8px; font-size: 13px; font-weight: 700; margin-bottom: 14px; }
.banner.ok { background: #e5f3e8; color: #408254; }
.banner.error { background: #f8e9df; color: #ac663a; }
.roster-card { background: #fff; border: 1px solid #e1e7e7; border-radius: 8px; padding: 6px 20px 16px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { text-align: left; color: #829093; font-size: 13px; padding: 10px; border-bottom: 1px solid #e1e7e7; }
td { padding: 11px 10px; border-bottom: 1px solid #edf0f0; }
select { padding: 7px 10px; border: 1px solid #cbd8d8; border-radius: 5px; background: #fff; font: inherit; }
.roster-foot { display: flex; justify-content: flex-end; padding-top: 14px; }
</style>
