import { createRouter, createWebHistory } from 'vue-router';
import { useAuth } from './stores/auth';

export const roleHome = (role?: string) =>
  role === 'PROFESSOR' ? '/teach' : role === 'REGISTRAR' ? '/admin/courses' : '/';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('./pages/Dashboard.vue'), meta: { roles: ['STUDENT'] } },
    { path: '/catalog', component: () => import('./pages/Catalog.vue'), meta: { roles: ['STUDENT'] } },
    { path: '/schedule', component: () => import('./pages/Schedule.vue'), meta: { roles: ['STUDENT'] } },
    { path: '/report-card', component: () => import('./pages/ReportCard.vue'), meta: { roles: ['STUDENT'] } },
    { path: '/teach', component: () => import('./pages/TeachCourses.vue'), meta: { roles: ['PROFESSOR'] } },
    { path: '/teaching-schedule', component: () => import('./pages/TeachingSchedule.vue'), meta: { roles: ['PROFESSOR'] } },
    { path: '/grades', component: () => import('./pages/SubmitGrades.vue'), meta: { roles: ['PROFESSOR'] } },
    { path: '/admin/courses', component: () => import('./pages/AdminCourses.vue'), meta: { roles: ['REGISTRAR'] } },
    { path: '/admin/students', component: () => import('./pages/AdminStudents.vue'), meta: { roles: ['REGISTRAR'] } },
    { path: '/admin/professors', component: () => import('./pages/AdminProfessors.vue'), meta: { roles: ['REGISTRAR'] } },
    { path: '/admin/close', component: () => import('./pages/AdminCloseRegistration.vue'), meta: { roles: ['REGISTRAR'] } },
    { path: '/login', component: () => import('./pages/Login.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
});

router.beforeEach((to) => {
  const auth = useAuth();
  if (to.path === '/login') return auth.user ? roleHome(auth.user.role) : true;
  if (!to.meta.roles) return true;
  if (!auth.user) return '/login';
  if (!(to.meta.roles as string[]).includes(auth.user.role)) return roleHome(auth.user.role);
});

export default router;
