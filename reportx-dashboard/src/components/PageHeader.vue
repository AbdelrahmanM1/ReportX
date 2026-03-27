<template>
  <div class="px-8 py-6 border-b border-base-700 flex items-center justify-between">
    <div>
      <h1 class="text-2xl font-display font-extrabold tracking-tight">{{ title }}</h1>
      <p v-if="sub" class="text-sm text-gray-500 font-mono mt-0.5">{{ sub }}</p>
    </div>
    <slot />
  </div>
</template>

<script setup>
defineProps({ title: String, sub: String })
</script>