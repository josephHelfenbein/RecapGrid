package com.recapgrid;

import com.recapgrid.model.Video;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.recapgrid.model.ClerkUser;
import com.recapgrid.model.Processed;
import com.recapgrid.model.UserEntity;
import com.recapgrid.repository.VideoRepository;
import com.recapgrid.repository.ProcessedRepository;
import com.recapgrid.repository.UserRepository;

import java.util.ArrayList;
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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
    public ResponseEntity<Processed> processVideo(@RequestBody Video video, @RequestParam String voice, @RequestParam String feel) {
        if (video == null) {
            logger.error("Processed object is null.");
            return ResponseEntity.badRequest().body(null);
        }
        String userId = video.getUserId();
        try {
            byte[] videoBytes = downloadVideo(video.getFileUrl());
            if (videoBytes == null || videoBytes.length == 0) {
                logger.error("Failed to download video from URL: {}", video.getFileUrl());
                return ResponseEntity.badRequest().body(null);
            }
            logger.info("Processing video: {}", video.getFileName());


            Path originalPath = Files.createTempFile("orig-", ".mp4");
            Files.write(originalPath, videoBytes);
            logger.info("Saved original video to temporary file: {}", originalPath.toAbsolutePath());

            String base64Video = Base64.getEncoder().encodeToString(videoBytes);

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Summarize this video for an editor.\n")
                .append("1. First, list important timestamps where meaningful events happen (format: [start-end]).\n");

            if (!voice.equalsIgnoreCase("none")) {
                promptBuilder.append("2. Then, write a short narration in a ")
                            .append(feel.toLowerCase()).append(" tone ")
                            .append("to accompany these clips, spoken in a ")
                            .append(voice.toLowerCase()).append(" voice.");
            } else promptBuilder.append("2. Do not include narration. Just return timestamps.");

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                    Map.of("text", promptBuilder.toString()),
                    Map.of("inline_data", Map.of(
                        "mime_type", "video/mp4",
                        "data", base64Video
                    ))
                ))),
                "generationConfig", Map.of(
                    "response_mime_type", "application/json",
                    "response_schema", Map.of(
                        "type", "ARRAY",
                        "items", Map.of(
                            "type", "OBJECT",
                            "properties", Map.of(
                                "timestamps", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                                "narration", Map.of("type", "STRING")
                            )
                        )
                    )
                )
            );

            String requestJson = mapper.writeValueAsString(requestBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + geminiKey;
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("Error processing video: {} - Status: {}, Response: {}", video.getFileName(), response.getStatusCode(), response.getBody());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

            String responseBody = response.getBody();
            if (responseBody == null) {
                logger.error("Empty response from Gemini.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
            logger.info("Received response from Gemini");

            mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);

            JsonNode partsNode = root.path("candidates").get(0).path("content").path("parts");
            String textContent = partsNode.get(0).path("text").asText();

            ArrayNode structured = (ArrayNode) mapper.readTree(textContent);

            JsonNode timestampsNode = structured.get(0).path("timestamps");
            if (!timestampsNode.isArray()) throw new IllegalStateException("Expected timestamps array, got: " + timestampsNode);
            
            File original = originalPath.toFile();
            Path tmpDir = Files.createTempDirectory("splice-");
            List<String> segmentPaths = new ArrayList<>();

            int i = 0;
            for(JsonNode timestampNode : timestampsNode){
                String timestamp = timestampNode.asText();
                String[] parts = timestamp.split("-");
                if (parts.length != 2) {
                    logger.warn("Skipping malformed timestamp: {}", timestamp);
                    continue;
                }
                String start = normalizeTime(parts[0].trim());
                String end   = normalizeTime(parts[1].trim());
                File seg = tmpDir.resolve("seg" + (i++) + ".mp4").toFile();
                new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", original.getAbsolutePath(),
                    "-ss", start,
                    "-to", end,
                    "-c", "copy",
                    seg.getAbsolutePath()
                ).inheritIO().start().waitFor();
                segmentPaths.add(seg.getAbsolutePath());
            }
            File listFile = tmpDir.resolve("list.txt").toFile();
            try (PrintWriter writer = new PrintWriter(listFile)){
                for(String p : segmentPaths) writer.println("file '"+p.replace("'", "\\'") + "'");
            }
            File output = tmpDir.resolve("output.mp4").toFile();
            new ProcessBuilder(
                "ffmpeg", "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", listFile.getAbsolutePath(),
                "-c", "copy",
                output.getAbsolutePath()
            ).inheritIO().start().waitFor();
            return uploadProcessed(userId, output, "processed-" + video.getFileName()); 

        } catch (IOException e) {
            logger.error("IO error while processing video: {}", video.getFileName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        } catch (Exception e) {
            logger.error("Error while processing video: {}", video.getFileName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private String normalizeTime(String time){
        String[] segs = time.split(":");
        try{
            if(segs.length == 2){
                int m = Integer.parseInt(segs[0]);
                int s = Integer.parseInt(segs[1]);
                return String.format("%02d:%02d", m, s);
            } else if(segs.length == 3){
                int h = Integer.parseInt(segs[0]);
                int m = Integer.parseInt(segs[1]);
                int s = Integer.parseInt(segs[2]);
                return String.format("%02d:%02d:%02d", h, m, s);
            }
        } catch(NumberFormatException e){
            logger.warn("Invalid time format: {}", time);
        }
        return time;
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

    private ResponseEntity<Processed> uploadProcessed(
        String userId,
        File fileData,
        String fileName) {
        
        logger.info("Uploading processed video '{}' for user: {}", fileName, userId);
        ensureUserFolderExists(userId, "processed");

        String encodedFileName = UriUtils.encodePath(fileName, StandardCharsets.UTF_8);
        String storagePath = "processed/" + userId + "/" + encodedFileName;
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + storagePath;

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<ByteArrayResource> uploadEntity;
        try {
            ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(fileData.toPath())) {
                @Override public String getFilename() { return fileName; }
            };
            uploadEntity = new HttpEntity<>(resource, headers);

            ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.PUT, uploadEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String fileUrl = supabaseUrl + "/storage/v1/object/public/" + storagePath;
                Processed saving = new Processed(userId, fileName, fileUrl);
                processedRepository.save(saving);
                logger.info("Processed video uploaded successfully: {}", fileUrl);
                return ResponseEntity.ok(saving);
            } else {
                logger.error("Error uploading processed video '{}', Status: {}, Response: {}", fileName, response.getStatusCode(), response.getBody());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
        } catch (Exception e) {
            logger.error("Error uploading video '{}'", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
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
