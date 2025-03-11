package com.recapgrid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.recapgrid.model.ClerkUser;
import com.recapgrid.model.UserEntity;
import com.recapgrid.model.Video;
import com.recapgrid.repository.UserRepository;
import com.recapgrid.repository.VideoRepository; 

@SpringBootApplication
@RestController
@RequestMapping("/api")
public class App {
    
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Autowired
    private UserRepository repository;

    @GetMapping("/health")
    public String healthCheck() {
        return "Backend is running!";
    }

    @PostMapping("/clerk-user")
    public ResponseEntity<String> createClerkUser(@RequestBody ClerkUser clerkUser) {
        if(clerkUser == null || clerkUser.getData() == null) {
            return ResponseEntity.badRequest().body("Error: ClerkUser data is missing!");
        }
        Optional<UserEntity> existingUser = repository.findById(clerkUser.getData().getId());
        if(existingUser.isPresent()) {
            UserEntity entity = existingUser.get();
            entity.setFullName(clerkUser.getData().getFirstName() + " " + clerkUser.getData().getLastName());
            entity.setProfilePicture(clerkUser.getData().getImageUrl());
            if(clerkUser.getData().getEmailAddresses()!=null && !clerkUser.getData().getEmailAddresses().isEmpty()) {
                entity.setEmail(clerkUser.getData().getEmailAddresses().get(0).getEmailAddress());
            }
            try {
                repository.save(entity);
                return ResponseEntity.ok("User updated with name: " + entity.getFullName());
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating user: " + e.getMessage());
            }
        } else
        {
            UserEntity entity = new UserEntity();
            entity.setId(clerkUser.getData().getId());
            entity.setCreatedAt(OffsetDateTime.now());
            entity.setFullName(clerkUser.getData().getFirstName() + " " + clerkUser.getData().getLastName());
            entity.setProfilePicture(clerkUser.getData().getImageUrl());
            if(clerkUser.getData().getEmailAddresses()!=null && !clerkUser.getData().getEmailAddresses().isEmpty()) {
                entity.setEmail(clerkUser.getData().getEmailAddresses().get(0).getEmailAddress());
            } else {
                entity.setEmail(null);
            }

            try {
                repository.save(entity);
                return ResponseEntity.ok("User saved with name: " + entity.getFullName());
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving user: " + e.getMessage());
            }
        }
    }

    @PostMapping("/delete-user")
    public ResponseEntity<String> deleteClerkUser(@RequestBody ClerkUser clerkUser){
        if(clerkUser == null || clerkUser.getData() == null) {
            return ResponseEntity.badRequest().body("Error: ClerkUser data is missing!");
        }
        Optional<UserEntity> existingUser = repository.findById(clerkUser.getData().getId());
        if(existingUser.isPresent()) {
            UserEntity entity = existingUser.get();
            try {
                repository.delete(entity);
                return ResponseEntity.ok("User deleted with name: " + entity.getFullName());
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting user: " + e.getMessage());
            }
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found!");
        }
    }

    @RequestMapping("/videos")
    public class VideoController{
        @Value("${supabase.url}")
        private String supabaseUrl;
    
        @Value("${supabase.key}")
        private String supabaseKey;
    
        private final VideoRepository videoRepository;
        private final RestTemplate restTemplate = new RestTemplate();
    
        public VideoController(VideoRepository videoRepository) {
            this.videoRepository = videoRepository;
        }
    
        private void ensureUserFolderExists(String userId) {
            String folderPath = "videos/" + userId + "/dummy.txt";
            String checkUrl = supabaseUrl + "/storage/v1/object/public/" + folderPath;
    
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseKey);
            headers.set("Authorization", "Bearer " + supabaseKey);
    
            ResponseEntity<String> response = restTemplate.exchange(checkUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                String uploadUrl = supabaseUrl + "/storage/v1/object/" + folderPath;
                HttpHeaders uploadHeaders = new HttpHeaders();
                uploadHeaders.set("apikey", supabaseKey);
                uploadHeaders.set("Authorization", "Bearer " + supabaseKey);
                uploadHeaders.set("Content-Type", "text/plain");
    
                ByteArrayResource resource = new ByteArrayResource("Dummy file content".getBytes());
                HttpEntity<ByteArrayResource> uploadEntity = new HttpEntity<>(resource, uploadHeaders);
    
                restTemplate.exchange(uploadUrl, HttpMethod.PUT, uploadEntity, String.class);
            }
        }
    
        @GetMapping
        public ResponseEntity<List<Video>> getVideos(@RequestParam String userId) {
            ensureUserFolderExists(userId);
            List<Video> videos = videoRepository.findByUserId(userId);
            return ResponseEntity.ok(videos);
        }
    
        @PostMapping("/upload")
        public ResponseEntity<String> uploadVideo(@RequestParam String userId, @RequestBody byte[] fileData, @RequestParam String fileName) {
            ensureUserFolderExists(userId);
    
            String storagePath = String.format("videos/%s/%s", userId, fileName);
            String uploadUrl = String.format("%s/storage/v1/object/%s", supabaseUrl, storagePath);
    
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseKey);
            headers.set("Authorization", "Bearer " + supabaseKey);
            headers.set("Content-Type", "video/mp4");
    
            ByteArrayResource resource = new ByteArrayResource(fileData);
            HttpEntity<ByteArrayResource> requestEntity = new HttpEntity<>(resource, headers);
    
            ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.PUT, requestEntity, String.class);
    
            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                String fileUrl = supabaseUrl + "/storage/v1/object/public/" + storagePath;
    
                Video video = new Video(userId, fileName, fileUrl);
                videoRepository.save(video);
    
                return ResponseEntity.ok("Video uploaded successfully: " + fileUrl);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading video");
            }
        }
    
        @DeleteMapping("/delete")
        public ResponseEntity<String> deleteVideo(@RequestParam Long videoId) {
            Optional<Video> videoOpt = videoRepository.findById(videoId);
            if (videoOpt.isPresent()) {
                Video video = videoOpt.get();
    
                String deleteUrl = String.format("%s/storage/v1/object/%s", supabaseUrl, video.getFileUrl().replace(supabaseUrl + "/storage/v1/object/public/", ""));
    
                HttpHeaders headers = new HttpHeaders();
                headers.set("apikey", supabaseKey);
                headers.set("Authorization", "Bearer " + supabaseKey);
    
                HttpEntity<String> requestEntity = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.exchange(deleteUrl, HttpMethod.DELETE, requestEntity, String.class);
    
                if (response.getStatusCode() == HttpStatus.NO_CONTENT || response.getStatusCode() == HttpStatus.OK) {
                    videoRepository.delete(video);
                    return ResponseEntity.ok("Video deleted successfully");
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting video");
                }
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Video not found");
            }
        }
    }

}