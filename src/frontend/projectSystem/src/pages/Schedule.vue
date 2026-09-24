<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { api, type Offering } from '../api';
import { useRegistration } from '../stores/registration';
import { CURRENT_TERM, useTerm } from '../stores/term';
import { makeCourseColorOf } from '../utils/color';

const router = useRouter();
const reg = useRegistration();
const term = useTerm();
const offerings = ref<Offering[]>([]);
const loading = ref(true);

onMounted(async () => {
  await reg.load();
  offerings.value = await api.offerings();
  loading.value = false;
});

const byId = computed(() => new Map(offerings.value.map(o => [o.id, o])));

const days = ['周一', '周二', '周三', '周四', '周五'];
const slots: [number, number][] = [[480, 540], [540, 630], [660, 750], [780, 930], [930, 1020]];
const slotText = ['08:00 - 09:00', '09:00 - 10:30', '11:00 - 12:30', '13:00 - 15:30', '15:30 - 17:00'];

function at(si: number, di: number) {
  return reg.schedule.items.find(it => {
    const o = byId.value.get(it.offeringId);
    return o && o.days.includes(days[di]) && o.start >= slots[si][0] && o.start < slots[si][1];
  });
}
/** 课程配色：以课程全集排序固定分色，同屏课程颜色互不相同，且跨页面/重进保持一致 */
const itemColor = computed(() => makeCourseColorOf(offerings.value.map(o => o.id)));
const fmt = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;

const showConfirm = ref(false);
const clearing = ref(false);
const clearError = ref('');

function requestClear() { showConfirm.value = true; }
function cancelClear() { showConfirm.value = false; }
async function confirmClear() {
  clearing.value = true;
  clearError.value = '';
  try {
    await reg.reset();
    showConfirm.value = false;
  } catch (e: any) {
    clearError.value = e?.message || '删除失败，请稍后再试。';
    showConfirm.value = false;
  } finally { clearing.value = false; }
}
</script>

