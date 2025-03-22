package com.recapgrid;

import com.recapgrid.model.Video;
import com.recapgrid.model.ClerkUser;
import com.recapgrid.model.UserEntity;
import com.recapgrid.repository.VideoRepository;
import com.recapgrid.repository.UserRepository;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(App.class);

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
        logger.info("Ensuring folder exists for user: {}", userId);
        String folderPath = "videos/" + userId;
        
        String listUrl = supabaseUrl + "/storage/v1/object/list/" + folderPath;
        HttpHeaders headers = createHeaders();
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(listUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                if (response.getBody() == null || response.getBody().isEmpty()) {
                    logger.info("Folder exists but is empty, creating dummy file.");
                    createDummyFile(folderPath);
                } else {
                    logger.info("Folder already exists for user: {}", userId);
                }
            } else if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                logger.info("Folder not found, creating folder for user: {}", userId);
                createDummyFile(folderPath);
            } else {
                logger.error("Error checking folder: {} - Status: {}", folderPath, response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("An error occurred while checking the folder: {}", folderPath, e);
            createDummyFile(folderPath);
        }
    }
    private void createDummyFile(String folderPath) {
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + folderPath + "/dummy.txt";
    
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.set("apikey", supabaseKey);
        uploadHeaders.set("Content-Type", "text/plain");
    
        ByteArrayResource resource = new ByteArrayResource("Dummy file content".getBytes());
        HttpEntity<ByteArrayResource> uploadEntity = new HttpEntity<>(resource, uploadHeaders);
    
        try {
            ResponseEntity<String> uploadResponse = restTemplate.exchange(uploadUrl, HttpMethod.PUT, uploadEntity, String.class);
    
            if (uploadResponse.getStatusCode() == HttpStatus.OK || uploadResponse.getStatusCode() == HttpStatus.CREATED) {
                logger.info("Dummy file created successfully to ensure folder exists");
            } else {
                logger.error("Error creating dummy file for folder: {}, Status: {}, Response Body: {}", 
                              folderPath, uploadResponse.getStatusCode(), uploadResponse.getBody());
            }
        } catch (Exception e) {
            logger.error("Error uploading dummy file to folder: {}", folderPath, e);
        }
    }
    
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseKey);
        return headers;
    }
    
    @GetMapping("/videos")
    public ResponseEntity<List<Video>> getVideos(@RequestParam String userId) {
        logger.info("Fetching videos for user: {}", userId);
        ensureUserFolderExists(userId);
        List<Video> videos = videoRepository.findByUserId(userId);
        if (videos == null || videos.isEmpty()) {
            logger.info("No videos found for user: {}", userId);
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(videos);
    }
    
    @PostMapping("/videos/upload")
    public ResponseEntity<String> uploadVideo(@RequestParam String userId, @RequestBody byte[] fileData, @RequestParam String fileName) {
        logger.info("Uploading video '{}' for user: {}", fileName, userId);
        ensureUserFolderExists(userId);
    
        String storagePath = String.format("videos/%s/%s", userId, fileName);
        String uploadUrl = String.format("%s/storage/v1/object/%s", supabaseUrl, storagePath);
    
        HttpHeaders headers = createHeaders();
        headers.set("Content-Type", "video/mp4");
    
        ByteArrayResource resource = new ByteArrayResource(fileData);
        HttpEntity<ByteArrayResource> requestEntity = new HttpEntity<>(resource, headers);
    
        ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.PUT, requestEntity, String.class);
    
        if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
            String fileUrl = supabaseUrl + "/storage/v1/object/public/" + storagePath;
            videoRepository.save(new Video(userId, fileName, fileUrl));
            logger.info("Video uploaded successfully: {}", fileUrl);
            return ResponseEntity.ok("Video uploaded successfully: " + fileUrl);
        } else {
            logger.error("Error uploading video '{}', status: {}, response: {}", fileName, response.getStatusCode(), response.getBody());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading video");
        }
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
