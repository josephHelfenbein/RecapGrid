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
import java.util.Comparator;

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
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;

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
        Path gcsTempDir = Paths.get("/mnt/gcs", "temp", userId, UUID.randomUUID().toString());
        ResponseEntity<Processed> result;
        try {
            Files.createDirectories(gcsTempDir);
            logger.info("Created temporary directory: {}", gcsTempDir.toAbsolutePath());
            Path originalPath = gcsTempDir.resolve("orig-" + UUID.randomUUID() + ".mp4");
            logger.info("Temporary original video path: {}", originalPath.toAbsolutePath());

            logger.info("Downloading video to: {}", originalPath);
            try (InputStream in = new URL(video.getFileUrl()).openStream();
                OutputStream out = Files.newOutputStream(originalPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
            logger.info("Saved original video to GCS temp: {}", originalPath);

            String base64Video = base64EncodeFile(originalPath);
            logger.info("Encoded video to Base64");
            if (base64Video == null) {
                logger.error("Failed to encode video to Base64.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Summarize this video for an editor.\n")
                        .append("1. First, list important timestamps where meaningful events happen (format: [start-end]).\n");

            if (!voice.equalsIgnoreCase("none")) {
                promptBuilder.append("2. Then, write a short narration in a ")
                            .append(feel.toLowerCase()).append(" tone ")
                            .append("to accompany these clips, spoken in a ")
                            .append(voice.toLowerCase()).append(" voice. Do not include timestamps in the narration. There should be a short narration string for each timestamp range, so the array sizes for timestamps and narration should be exactly equal.\n");
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
                                "narration", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"))
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
            
            List<Path> segmentPaths = new ArrayList<>();

            JsonNode narrationsNode = null;
            SsmlVoiceGender ssmlGender = voice.equalsIgnoreCase("female") ? SsmlVoiceGender.FEMALE : voice.equalsIgnoreCase("male") ? SsmlVoiceGender.MALE : SsmlVoiceGender.NEUTRAL;
            if(!voice.equalsIgnoreCase("none")){
                narrationsNode = structured.get(1).path("narration");
                if (!narrationsNode.isArray()) throw new IllegalStateException("Expected narration array, got: " + narrationsNode);
                if (narrationsNode.size() != timestampsNode.size()) {
                    logger.error("Mismatch between timestamps and narration sizes: {} vs {}", timestampsNode.size(), narrationsNode.size());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
                }
            }

            int i = 0;
            for(JsonNode timestampNode : timestampsNode){
                String[] parts = timestampNode.asText().split("-");
                if (parts.length != 2) {
                    logger.warn("Skipping malformed timestamp: {}", timestampNode.asText());
                    continue;
                }
                String start = normalizeTime(parts[0].trim());
                String end = normalizeTime(parts[1].trim());
                Duration dur = parseDuration(end).minus(parseDuration(start));
                Path seg = gcsTempDir.resolve("seg-" + i + ".mp4");
                logger.info("Creating segment {} -> {}", seg, timestampNode.asText());

                ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", originalPath.toString(),
                    "-ss", start,
                    "-t", String.valueOf(dur.toSeconds()),
                    "-c:v", "libx264",
                    "-preset", "fast",
                    "-crf", "23",
                    "-c:a", "aac",
                    "-b:a", "128k",
                    "-movflags", "+faststart",
                    seg.toString()
                );
                processBuilder.inheritIO();
                int code = processBuilder.start().waitFor();
                if (code != 0) {
                    logger.error("Error processing segment: {} - Code: {}", timestampNode.asText(), code);
                    continue;
                }

                if(!voice.equalsIgnoreCase("none")){
                    String narration = narrationsNode.get(i).asText();
                    logger.info("Generated narration: {}", narration);
                    try(TextToSpeechClient tts = TextToSpeechClient.create()){
                        SynthesisInput input = SynthesisInput.newBuilder().setText(narration).build();
                        VoiceSelectionParams voiceParams = VoiceSelectionParams.newBuilder().setLanguageCode("en-US").setSsmlGender(ssmlGender).build();
                        AudioConfig audioConfig = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.LINEAR16).setSampleRateHertz(44100).setSpeakingRate(1.5).build();
    
                        SynthesizeSpeechResponse resp = tts.synthesizeSpeech(input, voiceParams, audioConfig);
                        ByteString audioBytes = resp.getAudioContent();
    
                        Path audioPath = gcsTempDir.resolve("narration" + i + ".wav");
                        Files.write(audioPath, audioBytes.toByteArray());
                        logger.info("Generated narration audio file: {}", audioPath);

                        double videoLen = dur.getSeconds();
                        AudioInputStream ais = AudioSystem.getAudioInputStream(audioPath.toFile());
                        AudioFormat fmt = ais.getFormat();
                        double audioLen = ais.getFrameLength() / fmt.getFrameRate();
                        ais.close();
                        double speedFactor = videoLen / audioLen;
                        logger.info("Speed factor: {}", speedFactor);

                        String filter = String.format(
                            "[0:v]setpts=PTS*%f[v];" +
                            "[0:a][1:a]amix=inputs=2:duration=first[a]",
                            speedFactor
                        );

                        Path finalSegment = gcsTempDir.resolve("final-seg-" + i + ".mp4");
                        ProcessBuilder voiceProcessBuilder = new ProcessBuilder(
                            "ffmpeg", "-y",
                            "-i", seg.toString(),
                            "-i", audioPath.toString(),
                            "-filter_complex", filter,
                            "-map", "[v]",
                            "-map", "[a]",
                            "-c:v", "libx264", "-preset", "fast", "-crf", "23",
                            "-c:a", "aac", "-b:a", "192k", "-ar", "44100",
                            "-movflags", "+faststart",
                            finalSegment.toString()
                        );
                        voiceProcessBuilder.inheritIO();
                        int voiceCode = voiceProcessBuilder.start().waitFor();
                        if (voiceCode != 0) {
                            logger.error("Error adding voice to segment: {} - Code: {}", timestampNode.asText(), voiceCode);
                            continue;
                        }
                        seg = finalSegment;
                    }
                }
                i++;
                segmentPaths.add(seg);
            }
            Path listFile = gcsTempDir.resolve("list.txt");
            try (PrintWriter writer = new PrintWriter(listFile.toFile())){
                for(Path p : segmentPaths) writer.println("file '" + p.toString().replace("'", "\\'") + "'");
            }
            Path output = gcsTempDir.resolve("output-" + UUID.randomUUID() + ".mp4");
            ProcessBuilder concatProcessBuilder = new ProcessBuilder(
                "ffmpeg", "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", listFile.toString(),
                "-c:v", "copy",
                "-c:a", "copy",
                "-movflags", "+faststart",
                output.toString()
            );
            concatProcessBuilder.inheritIO();
            int concatCode = concatProcessBuilder.start().waitFor();
            if (concatCode != 0) {
                logger.error("Error concatenating segments - Code: {}", concatCode);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

            result = uploadProcessed(userId, output.toFile(), "processed-" + UUID.randomUUID() + "-" + video.getFileName()); 

        } catch (IOException e) {
            logger.error("IO error while processing video: {}", video.getFileName(), e);
            result = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        } catch (Exception e) {
            logger.error("Error while processing video: {}", video.getFileName(), e);
            result = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        } finally {
            try {
                Files.walk(gcsTempDir).sorted(Comparator.reverseOrder()).forEach(p->{
                    try{
                        Files.deleteIfExists(p);
                    } catch(IOException e){
                        logger.error("Error deleting file: {}", p, e);
                    }
                });
                logger.info("Cleaned up temp directory: {}", gcsTempDir);
            } catch (IOException e){
                logger.error("Error cleaning up temp directory: {}", gcsTempDir, e);
            }
        }
        return result;
    }

    private String base64EncodeFile(Path path) throws IOException{
        try(ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OutputStream b64Out = Base64.getEncoder().wrap(baos);
            InputStream in = Files.newInputStream(path, StandardOpenOption.READ)){
                byte[] buffer = new byte[8 * 1024];
                int len;
                while((len = in.read(buffer)) != -1) b64Out.write(buffer, 0, len);
                b64Out.close();
                return baos.toString(StandardCharsets.UTF_8);
            }
    }

    private Duration parseDuration(String timestamp) {
        String[] parts = timestamp.split(":");
        try{
            if (parts.length == 2) {
                long m = Long.parseLong(parts[0]);
                long s = Long.parseLong(parts[1]);
                return Duration.ofMinutes(m).plusSeconds(s);
            } else if (parts.length == 3) {
                long h = Long.parseLong(parts[0]);
                long m = Long.parseLong(parts[1]);
                long s = Long.parseLong(parts[2]);
                return Duration.ofHours(h).plusMinutes(m).plusSeconds(s);
            }
        } catch(NumberFormatException e) {
            logger.warn("Invalid time format: {}", timestamp);
        }
        return Duration.ZERO;
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

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(fileData.toPath());
        } catch (IOException e) {
            logger.error("Failed to read processed file bytes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }

        HttpHeaders headers = createHeaders();
        String ct = null;
        try {
            ct = Files.probeContentType(fileData.toPath());
        } catch (IOException ignored) { }
        headers.set("Content-Type", ct != null ? ct : MediaType.APPLICATION_OCTET_STREAM_VALUE);

        HttpEntity<byte[]> uploadEntity = new HttpEntity<>(bytes, headers);
        try {
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