<template>
  <div class="tt-page">
    <section class="tt-head">
      <div>
        <span class="eyebrow">{{ term.isHistorical ? '历史学期' : '当前学期' }} · {{ term.selected }}</span>
        <h2>我的课程表</h2>
        <p class="muted">
          <template v-if="term.isHistorical">{{ term.selected }}学期注册已结束，课表为只读状态。</template>
          <template v-else>
            共 {{ reg.schedule.items.length }} 门课程
            （主选 {{ reg.primaries.length }} · 备选 {{ reg.alternates.length }}）
            <template v-if="reg.closed"> · 注册已关闭，课表已锁定</template>
            <template v-else-if="reg.schedule.status === 'submitted'"> · 已提交注册</template>
          </template>
        </p>
      </div>
      <div class="tt-actions">
        <template v-if="term.isHistorical">
          <button class="tt-back" @click="router.push('/report-card')">查看成绩单 <span class="tt-arrow">→</span></button>
          <button class="tt-back primary-back" @click="term.select(CURRENT_TERM)">回到 {{ CURRENT_TERM }}学期</button>
        </template>
        <template v-else>
          <button v-if="reg.schedule.items.length && !reg.closed" class="tt-danger" @click="requestClear">删除全部选课</button>
          <button class="tt-back" @click="router.push('/catalog')"><span class="tt-arrow">←</span> 去选课</button>
        </template>
      </div>
    </section>

    <p v-if="clearError" class="tt-alert">{{ clearError }}</p>

    <section v-if="term.isHistorical" class="tt-card hist-card">
      <b>你正在查看历史学期（{{ term.selected }}）的课表</b>
      <p class="muted">该学期注册已结束，课表不可再调整。选课与课表操作仅在当前注册学期（{{ CURRENT_TERM }}）开放，该学期最终成绩请在成绩单中查看。</p>
      <button class="primary" @click="router.push('/report-card')">前往成绩单 →</button>
    </section>

    <template v-else>
    <p v-if="loading" class="muted">课表加载中…</p>

    <section v-else-if="reg.schedule.items.length" class="tt-card">
      <div class="tt">
        <div class="tt-cell tt-corner"></div>
        <div v-for="d in days" :key="d" class="tt-cell tt-day">{{ d }}</div>
        <template v-for="(s, si) in slots" :key="s[0]">
          <div class="tt-cell tt-time">{{ slotText[si] }}</div>
          <div v-for="(d, di) in days" :key="d" class="tt-cell tt-slot" :class="{ empty: !at(si, di) }">
            <div v-if="at(si, di)" class="tt-block" :class="[itemColor(at(si, di)!.offeringId), { alt: at(si, di)!.type === 'alternate' }]">
              <b>{{ at(si, di)!.offeringId }} <i class="tt-tag">{{ at(si, di)!.type === 'primary' ? '主选' : '备选' }}</i></b>
              <span>{{ byId.get(at(si, di)!.offeringId)?.title }}</span>
              <small>
                {{ byId.get(at(si, di)!.offeringId)?.room }}
                · {{ fmt(byId.get(at(si, di)!.offeringId)!.start) }} - {{ fmt(byId.get(at(si, di)!.offeringId)!.end) }}
                <em v-if="at(si, di)!.status === 'enrolled'">已注册</em>
              </small>
            </div>
          </div>
        </template>
      </div>
    </section>

    <section v-else class="tt-card empty-card">
      <p class="muted">暂未选择课程。进入课程目录添加主选与备选课程后，这里会显示完整的每周课表。</p>
      <button class="primary" @click="router.push('/catalog')">去选课 →</button>
    </section>

    <section v-if="reg.schedule.items.length" class="tt-card tt-list">
      <span class="eyebrow">课程明细</span>
      <div class="tt-part">
        <h5>主选课程（{{ reg.primaries.length }}）</h5>
        <div v-if="!reg.primaries.length" class="tt-empty">无</div>
        <div v-for="i in reg.primaries" :key="i.offeringId" class="tt-row">
          <span class="tt-dot" :class="itemColor(i.offeringId)"></span>
          <b>{{ i.offeringId }} · {{ byId.get(i.offeringId)?.title }}</b>
          <small>{{ byId.get(i.offeringId)?.professor || '待分配教师' }} · {{ byId.get(i.offeringId)?.room }}</small>
          <span class="pill st-open">主选</span>
          <span class="pill" :class="i.status === 'enrolled' ? 'st-open' : ''">{{ i.status === 'enrolled' ? '已注册' : '已选择' }}</span>
        </div>
      </div>
      <div class="tt-part">
        <h5>备选课程（{{ reg.alternates.length }}）</h5>
        <div v-if="!reg.alternates.length" class="tt-empty">无</div>
        <div v-for="i in reg.alternates" :key="i.offeringId" class="tt-row">
          <span class="tt-dot" :class="itemColor(i.offeringId)"></span>
          <b>{{ i.offeringId }} · {{ byId.get(i.offeringId)?.title }}</b>
          <small>{{ byId.get(i.offeringId)?.professor || '待分配教师' }} · {{ byId.get(i.offeringId)?.room }}</small>
          <span class="pill st-full">备选 · 优先级 {{ i.priority ?? '-' }}</span>
          <span class="pill">已选择</span>
        </div>
      </div>
    </section>
    </template>

    <!-- 自定义确认弹窗（替代浏览器 confirm） -->
    <transition name="modal-fade">
      <div v-if="showConfirm" class="modal-mask" @click.self="cancelClear">
        <div class="modal-box">
          <h4>确认删除全部选课？</h4>
          <p>将清空所有主选与备选课程，此操作不可恢复。</p>
          <div class="modal-actions">
            <button class="modal-btn ghost" @click="cancelClear" :disabled="clearing">取消</button>
            <button class="modal-btn danger" @click="confirmClear" :disabled="clearing">
              {{ clearing ? '删除中…' : '确认删除' }}
            </button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.tt-page { max-width: 1180px; margin: 0 auto; padding: 34px 54px 60px; }
