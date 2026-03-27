<template>
  <div class="animate-fade-in">
    <PageHeader title="Audit Log" :sub="`Last ${logs.length} staff actions`">
      <select v-model="limit" class="input" @change="fetchLogs">
        <option value="50">Last 50</option>
        <option value="100">Last 100</option>
        <option value="250">Last 250</option>
      </select>
    </PageHeader>

    <div class="p-8">
      <div class="card p-0 overflow-hidden responsive-table">
        <div v-if="loading" class="flex items-center justify-center py-20">
          <div class="w-6 h-6 border-2 border-red-500 border-t-transparent rounded-full animate-spin"></div>
        </div>

        <table v-else class="w-full text-sm">
          <thead>
            <tr class="border-b border-base-600">
              <th class="th">Time</th>
              <th class="th">Staff</th>
              <th class="th">Action</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!logs.length">
              <td colspan="3" class="text-center py-16 text-gray-600 font-mono">No audit log entries.</td>
            </tr>
            <tr v-for="log in logs" :key="log.id"
              class="border-b border-base-700 hover:bg-base-700/30 transition-colors">
              <td class="td font-mono text-xs text-gray-500 whitespace-nowrap">{{ formatDate(log.timestamp) }}</td>
              <td class="td font-mono text-xs text-blue-400">{{ log.staffName ? `${log.staffName} (${log.staffUuid.slice(0,8)})` : log.staffUuid.slice(0,8) }}</td>
              <td class="td text-gray-300 text-sm">{{ log.action }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import * as api   from '../api/reports.js'
import PageHeader from '../components/PageHeader.vue'

const logs    = ref([])
const loading = ref(true)
const limit   = ref(50)

onMounted(() => fetchLogs())

async function fetchLogs() {
  loading.value = true
  try {
    const res = await api.getAuditLog({ limit: limit.value })
    logs.value = res.data
  } finally {
    loading.value = false
  }
}

function formatDate(dt) {
  if (!dt) return '—'
  return new Date(dt).toLocaleString('en-GB', {
    day: '2-digit', month: 'short', year: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}
</script>