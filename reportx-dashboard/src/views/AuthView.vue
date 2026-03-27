<template>
  <div class="flex h-screen items-center justify-center bg-base-950">
    <div class="text-center space-y-4">
      <div v-if="!error">
        <div class="w-8 h-8 border-2 border-red-500 border-t-transparent
                    rounded-full animate-spin mx-auto mb-4"></div>
        <p class="font-mono text-sm text-gray-500">Verifying your token…</p>
      </div>
      <div v-else class="card max-w-sm mx-auto">
        <p class="text-red-400 font-mono text-sm">{{ error }}</p>
        <p class="text-gray-500 text-xs mt-2">
          Run /report web in-game to get a new link.
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route  = useRoute()
const router = useRouter()
const auth   = useAuthStore()
const error  = ref(null)

onMounted(async () => {
  const pluginToken = route.query.token
  if (!pluginToken) {
    error.value = 'No token in URL. Use /report web in-game.'
    return
  }
  try {
    await auth.login(pluginToken)
    window.history.replaceState({}, '', '/auth')
    router.replace('/')
  } catch (e) {
    const msg = e?.response?.data?.error
    if (msg === 'Token already used') {
      router.replace('/token-already-used')
    } else if (msg === 'Token expired — run /report web again') {
      error.value = 'This link expired (30 min limit). Run /report web again.'
    } else {
      error.value = 'Invalid token. Run /report web in-game to get a new link.'
    }
  }
})
</script>