<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuth } from '../stores/auth';
import { roleHome } from '../router';

const router = useRouter();
const auth = useAuth();
const username = ref('');
const password = ref('');
const error = ref('');
const busy = ref(false);

async function login() {
  if (!username.value || !password.value) { error.value = '请输入用户名和密码。'; return; }
  busy.value = true;
  error.value = '';
  try {
    await auth.login(username.value, password.value);
    router.push(roleHome(auth.user?.role));
  } catch (e: any) {
    error.value = e.message || '登录失败，请稍后再试。';
  } finally {
    busy.value = false;
  }
}
function cancel() {
  username.value = '';
  password.value = '';
  error.value = '';
}
</script>

<template>
  <div class="login-page">
    <section class="login-art">
      <span class="art-label">怀利学院</span>
      <div>
        <h1>规划你的<br><em>新学期。</em></h1>
        <p>在一个清晰易用的平台中选择课程、管理课表，顺利完成学业规划。</p>
      </div>
      <small>课程注册系统 · 2026</small>
    </section>
    <section class="login-form">
      <div class="login-inner">
        <span class="eyebrow">欢迎回来</span>
        <h2>登录课程注册系统</h2>
        <p class="muted">请输入学院账号继续。</p>
        <form @submit.prevent="login">
          <label>用户名<input v-model="username" placeholder="例如：student" /></label>
          <label>密码<input v-model="password" type="password" placeholder="请输入密码" /></label>
          <p v-if="error" class="error">{{ error }}</p>
          <div class="login-actions">
            <button class="primary" :disabled="busy">{{ busy ? '登录中…' : '登录' }}</button>
            <button type="button" class="ghost" @click="cancel">取消</button>
          </div>
        </form>
        <p class="hint">演示账号（密码均为 123456）：<br>学生 student · 教授 teacher · 教务 admin</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-actions { display: flex; gap: 12px; }
.login-actions button { flex: 1; padding: 13px 0; border-radius: 7px; font-size: 16px; font-weight: 800; cursor: pointer; }
.login-actions .ghost { border: 1px solid #cbd8d8; background: #fff; color: #315154; }
.login-actions .ghost:hover { background: #f4f7f6; }
</style>
