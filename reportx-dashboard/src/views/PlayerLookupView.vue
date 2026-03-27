<template>
  <div class="animate-fade-in">
    <PageHeader title="Player Lookup" sub="Search players by name or UUID" />

    <div class="p-8 space-y-6">
      <!-- Search bar with autocomplete -->
      <div class="relative max-w-xl">
        <div class="flex gap-3">
          <div class="relative flex-1">
            <input
              v-model="query"
              class="input w-full pr-10"
              placeholder="Player name or UUID…"
              autocomplete="off"
              @keyup.enter="search"
              @input="onInput"
              @keydown.down.prevent="moveSuggestion(1)"
              @keydown.up.prevent="moveSuggestion(-1)"
              @keydown.escape="closeSuggestions"
              @blur="onBlur"
            />
            <div v-if="suggestionsLoading" class="absolute right-3 top-1/2 -translate-y-1/2">
              <div class="w-4 h-4 border-2 border-red-500 border-t-transparent rounded-full animate-spin"></div>
            </div>
          </div>
          <button class="btn-primary" @click="search">Search</button>
        </div>

        <!-- Autocomplete dropdown -->
        <div
          v-if="suggestions.length && showSuggestions"
          class="absolute z-50 top-full mt-1 w-full bg-base-800 border border-base-700 rounded-xl shadow-2xl overflow-hidden"
        >
          <div
            v-for="(s, i) in suggestions"
            :key="s.uuid"
            class="flex items-center gap-3 px-4 py-2.5 cursor-pointer transition-colors"
            :class="i === activeSuggestion ? 'bg-base-700' : 'hover:bg-base-700'"
            @mousedown.prevent="selectSuggestion(s)"
          >
            <!-- Player head from mc-heads.net (reliable, no CORS) -->
            <div class="relative group/head">
              <img
                :src="`https://mc-heads.net/avatar/${s.uuid}/32`"
                :alt="s.lastKnownName"
                class="w-8 h-8 rounded image-render-pixel"
                @error="onHeadError($event)"
              />
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-gray-200 truncate">{{ s.lastKnownName }}</p>
              <p class="text-xs font-mono text-gray-600 truncate">{{ s.uuid }}</p>
            </div>
            <span v-if="s.lastSeen" class="text-xs text-gray-600 font-mono shrink-0">
              {{ formatDateShort(s.lastSeen) }}
            </span>
          </div>
        </div>
      </div>

      <!-- Loading state -->
      <div v-if="loading" class="flex items-center justify-center py-20">
        <div class="w-6 h-6 border-2 border-red-500 border-t-transparent rounded-full animate-spin"></div>
      </div>

      <!-- Player result -->
      <div v-else-if="player" class="space-y-6 animate-slide-up">
        <!-- Player header with NameMC head -->
        <div class="card flex items-center gap-5">
          <!-- Player head with hover tooltip -->
          <div class="relative group">
            <a
              :href="`https://namemc.com/profile/${player.uuid}`"
              target="_blank"
              rel="noopener noreferrer"
              class="block"
              title="View on NameMC"
            >
              <img
                :src="`https://mc-heads.net/avatar/${player.uuid}/56`"
                :alt="player.lastKnownName ?? '?'"
                class="w-14 h-14 rounded-xl image-render-pixel hover:ring-2 hover:ring-red-500 transition-all"
                @error="onHeadError($event)"
              />
            </a>
            <!-- Skin body on hover (3D body render) -->
            <div class="absolute left-1/2 -translate-x-1/2 bottom-full mb-2 z-50
                        opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none">
              <div class="bg-base-800 border border-base-700 rounded-xl p-2 shadow-2xl text-center">
                <img
                  :src="`https://mc-heads.net/body/${player.uuid}/80`"
                  :alt="player.lastKnownName"
                  class="w-16 image-render-pixel mx-auto"
                  @error="onHeadError($event)"
                />
                <p class="text-xs font-bold text-gray-200 mt-1">{{ player.lastKnownName ?? player.uuid }}</p>
                <p class="text-xs text-gray-500">Click head to view on NameMC</p>
              </div>
            </div>
          </div>

          <div>
            <h2 class="text-xl font-display font-bold">{{ player.lastKnownName ?? 'Unknown' }}</h2>
            <p class="font-mono text-xs text-gray-500 mt-0.5">{{ player.uuid }}</p>
            <p class="font-mono text-xs text-gray-600 mt-0.5">
              Last seen: <span class="text-gray-400">{{ formatDate(player.lastSeen) }}</span>
            </p>
          </div>
        </div>

        <!-- Stats -->
        <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard label="Times Reported" :value="player.reportedCount"   icon="⚑" value-color="text-red-400" />
          <StatCard label="Reports Filed"  :value="player.reporterCount"   icon="◎" />
          <StatCard label="Open Against"   :value="player.openAgainst"     icon="◯" value-color="text-yellow-400" />
          <StatCard label="Resolved"       :value="player.resolvedAgainst" icon="✓" value-color="text-green-400" />
        </div>

        <!-- Reports against -->
        <div class="card">
          <h2 class="text-sm font-mono text-gray-500 uppercase tracking-widest mb-4">Reports Against This Player</h2>
          <div v-if="player.reportsAgainst?.length" class="space-y-2">
            <div
              v-for="r in player.reportsAgainst"
              :key="r.id"
              class="flex items-center gap-4 px-3 py-2.5 rounded-lg hover:bg-base-700 transition-colors cursor-pointer"
              @click="$router.push(`/reports/${r.id}`)"
            >
              <!-- Reporter head mini -->
              <div class="relative group/mini shrink-0">
                <img
                  :src="`https://mc-heads.net/avatar/${r.reporterUuid}/24`"
                  :alt="r.reporterName"
                  class="w-6 h-6 rounded image-render-pixel"
                  @error="onHeadError($event)"
                />
                <!-- Tooltip: reporter name -->
                <div class="absolute left-1/2 -translate-x-1/2 bottom-full mb-1 z-50
                            opacity-0 group-hover/mini:opacity-100 transition-opacity pointer-events-none whitespace-nowrap">
                  <div class="bg-base-800 border border-base-700 rounded-lg px-2 py-1 text-xs text-gray-300 shadow">
                    {{ r.reporterName ?? r.reporterUuid }}
                  </div>
                </div>
              </div>

              <span class="font-mono text-xs text-gray-600 w-8">#{{ r.id }}</span>
              <StatusBadge :status="r.status" />
              <span class="flex-1 text-sm text-gray-300 truncate">{{ r.reason }}</span>
              <span class="text-xs text-gray-600 font-mono">{{ formatDate(r.createdAt) }}</span>
              <span class="text-red-500 text-xs">View →</span>
            </div>
          </div>
          <p v-else class="text-gray-600 font-mono text-xs">No reports found against this player.</p>
        </div>
      </div>

      <!-- Not found -->
      <div v-else-if="searched && !player" class="text-center py-20 text-gray-600 font-mono">
        No player found for "{{ query }}"
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import * as api    from '../api/reports.js'
import StatCard    from '../components/StatCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import PageHeader  from '../components/PageHeader.vue'