.tt-head { display: flex; justify-content: space-between; align-items: end; margin-bottom: 27px; }
.tt-head h2 { font-size: 30px; margin: 8px 0; }
.tt-head .muted { color: #6d7c7e; }
.tt-back { padding: 10px 16px; border: 1px solid #cbd8d8; background: #fff; border-radius: 6px; color: #315154; font-weight: 700; display: inline-flex; align-items: center; }
.tt-arrow { margin-right: 6px; font-weight: 800; }
.tt-actions { display: flex; gap: 10px; }
.tt-danger { padding: 10px 16px; border: 1px solid #dcb2a0; background: #fff; border-radius: 6px; color: #a04b2e; font-weight: 700; }
.tt-danger:hover { background: #fdf3ec; }
.tt-card { background: #fff; border: 1px solid #e1e7e7; border-radius: 8px; padding: 20px; margin-bottom: 22px; }
.tt-alert { background: #f8e9df; color: #ac663a; padding: 12px 16px; border-radius: 8px; font-weight: 700; margin: -8px 0 18px; }
.hist-card { border-left: 4px solid #c98a4b; display: flex; flex-direction: column; align-items: flex-start; gap: 10px; }
.hist-card b { font-size: 16px; color: #315154; }
.hist-card p { margin: 0; line-height: 1.7; }
.hist-card .primary { margin-top: 6px; padding: 11px 20px; }
.primary-back { background: #173a38; color: #eaf2df; border-color: #173a38; }
.empty-card { text-align: center; padding: 40px 20px; }
.empty-card .primary { margin-top: 14px; }
.tt { display: grid; grid-template-columns: 110px repeat(5, 1fr); gap: 8px; }
.tt-cell { border-radius: 6px; }
.tt-day { background: #173a38; color: #eaf2df; text-align: center; padding: 10px 0; font-weight: 700; font-size: 13px; }
.tt-time { font-size: 14px; color: #6d7c7e; display: flex; align-items: center; }
.tt-slot { min-height: 84px; background: #f6f8f8; padding: 6px; }
.tt-slot.empty { background: #fbfcfc; border: 1px dashed #e1e7e7; }
.tt-block { height: 100%; border-radius: 5px; padding: 8px 10px; display: flex; flex-direction: column; gap: 3px; }
.tt-block b { font-size: 14px; }
.tt-block span { font-size: 13px; font-weight: 700; }
.tt-block small { font-size: 13px; opacity: .75; }
.tt-block small em { font-style: normal; color: #408254; font-weight: 700; }
.tt-block.blue { background: #e3eef8; color: #31608f; }
.tt-block.orange { background: #f8ede0; color: #a9683c; }
.tt-block.green { background: #e5f3e8; color: #408254; }
.tt-block.purple { background: #efe8f6; color: #6b4a9c; }
.tt-block.red { background: #fbe6e6; color: #b04a4a; }
.tt-block.cyan { background: #e0f2f1; color: #2e7d7b; }
.tt-block.amber { background: #fbf3dc; color: #8a6a12; }
.tt-block.pink { background: #f9e7f0; color: #a04b73; }
.tt-block.alt { border: 1.5px dashed currentColor; background: #fff; }
.tt-tag { font-style: normal; font-size: 14px; border: 1px solid currentColor; border-radius: 8px; padding: 0 5px; margin-left: 4px; }
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
.tt-part { margin-top: 14px; }
.tt-part:first-of-type { margin-top: 6px; }
.tt-part h5 { margin: 0 0 8px; font-size: 13px; color: #315154; }
.tt-empty { color: #9aa7a8; font-size: 13px; padding: 4px 0; }
</style>
