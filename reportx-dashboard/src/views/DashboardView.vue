<template>
  <div class="animate-fade-in">
    <PageHeader title="Dashboard" sub="Overview of your server's report activity" />

    <div class="p-8 space-y-8">
      <!-- Stats Grid -->
      <div v-if="store.stats" class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Total Reports"  :value="store.stats.total"              icon="⚑" delay="0ms" />
        <StatCard label="Open"           :value="store.stats.byStatus?.OPEN"     icon="◯" value-color="text-yellow-400" delay="60ms" />
        <StatCard label="Resolved"       :value="store.stats.byStatus?.RESOLVED" icon="✓" value-color="text-green-400"  delay="120ms" />
        <StatCard label="Avg Resolution" :value="avgResolution"                  icon="⏱" sub="hours average"           delay="180ms" />
      </div>

      <div v-if="auth.canViewSensitive()" class="card mb-4 p-4 bg-base-800 border border-yellow-500/40">
        <p class="text-xs text-yellow-300 font-mono">Admin mode enabled: sensitive fields and private IPs will be visible.</p>
      </div>

      <!-- Status breakdown + top reported -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div class="card space-y-3">
          <h2 class="text-sm font-mono text-gray-500 uppercase tracking-widest">Status Breakdown</h2>
          <div v-for="(count, status) in store.stats?.byStatus" :key="status" class="space-y-1">
            <div class="flex justify-between text-sm">
              <StatusBadge :status="status" />
              <span class="font-mono text-gray-300">{{ count }}</span>
            </div>
            <div class="w-full bg-base-600 rounded-full h-1.5">
              <div class="h-1.5 rounded-full transition-all duration-700"
                :class="barColor(status)"
                :style="{ width: barWidth(count) + '%' }"
              ></div>
            </div>
          </div>
        </div>

        <div class="card space-y-3">
          <h2 class="text-sm font-mono text-gray-500 uppercase tracking-widest mb-2">Most Reported Players</h2>
          <div v-if="store.stats?.topAccused?.length">
            <div v-for="(entry, i) in store.stats.topAccused.slice(0, 5)" :key="entry.uuid"
              class="flex items-center gap-3 py-2 border-b border-base-600 last:border-0">
              <span class="w-5 h-5 rounded bg-base-600 flex items-center justify-center text-xs font-mono text-gray-400">{{ i + 1 }}</span>
              <RouterLink :to="`/players?uuid=${entry.uuid}`"
                class="font-mono text-sm text-gray-200 hover:text-red-400 transition-colors truncate flex-1">
                {{ entry.name ?? entry.uuid.slice(0, 8) + '…' }}
              </RouterLink>
              <span class="font-mono text-xs text-red-400 font-bold">{{ entry.count }} reports</span>
            </div>
          </div>
          <p v-else class="text-gray-600 text-sm font-mono">No data yet.</p>
        </div>
      </div>

      <!-- Recent Reports -->
      <div class="card">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-sm font-mono text-gray-500 uppercase tracking-widest">Recent Reports</h2>
          <RouterLink to="/reports" class="text-xs text-red-400 hover:text-red-300 font-mono">View all →</RouterLink>
        </div>
        <div v-if="store.stats?.recentReports?.length" class="space-y-2">
          <div v-for="r in store.stats.recentReports" :key="r.id"
            class="flex items-center gap-4 px-3 py-2.5 rounded-lg hover:bg-base-700 transition-colors cursor-pointer"
            @click="$router.push(`/reports/${r.id}`)">
            <span class="font-mono text-xs text-gray-600 w-8">#{{ r.id }}</span>
            <StatusBadge :status="r.status" />
            <span class="flex-1 text-sm text-gray-300 truncate">{{ r.reason }}</span>
            <span class="text-xs text-gray-600 font-mono">{{ formatDate(r.createdAt) }}</span>
          </div>
        </div>
        <p v-else class="text-gray-600 text-sm font-mono">No recent reports.</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useReportsStore } from '../stores/reports'
import { useAuthStore } from '../stores/auth'
import StatCard    from '../components/StatCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import PageHeader  from '../components/PageHeader.vue'

const store = useReportsStore()
const auth = useAuthStore()
onMounted(() => store.fetchStats())

const avgResolution = computed(() => {
  const h = store.stats?.avgResolutionHours
  return h != null ? h.toFixed(1) + 'h' : null
})

function barWidth(count) {
  const total = store.stats?.total ?? 1
  return Math.round((count / total) * 100)
}

function barColor(status) {
  return { OPEN: 'bg-yellow-400', CLAIMED: 'bg-blue-400', RESOLVED: 'bg-green-400',
           REJECTED: 'bg-red-400', ESCALATED: 'bg-purple-400' }[status] ?? 'bg-gray-400'
}

function formatDate(dt) {
  if (!dt) return '—'
  return new Date(dt).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
}
</script>