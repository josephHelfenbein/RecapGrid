<template>
    <div class="h-screen bg-background flex pt-20">
      <SignedOut>
        <RedirectToSignUp />
      </SignedOut>
      <main class="flex-1 overflow-auto">
        <transition
          enter-active-class="transition ease-out duration-300"
          enter-from-class="opacity-0 translate-y-2"
          enter-to-class="opacity-100 translate-y-0"
          leave-active-class="transition ease-in duration-200"
          leave-from-class="opacity-100 translate-y-0"
          leave-to-class="opacity-0 translate-y-2"
        >
            <Alert v-if="showError" class="fixed bottom-0 z-50 m-5 w-full max-w-md animate-fade-up">
              <AlertTitle class="flex items-center">
                <FileWarningIcon class="h-6 w-6 mr-2" />
                Heads up!</AlertTitle>
              <AlertDescription class="flex items-center">
                <p class="truncate">{{ errorMessage }}</p>
                <Button variant="secondary" class="absolute top-2 right-2 bg-color-none" @click="showError = false">
                  <XIcon class="h-4 w-4" />
                </Button>
              </AlertDescription>
            </Alert>
          </transition>
          <transition
            enter-active-class="transition ease-out duration-300"
            enter-from-class="opacity-0 translate-y-2"
            enter-to-class="opacity-100 translate-y-0"
            leave-active-class="transition ease-in duration-200"
            leave-from-class="opacity-100 translate-y-0"
            leave-to-class="opacity-0 translate-y-2"
          >
            <Alert v-if="showAlert" class="fixed bottom-0 z-50 m-5 w-full max-w-md animate-fade-up">
              <AlertTitle class="flex items-center">
                <BellIcon class="h-6 w-6 mr-2" />
                {{ alertVar.stage }}</AlertTitle>
              <AlertDescription class="flex items-center">
                <p class="truncate">{{ alertVar.info }}</p>
                <Button variant="secondary" class="absolute top-2 right-2 bg-color-none" @click="showAlert = false">
                  <XIcon class="h-4 w-4" />
                </Button>
              </AlertDescription>
            </Alert>
          </transition>
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
                      or click to browse (MP4 or MOV, up to 30MB)
                    </p>
                  </div>
                </div>
                <input
                  type="file"
                  accept="video/mp4, video/quicktime"
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
                  <div class="flex-1 min-w-0">
                    <CardTitle class="truncate">{{ video.fileName }}</CardTitle>
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
                  <div class="flex-1 min-w-0">
                    <CardTitle class="truncate">{{ video.fileName }}</CardTitle>
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
    Wand2Icon,
    XIcon,
    FileWarningIcon,
  } from 'lucide-vue-next'
  import Loader from '@/components/Loader.vue'
  import ProcessWindow from '@/components/ProcessWindow.vue'
  import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
  

  import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
  import { useNuxtApp } from '#app'
  import { SignedOut } from '@clerk/vue';
  import { useAuth } from '@clerk/vue'

  const { user } = useUser();
  const { getToken } = useAuth();
  const videoPopup = ref(null)
  const videos = ref([]);
  const pendingVideos = ref([]);
  const loadedVideos = ref(false);
  const loadedPending = ref(false);
  const fileToProcess = ref(null);
  const showError = ref(false);
  const errorMessage = ref('');

  const { $supabase } = useNuxtApp();
  let channel = null

  const fileInput = ref(null)
  const showAlert = ref(false);
  const alertVar = ref({
    stage: '',
    info: ''
  });

  useSeoMeta({
    title: 'Dashboard',
    description: 'Instantly turn videos into shareable highlight reels with smart timestamps, AI narration, and background music.',
    ogTitle: 'RecapGrid - Dashboard',
    twitterTitle: 'RecapGrid - Dashboard',
  });

  async function getVideos(userId){
    try{
      const token = await getToken.value();
      if(!token) throw new Error('Token is not available');
      const response = await fetch(`/api/processed?userId=${encodeURIComponent(userId)}`, {
        headers: {
          "Authorization": `Bearer ${token}`,
        },
      });
      const data = await response.json();
      videos.value = data;
      loadedVideos.value = true;
      console.log(data);
    } catch (error) {
      console.error('Error fetching videos:', error);
      alertNew("Error fetching videos:", error);
    }
  }
  async function getPending(userId){
    try{
      const token = await getToken.value();
      if(!token) throw new Error('Token is not available');
      const response = await fetch(`/api/videos?userId=${encodeURIComponent(userId)}`, {
          headers: {
            "Authorization": `Bearer ${token}`,
          },
        });
      const data = await response.json();
      pendingVideos.value = data;
      loadedPending.value = true;
      console.log(data);
    } catch (error) {
      console.error('Error fetching pending videos:', error);
      alertNew("Error fetching pending videos:", error);
    }
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

  const alertNew = (message) =>{
    showError.value = true;
    errorMessage.value = message;
    setTimeout(() => {
      showError.value = false;
    }, 4000);
  }

  const alertNewVar = (message, stage) =>{
    showAlert.value = true;
    alertVar.value.info = message;
    alertVar.value.stage = stage;
  }

  const uploadVideo = async (file) => {
    if(file.size > 30 * 1024 * 1024) {
      alertNew("File size exceeds 30MB limit.");
      return;
    }
    if(file.type !== "video/mp4" && file.type !== "video/quicktime") {
      alertNew("Unsupported file format.");
      return;
    }
    if(!user.value) {
      alertNew("Please log in to upload videos.");
      return;
    }
    if(!file) {
      alertNew("No file selected.");
      return;
    }
    const formData = new FormData();
    formData.append("fileData", file);
    formData.append("fileName", file.name);

    try {
      const token = await getToken.value();
      if(!token) throw new Error('Token is not available');
      const response = await fetch(`/api/videos/upload?userId=${encodeURIComponent(user.value.id)}`, {
        method: "POST",
        body: formData,
        headers: {
          "Authorization": `Bearer ${token}`,
        },
      });

      if (!response.ok) throw new Error(`Upload failed with status ${response.status}`);

      const data = await response.json();
      pendingVideos.value.push(data);
    } catch (error) {
      alertNew("Error uploading video:", error);
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
    const queryParams = new URLSearchParams({
      voice: data.voice,
      feel: data.feel,
      music: data.music,
      userId: fileToProcess.value.userId,
    });
    try{
      const token = await getToken.value();
      if(!token) throw new Error('Token is not available');
      const response = await fetch(`/api/processVideo?${queryParams.toString()}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${token}`,
        },
        body: JSON.stringify({
          userId: fileToProcess.value.userId,
          fileName: fileToProcess.value.fileName,
          fileUrl: fileToProcess.value.fileUrl,
        }),
      });
      if (!response.ok) throw new Error(`Adding to queue failed with status ${response.status}`);
    } catch (error) {
      alertNew("Error processing video:", error);
    }
  }

  watch(() => user.value, async (newUser) => {
    if(newUser){
      await getVideos(newUser.id);
      await getPending(newUser.id);
      if (channel) {
        await $supabase.removeChannel(channel)
        channel = null
      }
      channel = $supabase
      .channel(`public:status-and-processed:${newUser.id}`)
      .on(
        'postgres_changes',
        {
          event: 'UPDATE',
          schema: 'public',
          table: 'status',
          filter: `id=eq.${newUser.id}`,
        },
        (payload) => {
          console.log('New status:', payload.new)
          alertNewVar(payload.new.info, payload.new.stage)
        }
      )
      .on(
        'postgres_changes',
        {
          event: 'INSERT',
          schema: 'public',
          table: 'processed',
          filter: `user_id=eq.${newUser.id}`,
        },
        (payload) => {
          console.log('New processed video:', payload.new)
          const newVideo = {
            id: payload.new.id,
            fileUrl: payload.new.file_url,
            fileName: payload.new.file_name,
            uploadedAt: payload.new.uploaded_at,
            userId: payload.new.user_id,
          }
          videos.value.push(newVideo);
        }
      )
      .subscribe((status) => {
        console.log('Status channel subscribe status ', status)
      });
    }
  }, { immediate: true });
  onBeforeUnmount(() => {
    if(channel) $supabase.removeChannel(subscription);
    channel = null
  });

  </script>