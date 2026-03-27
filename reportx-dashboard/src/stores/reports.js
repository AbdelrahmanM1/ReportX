import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from '../api/reports.js'

export const useReportsStore = defineStore('reports', () => {
  const reports      = ref([])
  const stats        = ref(null)
  const loading      = ref(false)
  const total        = ref(0)
  const page         = ref(1)
  const pageSize     = ref(20)
  const statusFilter = ref('')
  const searchFilter = ref('')

  const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

  async function fetchReports() {
    loading.value = true
    try {
      const params = { page: page.value, size: pageSize.value }
      if (statusFilter.value) params.status = statusFilter.value
      if (searchFilter.value?.trim()) params.search = searchFilter.value.trim()
      const res = await api.getReports(params)
      reports.value = res.data.content
      total.value   = res.data.totalElements
    } finally {
      loading.value = false
    }
  }

  async function fetchStats() {
    const res = await api.getStats()
    stats.value = res.data
  }

  function setPage(p)   { page.value = p; fetchReports() }
  function setStatus(s) { statusFilter.value = s; page.value = 1; fetchReports() }
  function setSearch(q) { searchFilter.value = q; page.value = 1; fetchReports() }

  return { reports, stats, loading, total, page, pageSize, statusFilter, searchFilter, totalPages,
           fetchReports, fetchStats, setPage, setStatus, setSearch }
})