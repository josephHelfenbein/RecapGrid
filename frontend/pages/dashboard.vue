<template>
    <div class="h-screen bg-background flex pt-20">
      <main class="flex-1 overflow-auto">
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
                      or click to browse (MP4 up to 20MB)
                    </p>
                  </div>
                </div>
                <input
                  type="file"
                  accept="video/mp4"
                  class="hidden"
                  ref="fileInput"
                  @change="handleFileSelect"
                />
                <Button variant="secondary" @click="selectFile">Select Video</Button>
              </div>
            </CardContent>
          </Card>

          <h3 class="text-lg text-center font-semibold">Pending Videos</h3>
          <div v-if="!loadedPending" class="flex items-center justify-center py-6">
            <Loader size="40px" />
          </div>
          <div v-else-if="pendingVideos.length>0" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 p-2">
            <Card v-for="video in pendingVideos" :key="video.id">
              <CardHeader>
                <video class="w-full h-36 object-cover rounded-lg" :src="video.fileUrl" controls></video>
                <div class="flex items-center justify-between mt-4">
                  <div>
                    <CardTitle>{{ video.fileName }}</CardTitle>
                    <p class="text-foreground text-xs">Uploaded on {{ new Date(video.uploadedAt).toLocaleDateString() }}</p>
                  </div>
                  <Button variant="secondary" size="sm" @click="processVideo(video)">Process</Button>
                </div>
              </CardHeader>
            </Card>
          </div>
          <div v-else class="text-foreground text-center py-6">No videos uploaded yet. Drop a video above to get started.</div>
          <ProcessWindow ref="videoPopup" @submit="handleProcessing" />

          <h3 class="text-lg text-center font-semibold">Processed Videos</h3>
          <div v-if="!loadedVideos" class="flex items-center justify-center py-6">
            <Loader size="40px" />
          </div>
          <div v-else-if="videos.length>0" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 p-2">
            <Card v-for="video in videos" :key="video.id">
              <CardHeader>
                <video class="w-full h-36 object-cover rounded-lg" :src="video.fileUrl" controls></video>
                <div class="flex items-center justify-between mt-4">
                  <div>
                    <CardTitle>{{ video.fileName }}</CardTitle>
                    <p class="text-foreground text-xs">Processed on {{ new Date(video.uploadedAt).toLocaleDateString() }}</p>
                  </div>
                  <Button variant="secondary" size="sm" @click="downloadVideo(video.fileUrl, video.fileName)">Download</Button>
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
  import Loader from '@/components/Loader.vue'
  import ProcessWindow from '@/components/ProcessWindow.vue'

  import { ref } from 'vue'

  const { user } = useUser();
  const videoPopup = ref(null)
  const videos = ref([]);
  const pendingVideos = ref([]);
  const loadedVideos = ref(false);
  const loadedPending = ref(false);
  const fileToProcess = ref(null);

  const fileInput = ref(null)


  async function getVideos(userId){
    const response = await fetch(`/api/processed?userId=${encodeURIComponent(userId)}`);
    const data = await response.json();
    videos.value = data;
    loadedVideos.value = true;
    console.log(data);
  }
  async function getPending(userId){
    const response = await fetch(`/api/videos?userId=${encodeURIComponent(userId)}`);
    const data = await response.json();
    pendingVideos.value = data;
    loadedPending.value = true;
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

  const downloadVideo = (url, fileName) => {
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName || 'video.mp4';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const processVideo = (video) =>{
    videoPopup.value.openPopup()
    fileToProcess.value = video;
  }

  const handleProcessing = async (data) =>{
    console.log(data);
    console.log(fileToProcess.value);
    const response = await fetch("/api/videos/processVideo", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: fileToProcess.value,
    });
    if (!response.ok) console.error(`Processing failed with status ${response.status}`);
    console.log(response.body);
  }

  watch(() => user.value, async (newUser) => {
    if(newUser){
      await getVideos(newUser.id);
      await getPending(newUser.id);
    }
  });

  </script>