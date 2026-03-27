<template>
  <div class="card flex flex-col gap-2 animate-slide-up" :style="{ animationDelay: delay }">
    <div class="flex items-center justify-between">
      <span class="text-xs font-mono text-gray-500 uppercase tracking-widest">{{ label }}</span>
      <span class="text-lg">{{ icon }}</span>
    </div>
    <div class="text-3xl font-display font-extrabold" :class="valueColor">
      {{ value ?? '—' }}
    </div>
    <div v-if="sub" class="text-xs text-gray-500 font-mono">{{ sub }}</div>
  </div>
</template>

<script setup>
defineProps({
  label:      String,
  value:      [String, Number],
  icon:       String,
  sub:        String,
  valueColor: { type: String, default: 'text-white' },
  delay:      { type: String, default: '0ms' },
})
</script>