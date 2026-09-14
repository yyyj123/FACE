import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import ClientShell from '../components/ClientShell.vue'
import TechnicianShell from '../components/TechnicianShell.vue'
import { IS_TECHNICIAN_PORTAL } from '../config/portal'

const customerChildren = [
  { path: '', name: 'home', component: () => import('../views/HomeView.vue'), meta: { title: '首页' } },
  { path: 'services', name: 'services', component: () => import('../views/ServicesView.vue'), meta: { title: '护理项目' } },
  { path: 'services/:id', name: 'service-detail', component: () => import('../views/ServiceDetailView.vue'), meta: { title: '项目详情' } },
  { path: 'staff/:id', name: 'staff-detail', component: () => import('../views/StaffDetailView.vue'), meta: { title: '技师详情' } },
  { path: 'staff-schedule', name: 'staff-schedule', component: () => import('../views/StaffScheduleView.vue'), meta: { title: '技师排班' } },
  { path: 'booking', name: 'booking', component: () => import('../views/BookingView.vue'), meta: { requiresAuth: true, title: '预约护理' } },
  { path: 'checkout', name: 'checkout', component: () => import('../views/CheckoutView.vue'), meta: { requiresAuth: true, title: '确认结算' } },
  { path: 'appointments', name: 'appointments', component: () => import('../views/AppointmentsView.vue'), meta: { requiresAuth: true, title: '预约记录' } },
  { path: 'benefits', name: 'benefits', component: () => import('../views/BenefitsView.vue'), meta: { requiresAuth: true, title: '我的资产' } },
  { path: 'points-store', name: 'points-store', component: () => import('../views/PointsStoreView.vue'), meta: { requiresAuth: true, title: '积分商城' } },
  { path: 'after-sales', name: 'after-sales', component: () => import('../views/AfterSalesView.vue'), meta: { requiresAuth: true, title: '售后服务' } },
  { path: 'care-feedback', name: 'care-feedback', component: () => import('../views/CareFeedbackView.vue'), meta: { requiresAuth: true, title: '评价与售后' } },
  { path: 'notifications', name: 'notifications', component: () => import('../views/NotificationsView.vue'), meta: { requiresAuth: true, title: '消息中心' } },
  { path: 'profile', name: 'profile', component: () => import('../views/ProfileView.vue'), meta: { requiresAuth: true, title: '个人中心' } },
]

const technicianChildren = [
  { path: '', redirect: '/workbench' },
  { path: 'workbench', name: 'workbench', component: () => import('../views/WorkbenchView.vue'), meta: { requiresAuth: true, title: '工作台' } },
  { path: 'appointments', name: 'appointments', component: () => import('../views/AppointmentsView.vue'), meta: { requiresAuth: true, title: '预约记录' } },
  { path: 'notifications', name: 'notifications', component: () => import('../views/NotificationsView.vue'), meta: { requiresAuth: true, title: '消息中心' } },
  { path: 'profile', name: 'profile', component: () => import('../views/ProfileView.vue'), meta: { requiresAuth: true, title: '个人中心' } },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: (to) => to.hash ? { el: to.hash, top: 84, behavior: 'smooth' } : { top: 0 },
  routes: [
    {
      path: '/',
      component: IS_TECHNICIAN_PORTAL ? TechnicianShell : ClientShell,
      children: IS_TECHNICIAN_PORTAL ? technicianChildren : customerChildren,
    },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/:pathMatch(.*)*', redirect: IS_TECHNICIAN_PORTAL ? '/workbench' : '/' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isSignedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isSignedIn) {
    return { name: IS_TECHNICIAN_PORTAL ? 'workbench' : 'profile' }
  }
  return true
})

export default router
