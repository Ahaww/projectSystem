<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuth } from './stores/auth';
import { CURRENT_TERM, TERMS, useTerm } from './stores/term';

const route = useRoute();
const router = useRouter();
const auth = useAuth();
const term = useTerm();
const termOpen = ref(false);

const roleLabel: Record<string, string> = { STUDENT: '学生', PROFESSOR: '教授', REGISTRAR: '教务' };
const portalLabel: Record<string, string> = { STUDENT: '学生门户', PROFESSOR: '教师门户', REGISTRAR: '教务门户' };

const navs = computed(() => {
  switch (auth.user?.role) {
    case 'PROFESSOR': return [{ to: '/teach', label: '选择任教' }, { to: '/teaching-schedule', label: '任教课表' }, { to: '/grades', label: '成绩录入' }];
    case 'REGISTRAR': return [{ to: '/admin/courses', label: '课程情况' }, { to: '/admin/students', label: '学生信息' }, { to: '/admin/professors', label: '教师信息' }, { to: '/admin/close', label: '关闭注册' }];
    default: return [{ to: '/', label: '首页概览' }, { to: '/catalog', label: '课程目录' }, { to: '/schedule', label: '我的课表' }, { to: '/report-card', label: '成绩单' }];
  }
});

const titles: Record<string, string> = {
  '/': '注册概览', '/catalog': '课程目录', '/schedule': '我的课表', '/report-card': '成绩单',
  '/teach': '选择任教课程', '/teaching-schedule': '我的任教课表', '/grades': '成绩录入',
  '/admin/courses': '课程开设情况',
  '/admin/students': '学生信息维护', '/admin/professors': '教师信息维护', '/admin/close': '关闭注册',
};
const title = computed(() => titles[route.path] ?? '课程注册系统');
const initials = computed(() => {
  const n = auth.user?.name ?? '';
  return /^[a-zA-Z]/.test(n) ? n.split(/\s+/).map(w => w[0]?.toUpperCase() ?? '').join('').slice(0, 2) : n.slice(0, 1);
});

function logout() { auth.logout(); router.push('/login'); }
</script>

<template>
  <div v-if="auth.user" class="shell">
    <aside>
      <div class="brand"><span class="brand-mark">W</span><div><strong>怀利学院</strong><small>课程注册系统</small></div></div>
      <nav>
        <router-link v-for="n in navs" :key="n.to" :to="n.to" :class="{ active: route.path === n.to }">{{ n.label }}</router-link>
      </nav>
      <div class="side-footer">
        <span class="avatar">{{ initials }}</span>
        <div><b>{{ auth.user.name }}</b><small>{{ roleLabel[auth.user.role] }}</small></div>
        <button @click="logout" title="退出登录">↪</button>
      </div>
    </aside>
    <main>
      <header>
        <div><span class="eyebrow">{{ portalLabel[auth.user.role] }}</span><h1>{{ title }}</h1></div>
        <div v-if="auth.user.role === 'STUDENT'" class="term-select">
          <button type="button" class="term-btn" :class="{ open: termOpen }" @click="termOpen = !termOpen">
            {{ term.selected }}学期
            <i class="term-chev" :class="{ open: termOpen }"></i>
          </button>
          <transition name="term-fade">
            <div v-if="termOpen" class="term-menu">
              <button v-for="t in TERMS" :key="t" type="button" class="term-opt"
                :class="{ active: t === term.selected }" @click="term.select(t); termOpen = false">
                <span>{{ t }}学期</span>
                <small v-if="t === CURRENT_TERM">注册中</small>
                <i v-else-if="t === term.selected" class="term-tick">✓</i>
              </button>
            </div>
          </transition>
          <!-- 透明遮罩：点击菜单外部关闭 -->
          <div v-if="termOpen" class="term-mask" @click="termOpen = false"></div>
        </div>
      </header>
      <router-view />
    </main>
  </div>
  <router-view v-else />
</template>

<style scoped>
/* 学期切换下拉 */
.term-select { position: relative; }
.term-btn {
  display: inline-flex; align-items: center; gap: 10px;
  border: 1px solid #dbe2e2; border-radius: 8px; padding: 9px 16px;
  background: #fff; color: #315154; font: inherit; font-size: 15px; font-weight: 700;
  cursor: pointer; transition: border-color .15s, box-shadow .15s;
}
.term-btn:hover, .term-btn.open { border-color: #173a38; }
.term-btn.open { box-shadow: 0 0 0 3px rgba(23, 58, 56, .08); }
/* CSS 绘制的下拉箭头（替代字形 ⌄），展开时旋转 180° */
.term-chev {
  width: 8px; height: 8px; flex-shrink: 0;
  border-right: 2px solid #829395; border-bottom: 2px solid #829395;
  transform: translateY(-2px) rotate(45deg);
  transition: transform .2s ease;
}
.term-chev.open { transform: translateY(2px) rotate(225deg); }
.term-mask { position: fixed; inset: 0; z-index: 40; }
.term-menu {
  position: absolute; top: calc(100% + 8px); right: 0; z-index: 41;
  min-width: 190px; background: #fff; border: 1px solid #e1e7e7; border-radius: 10px;
  box-shadow: 0 12px 32px rgba(15, 35, 40, .16); padding: 6px;
}
.term-opt {
  width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding: 10px 12px; border: 0; border-radius: 7px; background: transparent;
  font: inherit; font-size: 15px; font-weight: 600; color: #315154;
  text-align: left; cursor: pointer;
}
.term-opt:hover { background: #f3f7ee; }
.term-opt.active { background: #173a38; color: #eaf2df; font-weight: 800; }
.term-opt small { font-size: 12px; font-weight: 400; opacity: .75; }
.term-tick { font-style: normal; color: #408254; font-weight: 800; font-size: 14px; }
.term-opt.active .term-tick { color: #eaf2df; }
.term-fade-enter-active, .term-fade-leave-active { transition: opacity .15s, transform .15s; }
.term-fade-enter-from, .term-fade-leave-to { opacity: 0; transform: translateY(-4px); }
</style>
