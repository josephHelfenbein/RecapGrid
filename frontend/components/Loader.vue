<template>
    <div class="relative inline-block" :style="{ width: size, height: size }">
      <div
        class="absolute top-0 left-0 rounded-full border-2 border-gray-100"
        :style="{ width: size, height: size, animation: 'animloader14 2s linear infinite 1s', opacity: 0 }"
      ></div>
      <div
        class="absolute top-0 left-0 rounded-full border-2 border-gray-400"
        :style="{ width: size, height: size, animation: 'animloader14 2s linear infinite' }"
      ></div>
    </div>
</template>
  
<script setup>
  defineProps({
    size: {
      type: String,
      default: '40px',
    },
  });
  </script>
  
  <style>
  @keyframes animloader14 {
    0% {
      transform: scale(0);
      opacity: 1;
    }
    50% {
      opacity: 1;
    }
    100% {
      transform: scale(1);
      opacity: 0;
    }
  }
</style>