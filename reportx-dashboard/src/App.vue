<template>
  <div class="flex h-screen overflow-hidden bg-base-950">
    <!-- Sidebar -->
    <aside class="w-56 flex-shrink-0 bg-base-900 border-r border-base-700 flex flex-col">
      <!-- Logo -->
      <div class="px-5 py-6 border-b border-base-700">
        <div class="flex items-center gap-2">
          <span class="w-7 h-7 bg-red-500 rounded flex items-center justify-center text-white font-display font-black text-sm">⚑</span>
          <span class="font-display font-bold text-lg tracking-tight">
            Report<span class="text-red-500">X</span>
          </span>
        </div>
        <p class="text-xs text-gray-500 font-mono mt-1">Staff Dashboard</p>
      </div>

      <!-- Nav -->
      <nav class="flex-1 px-3 py-4 space-y-1">
        <RouterLink
          v-for="item in navItems" :key="item.path"
          :to="item.path"
          class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-display font-medium transition-all duration-150"
          :class="isActive(item.path)
            ? 'bg-red-500/15 text-red-400 border border-red-500/30'
            : 'text-gray-400 hover:text-gray-100 hover:bg-base-700'"
        >
          <span class="text-base" v-html="item.icon"></span>
          {{ item.label }}
          <span v-if="item.badge" class="ml-auto badge bg-red-500/20 text-red-400">{{ item.badge }}</span>
        </RouterLink>
      </nav>

      <!-- Footer -->
      <div class="px-5 py-4 border-t border-base-700">
        <div class="flex items-center justify-between gap-2">
          <div class="flex items-center gap-2">
            <span class="w-2 h-2 rounded-full" :class="auth.isAuthenticated ? 'bg-green-400 animate-pulse-dot' : 'bg-red-500'"></span>
            <span class="text-xs" :class="auth.isAuthenticated ? 'text-gray-500' : 'text-red-400'">
              {{ auth.isAuthenticated ? `Logged in as ${auth.staffInfo?.name || auth.staffInfo?.username || 'staff'}` : 'Not authenticated' }}
            </span>
          </div>
          <button v-if="auth.isAuthenticated" class="btn-ghost text-xs" @click="auth.logout(); $router.push('/login')">Logout</button>
        </div>
      </div>
    </aside>

    <!-- Main content -->
    <main class="flex-1 overflow-y-auto">
      <RouterView v-slot="{ Component }">
        <Transition name="fade" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useReportsStore } from './stores/reports'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const router = useRouter()
const store = useReportsStore()
const auth = useAuthStore()

// Called once here only — not in router guard
auth.init()

onMounted(() => {
  if (auth.isAuthenticated) {
    store.fetchStats()
  }

  window.addEventListener('reportx-auth-expired', () => {
    auth.logout()
    router.replace('/login')
  })
})

const openCount = computed(() => store.stats?.byStatus?.OPEN ?? '')

const navItems = computed(() => [
  { path: '/',        label: 'Dashboard',     icon: '◈' },
  { path: '/reports', label: 'Reports',       icon: '⚑', badge: openCount.value || undefined },
  ...(auth.isModerator ? [{ path: '/audit', label: 'Audit Log', icon: '◎' }] : []),
  { path: '/players', label: 'Player Lookup', icon: '◉' },
])

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}
</script>

<style>
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>