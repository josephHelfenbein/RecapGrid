package com.recapgrid;

import com.recapgrid.model.Video;
import com.recapgrid.model.ClerkUser;
import com.recapgrid.model.UserEntity;
import com.recapgrid.repository.VideoRepository;
import com.recapgrid.repository.UserRepository;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
@RestController
@RequestMapping("/api")
public class App {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private UserRepository userRepository;

    private RestTemplate restTemplate = new RestTemplate();

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @GetMapping("/health")
    public String healthCheck() {
        return "Backend is running!";
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

    @GetMapping("/videos")
    public ResponseEntity<List<Video>> getVideos(@RequestParam String userId) {
        ensureUserFolderExists(userId);
        return ResponseEntity.ok(videoRepository.findByUserId(userId));
    }

    @PostMapping("/videos/upload")
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
            videoRepository.save(new Video(userId, fileName, fileUrl));
            return ResponseEntity.ok("Video uploaded successfully: " + fileUrl);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading video");
    }

    @DeleteMapping("/videos/delete")
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
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting video");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Video not found");
    }

    @PostMapping("/clerk-user")
    public ResponseEntity<String> createClerkUser(@RequestBody ClerkUser clerkUser) {
        if (clerkUser == null || clerkUser.getData() == null) {
            return ResponseEntity.badRequest().body("Error: ClerkUser data is missing!");
        }

        Optional<UserEntity> existingUser = userRepository.findById(clerkUser.getData().getId());
        if (existingUser.isPresent()) {
            return updateUser(existingUser.get(), clerkUser);
        }
        return saveNewUser(clerkUser);
    }

    @PostMapping("/delete-user")
    public ResponseEntity<String> deleteClerkUser(@RequestBody ClerkUser clerkUser) {
        if (clerkUser == null || clerkUser.getData() == null) {
            return ResponseEntity.badRequest().body("Error: ClerkUser data is missing!");
        }
        Optional<UserEntity> existingUser = userRepository.findById(clerkUser.getData().getId());
        if (existingUser.isPresent()) {
            userRepository.delete(existingUser.get());
            return ResponseEntity.ok("User deleted: " + existingUser.get().getFullName());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found!");
    }

    private ResponseEntity<String> updateUser(UserEntity entity, ClerkUser clerkUser) {
        entity.setFullName(clerkUser.getData().getFirstName() + " " + clerkUser.getData().getLastName());
        entity.setProfilePicture(clerkUser.getData().getImageUrl());
        if (!clerkUser.getData().getEmailAddresses().isEmpty()) {
            entity.setEmail(clerkUser.getData().getEmailAddresses().get(0).getEmailAddress());
        }
        userRepository.save(entity);
        return ResponseEntity.ok("User updated: " + entity.getFullName());
    }

    private ResponseEntity<String> saveNewUser(ClerkUser clerkUser) {
        UserEntity entity = new UserEntity();
        entity.setId(clerkUser.getData().getId());
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setFullName(clerkUser.getData().getFirstName() + " " + clerkUser.getData().getLastName());
        entity.setProfilePicture(clerkUser.getData().getImageUrl());
        entity.setEmail(clerkUser.getData().getEmailAddresses().isEmpty() ? null : clerkUser.getData().getEmailAddresses().get(0).getEmailAddress());
        userRepository.save(entity);
        return ResponseEntity.ok("User saved: " + entity.getFullName());
    }
}
