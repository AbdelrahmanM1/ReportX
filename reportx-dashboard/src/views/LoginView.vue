<template>
  <div class="min-h-screen flex items-center justify-center bg-base-950">
    <div class="w-full max-w-md p-8 bg-base-900 border border-base-700 rounded-2xl shadow-lg">
      <h1 class="text-2xl font-display font-bold text-white">ReportX Staff Portal</h1>
      <p class="text-gray-400 text-sm mt-2">Enter your temporary token from <strong>/reportadmin web</strong> (valid for 30 min).</p>

      <label class="block mt-6 text-xs font-mono text-gray-400">Staff token</label>
      <input
        v-model="tokenInput"
        class="input w-full"
        type="text"
        placeholder="Paste token here"
        @keyup.enter="submit"
      />

      <button
        class="btn-primary w-full mt-4"
        :disabled="!tokenInput.trim() || loading"
        @click="submit"
      >
        {{ loading ? 'Verifying...' : 'Login' }}
      </button>

      <p v-if="error" class="text-sm text-red-400 mt-3">{{ error }}</p>

      <p class="text-gray-500 text-xs mt-6">
        You may also access as staff by command: <code>/reportadmin web</code>. The response is your staff token.
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const tokenInput = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  if (!tokenInput.value.trim()) return
  loading.value = true

  try {
    await auth.login(tokenInput.value.trim())
    router.push('/')
  } catch (err) {
    error.value = 'Invalid or expired token. Please request a new /reportadmin web token.'
  } finally {
    loading.value = false
  }
}
</script>
