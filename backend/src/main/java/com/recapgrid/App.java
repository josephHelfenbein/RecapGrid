package com.recapgrid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.recapgrid.model.ClerkUser;
import com.recapgrid.model.UserEntity;
import com.recapgrid.repository.UserRepository; 

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

}