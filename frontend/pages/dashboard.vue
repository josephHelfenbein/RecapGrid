<template>
    <div class="h-screen bg-background flex">
      <main class="flex-1 overflow-auto">
        <div class="h-14 border-b flex items-center justify-between px-4 lg:h-[60px]">
          <h1 class="font-semibold">RecapGrid</h1>
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
        
        <div class="w-screen flex justify-center">
        <div class="container max-w-4xl py-6">
          <Card class="mb-6">
            <CardContent class="pt-6">
              <div 
                class="border-2 border-dashed rounded-lg p-8 text-center space-y-4"
                @dragover.prevent
                @drop="handleDrop"
              >
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
                <input
                  type="file"
                  accept="video/mp4,video/quicktime"
                  class="hidden"
                  ref="fileInput"
                  @change="handleFileSelect"
                />
                <Button variant="secondary" @click="selectFile">Select Video</Button>
              </div>
            </CardContent>
          </Card>

          <h3 class="text-lg text-center font-semibold">Pending Videos</h3>
          <div v-if="pendingVideos.length>0" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 p-2">
            <Card v-for="video in pendingVideos" :key="video.id">
              <CardHeader>
                <video class="w-full h-36 object-cover rounded-lg" :src="video.fileUrl" controls></video>
                <div class="flex items-center justify-between mt-4">
                  <div>
                    <CardTitle>{{ video.fileName }}</CardTitle>
                    <p class="text-foreground text-xs">Uploaded on {{ new Date(video.uploadedAt).toLocaleDateString() }}</p>
                  </div>
                  <Button variant="secondary" size="sm">Process</Button>
                </div>
              </CardHeader>
            </Card>
          </div>
          <div v-else class="text-foreground text-center py-6">No videos uploaded yet. Drop a video above to get started.</div>

          <h3 class="text-lg text-center font-semibold">Processed Videos</h3>
          <div v-if="videos.length>0" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 p-2">
            <Card v-for="video in videos" :key="video.id">
              <CardHeader>
                <video class="w-full h-36 object-cover rounded-lg" :src="video.fileUrl" controls></video>
                <div class="flex items-center justify-between mt-4">
                  <div>
                    <CardTitle>{{ video.fileName }}</CardTitle>
                    <p class="text-foreground text-xs">Processed on {{ new Date(video.uploadedAt).toLocaleDateString() }}</p>
                  </div>
                  <Button variant="secondary" size="sm">Download</Button>
                </div>
              </CardHeader>
            </Card>
          </div>
          <div v-else class="text-foreground text-center py-6">No videos processed yet.</div>
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

  import { ref } from 'vue'
  const { user } = useUser();
  const videos = ref([]);
  const pendingVideos = ref([]);

  const fileInput = ref(null)

  async function getVideos(userId){
    const response = await fetch(`/api/processed?userId=${encodeURIComponent(userId)}`);
    const data = await response.json();
    videos.value = data;
    console.log(data);
  }
  async function getPending(userId){
    const response = await fetch(`/api/videos?userId=${encodeURIComponent(userId)}`);
    const data = await response.json();
    pendingVideos.value = data;
    console.log(data);
  }

  const handleDrop = (event) => {
    event.preventDefault();
    const file = event.dataTransfer.files[0];
    if (file) uploadVideo(file);
  };

  const handleFileSelect = (event) => {
    const file = event.target.files[0];
    if (file) uploadVideo(file);
  };

  const selectFile = () => {
    fileInput.value.click();
  };

  const uploadVideo = async (file) => {
    const formData = new FormData();
    formData.append("fileData", file);
    formData.append("fileName", file.name);
    formData.append("userId", user.value.id);

    try {
      const response = await fetch("/api/videos/upload", {
        method: "POST",
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`Upload failed with status ${response.status}`);
      }

      const data = await response.json();
      pendingVideos.value.push(data);
    } catch (error) {
      console.error("Error uploading video:", error);
    }
  };

  watch(() => user.value, async (newUser) => {
    if(newUser){
      await getVideos(newUser.id);
      await getPending(newUser.id);
    }
  });

  </script>