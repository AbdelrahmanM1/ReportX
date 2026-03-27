import { createRouter, createWebHistory } from 'vue-router'
import DashboardView        from '../views/DashboardView.vue'
import ReportsView          from '../views/ReportsView.vue'
import ReportView           from '../views/ReportView.vue'
import AuditLogView         from '../views/AuditLogView.vue'
import PlayerLookupView     from '../views/PlayerLookupView.vue'
import LoginView            from '../views/LoginView.vue'
import AuthView             from '../views/AuthView.vue'
import TokenAlreadyUsedView from '../views/TokenAlreadyUsedView.vue'
import { useAuthStore }     from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login',              component: LoginView,           meta: { public: true, title: 'Staff Login' } },
    { path: '/auth',               component: AuthView,            meta: { public: true } },
    { path: '/token-already-used', component: TokenAlreadyUsedView, meta: { public: true, title: 'Link Already Used' } },
    { path: '/',                   component: DashboardView,       meta: { title: 'Dashboard' } },
    { path: '/reports',            component: ReportsView,         meta: { title: 'Reports' } },
    { path: '/reports/:id',        component: ReportView,          meta: { title: 'Report Detail' } },
    { path: '/audit',              component: AuditLogView,        meta: { title: 'Audit Log', roles: ['ADMIN', 'SENIOR_STAFF'] } },
    { path: '/players',            component: PlayerLookupView,    meta: { title: 'Player Lookup' } },
  ]
})

router.beforeEach((to, from, next) => {
  const auth = useAuthStore()

  if (!to.meta.public && !auth.isAuthenticated) {
    return next('/login')
  }

  if (to.path === '/login' && auth.isAuthenticated) {
    return next('/')
  }
  if (to.meta.roles && !to.meta.roles.includes(auth.role)) {
    return next('/')
  }

  next()
})

export default router