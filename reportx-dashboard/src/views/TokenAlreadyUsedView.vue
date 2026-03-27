<template>
  <div class="flex h-screen items-center justify-center bg-base-950">
    <div class="card max-w-md w-full mx-4 text-center space-y-6 animate-fade-in">
      <!-- Icon -->
      <div class="flex items-center justify-center">
        <div class="w-16 h-16 rounded-full bg-yellow-500/10 flex items-center justify-center">
          <svg class="w-8 h-8 text-yellow-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
          </svg>
        </div>
      </div>

      <!-- Title -->
      <div>
        <h1 class="text-xl font-display font-bold text-yellow-400 mb-2">Link Already Used</h1>
        <p class="text-gray-400 text-sm leading-relaxed">
          This login link has already been used and is no longer valid.
          Each link can only be used once for security reasons.
        </p>
      </div>

      <!-- Instructions -->
      <div class="bg-base-800 rounded-xl px-5 py-4 text-left space-y-2">
        <p class="text-xs font-mono text-gray-500 uppercase tracking-widest mb-3">How to get a new link</p>
        <div class="flex items-start gap-3">
          <span class="text-red-500 font-mono text-sm mt-0.5">1.</span>
          <p class="text-sm text-gray-300">Join the Minecraft server</p>
        </div>
        <div class="flex items-start gap-3">
          <span class="text-red-500 font-mono text-sm mt-0.5">2.</span>
          <p class="text-sm text-gray-300">
            Run <code class="bg-base-700 px-1.5 py-0.5 rounded text-red-400 font-mono text-xs">/reportadmin web</code>
          </p>
        </div>
        <div class="flex items-start gap-3">
          <span class="text-red-500 font-mono text-sm mt-0.5">3.</span>
          <p class="text-sm text-gray-300">Click the new link in chat (valid for 30 minutes)</p>
        </div>
      </div>

      <!-- Action button -->
      <button
        class="btn-primary w-full"
        @click="$router.replace('/login')"
      >
        Back to Login
      </button>

      <p class="text-xs text-gray-600 font-mono">
        Error code: TOKEN_ALREADY_USED
      </p>
    </div>
  </div>
</template>

<script setup>
// This view is shown when a staff member tries to reuse a one-time login link.
// The router navigates here instead of showing a transient error in AuthView.
</script>
