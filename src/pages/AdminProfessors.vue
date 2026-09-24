<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { api, type ProfessorRow } from '../api';

const rows = ref<ProfessorRow[]>([]);
const loading = ref(true);
const busy = ref(false);
const error = ref('');
const editingId = ref<string | null>(null);
const banner = ref('');
const pendingDelete = ref<ProfessorRow | null>(null);

// 按工号 / 姓名检索（FR-37、FR-39）
const keyword = ref('');
const query = ref('');
function doSearch() { query.value = keyword.value.trim(); error.value = ''; }
function clearSearch() { keyword.value = ''; query.value = ''; }
const filteredRows = computed(() => {
  if (!query.value) return rows.value;
  const q = query.value.toLowerCase();
  return rows.value.filter(r => r.id.toLowerCase().includes(q) || r.name.includes(query.value));
});
const notFound = computed(() => !!query.value && filteredRows.value.length === 0);

function requestDelete(r: ProfessorRow) { pendingDelete.value = r; }
function cancelDelete() { pendingDelete.value = null; }
async function confirmDelete() {
  const r = pendingDelete.value;
  pendingDelete.value = null;
  if (!r) return;
  error.value = '';
  banner.value = '';
  try { await api.deleteProfessor(r.id); banner.value = `教师 ${r.name} 已删除。`; await load(); }
  catch (e: any) { error.value = e.message || '删除失败'; }
}

const blank = () => ({ name: '', dob: '', ssn: '', status: '在职', dept: '' });
const form = reactive(blank());

async function load() { rows.value = await api.professors(); }

onMounted(async () => { await load(); loading.value = false; });

function edit(r: ProfessorRow) {
  editingId.value = r.id;
  Object.assign(form, { name: r.name, dob: r.dob, ssn: r.ssn, status: r.status, dept: r.dept });
}

function cancelEdit() { editingId.value = null; Object.assign(form, blank()); }

async function submit() {
  if (!form.name || !form.dob || !form.ssn || !form.dept) { error.value = '请填写姓名、出生日期、证件号和系别。'; return; }
  busy.value = true;
  error.value = '';
  banner.value = '';
  try {
    if (editingId.value) {
      await api.updateProfessor(editingId.value, { ...form });
      banner.value = '教师信息已更新。';
    } else {
      const created = await api.addProfessor({ ...form });
      banner.value = `教师已添加，工号：${created.id}。`;
    }
    cancelEdit();
    await load();
  } catch (e: any) { error.value = e.message || '操作失败'; }
  finally { busy.value = false; }
}
</script>

<template>
  <div class="content">
    <p v-if="banner" class="banner ok">{{ banner }}</p>
    <p v-if="error" class="banner error">{{ error }}</p>

    <section class="form-card">
      <span class="eyebrow">{{ editingId ? `编辑教师（${editingId}）` : '新增教师' }}</span>
      <form class="form-grid" @submit.prevent="submit">
        <label>姓名<input v-model="form.name" placeholder="教师姓名" /></label>
        <label>出生日期<input v-model="form.dob" type="date" /></label>
        <label>证件号<input v-model="form.ssn" placeholder="身份证号" /></label>
        <label>系别<input v-model="form.dept" placeholder="如：计算机科学" /></label>
        <label>状态<select v-model="form.status"><option>在职</option><option>休假</option><option>离职</option></select></label>
        <div class="form-actions">
          <button class="primary" :disabled="busy">{{ busy ? '提交中…' : editingId ? '保存修改' : '添加教师' }}</button>
          <button v-if="editingId" type="button" class="ghost" @click="cancelEdit">取消</button>
        </div>
      </form>
    </section>

    <div class="table-card">
      <form class="search-bar" @submit.prevent="doSearch">
        <input v-model="keyword" placeholder="输入工号或姓名检索，如 P001" />
        <button class="primary" type="submit">检索</button>
        <button v-if="query" type="button" class="ghost" @click="clearSearch">清除检索</button>
        <span v-if="query && !notFound" class="search-tip">已按「{{ query }}」筛选出 {{ filteredRows.length }} 条记录</span>
      </form>
      <p v-if="loading" class="muted">教师列表加载中…</p>
      <table v-else>
        <thead><tr><th>工号</th><th>姓名</th><th>出生日期</th><th>证件号</th><th>系别</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="r in filteredRows" :key="r.id">
            <td>{{ r.id }}</td><td>{{ r.name }}</td><td>{{ r.dob }}</td><td>{{ r.ssn }}</td><td>{{ r.dept }}</td>
            <td><span class="pill" :class="r.status === '在职' ? 'st-open' : 'st-full'">{{ r.status }}</span></td>
            <td class="row-actions">
              <button @click="edit(r)">编辑</button>
              <button class="danger" @click="requestDelete(r)">删除</button>
            </td>
          </tr>
          <tr v-if="notFound"><td colspan="7" class="muted not-found">未找到工号或姓名为「{{ query }}」的教师，请重新输入或清除检索。</td></tr>
          <tr v-else-if="!filteredRows.length"><td colspan="7" class="muted">暂无教师信息。</td></tr>
        </tbody>
      </table>
    </div>

    <transition name="modal-fade">
      <div v-if="pendingDelete" class="modal-mask" @click.self="cancelDelete">
        <div class="modal-box">
          <h4>确认删除教师？</h4>
          <p>将删除教师 {{ pendingDelete.name }}（{{ pendingDelete.id }}），此操作不可恢复。</p>
          <div class="modal-actions">
            <button class="modal-btn ghost" @click="cancelDelete">取消</button>
            <button class="modal-btn danger" @click="confirmDelete">确认删除</button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.banner { padding: 12px 16px; border-radius: 8px; font-size: 13px; font-weight: 700; margin-bottom: 14px; }
.banner.ok { background: #e5f3e8; color: #408254; }
.banner.error { background: #f8e9df; color: #ac663a; }
.form-card, .table-card { background: #fff; border: 1px solid #e1e7e7; border-radius: 8px; padding: 18px 20px; margin-bottom: 20px; }
.search-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.search-bar input { flex: 0 0 300px; padding: 9px 12px; border: 1px solid #cbd8d8; border-radius: 5px; font: inherit; }
.search-bar .primary { padding: 9px 20px; border: 0; background: #c7d651; border-radius: 5px; color: #173a38; font-weight: 800; cursor: pointer; }
.search-tip { color: #408254; font-size: 13px; font-weight: 700; }
.not-found { color: #ac663a !important; font-weight: 700; }
.form-grid { display: grid; grid-template-columns: repeat(3, 1fr) auto; gap: 12px 14px; margin-top: 10px; align-items: end; }
.form-grid label { display: flex; flex-direction: column; gap: 5px; font-size: 14px; font-weight: 700; color: #4a5a5c; }
.form-grid input, .form-grid select { padding: 9px 11px; border: 1px solid #cbd8d8; border-radius: 5px; font: inherit; }
.form-actions { display: flex; gap: 8px; }
.ghost { padding: 9px 14px; border: 1px solid #cbd8d8; background: #fff; border-radius: 5px; color: #315154; font-weight: 700; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { text-align: left; color: #829093; font-size: 13px; padding: 9px 10px; border-bottom: 1px solid #e1e7e7; }
td { padding: 11px 10px; border-bottom: 1px solid #edf0f0; }
tr:last-child td { border-bottom: 0; }
.row-actions { display: flex; gap: 8px; }
.row-actions button { padding: 6px 12px; border: 1px solid #cbd8d8; background: #fff; border-radius: 5px; color: #315154; font-weight: 700; }
.row-actions .danger { color: #a04b2e; border-color: #e0b7a0; }
</style>
