<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { api, type Offering } from '../api';
import { useRegistration } from '../stores/registration';
import { CURRENT_TERM, useTerm } from '../stores/term';
import { makeCourseColorOf } from '../utils/color';

const reg = useRegistration();
const term = useTerm();
const offerings = ref<Offering[]>([]);
const loading = ref(true);
const tab = ref('all');
const search = ref('');
const banner = ref<{ type: 'ok' | 'error'; text: string } | null>(null);
const errorRows = ref<Record<string, string>>({});
const loadError = ref('');

const depts = computed(() => ['all', ...Array.from(new Set(offerings.value.map(o => o.dept)))]);
const deptLabel = (d: string) => (d === 'all' ? '全部课程' : d);
const filtered = computed(() => offerings.value.filter(o =>
  (tab.value === 'all' || o.dept === tab.value) &&
  (!search.value || `${o.code}${o.title}${o.dept}`.includes(search.value))));

const fmtTime = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
const isFull = (o: Offering) => seatsOf(o) >= o.seatsTotal;
/** 课程配色：按课程全集排序固定分色，目录中各门课颜色互不相同 */
const colorOf = computed(() => makeCourseColorOf(offerings.value.map(o => o.id)));
const byCode = (id: string) => {
  const o = offerings.value.find(x => x.id === id);
  return o ? `${o.code} · ${o.title}` : id;
};

/** 客户端时间冲突检测：课程 o 与已选的任意课程（主选+备选，不含自身）是否时间重叠 */
function hasConflict(o: Offering): boolean {
  return reg.schedule.items.some(it => {
    if (it.offeringId === o.id) return false;
    const other = offerings.value.find(x => x.id === it.offeringId);
    if (!other) return false;
    return o.days.some(d => other.days.includes(d)) && o.start < other.end && other.start < o.end;
  });
}
function conflictOf(o: Offering): string | null {
  for (const it of reg.schedule.items) {
    if (it.offeringId === o.id) continue;
    const other = offerings.value.find(x => x.id === it.offeringId);
    if (!other) continue;
    if (o.days.some(d => other.days.includes(d)) && o.start < other.end && other.start < o.end) {
      return `${o.code} 与 ${other.code} 上课时间冲突`;
    }
  }
  return null;
}

const canPickPrimary = (o: Offering) => reg.editable && !isFull(o) && !hasConflict(o) && reg.primaries.length < 4;
const canPickAlternate = (o: Offering) => reg.editable && !isFull(o) && !hasConflict(o) && reg.alternates.length < 2;
const disabledTip = (o: Offering) => {
  if (reg.closed) return '注册已关闭';
  if (isFull(o)) return '该课程名额已满';
  const c = conflictOf(o);
  if (c) return '课程冲突：' + c;
  return '已达选择上限';
};

onMounted(async () => {
  try {
    await reg.load();
    offerings.value = await api.offerings();
  } catch (e: any) {
    loadError.value = e?.message || String(e);
  } finally {
    loading.value = false;
  }
});

function pick(o: Offering, type: 'primary' | 'alternate') {
  banner.value = null;
  errorRows.value = {};
  reg.toggle(o, type);
}

/** 显示人数 = 真实已选 + 本人暂存未提交的主选（提交后转入真实计数，不重复加） */
function seatsOf(o: Offering) {
  const pending = reg.schedule.items.some(i => i.offeringId === o.id && i.type === 'primary' && i.status !== 'enrolled');
  return o.seatsTaken + (pending ? 1 : 0);
}

async function save() {
  banner.value = null;
  await reg.save();
  banner.value = { type: 'ok', text: '课表已保存（尚未提交注册），可随时回来继续调整。' };
}

async function submit() {
  banner.value = null;
  errorRows.value = {};
  const r = await reg.submit();
  if (r.ok) {
    banner.value = { type: 'ok', text: `注册提交成功，已注册 ${reg.primaries.filter(i => i.status === 'enrolled').length} 门主选课程。` };
    offerings.value = await api.offerings();
  } else {
    banner.value = { type: 'error', text: r.message || '提交失败，请稍后再试。' };
    const m: Record<string, string> = {};
    r.errors.forEach(e => { if (e.offeringId) m[e.offeringId] = e.message; });
    errorRows.value = m;
  }
}

async function reset() {
  banner.value = null;
  errorRows.value = {};
  await reg.reset();
  offerings.value = await api.offerings();
}
</script>

