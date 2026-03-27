<template>
  <span class="badge" :class="classes[status] ?? 'bg-gray-500/20 text-gray-400'">
    <span class="w-1.5 h-1.5 rounded-full mr-1.5" :class="dotClasses[status] ?? 'bg-gray-400'"></span>
    {{ status }}
  </span>
</template>

<script setup>
defineProps({ status: String })

const classes = {
  OPEN:      'bg-yellow-500/15 text-yellow-400 border border-yellow-500/30',
  CLAIMED:   'bg-blue-500/15 text-blue-400 border border-blue-500/30',
  RESOLVED:  'bg-green-500/15 text-green-400 border border-green-500/30',
  REJECTED:  'bg-red-500/15 text-red-400 border border-red-500/30',
  ESCALATED: 'bg-purple-500/15 text-purple-400 border border-purple-500/30',
}

const dotClasses = {
  OPEN:      'bg-yellow-400',
  CLAIMED:   'bg-blue-400',
  RESOLVED:  'bg-green-400',
  REJECTED:  'bg-red-400',
  ESCALATED: 'bg-purple-400',
}
</script>