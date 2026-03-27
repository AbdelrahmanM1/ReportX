<template>
  <div class="animate-fade-in">
    <PageHeader :title="`Report #${route.params.id}`" :sub="report?.reason">
      <RouterLink to="/reports" class="btn-ghost text-xs">← Back</RouterLink>
    </PageHeader>

    <div class="p-8" v-if="report">
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">

        <!-- Main Info -->
        <div class="lg:col-span-2 space-y-6">

          <!-- Details -->
          <div class="card space-y-4">
            <div class="flex items-center justify-between">
              <h2 class="text-sm font-mono text-gray-500 uppercase tracking-widest">Report Details</h2>
              <StatusBadge :status="report.status" />
            </div>

            <div class="grid grid-cols-2 gap-4 text-sm">
              <div>
                <span class="text-gray-500 font-mono text-xs block mb-1">REPORTER</span>
                <span class="font-mono text-gray-200">
                  {{ report.reporterName
                    ? `${report.reporterName} (${report.reporterUuid?.slice(0,8)})`
                    : report.reporterUuid }}
                </span>
              </div>

              <div>
                <span class="text-gray-500 font-mono text-xs block mb-1">ACCUSED</span>
                <RouterLink
                  v-if="report.accusedUuid"
                  :to="`/players?uuid=${report.accusedUuid}`"
                  class="font-mono text-red-400 hover:text-red-300"
                >
                  {{ report.accusedName
                    ? `${report.accusedName} (${report.accusedUuid?.slice(0,8)})`
                    : report.accusedUuid }}
                </RouterLink>
                <span v-else class="font-mono text-gray-400">—</span>
              </div>

              <div>
                <span class="text-gray-500 font-mono text-xs block mb-1">REASON</span>
                <span class="font-mono text-gray-200">{{ report.reason ?? '—' }}</span>
              </div>

              <div>
                <span class="text-gray-500 font-mono text-xs block mb-1">STATUS</span>
                <StatusBadge :status="report.status" />
              </div>

              <div>
                <span class="text-gray-500 font-mono text-xs block mb-1">CREATED</span>
                <span class="font-mono text-gray-200">{{ formatDate(report.createdAt) }}</span>
              </div>

              <div>
                <span class="text-gray-500 font-mono text-xs block mb-1">RESOLVED</span>
                <span class="font-mono text-gray-200">{{ formatDate(report.resolvedAt) ?? '—' }}</span>
              </div>

              <div>
                <span class="text-gray-500 font-mono text-xs block mb-1">WORLD</span>
                <span class="font-mono text-gray-200">{{ report.world ?? '—' }}</span>
              </div>

              <div>
                <span class="text-gray-500 font-mono text-xs block mb-1">LOCATION</span>
                <span class="font-mono text-gray-200">{{ coords }}</span>
              </div>

              <div v-if="report.claimedByName">
                <span class="text-gray-500 font-mono text-xs block mb-1">CLAIMED BY</span>
                <span class="font-mono text-blue-400">{{ report.claimedByName }}</span>
              </div>

              <div v-if="report.verdict">
                <span class="text-gray-500 font-mono text-xs block mb-1">VERDICT</span>
                <span class="font-mono text-green-400">{{ report.verdict }}</span>
              </div>
            </div>
          </div>

          <!-- Notes -->
          <div class="card space-y-4">
            <h2 class="text-sm font-mono text-gray-500 uppercase tracking-widest">Staff Notes</h2>

            <div v-if="notes.length" class="space-y-3">
              <div v-for="note in notes" :key="note.id" class="bg-base-900 rounded-lg p-3">
                <div class="flex items-center gap-2 mb-1">
                  <span class="font-mono text-xs text-blue-400">
                    {{ note.staffName
                      ? `${note.staffName} (${note.staffUuid?.slice(0,8)})`
                      : note.staffUuid?.slice(0,8) }}
                  </span>
                  <span class="font-mono text-xs text-gray-600">{{ formatDate(note.createdAt) }}</span>
                </div>
                <p class="text-sm text-gray-300">{{ note.note }}</p>
              </div>
            </div>

            <p v-else class="text-gray-600 font-mono text-xs">No notes yet.</p>

            <div class="flex gap-2 pt-2 border-t border-base-600">
              <input
                v-model="newNote"
                class="input flex-1"
                placeholder="Add a staff note…"
                @keyup.enter="submitNote"
              />
              <button class="btn-primary" :disabled="submittingNote" @click="submitNote">
                {{ submittingNote ? '…' : 'Add' }}
              </button>
            </div>
          </div>
        </div>

        <!-- Actions Panel -->
        <div class="space-y-4">
          <div class="card space-y-3">
            <h2 class="text-sm font-mono text-gray-500 uppercase tracking-widest">Actions</h2>

            <p v-if="actionError" class="text-xs text-red-400 font-mono">{{ actionError }}</p>

            <!-- OPEN -->
            <div v-if="report.status === 'OPEN'" class="space-y-2">
              <button class="btn-primary w-full" :disabled="acting" @click="changeStatus('CLAIMED')">Claim Report</button>
              <button class="btn-ghost w-full" :disabled="acting" @click="changeStatus('REJECTED')">Reject</button>
              <button class="btn-ghost w-full" :disabled="acting" @click="changeStatus('ESCALATED')">Escalate</button>
            </div>

            <!-- CLAIMED (FIXED 🔥) -->
            <div v-else-if="report.status === 'CLAIMED'" class="space-y-2">

              <!-- Warning -->
              <div v-if="!canActOnClaimed"
                   class="flex items-center gap-2 bg-yellow-500/10 border border-yellow-500/20 rounded-lg px-3 py-2">
                <span class="text-yellow-400 text-sm">⚠</span>
                <p class="text-xs text-yellow-400 font-mono">
                  Claimed by <span class="font-bold">{{ report.claimedByName }}</span>.
                  Only they or an admin can resolve this.
                </p>
              </div>

              <!-- Allowed actions -->
              <template v-if="canActOnClaimed">
                <input v-model="verdict" class="input w-full" placeholder="Verdict (required to resolve)…" />

                <button class="btn-primary w-full"
                        :disabled="!verdict || acting"
                        @click="changeStatus('RESOLVED', verdict)">
                  Resolve
                </button>

                <button class="btn-ghost w-full"
                        :disabled="acting"
                        @click="changeStatus('REJECTED')">
                  Reject
                </button>

                <button class="btn-ghost w-full"
                        :disabled="acting"
                        @click="changeStatus('ESCALATED')">
                  Escalate
                </button>
              </template>
            </div>

            <!-- ESCALATED -->
            <div v-else-if="report.status === 'ESCALATED'" class="space-y-2">
              <input v-model="verdict" class="input w-full" placeholder="Verdict (required to resolve)…" />
              <button class="btn-primary w-full" :disabled="!verdict || acting" @click="changeStatus('RESOLVED', verdict)">
                Resolve
              </button>
              <button class="btn-ghost w-full" :disabled="acting" @click="changeStatus('REJECTED')">Reject</button>
            </div>

            <div v-else class="text-xs font-mono text-gray-500">
              Report is {{ report.status }}. No further actions available.
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import * as api from '../api/reports.js'
import StatusBadge from '../components/StatusBadge.vue'
import PageHeader from '../components/PageHeader.vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()

