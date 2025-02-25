<template>
    <Button 
      variant="ghost" 
      size="icon" 
      @click="toggleTheme"
      class="relative"
      aria-label="Toggle theme"
    >
      <Sun 
        class="h-5 w-5 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" 
        aria-hidden="true"
      />
      <Moon 
        class="absolute h-5 w-5 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" 
        aria-hidden="true"
      />
      <span class="sr-only">{{ isDark ? 'Switch to light theme' : 'Switch to dark theme' }}</span>
    </Button>
  </template>
  
  <script setup>
  import { ref, onMounted } from 'vue'
  import { Button } from '@/components/ui/button'
  import { Moon, Sun } from 'lucide-vue-next'
  
  const isDark = ref(false)
  
  const toggleTheme = () => {
    isDark.value = !isDark.value
    document.documentElement.classList.toggle('dark')
    localStorage.setItem('theme', isDark.value ? 'dark' : 'light')
  }
  
  onMounted(() => {
    const theme = localStorage.getItem('theme') || 
      (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
    
    isDark.value = theme === 'dark'
    
    if (isDark.value) {
      document.documentElement.classList.add('dark')
    }
  })
  </script>