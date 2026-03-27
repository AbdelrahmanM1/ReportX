<template>
  <div class="animate-fade-in">
    <PageHeader title="Reports" :sub="`${store.total} total reports`">
      <div class="flex items-center gap-2">
        <input v-model="search" class="input w-48" placeholder="Search reason…" @input="debouncedSearch" />
        <select v-model="statusFilter" class="input pr-8" @change="applyFilter">
          <option value="">All Statuses</option>
          <option v-for="s in statuses" :key="s" :value="s">{{ s }}</option>
        </select>
      </div>
    </PageHeader>

    <div class="p-8">
      <div class="card p-0 overflow-hidden responsive-table">
        <div v-if="store.loading" class="flex items-center justify-center py-20">
          <div class="w-6 h-6 border-2 border-red-500 border-t-transparent rounded-full animate-spin"></div>
        </div>

        <table v-else class="w-full text-sm">
          <thead>
            <tr class="border-b border-base-600">
              <th class="th">ID</th>
              <th class="th">Status</th>
              <th class="th">Reason</th>
              <th class="th">Reporter</th>
              <th class="th">Accused</th>
              <th class="th">World</th>
              <th class="th">Created</th>
              <th class="th"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!store.reports.length">
              <td colspan="8" class="text-center py-16 text-gray-600 font-mono">No reports found.</td>
            </tr>
            <tr v-for="r in store.reports" :key="r.id"
              class="border-b border-base-700 hover:bg-base-700/50 transition-colors cursor-pointer"
              @click="$router.push(`/reports/${r.id}`)">
              <td class="td font-mono text-gray-500">#{{ r.id }}</td>
              <td class="td"><StatusBadge :status="r.status" /></td>
              <td class="td max-w-xs truncate text-gray-300">{{ r.reason }}</td>
              <td class="td font-mono text-xs text-gray-400">{{ r.reporterName || shortUuid(r.reporterUuid) }}</td>
              <td class="td font-mono text-xs text-gray-400">{{ r.accusedName || shortUuid(r.accusedUuid) }}</td>
              <td class="td font-mono text-xs text-gray-500">{{ r.world ?? '—' }}</td>
              <td class="td font-mono text-xs text-gray-500">{{ formatDate(r.createdAt) }}</td>
              <td class="td text-right text-red-500 text-xs">View →</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div class="flex items-center justify-between mt-4 text-sm font-mono text-gray-500">
        <span>Page {{ store.page }} of {{ store.totalPages }}</span>
        <div class="flex gap-2">
          <button class="btn-ghost text-xs" :disabled="store.page <= 1" @click="store.setPage(store.page - 1)">← Prev</button>
          <button class="btn-ghost text-xs" :disabled="store.page >= store.totalPages" @click="store.setPage(store.page + 1)">Next →</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useDebounceFn } from '@vueuse/core'
import { useReportsStore } from '../stores/reports'
import StatusBadge from '../components/StatusBadge.vue'
import PageHeader  from '../components/PageHeader.vue'

const store        = useReportsStore()
const search       = ref('')
const statusFilter = ref('')
const statuses     = ['OPEN', 'CLAIMED', 'RESOLVED', 'REJECTED', 'ESCALATED']

onMounted(() => store.fetchReports())

function applyFilter() { store.setStatus(statusFilter.value) }
const debouncedSearch = useDebounceFn(() => store.setSearch(search.value), 400)

function shortUuid(uuid) { return uuid ? uuid.slice(0, 8) + '…' : '—' }
function formatDate(dt) {
  if (!dt) return '—'
  return new Date(dt).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: '2-digit' })
}
</script>

<style>
.th { @apply px-4 py-3 text-left text-xs font-mono text-gray-500 uppercase tracking-widest; }
.td { @apply px-4 py-3; }
</style>