package com.recapgrid.controller;

import com.recapgrid.model.Video;
import com.recapgrid.repository.VideoRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    @Autowired
    private VideoRepository videoRepository;
    private RestTemplate restTemplate = new RestTemplate();

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