<template>
  <div class="content">
    <div class="cat-toolbar">
      <div>
        <span class="eyebrow">课程目录 · {{ term.selected }}学期</span>
        <h3>{{ term.isHistorical ? '历史学期课程（只读）' : '选择你的课程' }}</h3>
        <small v-if="!term.isHistorical" class="muted">主选 {{ reg.primaries.length }}/4 · 备选 {{ reg.alternates.length }}/2</small>
        <small v-else class="muted">{{ term.selected }}学期注册已结束</small>
      </div>
      <div class="cat-actions">
        <span v-if="term.isHistorical" class="pill st-full">历史学期</span>
        <span v-else-if="reg.closed" class="pill st-full">注册已关闭</span>
        <span v-else-if="!reg.editable" class="pill submitted">已提交注册</span>
        <template v-if="reg.editable && !term.isHistorical">
          <button class="cat-btn" :disabled="reg.saving" @click="reset">清空</button>
          <button class="cat-btn" :disabled="reg.saving || !reg.schedule.items.length" @click="save">
            {{ reg.saving ? '保存中…' : '保存课表' }}
          </button>
          <button class="primary" :disabled="reg.submitting || !reg.primaries.length" @click="submit">
            {{ reg.submitting ? '提交中…' : '提交注册' }}
          </button>
        </template>
      </div>
    </div>

    <p v-if="banner" class="banner" :class="banner.type">{{ banner.text }}</p>
    <p v-if="loadError" class="banner error">课程目录加载失败：{{ loadError }}</p>

    <!-- 历史学期：只读，不能再选课 -->
    <section v-if="term.isHistorical" class="hist-card">
      <b>{{ term.selected }}学期注册已结束</b>
      <p class="muted">选课与课表调整仅在当前注册学期（{{ CURRENT_TERM }}）开放。你可以切换回当前学期继续选课，或在成绩单中查看该学期最终成绩。</p>
      <button class="primary" @click="term.select(CURRENT_TERM)">回到 {{ CURRENT_TERM }}学期选课</button>
    </section>

    <template v-else>
    <!-- 我的选择：主选 / 备选 分区展示 -->
    <section v-if="reg.schedule.items.length" class="my-pick">
      <div class="pick-col">
        <h4>主选课程 <small>{{ reg.primaries.length }}/4</small></h4>
        <div v-if="!reg.primaries.length" class="pick-empty">尚未选择主选课程</div>
        <div v-for="i in reg.primaries" :key="i.offeringId" class="pick-chip primary">
          <b>{{ byCode(i.offeringId) }}</b>
          <span class="pill st-open">主选</span>
        </div>
      </div>
      <div class="pick-col">
        <h4>备选课程 <small>{{ reg.alternates.length }}/2</small></h4>
        <div v-if="!reg.alternates.length" class="pick-empty">尚未选择备选课程</div>
        <div v-for="i in reg.alternates" :key="i.offeringId" class="pick-chip alt">
          <b>{{ byCode(i.offeringId) }}</b>
          <span class="pill st-full">备选 · 优先级 {{ i.priority ?? '-' }}</span>
        </div>
      </div>
    </section>

    <section class="courses">
      <div class="section-head">
        <div><span class="eyebrow">本学期开课</span><h3>查找课程</h3></div>
        <div class="search">⌕ <input v-model="search" placeholder="搜索课程" /></div>
      </div>
      <div class="tabs">
        <button v-for="d in depts" :key="d" :class="{ selected: tab === d }" @click="tab = d">{{ deptLabel(d) }}</button>
      </div>
      <p v-if="loading" class="muted pad">课程目录加载中…</p>
      <div v-else class="course-list">
        <article v-for="o in filtered" :key="o.id" class="course-row" :class="{ 'row-error': errorRows[o.id] }">
          <span class="course-icon" :class="colorOf(o.id)">{{ o.code.slice(0, 2) }}</span>
          <div class="course-main">
            <div><b>{{ o.code }} · {{ o.title }}</b>
              <span class="pill" :class="isFull(o) ? 'st-full' : 'st-open'">{{ isFull(o) ? '已满' : '开放' }}</span>
              <span v-if="reg.typeOf(o.id)" class="pill picked">{{ reg.typeOf(o.id) === 'primary' ? '主选' : '备选' }}</span>
              <span v-if="hasConflict(o) && !reg.typeOf(o.id)" class="pill conflict">课程冲突</span>
            </div>
            <small>{{ o.dept }}<template v-if="o.prerequisites.length"> · 先修课：{{ o.prerequisites.join('、') }}</template></small>
          </div>
          <div class="course-time">
            <b>{{ o.days.join(' · ') }}</b>
            <small>{{ fmtTime(o.start) }} - {{ fmtTime(o.end) }} · {{ o.room }}</small>
          </div>
          <div class="seats"><b>{{ seatsOf(o) }}<i>/{{ o.seatsTotal }}</i></b><small>已选人数</small></div>
          <div v-if="reg.editable" class="row-actions">
            <!-- 上按钮固定管"主选"：未选→设为主选；备选→升级为主选；已是主选→移除 -->
            <button v-if="reg.typeOf(o.id) === 'primary'" class="row-btn danger" @click="pick(o, 'primary')">移除主选</button>
            <button v-else class="row-btn solid" :disabled="!canPickPrimary(o)"
              :title="reg.typeOf(o.id) === 'alternate' ? '将备选升级为主选' : (canPickPrimary(o) ? '' : disabledTip(o))"
              @click="pick(o, 'primary')">{{ reg.typeOf(o.id) === 'alternate' ? '升级为主选' : '设为主选' }}</button>
            <!-- 下按钮固定管"备选"：未选/主选→设为备选；已是备选→移除 -->
            <button v-if="reg.typeOf(o.id) === 'alternate'" class="row-btn danger" @click="pick(o, 'alternate')">移除备选</button>
            <button v-else class="row-btn" :disabled="!canPickAlternate(o)" :title="canPickAlternate(o) ? '' : disabledTip(o)" @click="pick(o, 'alternate')">设为备选</button>
          </div>
        </article>
        <p v-if="!filtered.length" class="muted pad">没有符合条件的课程。</p>
      </div>
    </section>

    <div class="error-summary">
      <p v-for="(msg, id) in errorRows" :key="id" class="banner error">{{ id }}：{{ msg }}</p>
    </div>
    </template>
  </div>
