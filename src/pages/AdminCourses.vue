<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { api, type Offering } from '../api';

const rows = ref<Offering[]>([]);
const loading = ref(true);
const closed = ref(false);

onMounted(async () => {
  rows.value = await api.adminOfferings();
  closed.value = (await api.registrationStatus()).closed;
  loading.value = false;
});

const fmt = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
const timeText = (o: Offering) => `${o.days.join('、')} ${fmt(o.start)} - ${fmt(o.end)}`;

/** 关闭注册时的风险：无教授任教 或 选课人数不足 3 */
function riskReasons(o: Offering): string[] {
  const r: string[] = [];
  if (!o.professor) r.push('无教授任教');
  if (o.seatsTaken < 3) r.push(`选课不足 3 人（当前 ${o.seatsTaken} 人）`);
  return r;
}
const willCancel = (o: Offering) => riskReasons(o).length > 0;
</script>

<template>
  <div class="ac-page">
    <section class="ac-head">
      <div>
        <span class="eyebrow">教务门户 · 2026 秋季</span>
        <h2>课程开设情况</h2>
        <p class="muted">本学期全部开课课程的任教与选课情况总览，供关闭注册前核对。</p>
      </div>
    </section>

    <p v-if="closed" class="banner info">本学期注册已关闭，以下为最终开设结果。</p>
    <p v-else class="banner warn">注册关闭时，橙色高亮的课程（无教授任教或选课不足 3 人）会被自动取消，相关学生课表将同步移除。</p>

    <div class="table-card">
      <p v-if="loading" class="muted">课程信息加载中…</p>
      <div v-else class="table-scroll">
        <table>
          <thead>
            <tr>
              <th class="col-course">课程</th>
              <th class="col-dept">系别</th>
              <th class="col-time">上课时间</th>
              <th class="col-room">教室</th>
              <th class="col-num">选课人数</th>
              <th class="col-state">名额状态</th>
              <th class="col-prof">任教教授</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in rows" :key="o.id" :class="{ risk: !closed && willCancel(o), cancelled: closed && o.cancelled }">
              <td class="col-course">
                <b class="course-code">{{ o.code }}</b>
                <span class="course-title">{{ o.title }}</span>
              </td>
              <td class="col-dept">{{ o.dept }}</td>
              <td class="col-time nowrap">{{ timeText(o) }}</td>
              <td class="col-room nowrap">{{ o.room }}</td>
              <td class="col-num"><b class="num">{{ o.seatsTaken }}</b><i>/{{ o.seatsTotal }}</i></td>
              <td class="col-state">
                <span v-if="closed && o.cancelled" class="pill st-full">已取消</span>
                <span v-else class="pill" :class="o.seatsTaken >= o.seatsTotal ? 'st-full' : 'st-open'">
                  {{ o.seatsTaken >= o.seatsTotal ? '已满' : '开放' }}
                </span>
              </td>
              <td class="col-prof">
                <span v-if="o.professor" class="prof-name">{{ o.professor }}</span>
                <span v-else class="muted-text">待分配</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ac-page { max-width: 1280px; margin: 0 auto; padding: 34px 48px 60px; }
.ac-head { margin-bottom: 24px; }
.ac-head h2 { font-size: 30px; margin: 8px 0; }
.ac-head .muted { color: #6d7c7e; font-size: 15px; }

.banner { padding: 13px 18px; border-radius: 8px; font-size: 14px; font-weight: 700; margin-bottom: 18px; }
.banner.info { background: #e3eef8; color: #31608f; }
.banner.warn { background: #f8e9df; color: #ac663a; }

.table-card { background: #fff; border: 1px solid #e1e7e7; border-radius: 10px; padding: 8px 24px 20px; }
.table-scroll { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 14px; min-width: 920px; }
th { text-align: left; color: #829093; font-size: 13px; font-weight: 700; padding: 16px 14px 12px; border-bottom: 2px solid #e1e7e7; white-space: nowrap; }
td { padding: 15px 14px; border-bottom: 1px solid #edf0f0; vertical-align: middle; }
tbody tr:last-child td { border-bottom: 0; }

.col-course { min-width: 150px; }
.course-code { display: block; font-size: 14px; color: #173a38; }
.course-title { display: block; color: #5b6b6d; font-size: 13px; margin-top: 2px; }
.col-dept { white-space: nowrap; color: #4a5a5c; }
.col-time, .col-room { color: #4a5a5c; }
.nowrap { white-space: nowrap; }
.col-num { white-space: nowrap; }
.num { font-size: 16px; color: #173a38; }
.col-num i { font-style: normal; color: #9aa7a8; margin-left: 2px; }

.pill { display: inline-block; white-space: nowrap; }
.prof-name { font-weight: 700; color: #315154; white-space: nowrap; }
.muted-text { color: #9aa7a8; white-space: nowrap; }

.col-state, .col-prof { white-space: nowrap; }

tbody tr.risk { background: #fdf7f3; }
tbody tr.risk:hover { background: #fbf0e8; }
tbody tr:hover { background: #f8fafa; }
tbody tr.risk:hover { background: #fbf0e8; }
tbody tr.cancelled { opacity: .6; background: #f4f5f5; }
tbody tr.cancelled td { text-decoration: line-through; text-decoration-color: #b9c2c2; }
tbody tr.cancelled td.col-state, tbody tr.cancelled .pill { text-decoration: none; }
</style>
