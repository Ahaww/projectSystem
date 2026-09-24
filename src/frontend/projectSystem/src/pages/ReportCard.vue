<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { api, type ReportCard } from '../api';
import { CURRENT_TERM, useTerm } from '../stores/term';

const term = useTerm();
const data = ref<ReportCard | null>(null);
const loading = ref(true);
const error = ref('');

/** 顶栏选中学期对应的成绩卡片 */
const shown = computed(() => (data.value?.semesters ?? []).filter(s => s.term === term.selected));
const isCurrentTerm = computed(() => term.selected === CURRENT_TERM);
const emptyText = computed(() => isCurrentTerm.value
  ? `${term.selected}学期尚未结束，成绩公布后可在此查看。`
  : `暂无 ${term.selected}学期的成绩信息。`);

onMounted(async () => {
  try { data.value = await api.reportCard(); }
  catch (e: any) { error.value = e.message || '加载失败'; }
  finally { loading.value = false; }
});
</script>

<template>
  <div class="content">
    <section class="welcome">
      <div>
        <span class="eyebrow">学生门户 · {{ term.selected }}学期</span>
        <h2>我的成绩单</h2>
        <p class="muted">正在查看 <b>{{ term.selected }}学期</b> 的已修课程成绩，可在右上角切换学期。</p>
      </div>
    </section>

    <p v-if="loading" class="muted">成绩加载中…</p>
    <p v-else-if="error" class="rc-error">{{ error }}</p>

    <template v-else-if="shown.length">
      <section v-for="s in shown" :key="s.term" class="rc-card">
        <div class="rc-head"><b>{{ s.term }}</b><small>共 {{ s.items.length }} 门课程</small></div>
        <table>
          <thead><tr><th>课程</th><th>学分</th><th>成绩</th></tr></thead>
          <tbody>
            <tr v-for="i in s.items" :key="i.code">
              <td>{{ i.code }} · {{ i.title }}</td>
              <td>{{ i.credits }}</td>
              <td><b class="rc-grade">{{ i.grade ?? '—' }}</b></td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
    <p v-else class="muted rc-empty">{{ emptyText }}</p>
  </div>
</template>

<style scoped>
.welcome h2 { font-size: 27px; margin: 8px 0; }
.rc-card { background: #fff; border: 1px solid #e1e7e7; border-radius: 8px; padding: 18px 20px; margin-bottom: 18px; }
.rc-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 10px; }
.rc-head small { color: #829093; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { text-align: left; color: #829093; font-weight: 700; font-size: 13px; text-transform: uppercase; padding: 8px 10px; border-bottom: 1px solid #e1e7e7; }
td { padding: 11px 10px; border-bottom: 1px solid #edf0f0; }
tr:last-child td { border-bottom: 0; }
.rc-grade { color: #173a38; font-size: 15px; }
.rc-error { background: #f8e9df; color: #ac663a; padding: 12px 16px; border-radius: 8px; font-weight: 700; }
.rc-empty { background: #fff; border: 1px dashed #cbd8d8; border-radius: 8px; padding: 34px 20px; text-align: center; }
</style>
