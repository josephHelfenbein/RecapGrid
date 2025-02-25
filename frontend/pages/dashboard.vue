<template>
    <div class="h-screen bg-background flex">
      <aside class="hidden lg:flex w-64 flex-col border-r bg-muted/40">
        <div class="flex h-14 items-center border-b px-4 lg:h-[60px]">
          <div class="flex items-center gap-2 font-semibold">
            <VideoIcon class="h-6 w-6 text-primary" />
            <span>RecapGrid</span>
          </div>
        </div>
        <nav class="flex-1 space-y-1 p-2">
          <Button variant="ghost" class="w-full justify-start gap-2">
            <LayoutDashboardIcon class="h-4 w-4" />
            Dashboard
          </Button>
          <Button variant="secondary" class="w-full justify-start gap-2">
            <PlusIcon class="h-4 w-4" />
            New Video
          </Button>
          <Button variant="ghost" class="w-full justify-start gap-2">
            <FolderIcon class="h-4 w-4" />
            My Videos
          </Button>
          <Button variant="ghost" class="w-full justify-start gap-2">
            <SettingsIcon class="h-4 w-4" />
            Settings
          </Button>
        </nav>
        <div class="border-t p-4">
          <div class="flex items-center gap-2">
            <Avatar>
              <AvatarImage src="/placeholder.svg" />
              <AvatarFallback>JD</AvatarFallback>
            </Avatar>
            <div class="flex flex-col">
              <span class="text-sm font-medium">John Doe</span>
              <span class="text-xs text-muted-foreground">john@example.com</span>
            </div>
          </div>
        </div>
      </aside>
  
      <main class="flex-1 overflow-auto">
        <div class="h-14 border-b flex items-center justify-between px-4 lg:h-[60px]">
          <h1 class="font-semibold">New Video</h1>
          <Button variant="ghost" size="icon">
            <BellIcon class="h-4 w-4" />
          </Button>
        </div>
  
        <div class="container max-w-4xl py-6">
          <Card class="mb-6">
            <CardContent class="pt-6">
              <div class="border-2 border-dashed rounded-lg p-8 text-center space-y-4">
                <div class="flex flex-col items-center gap-2">
                  <div class="h-12 w-12 rounded-full bg-primary/10 flex items-center justify-center">
                    <UploadCloudIcon class="h-6 w-6 text-primary" />
                  </div>
                  <div class="flex flex-col items-center gap-1">
                    <h3 class="font-medium">Drop your video here</h3>
                    <p class="text-sm text-muted-foreground">
                      or click to browse (MP4, MOV up to 1GB)
                    </p>
                  </div>
                </div>
                <Button variant="secondary">Select Video</Button>
              </div>
            </CardContent>
          </Card>
  
          <div class="grid gap-6">
            <Card>
              <CardHeader>
                <CardTitle>Video Settings</CardTitle>
                <CardDescription>Configure how your video will be processed</CardDescription>
              </CardHeader>
              <CardContent class="space-y-4">
                <div class="space-y-2">
                  <Label>Title</Label>
                  <Input placeholder="Enter video title" />
                </div>
                
                <div class="space-y-2">
                  <Label>Platform</Label>
                  <Select>
                    <SelectTrigger>
                      <SelectValue placeholder="Select platform" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="tiktok">TikTok</SelectItem>
                      <SelectItem value="instagram">Instagram Reels</SelectItem>
                      <SelectItem value="youtube">YouTube Shorts</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
  
                <div class="space-y-2">
                  <Label>AI Features</Label>
                  <div class="grid gap-2">
                    <div class="flex items-center space-x-2">
                      <Checkbox id="captions" />
                      <label for="captions" class="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
                        Auto-generate captions
                      </label>
                    </div>
                    <div class="flex items-center space-x-2">
                      <Checkbox id="music" />
                      <label for="music" class="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
                        Add AI background music
                      </label>
                    </div>
                    <div class="flex items-center space-x-2">
                      <Checkbox id="voice" />
                      <label for="voice" class="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
                        Enhance voice audio
                      </label>
                    </div>
                  </div>
                </div>
              </CardContent>
              <CardFooter>
                <Button class="ml-auto">
                  <Wand2Icon class="mr-2 h-4 w-4" />
                  Process Video
                </Button>
              </CardFooter>
            </Card>
          </div>
        </div>
      </main>
    </div>
  </template>
  
  <script setup>
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