const report = ref(null)
const notes = ref([])
const loading = ref(true)
const newNote = ref('')
const verdict = ref('')
const acting = ref(false)
const submittingNote = ref(false)
const actionError = ref('')

onMounted(loadReport)

async function loadReport() {
  try {
    const res = await api.getReport(route.params.id)
    report.value = res.data
    notes.value = res.data.notes ?? []
    verdict.value = res.data.verdict ?? ''
  } finally {
    loading.value = false
  }
}

const canActOnClaimed = computed(() => {
  if (!report.value || report.value.status !== 'CLAIMED') return true

  const role = auth.role

  if (role === 'ADMIN' || role === 'SENIOR_STAFF') return true

  return auth.staffInfo?.uuid === report.value.claimedByUuid
})

const coords = computed(() => {
  const r = report.value
  if (!r || r.x == null) return '—'
  return `${r.x.toFixed(1)}, ${r.y.toFixed(1)}, ${r.z.toFixed(1)}`
})

function formatDate(dt) {
  if (!dt) return null
  return new Date(dt).toLocaleString('en-GB')
}

async function changeStatus(status, v) {
  acting.value = true
  actionError.value = ''
  try {
    await api.updateStatus(route.params.id, status, v)
    await loadReport()
  } catch (e) {
    actionError.value = e?.response?.data?.error ?? 'Failed to update status.'
  } finally {
    acting.value = false
  }
}

async function submitNote() {
  if (!newNote.value.trim()) return
  submittingNote.value = true
  try {
    await api.addNote(route.params.id, newNote.value.trim())
    newNote.value = ''
    await loadReport()
  } finally {
    submittingNote.value = false
  }
}
</script>