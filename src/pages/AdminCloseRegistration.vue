<script setup lang="ts">
import { ref } from 'vue';
import { api, type CloseResult } from '../api';

const busy = ref(false);
const error = ref('');
const result = ref<CloseResult | null>(null);
const showConfirm = ref(false);

function requestClose() { showConfirm.value = true; }
function cancelClose() { showConfirm.value = false; }
async function confirmClose() {
  showConfirm.value = false;
  busy.value = true;
  error.value = '';
  result.value = null;
  try { result.value = await api.closeRegistration(); }
  catch (e: any) { error.value = e.message || '操作失败'; }
  finally { busy.value = false; }
}
</script>

<template>
  <div class="content">
    <section class="rule-card">
      <span class="eyebrow">Close Registration · 教务专用</span>
      <h2>关闭本学期注册</h2>
      <p class="muted">执行后系统将完成以下动作（不可撤销）：</p>
      <ol class="rules">
        <li>检查每门课程是否有任教教授；<b>没有教授的课程将被取消</b></li>
        <li>检查每门课程选课人数；<b>不足 3 人的课程将被取消</b>，相关学生课表同步移除</li>
        <li>对课表未满 4 门主选的学生，从其<b>备选课程</b>中自动顶替</li>
        <li>向<b>计费系统</b>发送每个学生的账单事务</li>
        <li>注册关闭后，学生和教授都无法再进行选课/任教操作</li>
      </ol>
      <button class="primary big" :disabled="busy" @click="requestClose">{{ busy ? '正在关闭…' : '确认关闭注册' }}</button>
    </section>

    <p v-if="error" class="alert error">{{ error }}</p>

    <section v-if="result" class="result-card">
      <h3>关闭注册完成</h3>
      <div class="result-grid">
        <div class="result-item">
          <span>已取消课程（{{ result.cancelled.length }} 门）</span>
          <div v-if="result.cancelled.length" class="chips">
            <span v-for="c in result.cancelled" :key="c" class="chip danger">{{ c }}</span>
          </div>
          <p v-else class="muted">无（所有课程均满足开课条件）</p>
          <small>取消条件：无授课教授 或 注册人数不足 3 人</small>
        </div>
        <div class="result-item">
          <span>课表配平升级（{{ (result.leveled ?? []).length }} 门）</span>
          <div v-if="(result.leveled ?? []).length" class="chips">
            <span v-for="c in result.leveled" :key="c" class="chip ok">{{ c }}</span>
          </div>
          <p v-else class="muted">无需配平（所有学生主选已满 4 门）</p>
          <small>主选不足 4 门时按备选优先级依次补选</small>
        </div>
        <div class="result-item">
          <span>已发送计费事务</span>
          <strong>{{ result.billed }} 条</strong>
          <small>账单将包含学生本学期最终课表</small>
        </div>
      </div>
    </section>

    <transition name="modal-fade">
      <div v-if="showConfirm" class="modal-mask" @click.self="cancelClose">
        <div class="modal-box">
          <h4>确认关闭本学期注册？</h4>
          <p>关闭后将：取消人数不足 3 人的课程、锁定所有课表、并向计费系统发送账单。此操作不可撤销。</p>
          <div class="modal-actions">
            <button class="modal-btn ghost" @click="cancelClose" :disabled="busy">取消</button>
            <button class="modal-btn danger" @click="confirmClose" :disabled="busy">确认关闭</button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.rule-card { background: #fff; border: 1px solid #e1e7e7; border-radius: 8px; padding: 24px 26px; margin-bottom: 20px; }
.rule-card h2 { font-size: 24px; margin: 8px 0; }
.rules { color: #4a5a5c; font-size: 13px; line-height: 2; margin: 10px 0 18px; padding-left: 18px; }
.primary.big { padding: 12px 26px; font-size: 14px; }
.primary:disabled { opacity: .6; cursor: not-allowed; }
.alert { padding: 12px 16px; border-radius: 8px; font-weight: 700; margin-bottom: 16px; }
.alert.error { background: #f8e9df; color: #ac663a; }
.result-card { background: #fff; border: 1px solid #e1e7e7; border-radius: 8px; padding: 22px 26px; }
.result-card h3 { margin-bottom: 14px; }
.result-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.result-item { border: 1px solid #edf0f0; border-radius: 8px; padding: 14px 16px; display: flex; flex-direction: column; gap: 8px; }
.result-item span { font-size: 14px; font-weight: 700; color: #4a5a5c; }
.result-item strong { font-size: 26px; color: #173a38; }
.result-item small { color: #829093; }
.chips { display: flex; gap: 8px; flex-wrap: wrap; }
.chip { padding: 4px 10px; border-radius: 20px; font-size: 14px; font-weight: 700; }
.chip.danger { background: #f8e9df; color: #ac663a; }
.chip.ok { background: #e5f3e8; color: #408254; }
</style>
