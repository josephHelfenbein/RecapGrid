<template>
    <Teleport to="body">
        <div v-if="isOpen" class="fixed inset-0 flex items-center justify-center bg-black bg-opacity-50 z-50">
        <div class="bg-background p-6 rounded-lg shadow-lg w-96">
            <h2 class="text-lg font-semibold mb-4">Process Video</h2>

            <label class="block text-sm font-medium mb-1">Feel of the Video:</label>
            <select v-model="selectedFeel" class="w-full bg-secondary p-2 border rounded mb-3">
            <option value="cinematic">Cinematic</option>
            <option value="fast-paced">Comedic</option>
            <option value="calm">Informational</option>
            </select>

            <label class="block text-sm font-medium mb-1">AI Voice:</label>
            <select v-model="selectedVoice" class="w-full bg-secondary p-2 border rounded mb-3">
            <option value="male">Male</option>
            <option value="female">Female</option>
            <option value="robotic">Robotic</option>
            </select>

            <div class="flex justify-end space-x-2 mt-4">
            <button @click="closePopup" class="px-4 py-2 text-primary bg-secondary rounded">Cancel</button>
            <button @click="processVideo" class="px-4 py-2 text-primary-foreground bg-primary rounded">Process Video</button>
            </div>
        </div>
        </div>
    </Teleport>
</template>

<script setup>
  import { ref, defineEmits } from 'vue'
  
  const isOpen = ref(false)
  const selectedFeel = ref('cinematic')
  const selectedVoice = ref('male')
  const selectedAspectRatio = ref('9:16')
  
  const emit = defineEmits(['submit'])
  
  const openPopup = () => (isOpen.value = true)
  const closePopup = () => (isOpen.value = false)
  
  const processVideo = () => {
    emit('submit', {
      feel: selectedFeel.value,
      voice: selectedVoice.value,
    })
    closePopup()
  }
  
  defineExpose({ openPopup })
</script>
  