</template>

<style scoped>
.pad { padding: 16px 20px; }
.cat-toolbar { display: flex; justify-content: space-between; align-items: end; margin-bottom: 18px; }
.cat-toolbar h3 { font-size: 22px; margin: 6px 0 4px; }
.cat-actions { display: flex; gap: 10px; align-items: center; }
.cat-btn { padding: 11px 15px; border: 1px solid #cbd8d8; background: #fff; border-radius: 6px; color: #315154; font-weight: 700; }
.cat-btn:disabled { color: #aab5b5; cursor: not-allowed; }
.pill.picked { background: #e8eef8; color: #31608f; }
.pill.submitted { background: #e5f3e8; color: #408254; }
.banner { padding: 12px 16px; border-radius: 8px; font-size: 13px; font-weight: 700; margin-bottom: 14px; }
.banner.ok { background: #e5f3e8; color: #408254; }
.banner.error { background: #f8e9df; color: #ac663a; }
/* 标题行：课程名 + 徽标横向排布，不换行不拉伸 */
.course-main { min-width: 0; overflow: hidden; }
.course-main > div:first-child { display: flex; align-items: center; gap: 8px; }
.course-main b { white-space: nowrap; }
.course-main .pill { margin-left: 0; white-space: nowrap; flex-shrink: 0; }
/* 窄屏兼容：行保持最小宽度横向滚动，分类页签不折行 */
.course-list { overflow-x: auto; }
.course-row { min-width: 760px; }
.tabs { overflow-x: auto; }
.tabs button { white-space: nowrap; }
/* 行操作按钮列：固定宽度，按钮不折行、层次分明 */
.row-actions { width: 96px; display: flex; flex-direction: column; gap: 8px; flex-shrink: 0; }
.row-btn {
  width: 100%; padding: 9px 0; font-size: 14px; font-weight: 700; line-height: 1;
  border-radius: 6px; border: 1px solid #cbd8d8; background: #fff; color: #315154;
  white-space: nowrap; text-align: center; cursor: pointer; transition: background .15s, color .15s;
}
.row-btn:hover:not(:disabled) { border-color: #173a38; color: #173a38; }
.row-btn:disabled { color: #aab5b5; background: #f6f8f8; border-color: #e1e7e7; cursor: not-allowed; }
.row-btn.solid { background: #173a38; border-color: #173a38; color: #eaf2df; }
.row-btn.solid:hover:not(:disabled) { background: #0f2a28; color: #fff; }
.row-btn.danger { color: #a04b2e; border-color: #e0b7a0; }
.row-btn.danger:hover:not(:disabled) { background: #fdf3ec; color: #7d3419; }
.course-row.row-error { background: #fdf3ec; }
.error-summary:empty { display: none; }
/* 我的选择：主选 / 备选 分区 */
.my-pick { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin: 18px 0 8px; }
.pick-col { background: #fff; border: 1px solid #e1e7e7; border-radius: 8px; padding: 14px 18px; }
.pick-col h4 { margin: 0 0 10px; font-size: 14px; display: flex; align-items: baseline; gap: 8px; }
.pick-col h4 small { font-size: 14px; color: #6d7c7e; font-weight: 400; }
.pick-empty { color: #9aa7a8; font-size: 13px; padding: 6px 0; }
.pick-chip { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 9px 12px; border-radius: 6px; margin-bottom: 8px; }
.pick-chip b { font-size: 13px; }
.pick-chip.primary { background: #e5f3e8; border-left: 3px solid #408254; }
.pick-chip.alt { background: #f8ede0; border-left: 3px solid #c98a4b; }
.pick-chip .pill { margin-left: 0; }
.pill.conflict { background: #f8e9df; color: #ac663a; }
/* 历史学期只读提示卡 */
.hist-card { background: #fff; border: 1px solid #e1e7e7; border-left: 4px solid #c98a4b; border-radius: 8px; padding: 26px 28px; display: flex; flex-direction: column; align-items: flex-start; gap: 10px; }
.hist-card b { font-size: 17px; color: #315154; }
.hist-card .muted { margin: 0; line-height: 1.7; }
.hist-card .primary { margin-top: 6px; padding: 11px 20px; }
</style>
