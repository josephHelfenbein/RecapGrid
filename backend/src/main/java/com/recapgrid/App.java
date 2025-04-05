package com.recapgrid;

import com.recapgrid.model.Video;
import com.recapgrid.model.ClerkUser;
import com.recapgrid.model.Processed;
import com.recapgrid.model.UserEntity;
import com.recapgrid.repository.VideoRepository;
import com.recapgrid.repository.ProcessedRepository;
import com.recapgrid.repository.UserRepository;

import java.util.Base64;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

    @Value("${gemini.key}")
    private String geminiKey;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProcessedRepository processedRepository;

    private RestTemplate restTemplate = new RestTemplate();

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @GetMapping("/health")
    public String healthCheck() {
        return "Backend is running!";
    }
    private void ensureUserFolderExists(String userId, String folder) {
        logger.info("Ensuring folder exists for user: {}", userId);
        String folderPath = folder + "/" + userId;
        
        String listUrl = supabaseUrl + "/storage/v1/object/list/" + folderPath;
        HttpHeaders headers = createHeaders();
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(listUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                if (response.getBody() == null || response.getBody().isEmpty()) {
                    logger.info("Folder exists but is empty, creating dummy file.");
                    createDummyFile(folderPath);
                } else logger.info("Folder already exists for user: {}", userId);
            } else if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                logger.info("Folder not found, creating folder for user: {}", userId);
                createDummyFile(folderPath);
            } else logger.error("Error checking folder: {} - Status: {}", folderPath, response.getStatusCode());

        } catch (Exception e) {
            logger.error("An error occurred while checking the folder: {}", folderPath, e);
            createDummyFile(folderPath);
        }
    }
    private void createDummyFile(String folderPath) {
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + folderPath + "/dummy.txt";
        HttpHeaders uploadHeaders = createHeaders();
        uploadHeaders.set("Content-Type", "text/plain");
    
        ByteArrayResource resource = new ByteArrayResource("Dummy file content".getBytes());
        HttpEntity<ByteArrayResource> uploadEntity = new HttpEntity<>(resource, uploadHeaders);
    
        try {
            ResponseEntity<String> uploadResponse = restTemplate.exchange(uploadUrl, HttpMethod.PUT, uploadEntity, String.class);
    
            if (uploadResponse.getStatusCode() == HttpStatus.OK || uploadResponse.getStatusCode() == HttpStatus.CREATED) logger.info("Dummy file created successfully to ensure folder exists");
            else logger.error("Error creating dummy file for folder: {}, Status: {}, Response Body: {}", folderPath, uploadResponse.getStatusCode(), uploadResponse.getBody());
        } catch (Exception e) {
            logger.error("Error uploading dummy file to folder: {}", folderPath, e);
        }
    }
      
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseKey);
        headers.set("Authorization", "Bearer " + supabaseKey);
        return headers;
    }
    
    @GetMapping("/videos")
    public ResponseEntity<List<Video>> getVideos(@RequestParam String userId) {
        logger.info("Fetching videos for user: {}", userId);
        ensureUserFolderExists(userId, "videos");
        List<Video> videos = videoRepository.findByUserId(userId);
        if (videos == null || videos.isEmpty()) {
            logger.info("No videos found for user: {}", userId);
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(videos);
    }

    @GetMapping("/processed")
    public ResponseEntity<List<Processed>> getProcessed(@RequestParam String userId) {
        logger.info("Fetching processed videos for user: {}", userId);
        ensureUserFolderExists(userId, "processed");
        List<Processed> videos = processedRepository.findByUserId(userId);
        if (videos == null || videos.isEmpty()) {
            logger.info("No videos found for user: {}", userId);
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(videos);
    }

    @PostMapping("/processVideo")
    public ResponseEntity<String> processVideo(@RequestBody Video video) {
        if (video == null) return ResponseEntity.badRequest().body("Processed object is null.");
        try {
            byte[] videoBytes = downloadVideo(video.getFileUrl());
            if (videoBytes == null || videoBytes.length == 0) return ResponseEntity.badRequest().body("Failed to download video.");
            logger.info("Processing video: {}", video.getFileName());

            String base64Video = Base64.getEncoder().encodeToString(videoBytes);
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + geminiKey;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String requestJson = """
            {
            "contents": [{
                "parts": [
                { "text": "Can you summarize this video?" },
                {
                    "inline_data": {
                    "mime_type": "video/mp4",
                    "data": "%s"
                    }
                }
                ]
            }]
            }
            """.formatted(base64Video);

            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);
            String response = restTemplate.postForEntity(url, entity, String.class).getBody();
            if (response == null) return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process video.");
            logger.info("Video processed successfully: {}", video.getFileName());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to download or encode video.");
        }
    }
    public byte[] downloadVideo(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        logger.info("Downloading video from URL: {}", fileUrl);

        try (InputStream inputStream = connection.getInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1)  outputStream.write(buffer, 0, bytesRead);
            return outputStream.toByteArray();
        }
    }

    
    @PostMapping("/videos/upload")
    public ResponseEntity<Video> uploadVideo(
        @RequestParam("userId") String userId,
        @RequestParam("fileData") MultipartFile fileData,
        @RequestParam("fileName") String fileName) {
        
        logger.info("Uploading video '{}' for user: {}", fileName, userId);
        ensureUserFolderExists(userId, "videos");

        String encodedFileName = UriUtils.encodePath(fileName, StandardCharsets.UTF_8);
        String storagePath = "videos/" + userId + "/" + encodedFileName;
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + storagePath;

        HttpHeaders headers = createHeaders();
        headers.set("Content-Type", fileData.getContentType());

        HttpEntity<ByteArrayResource> uploadEntity;
        try {
            ByteArrayResource resource = new ByteArrayResource(fileData.getBytes());
            uploadEntity = new HttpEntity<>(resource, headers);

            ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.PUT, uploadEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String fileUrl = supabaseUrl + "/storage/v1/object/public/" + storagePath;
                Video saving = new Video(userId, fileName, fileUrl);
                videoRepository.save(saving);
                logger.info("Video uploaded successfully: {}", fileUrl);
                return ResponseEntity.ok(saving);
            } else {
                logger.error("Error uploading video '{}', Status: {}, Response: {}", fileName, response.getStatusCode(), response.getBody());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
        } catch (Exception e) {
            logger.error("Error uploading video '{}'", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PostMapping("/clerk-user")
    public ResponseEntity<String> createClerkUser(@RequestBody ClerkUser clerkUser) {
        if (clerkUser == null || clerkUser.getData() == null) return ResponseEntity.badRequest().body("Error: ClerkUser data is missing!");
        Optional<UserEntity> existingUser = userRepository.findById(clerkUser.getData().getId());
        if (existingUser.isPresent()) return updateUser(existingUser.get(), clerkUser);
        return saveNewUser(clerkUser);
    }

    @PostMapping("/delete-user")
    public ResponseEntity<String> deleteClerkUser(@RequestBody ClerkUser clerkUser) {
        if (clerkUser == null || clerkUser.getData() == null) return ResponseEntity.badRequest().body("Error: ClerkUser data is missing!");
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
        if (!clerkUser.getData().getEmailAddresses().isEmpty()) entity.setEmail(clerkUser.getData().getEmailAddresses().get(0).getEmailAddress());
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