const route           = useRoute()
const query           = ref('')
const player          = ref(null)
const loading         = ref(false)
const searched        = ref(false)
const suggestions     = ref([])
const showSuggestions = ref(false)
const suggestionsLoading = ref(false)
const activeSuggestion   = ref(-1)

let debounceTimer = null

onMounted(() => {
  const uuid = route.query.uuid
  if (uuid) { query.value = uuid; search() }
})

/* ── Autocomplete ─────────────────────────────────────────────────── */
function onInput() {
  activeSuggestion.value = -1
  clearTimeout(debounceTimer)
  const val = query.value.trim()
  if (!val || val.length < 2) {
    suggestions.value = []
    showSuggestions.value = false
    return
  }
  // Don't suggest for UUIDs
  if (/^[0-9a-f-]{8,36}$/i.test(val)) return

  debounceTimer = setTimeout(() => fetchSuggestions(val), 250)
}

async function fetchSuggestions(name) {
  suggestionsLoading.value = true
  try {
    const res = await api.searchSuggestions(name)
    suggestions.value = res.data ?? []
    showSuggestions.value = suggestions.value.length > 0
  } catch {
    suggestions.value = []
  } finally {
    suggestionsLoading.value = false
  }
}

function selectSuggestion(s) {
  query.value = s.lastKnownName
  showSuggestions.value = false
  suggestions.value = []
  search()
}

function moveSuggestion(dir) {
  if (!showSuggestions.value) return
  activeSuggestion.value = Math.max(-1,
    Math.min(suggestions.value.length - 1, activeSuggestion.value + dir))
}

function closeSuggestions() {
  showSuggestions.value = false
}

function onBlur() {
  // Slight delay so mousedown on suggestion fires first
  setTimeout(closeSuggestions, 150)
}

/* ── Search ───────────────────────────────────────────────────────── */
async function search() {
  if (!query.value.trim()) return
  showSuggestions.value = false
  loading.value  = true
  searched.value = false
  player.value   = null
  try {
    const isUuid = /^[0-9a-f-]{36}$/i.test(query.value.trim())
    const res = isUuid
      ? await api.getPlayer(query.value.trim())
      : await api.searchPlayer(query.value.trim())
    player.value = res.data
  } catch {
    player.value = null
  } finally {
    loading.value  = false
    searched.value = true
  }
}

function onHeadError(event) {
  // Fallback to Steve head if mc-heads.net fails
  event.target.src = 'https://mc-heads.net/avatar/MHF_Steve/32'
  event.target.onerror = null
}

function formatDate(dt) {
  if (!dt) return '—'
  return new Date(dt).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function formatDateShort(dt) {
  if (!dt) return ''
  const d = new Date(dt)
  const now = new Date()
  const diff = now - d
  if (diff < 86400000) return 'Today'
  if (diff < 172800000) return 'Yesterday'
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' })
}
</script>

<style scoped>
.image-render-pixel {
  image-rendering: pixelated;
}
</style>
