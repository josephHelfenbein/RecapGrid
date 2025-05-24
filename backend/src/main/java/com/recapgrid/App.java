package com.recapgrid;

import com.recapgrid.model.Video;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.recapgrid.model.ClerkUser;
import com.recapgrid.model.Processed;
import com.recapgrid.model.StatusEntity;
import com.recapgrid.model.UserEntity;
import com.recapgrid.repository.VideoRepository;
import com.recapgrid.repository.ProcessedRepository;
import com.recapgrid.repository.StatusRepository;
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
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.google.api.Http;
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

    @Autowired
    private StatusRepository statusRepository;

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
        String errorMessage = "";
        ResponseEntity<Processed> result;
        try {
            updateInfo(userId, "Creating temp directory...", "Processing video...");

            Files.createDirectories(gcsTempDir);
            logger.info("Created temporary directory: {}", gcsTempDir.toAbsolutePath());
            Path originalPath = gcsTempDir.resolve("orig-" + UUID.randomUUID() + ".mp4");
            logger.info("Temporary original video path: {}", originalPath.toAbsolutePath());

            logger.info("Downloading video to: {}", originalPath);
            updateInfo(userId, "Downloading video to temp storage...", "Processing video...");
            try (InputStream in = new URL(video.getFileUrl()).openStream();
                OutputStream out = Files.newOutputStream(originalPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
            logger.info("Saved original video to GCS temp: {}", originalPath);

            updateInfo(userId, "Sending video to Gemini...", "Processing video...");

            String geminiUploadPath = uploadToGemini(video, originalPath);

            updateInfo(userId, "Generating video summary...", "Processing video...");

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("You are a video‐editing assistant that produces both key clip timestamps and matching narration in JSON form.\n")
                .append("1. First, list exactly 5 (up to 6) meaningful timestamp ranges where the video’s most important events happen, formatted as [\"M:SS–M:SS\"]. ")
                .append("Distribute these ranges evenly from beginning to end—cover intro, build-up, midpoint twist, climax, and conclusion. ")
                .append("Do not cluster them all at the start.\n")
                .append("2. Then, write one short narration line for each timestamp range (so narration array length = timestamps array length. It MUST be equal, PAY ATTENTION to this). ")
                .append("Each line must be 1–2 sentences, focused strictly on what happens inside that clip. Make sure the narrations also have a build-up, twist, and conclusion. ");

            if (feel.equalsIgnoreCase("funny")) 
                promptBuilder.append("Use genuine humor—craft witty punchlines, playful contrasts, or unexpected twists that earn a real laugh (not just “LOL”). Use modern slang and random funny comments.\n");
            else if (feel.equalsIgnoreCase("cinematic")) 
                promptBuilder.append("Adopt a movie-trailer style—build suspense, use dramatic pacing, teaser phrases like “coming up,” and evoke big-screen excitement.\n");
            else if (feel.equalsIgnoreCase("informational")) 
                promptBuilder.append("Adopt an educational tone—clearly explain or teach a key takeaway or concept, turning each clip into a short mini-lesson.\n");
            else promptBuilder.append("Blend humor, cinematic flair, and clear teaching points.\n");
            
            if (!voice.equalsIgnoreCase("none")) 
                promptBuilder.append("3. Speak each narration in a ").append(voice.toLowerCase())
                            .append(" voice. Do NOT include timestamps inside the narration text.\n");
            else promptBuilder.append("3. Do not include any voice/style directive; just return the narration text.\n");
            
            promptBuilder.append("4. Return ONLY the following JSON object (no extra commentary):\n")
                .append("{\n")
                .append("  \"timestamps\": [\"0:00-0:05\", \"0:10-0:15\", \"0:20-0:25\", \"0:30-0:35\", \"0:40-0:45\"],\n")
                .append("  \"narrations\": [\n")
                .append("    \"First, we dive in with a bang—watch how this simple test changes everything.\",\n")
                .append("    \"Now, the plot twists: you won’t believe the hack that makes it 10× faster.\",\n")
                .append("    \"At the midway mark, an epic showdown of technique versus tool ensues.\",\n")
                .append("    \"Here’s the climax—when theory meets practice in a dazzling display.\",\n")
                .append("    \"Finally, we wrap up with the key takeaway that’ll level up your workflow.\"\n")
                .append("  ]\n")
                .append("}");

            ObjectMapper generateMapper = new ObjectMapper();
            Map<String,Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                    "timestamps", Map.of(
                    "type",  "array",
                    "items", Map.of("type","string")
                    ),
                    "narrations", Map.of(
                    "type",  "array",
                    "items", Map.of("type","string")
                    )
                ),
                "required", List.of("timestamps","narrations")
            );

            Map<String,Object> generationConfig = Map.of(
                "response_mime_type", "application/json",
                "response_schema",   schema
            );

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + geminiKey;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> contents = Map.of(
                "parts", List.of(
                    Map.of("text", promptBuilder.toString()),
                    Map.of("file_data", Map.of(
                        "mime_type", "video/mp4",
                        "file_uri", geminiUploadPath
                    ))
                )
            );
            Map<String, Object> requestBody = Map.of(
                "contents", List.of(contents),
                "generationConfig", generationConfig
            );
            String requestJson = generateMapper.writeValueAsString(requestBody);

            HttpEntity<String> req = new HttpEntity<>(requestJson, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, req, String.class);

            updateInfo(userId, "Parsing AI response...", "Processing video...");

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("Error processing video: {} - Status: {}, Response: {}", video.getFileName(), response.getStatusCode(), response.getBody());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

            String responseBody = response.getBody();
            if (responseBody == null) {
                logger.error("Empty response from Gemini.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
            logger.info("Response body: {}", responseBody);

            ObjectMapper readMapper = new ObjectMapper();
            JsonNode root = readMapper.readTree(responseBody);

            JsonNode partsNode = root.path("candidates").get(0).path("content").path("parts");
            String textContent = partsNode.get(0).path("text").asText();

            JsonNode structured = readMapper.readTree(textContent);

            JsonNode timestampsNode = structured.path("timestamps");
            
            if (!timestampsNode.isArray()) throw new IllegalStateException("Expected timestamps array, got: " + timestampsNode);
            
            List<Path> segmentPaths = new ArrayList<>();

            JsonNode narrationsNode = null;
            SsmlVoiceGender ssmlGender = voice.equalsIgnoreCase("female") ? SsmlVoiceGender.FEMALE : voice.equalsIgnoreCase("male") ? SsmlVoiceGender.MALE : SsmlVoiceGender.NEUTRAL;
            if(!voice.equalsIgnoreCase("none")){
                narrationsNode = structured.path("narrations");
                if (!narrationsNode.isArray()) throw new IllegalStateException("Expected narration array, got: " + narrationsNode);
                if (narrationsNode.size() != timestampsNode.size()) {
                    logger.error("Mismatch between timestamps and narration sizes: {} vs {}", timestampsNode.size(), narrationsNode.size());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
                }
            }

            ArrayList<String> newTimestamps = new ArrayList<>();

            for(int i = 0; i<timestampsNode.size(); i++){
                updateInfo(userId, "Processing segment " + (i+1) + " of " + timestampsNode.size(), "Processing video...");
                JsonNode timestampNode = timestampsNode.get(i);
                String[] parts = timestampNode.asText().split("-");
                if (parts.length != 2) {
                    logger.warn("Skipping malformed timestamp: {}", timestampNode.asText());
                    continue;
                }
                String start = normalizeFfmpegTime(parts[0].trim());
                String end = normalizeFfmpegTime(parts[1].trim());
                Duration dur = parseDuration(end).minus(parseDuration(start));
                Path seg = gcsTempDir.resolve("seg-" + i + ".mp4");
                logger.info("Creating segment {} -> {}", seg, timestampNode.asText());

                ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", originalPath.toString(),
                    "-ss", start,
                    "-t", String.valueOf(dur.toSeconds()),
                    "-c:v", "libx264",
                    "-preset", "ultrafast",
                    "-crf", "30",
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
                    updateInfo(userId, "Generating narration for segment " + (i+1) + " of " + timestampsNode.size(), "Processing video...");
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
                        double speedFactor = audioLen / videoLen;
                        logger.info("Speed factor: {}", speedFactor);

                        if(newTimestamps.size()==0) newTimestamps.add(toTimestamp("0:00", audioLen));
                        else newTimestamps.add(toTimestamp(newTimestamps.get(newTimestamps.size()-1).split("-")[1].trim(), audioLen));

                        String filter = String.format(
                            "[0:v]setpts=PTS*%f[v]", 
                            speedFactor
                        );
                        
                        updateInfo(userId, "Adding narration to segment " + (i+1) + " of " + timestampsNode.size(), "Processing video...");
                        Path finalSegment = gcsTempDir.resolve("final-seg-" + i + ".mp4");
                        ProcessBuilder voiceProcessBuilder = new ProcessBuilder(
                            "ffmpeg", "-y",
                            "-i", seg.toString(),
                            "-i", audioPath.toString(),
                            "-filter_complex", filter,
                            "-map", "[v]",
                            "-map", "1:a",
                            "-c:v", "libx264", "-preset", "ultrafast", "-crf", "30",
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
                else newTimestamps.add(timestampNode.asText());
                segmentPaths.add(seg);
            }
            updateInfo(userId, "Concatenating segments...", "Processing video...");
            Path listFile = gcsTempDir.resolve("list.txt");
            try (PrintWriter writer = new PrintWriter(listFile.toFile())){
                for(Path p : segmentPaths) writer.println("file '" + p.toString().replace("'", "\\'") + "'");
            }
            Path output = gcsTempDir.resolve("output-" + UUID.randomUUID() + ".mp4");
            if(!voice.equalsIgnoreCase("none")){
                Path srt = gcsTempDir.resolve("captions.srt");
                try(BufferedWriter w = Files.newBufferedWriter(srt, StandardCharsets.UTF_8)){
                    for(int i=0; i<newTimestamps.size(); i++){
                        String timestamp = newTimestamps.get(i);
                        String start = normalizeSrtTime(timestamp.split("-")[0].trim());
                        String end = normalizeSrtTime(timestamp.split("-")[1].trim());
                        String narration = narrationsNode.get(i).asText();
                        w.write(Integer.toString(i+1)); w.newLine();
                        w.write(start + " --> " + end); w.newLine();
                        w.write(narration); w.newLine();
                        w.newLine();
                    }
                }
                ProcessBuilder concatProcessBuilder = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-f", "concat",
                    "-safe", "0",
                    "-i", listFile.toString(),
                    "-vf", "subtitles=" + srt.toString().replace(":", "\\:"),
                    "-c:v", "libx264",
                    "-preset", "fast",
                    "-crf", "23",
                    "-c:a", "aac",
                    "-b:a", "192k",
                    "-movflags", "+faststart",
                    output.toString()
                );
                concatProcessBuilder.inheritIO();
                int concatCode = concatProcessBuilder.start().waitFor();
                if (concatCode != 0) {
                    logger.error("Error concatenating segments - Code: {}", concatCode);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
                }
            }
            else{
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
            }
            updateInfo(userId, "Uploading processed video...", "Processing video...");
            result = uploadProcessed(userId, output.toFile(), "processed-" + UUID.randomUUID() + "-" + video.getFileName()); 

        } catch (IOException e) {
            logger.error("IO error while processing video: {}", video.getFileName(), e);
            result = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            errorMessage = e.getMessage();
        } catch (Exception e) {
            logger.error("Error while processing video: {}", video.getFileName(), e);
            result = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            errorMessage = e.getMessage();
        } finally {
            updateInfo(userId, "Cleaning up temp files...", "Processing video...");
            try {
                Files.walk(gcsTempDir).sorted(Comparator.reverseOrder()).forEach(p->{
                    try{
                        Files.deleteIfExists(p);
                    } catch(IOException e){
                        logger.error("Error deleting file: {}", p, e);
                    }
                });
                logger.info("Cleaned up temp directory: {}", gcsTempDir);
                if(errorMessage=="") updateInfo(userId, "", "Video processed successfully");
                else updateInfo(userId, errorMessage, "There was an error processing the video");
            } catch (IOException e){
                logger.error("Error cleaning up temp directory: {}", gcsTempDir, e);
            }
        }
        return result;
    }

    private String uploadToGemini(Video video, Path originalPath) {
        String url = "https://generativelanguage.googleapis.com/upload/v1beta/files?key=" + geminiKey;
        String mimeType = "video/mp4";
        Long fileSize = null;
        try{
            fileSize = Files.size(originalPath);
            System.out.println("File size: " + fileSize);
        } catch(IOException | UnsupportedOperationException e){
            fileSize = 0L;
            try (InputStream sizeStream = Files.newInputStream(originalPath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = sizeStream.read(buffer)) != -1) {
                    fileSize += bytesRead;
                }
            } catch (IOException ex) {
                throw new RuntimeException("Failed to determine file size", ex);
            }
        }
        String originalName = video.getFileName();
        String sanitizedDisplayName = originalName.toLowerCase()
            .replaceAll("[^a-z0-9-]", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
        String metadata = "{\"file\":{\"display_name\":\"" + sanitizedDisplayName + "\"}}";

        HttpHeaders startHeaders = new HttpHeaders();
        startHeaders.setContentType(MediaType.APPLICATION_JSON);
        startHeaders.set("X-Goog-Upload-Protocol", "resumable");
        startHeaders.set("X-Goog-Upload-Command", "start");
        startHeaders.set("X-Goog-Upload-Header-Content-Length", String.valueOf(fileSize));
        startHeaders.set("X-Goog-Upload-Header-Content-Type", mimeType);
        HttpEntity<String> startReq = new HttpEntity<>(metadata, startHeaders);
        ResponseEntity<Void> startResp = restTemplate.exchange(url, HttpMethod.POST, startReq, Void.class);

        System.out.println("Headers: " + startResp.getHeaders());
        String uploadUrl = startResp.getHeaders().getFirst("X-Goog-Upload-URL");
        if(uploadUrl == null) throw new IllegalStateException("No upload URL returned");

        RequestCallback uploadCallback = request -> {
            HttpHeaders h = request.getHeaders();
            h.set("X-Goog-Upload-Offset", "0");
            h.set("X-Goog-Upload-Command", "upload, finalize");
            h.setContentType(MediaType.parseMediaType(mimeType));
            try(InputStream in = Files.newInputStream(originalPath);
                OutputStream out = request.getBody()){
                    byte[] buf = new byte[16_384];
                    int r;
                    while((r = in.read(buf)) > 0) out.write(buf, 0, r);
            }
        };
        ResponseExtractor<String> extractor = clientResponse -> {
            try(InputStream in = clientResponse.getBody()){
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        };
        String finalizeJson = restTemplate.execute(uploadUrl, HttpMethod.PUT, uploadCallback, extractor);
        System.out.println("Finalized JSON: " + finalizeJson);
        if(finalizeJson==null) throw new IllegalStateException("No finalize JSON returned");

        try{
            JsonNode root = new ObjectMapper().readTree(finalizeJson);
            String fullName = root.path("file").path("name").asText(null);
            String fileUri = root.path("file").path("uri").asText(null);
            if(fileUri == null) throw new IllegalStateException("No file URI returned");
            waitForFileActive(fullName);
            return fileUri;
        } catch(IOException e){
            throw new RuntimeException("Failed to parse finalize JSON", e);
        } catch(Exception e){
            throw new RuntimeException("Error during upload", e);
        }
    }

    private void waitForFileActive(String fileName) throws Exception{
        ObjectMapper mapper = new ObjectMapper();
        logger.info("Waiting for file to become active: {}", fileName);
        String pollUrl = "https://generativelanguage.googleapis.com/v1beta/" + fileName + "?key=" + geminiKey;
        for(int i=0; i<50; i++){
            try {
                String body = restTemplate.getForObject(pollUrl, String.class);
                logger.info("Polled body = {}", body);
                String state = mapper.readTree(body).path("state").asText("");
                if ("ACTIVE".equalsIgnoreCase(state)) {
                    logger.info("File is now ACTIVE");
                    return;
                }
            } catch (HttpClientErrorException.NotFound nf) {
                logger.debug("File not found yet, retrying…");
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("File never became active");
    }

    private String toTimestamp(String start, double duration){
        Duration durStart = parseDuration(start);
        long addSecs = Math.round(duration);
        long addMillis = Math.round((duration - addSecs) * 1_000);
        Duration durEnd = durStart.plusSeconds(addSecs).plusMillis(addMillis);
        return formatFfmpegDuration(durStart) + "-" + formatFfmpegDuration(durEnd);
    }

    private String formatFfmpegDuration(Duration duration) {
        long ms = duration.toMillis();
        long hours = ms / 3_600_000;
        long minutes = (ms % 3_600_000) / 60_000;
        long seconds = (ms % 60_000) / 1_000;
        long millis = ms % 1_000;
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }

    private String formatSrtDuration(Duration duration) {
        long ms = duration.toMillis();
        long hours = ms / 3_600_000;
        long minutes = (ms % 3_600_000) / 60_000;
        long seconds = (ms % 60_000) / 1_000;
        long millis = ms % 1_000;
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }

    private String normalizeSrtTime(String ts){
        Duration dur = parseDuration(ts);
        return formatSrtDuration(dur);
    }
    private String normalizeFfmpegTime(String ts){
        Duration dur = parseDuration(ts);
        return formatFfmpegDuration(dur);
    }

    private Duration parseDuration(String timestamp) {
        String[] parts = timestamp.split(":");
        try{
            long h = 0, m = 0, s = 0, n = 0;
            double sDouble = 0;
            if (parts.length == 2) {
                m = Long.parseLong(parts[0]);
                sDouble = Double.parseDouble(parts[1]);
            } else if (parts.length == 3) {
                h = Long.parseLong(parts[0]);
                m = Long.parseLong(parts[1]);
                sDouble = Double.parseDouble(parts[2]);
            }
            s = (long) sDouble;
            n = (long) ((sDouble - s) * 1_000_000_000);
            return Duration.ofHours(h).plusMinutes(m).plusSeconds(s).plusNanos(n);
        } catch(NumberFormatException e) {
            logger.warn("Invalid time format: {}", timestamp);
        }
        return Duration.ZERO;
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

    private void updateInfo(String userId, String info, String stage){
        if(statusRepository.findById(userId).isPresent()){
            Optional<StatusEntity> status = statusRepository.findById(userId);
            if(status.isEmpty()) return;
            status.get().setUpdatedAt(OffsetDateTime.now());
            status.get().setInfo(info);
            status.get().setStage(stage);
            statusRepository.save(status.get());
            return;
        }
        StatusEntity status = new StatusEntity();
        status.setId(userId);
        status.setUpdatedAt(OffsetDateTime.now());
        status.setStage(stage);
        status.setInfo(info);
        statusRepository.save(status);
    }

    public static String sanitize(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String noAccents = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String safe = noAccents.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.replaceAll("_+", "_");
    }
    
    @PostMapping("/videos/upload")
    public ResponseEntity<Video> uploadVideo(
        @RequestParam("userId") String userId,
        @RequestParam("fileData") MultipartFile fileData,
        @RequestParam("fileName") String fileName) {
        
        String safeName = sanitize(fileName);
        logger.info("Uploading video '{}' with name '{}' for user: {}", fileName, safeName, userId);
        ensureUserFolderExists(userId, "videos");

        updateInfo(userId, "", "Uploading video...");

        String uploadUrl = UriComponentsBuilder
            .fromHttpUrl(supabaseUrl)
            .pathSegment("storage", "v1", "object", "videos", userId, safeName)
            .build()
            .encode()
            .toUriString();

        HttpHeaders headers = createHeaders();
        headers.set("Content-Type", fileData.getContentType());

        HttpEntity<ByteArrayResource> uploadEntity;
        try {
            ByteArrayResource resource = new ByteArrayResource(fileData.getBytes());
            uploadEntity = new HttpEntity<>(resource, headers);

            ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.PUT, uploadEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                updateInfo(userId, "", "Video uploaded successfully");
                String publicUrl = UriComponentsBuilder
                    .fromHttpUrl(supabaseUrl)
                    .pathSegment("storage", "v1", "object", "public", "videos", userId, safeName)
                    .build()
                    .encode()
                    .toUriString();
                
                Video saving = new Video(userId, safeName, publicUrl);
                videoRepository.save(saving);
                logger.info("Video uploaded successfully: {}", publicUrl);
                return ResponseEntity.ok(saving);
            } else {
                logger.error("Error uploading video '{}', Status: {}, Response: {}", safeName, response.getStatusCode(), response.getBody());
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
