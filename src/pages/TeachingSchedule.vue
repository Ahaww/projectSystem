<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { api, type Offering } from '../api';
import { makeCourseColorOf } from '../utils/color';

const router = useRouter();
const rows = ref<Offering[]>([]);
const allCourses = ref<Offering[]>([]);
const loading = ref(true);
const closed = ref(false);

onMounted(async () => {
  // 配色以全部课程为全集，保证同一门课在学生端/教师端颜色一致
  const [eligible, all] = await Promise.all([api.eligibleOfferings(), api.offerings()]);
  rows.value = eligible;
  allCourses.value = all;
  closed.value = (await api.registrationStatus()).closed;
  loading.value = false;
});

const teaching = computed(() => rows.value.filter(o => o.teaching));

const days = ['周一', '周二', '周三', '周四', '周五'];
const slots: [number, number][] = [[480, 540], [540, 630], [660, 750], [780, 930], [930, 1020]];
const slotText = ['08:00 - 09:00', '09:00 - 10:30', '11:00 - 12:30', '13:00 - 15:30', '15:30 - 17:00'];

function at(si: number, di: number) {
  return teaching.value.find(o =>
    o.days.includes(days[di]) && o.start >= slots[si][0] && o.start < slots[si][1]
  );
}

/** 课程配色：以全部课程全集排序固定分色，同屏互不相同，且与学生端同课同色、重进不变 */
const itemColor = computed(() => makeCourseColorOf(allCourses.value.map(o => o.id)));
const fmt = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
</script>

<template>
  <div class="tt-page">
    <section class="tt-head">
      <div>
        <span class="eyebrow">当前学期 · 2026 秋季</span>
        <h2>我的任教课表</h2>
        <p class="muted">本学期共任教 {{ teaching.length }} 门课程</p>
      </div>
      <button class="tt-back" @click="router.push('/teach')"><span class="tt-arrow">←</span> 选择任教</button>
    </section>

    <p v-if="loading" class="muted">课表加载中…</p>

    <section v-else-if="teaching.length" class="tt-card">
      <div class="tt">
        <div class="tt-cell tt-corner"></div>
        <div v-for="d in days" :key="d" class="tt-cell tt-day">{{ d }}</div>
        <template v-for="(s, si) in slots" :key="s[0]">
          <div class="tt-cell tt-time">{{ slotText[si] }}</div>
          <div v-for="(d, di) in days" :key="d" class="tt-cell tt-slot" :class="{ empty: !at(si, di) }">
            <div v-if="at(si, di)" class="tt-block" :class="itemColor(at(si, di)!.id)">
              <b>{{ at(si, di)!.code }}</b>
              <span>{{ at(si, di)!.title }}</span>
              <small>{{ at(si, di)!.room }} · {{ fmt(at(si, di)!.start) }} - {{ fmt(at(si, di)!.end) }}</small>
            </div>
          </div>
        </template>
      </div>
    </section>

    <section v-else class="tt-card empty-card">
      <p class="muted">暂未选择任教课程。进入"选择任教"页面勾选课程后，这里会显示你的每周授课安排。</p>
      <button class="primary" @click="router.push('/teach')">去选择任教 →</button>
    </section>

    <section v-if="teaching.length" class="tt-card tt-list">
      <span class="eyebrow">任教课程明细</span>
      <div v-for="o in teaching" :key="o.id" class="tt-row">
        <span class="tt-dot" :class="itemColor(o.id)"></span>
        <b>{{ o.code }} · {{ o.title }}</b>
        <small>{{ o.dept }} · {{ o.room }} · {{ o.days.join('、') }} {{ fmt(o.start) }}-{{ fmt(o.end) }}</small>
      </div>
    </section>
  </div>
</template>

<style scoped>
.tt-page { max-width: 1180px; margin: 0 auto; padding: 34px 54px 60px; }
.tt-head { display: flex; justify-content: space-between; align-items: end; margin-bottom: 27px; }
.tt-head h2 { font-size: 30px; margin: 8px 0; }
.tt-head .muted { color: #6d7c7e; }
.tt-back { padding: 10px 16px; border: 1px solid #cbd8d8; background: #fff; border-radius: 6px; color: #315154; font-weight: 700; display: inline-flex; align-items: center; }
.tt-arrow { margin-right: 6px; font-weight: 800; }
.tt-card { background: #fff; border: 1px solid #e1e7e7; border-radius: 8px; padding: 20px; margin-bottom: 22px; }
.empty-card { text-align: center; padding: 40px 20px; }
.empty-card .primary { margin-top: 14px; }
.tt { display: grid; grid-template-columns: 110px repeat(5, 1fr); gap: 8px; }
.tt-cell { border-radius: 6px; }
.tt-day { background: #173a38; color: #eaf2df; text-align: center; padding: 10px 0; font-weight: 700; font-size: 14px; }
.tt-time { font-size: 14px; color: #6d7c7e; display: flex; align-items: center; }
.tt-slot { min-height: 84px; background: #f6f8f8; padding: 6px; }
.tt-slot.empty { background: #fbfcfc; border: 1px dashed #e1e7e7; }
.tt-block { height: 100%; border-radius: 5px; padding: 8px 10px; display: flex; flex-direction: column; gap: 3px; }
.tt-block b { font-size: 14px; }
.tt-block span { font-size: 13px; font-weight: 700; }
.tt-block small { font-size: 13px; opacity: .75; }
.tt-block.blue { background: #e3eef8; color: #31608f; }
.tt-block.orange { background: #f8ede0; color: #a9683c; }
.tt-block.green { background: #e5f3e8; color: #408254; }
.tt-block.purple { background: #efe8f6; color: #6b4a9c; }
.tt-block.red { background: #fbe6e6; color: #b04a4a; }
.tt-block.cyan { background: #e0f2f1; color: #2e7d7b; }
.tt-block.amber { background: #fbf3dc; color: #8a6a12; }
.tt-block.pink { background: #f9e7f0; color: #a04b73; }
.tt-list .tt-row { display: flex; align-items: center; gap: 12px; padding: 12px 4px; border-bottom: 1px solid #edf0f0; }
.tt-list .tt-row:last-child { border-bottom: 0; }
.tt-list small { color: #829093; flex: 1; }
.tt-dot { width: 10px; height: 10px; border-radius: 50%; }
.tt-dot.blue { background: #31608f; }
.tt-dot.orange { background: #a9683c; }
.tt-dot.green { background: #408254; }
.tt-dot.purple { background: #6b4a9c; }
.tt-dot.red { background: #b04a4a; }
.tt-dot.cyan { background: #2e7d7b; }
.tt-dot.amber { background: #8a6a12; }
.tt-dot.pink { background: #a04b73; }
</style>
