import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const loadLogin = () => import('../views/LoginView.vue')
const loadAppShell = () => import('../components/AppShell.vue')
const loadOverview = () => import('../views/OverviewView.vue')
const loadWorkbench = () => import('../views/MobileOperationsView.vue')
const loadContent = () => import('../views/ContentView.vue')
const loadMasterData = () => import('../views/MasterDataView.vue')
const loadStaffProfiles = () => import('../views/StaffProfilesView.vue')
const loadBookingOperations = () => import('../views/BookingOperationsView.vue')
const loadBenefits = () => import('../views/BenefitsOperationsView.vue')
const loadPointsMall = () => import('../views/PointsMallOperationsView.vue')
const loadFulfillment = () => import('../views/FulfillmentOperationsView.vue')
const loadSettings = () => import('../views/SettingsView.vue')
const loadDataMigration = () => import('../views/DataMigrationView.vue')

const routeLoaders = new Map<string, () => Promise<unknown>>([
  ['login', loadLogin],
  ['overview', loadOverview],
  ['workbench', loadWorkbench],
  ['content', loadContent],
  ['master-data', loadMasterData],
  ['staff-profiles', loadStaffProfiles],
  ['booking-operations', loadBookingOperations],
  ['benefits', loadBenefits],
  ['points-mall', loadPointsMall],
  ['fulfillment', loadFulfillment],
  ['settings', loadSettings],
  ['data-migration', loadDataMigration],
])

function preloadRouteComponents(routeName: unknown, isPublic: boolean) {
  const pageLoader = routeLoaders.get(String(routeName ?? ''))
  const loaders = isPublic ? [pageLoader] : [loadAppShell, pageLoader]
  return Promise.all(loaders.filter((loader): loader is () => Promise<unknown> => Boolean(loader)).map((loader) => loader()))
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: loadLogin,
      meta: { public: true },
    },
    {
      path: '/',
      component: loadAppShell,
      children: [
        { path: '', name: 'overview', component: loadOverview, meta: { title: '运营总览' } },
        {
          path: 'workbench',
          name: 'workbench',
          component: loadWorkbench,
          meta: { permission: 'dashboard:view', title: '今日工作台' },
        },
        {
          path: 'content',
          name: 'content',
          component: loadContent,
          meta: { permission: 'content:view', title: '内容运营' },
        },
        {
          path: 'master-data',
          name: 'master-data',
          component: loadMasterData,
          meta: { permissionAny: ['service:view', 'staff:view'], title: '护理项目' },
        },
        {
          path: 'staff-profiles',
          name: 'staff-profiles',
          component: loadStaffProfiles,
          meta: { permission: 'staff:view', title: '技师公开资料' },
        },
        {
          path: 'booking-operations',
          name: 'booking-operations',
          component: loadBookingOperations,
          meta: { permissionAny: ['schedule:view', 'appointment:view', 'waitlist:view'], title: '预约与排班' },
        },
        {
          path: 'benefits',
          name: 'benefits',
          component: loadBenefits,
          meta: { permissionAny: ['benefit:view', 'payment_config:view'], title: '卡项与优惠' },
        },
        {
          path: 'points-mall',
          name: 'points-mall',
          component: loadPointsMall,
          meta: { permissionAny: ['points:view', 'mall:view'], title: '积分与商城' },
        },
        {
          path: 'fulfillment',
          name: 'fulfillment',
          component: loadFulfillment,
          meta: { permissionAny: ['fulfillment:view', 'review:moderate', 'aftersale:view'], title: '履约与售后' },
        },
        {
          path: 'settings',
          name: 'settings',
          component: loadSettings,
          meta: { permissionAny: ['system_config:view', 'admin_account:view'], title: '系统设置' },
        },
        {
          path: 'data-migration',
          name: 'data-migration',
          component: loadDataMigration,
          meta: { permission: 'import:view', title: '数据迁移' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const hasToken = Boolean(localStorage.getItem('face-chain-token'))
  if (!to.meta.public && !hasToken) return { name: 'login' }
  const auth = useAuthStore()
  const componentPreload = preloadRouteComponents(to.name, Boolean(to.meta.public))
  if (hasToken && !auth.context) {
    try {
      await Promise.all([auth.refreshContext(), componentPreload])
    } catch {
      auth.signOut()
      return { name: 'login' }
    }
  } else {
    void componentPreload.catch(() => undefined)
  }
  if (hasToken && !auth.context?.roles.some((role) => ['ADMIN', 'SUPER_ADMIN'].includes(role))) {
    auth.signOut()
    return { name: 'login' }
  }
  if (to.name === 'login' && hasToken) return { name: 'overview' }

  const permissions = auth.context?.permissions ?? []
  if (to.meta.permission && !permissions.includes(String(to.meta.permission))) {
    return { name: 'overview' }
  }
  const permissionAny = Array.isArray(to.meta.permissionAny)
    ? to.meta.permissionAny.map(String)
    : []
  if (permissionAny.length && !permissionAny.some((permission) => permissions.includes(permission))) {
    return { name: 'overview' }
  }
  return true
})

export default router
