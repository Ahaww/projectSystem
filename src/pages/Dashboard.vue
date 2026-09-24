<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { api, type Offering } from '../api';
import { useAuth } from '../stores/auth';
import { useRegistration } from '../stores/registration';
import { CURRENT_TERM, useTerm } from '../stores/term';

const router = useRouter();
const auth = useAuth();
const reg = useRegistration();
const term = useTerm();
const offerings = ref<Offering[]>([]);

const firstName = computed(() => (auth.user?.name ?? '同学').split(/\s+/)[0]);
const byId = computed(() => new Map(offerings.value.map(o => [o.id, o])));
const fmt = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
const timeOf = (id: string) => {
  const o = byId.value.get(id);
  return o ? `${o.days.join(' · ')} ${fmt(o.start)} - ${fmt(o.end)} · ${o.room}` : '';
};

onMounted(async () => {
  await reg.load();
  offerings.value = await api.offerings();
});
</script>

<template>
  <div class="content">
    <section class="welcome">
      <div>
        <span class="eyebrow">{{ term.isHistorical ? '历史学期' : '当前学期' }} · {{ term.selected }}</span>
        <h2>早上好，{{ firstName }}。</h2>
        <p v-if="term.isHistorical">
          {{ term.selected }}学期注册已结束，课表为只读状态。如需查看该学期成绩，请前往成绩单。
        </p>
        <p v-else>规划适合你的课表。注册截止日期：<b>8 月 28 日</b>。</p>
      </div>
      <div class="welcome-actions">
        <template v-if="term.isHistorical">
          <button class="primary" @click="router.push('/report-card')">查看成绩单 <span>→</span></button>
          <button class="ghost" @click="term.select(CURRENT_TERM)">回到 {{ CURRENT_TERM }}学期</button>
        </template>
        <template v-else>
          <button class="primary" @click="router.push('/catalog')">去选课 <span>→</span></button>
          <button class="ghost" @click="router.push('/schedule')">查看我的课表</button>
        </template>
      </div>
    </section>

    <section v-if="term.isHistorical" class="hist-card">
      <b>你正在查看历史学期（{{ term.selected }}）</b>
      <p class="muted">选课、提交注册与课表调整仅在当前注册学期（{{ CURRENT_TERM }}）开放。{{ term.selected }}学期的最终成绩已在成绩单中公布。</p>
      <button class="ghost sm" @click="router.push('/report-card')">前往成绩单 →</button>
    </section>

    <div v-else class="stats">
      <div>
        <span>注册状态</span>
        <strong class="status-dot">{{ reg.closed ? '已关闭' : (reg.schedule.status === 'submitted' ? '已提交' : '开放') }}</strong>
        <small>截止日期：2026 年 8 月 28 日</small>
      </div>
      <div>
        <span>主选课程</span>
        <strong>{{ reg.primaries.length }} <i>/ 4</i></strong>
        <small>还可选择 {{ Math.max(0, 4 - reg.primaries.length) }} 门</small>
      </div>
      <div>
        <span>备选课程</span>
        <strong>{{ reg.alternates.length }} <i>/ 2</i></strong>
        <small>还可选择 {{ Math.max(0, 2 - reg.alternates.length) }} 门</small>
      </div>
    </div>

    <section v-if="!term.isHistorical" id="schedule" class="schedule-preview">
      <div class="sp-head">
        <div><span class="eyebrow">我的课表</span><h3>{{ reg.schedule.items.length ? '已选课程' : '暂未添加课程' }}</h3></div>
        <button v-if="reg.schedule.items.length" class="ghost sm" @click="router.push('/catalog')">调整选课</button>
      </div>

      <div v-if="reg.schedule.items.length" class="cc-grid">
        <div v-for="i in reg.schedule.items" :key="i.offeringId" class="cc" :class="{ alt: i.type === 'alternate' }">
          <div class="cc-top">
            <b>{{ i.offeringId }} · {{ byId.get(i.offeringId)?.title }}</b>
            <span class="pill" :class="i.type === 'primary' ? 'st-open' : 'st-full'">{{ i.type === 'primary' ? '主选' : '备选' }}</span>
          </div>
          <small class="cc-meta">{{ byId.get(i.offeringId)?.professor || '待分配教师' }}<br>{{ timeOf(i.offeringId) }}</small>
          <small class="cc-state" :class="i.status === 'enrolled' ? 'ok' : 'pending'">
            {{ i.status === 'enrolled' ? '✓ 已注册' : '已选择 · 待提交注册' }}
          </small>
        </div>
      </div>
      <p v-else class="muted">进入课程目录选择主选与备选课程后，这里会显示你的课表。</p>
    </section>
  </div>
</template>

<style scoped>
.welcome-actions { display: flex; gap: 10px; }
.ghost { padding: 12px 17px; border: 1px solid #cbd8d8; background: #fff; border-radius: 6px; color: #315154; font-weight: 700; }
.ghost.sm { padding: 8px 14px; font-size: 13px; }
.sp-head { display: flex; justify-content: space-between; align-items: end; }
/* 已选课程卡片 */
.cc-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; margin-top: 16px; }
.cc { background: #fff; border: 1px solid #e1e7e7; border-left: 4px solid #408254; border-radius: 8px; padding: 13px 16px; display: flex; flex-direction: column; gap: 7px; }
.cc.alt { border-left-color: #c98a4b; }
.cc-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.cc-top b { font-size: 14px; }
.cc .pill { margin-left: 0; white-space: nowrap; flex-shrink: 0; }
.cc-meta { color: #6d7c7e; font-size: 14px; line-height: 1.55; }
.cc-state { font-size: 14px; font-weight: 700; }
.cc-state.ok { color: #408254; }
.cc-state.pending { color: #b26a2e; }
.hist-card { background: #fff; border: 1px solid #e1e7e7; border-left: 4px solid #c98a4b; border-radius: 8px; padding: 22px 24px; display: flex; flex-direction: column; align-items: flex-start; gap: 10px; }
.hist-card b { font-size: 16px; color: #315154; }
.hist-card .muted { margin: 0; line-height: 1.7; }
</style>
