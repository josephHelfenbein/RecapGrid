<template>
    <div class="min-h-screen bg-background">
      <header class="fixed top-0 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60 z-50">
        <nav class="max-w-screen-xl mx-auto px-4 sm:px-6 lg:px-8">
          <div class="flex h-16 items-center justify-between">
            <div class="flex items-center gap-2">
              <VideoIcon class="h-6 w-6 text-primary" />
              <span class="text-xl font-bold">RecapGrid</span>
            </div>
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
  
      <main class="max-w-screen-xl mx-auto px-4 sm:px-6 lg:px-8">
        <section class="pt-32 pb-20">
          <div class="flex flex-col items-center text-center gap-6">
            <h1 class="text-4xl sm:text-5xl md:text-6xl font-bold tracking-tight">
              Transform Your Videos with
              <span class="bg-gradient-to-r from-primary to-purple-600 bg-clip-text text-transparent">AI Magic</span>
            </h1>
            <p class="text-lg text-muted-foreground max-w-[750px]">
              Upload your videos and let our AI do the rest. Get perfect TikTok-ready content with AI captions, 
              voice-overs, music, and smart trimming - all in one place.
            </p>
            <div class="flex gap-4">
                <NuxtLink to="/dashboard">
                    <Button size="lg" class="bg-primary text-primary-foreground hover:bg-primary/90">
                        Try for Free
                    </Button>
                </NuxtLink>
            </div>
          </div>
  
        </section>
  
        <section class="py-20">
          <div class="bg-muted/50 -mx-4 sm:-mx-6 lg:-mx-8 px-4 sm:px-6 lg:px-8 py-20">
            <h2 class="text-3xl font-bold text-center mb-12">Everything You Need for Viral Content</h2>
            <div class="grid md:grid-cols-2 lg:grid-cols-3 gap-8">
              <Card v-for="feature in features" :key="feature.title" class="bg-card">
                <CardHeader>
                  <div class="h-12 w-12 rounded-lg bg-primary/10 flex items-center justify-center mb-4">
                    <component :is="feature.icon" class="h-6 w-6 text-primary" />
                  </div>
                  <CardTitle>{{ feature.title }}</CardTitle>
                </CardHeader>
                <CardContent>
                  <p class="text-muted-foreground">{{ feature.description }}</p>
                </CardContent>
              </Card>
            </div>
          </div>
        </section>
  
        <section class="py-20">
          <h2 class="text-3xl font-bold text-center mb-12">How It Works</h2>
          <div class="grid md:grid-cols-3 gap-8">
            <div v-for="(step, index) in steps" :key="index" class="flex flex-col items-center text-center gap-4">
              <div class="h-12 w-12 rounded-full bg-primary/10 flex items-center justify-center text-xl font-bold text-primary">
                {{ index + 1 }}
              </div>
              <h3 class="text-xl font-semibold">{{ step.title }}</h3>
              <p class="text-muted-foreground">{{ step.description }}</p>
            </div>
          </div>
        </section>
  
        <section class="py-20">
          <div class="bg-muted/50 -mx-4 sm:-mx-6 lg:-mx-8 px-4 sm:px-6 lg:px-8 py-20">
            <div class="max-w-3xl mx-auto text-center">
              <h2 class="text-3xl font-bold mb-6">Ready to Create Amazing Content?</h2>
              <p class="text-lg text-muted-foreground mb-8">
                Join thousands of content creators who are already using RecapGrid to create viral-worthy videos.
              </p>
              <NuxtLink to="/dashboard">
                <Button size="lg" class="bg-primary text-primary-foreground hover:bg-primary/90">
                    Get Started Now
                    <ArrowRightIcon class="ml-2 h-5 w-5" />
                </Button>
            </NuxtLink>
            </div>
          </div>
        </section>
      </main>
  
      <footer class="border-t">
        <div class="max-w-screen-xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div class="flex justify-between items-center">
            <div class="flex items-center gap-2">
              <VideoIcon class="h-6 w-6 text-primary" />
              <span class="font-semibold">RecapGrid</span>
            </div>
            <p class="text-sm text-muted-foreground">© 2025 RecapGrid. All rights reserved.</p>
          </div>
        </div>
      </footer>
    </div>
  </template>
  
  <script setup>
  import { 
    VideoIcon, 
    PlayCircleIcon,
    WandIcon,
    TypeIcon,
    MusicIcon,
    ScissorsIcon,
    BrainIcon,
    SparklesIcon,
    ArrowRightIcon 
  } from 'lucide-vue-next'
  import { Button } from '@/components/ui/button'
  import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
  
  const features = [
    {
      icon: WandIcon,
      title: 'Smart Trimming',
      description: 'AI-powered trimming that automatically identifies the best parts of your video.'
    },
    {
      icon: TypeIcon,
      title: 'Auto Captions',
      description: 'Generate accurate captions in multiple languages with perfect timing.'
    },
    {
      icon: MusicIcon,
      title: 'AI Music Match',
      description: 'Get AI-recommended background music that matches your content perfectly.'
    },
    {
      icon: ScissorsIcon,
      title: 'Perfect Cuts',
      description: 'Automatically trim and cut your videos to the ideal length for each platform.'
    },
    {
      icon: BrainIcon,
      title: 'AI Overview',
      description: 'Get smart suggestions and insights to optimize your content performance.'
    },
    {
      icon: SparklesIcon,
      title: 'Voice Enhancement',
      description: 'Crystal clear voice-overs and audio enhancement powered by AI.'
    }
  ]
  
  const steps = [
    {
      title: 'Upload Your Video',
      description: 'Simply drag and drop your video file or paste a URL to get started.'
    },
    {
      title: 'AI Processing',
      description: 'Our AI analyzes your content and applies smart enhancements automatically.'
    },
    {
      title: 'Download & Share',
      description: 'Get your perfectly optimized video ready for TikTok and other platforms.'
    }
  ]
  </script>