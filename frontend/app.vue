<script setup lang="ts"></script>
<template>
  <header class="fixed top-0 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60 z-50">
    <nav class="max-w-screen-xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex h-16 items-center justify-between">
        <NuxtLink to="/" class="flex items-center gap-2">
          <img src="/favicon.ico" alt="RecapGrid Logo" class="h-8 w-8" />
          <span class="text-xl font-bold">RecapGrid</span>
        </NuxtLink>
        <div class="flex space-x-4">
            <ThemeToggle />
            <SignedOut>
              <SignInButton />
            </SignedOut>
            <SignedIn>
              <div class="flex space-x-4">
                <NuxtLink to="/dashboard">
                  <Button class="bg-primary text-primary-foreground hover:bg-primary/90">Get Started</Button>
                </NuxtLink>
                <UserButton />
              </div>
            </SignedIn>
          </div>
        </div>
      </nav>
    </header>
  <NuxtPage />
</template>
<script setup lang="ts">
  import { 
    VideoIcon,
    LayoutDashboardIcon,
    PlusIcon,
    FolderIcon,
    SettingsIcon,
    BellIcon,
    UploadCloudIcon,
    Wand2Icon
  } from 'lucide-vue-next'
</script>