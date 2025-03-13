package com.recapgrid.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import com.recapgrid.model.ClerkUser;
import com.recapgrid.model.UserEntity;
import com.recapgrid.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserRepository repository;

    @GetMapping("/health")
    public String healthCheck() {
        return "Backend is running!";
    }

    @PostMapping("/clerk-user")
    public ResponseEntity<String> createClerkUser(@RequestBody ClerkUser clerkUser) {
        if (clerkUser == null || clerkUser.getData() == null) {
            return ResponseEntity.badRequest().body("Error: ClerkUser data is missing!");
        }

        Optional<UserEntity> existingUser = repository.findById(clerkUser.getData().getId());
        if (existingUser.isPresent()) {
            return updateUser(existingUser.get(), clerkUser);
        } else {
            return saveNewUser(clerkUser);
        }
    }

    @PostMapping("/delete-user")
    public ResponseEntity<String> deleteClerkUser(@RequestBody ClerkUser clerkUser) {
        if (clerkUser == null || clerkUser.getData() == null) {
            return ResponseEntity.badRequest().body("Error: ClerkUser data is missing!");
        }
        Optional<UserEntity> existingUser = repository.findById(clerkUser.getData().getId());
        if (existingUser.isPresent()) {
            repository.delete(existingUser.get());
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
        repository.save(entity);
        return ResponseEntity.ok("User updated: " + entity.getFullName());
    }

    private ResponseEntity<String> saveNewUser(ClerkUser clerkUser) {
        UserEntity entity = new UserEntity();
        entity.setId(clerkUser.getData().getId());
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setFullName(clerkUser.getData().getFirstName() + " " + clerkUser.getData().getLastName());
        entity.setProfilePicture(clerkUser.getData().getImageUrl());
        entity.setEmail(clerkUser.getData().getEmailAddresses().isEmpty() ? null : clerkUser.getData().getEmailAddresses().get(0).getEmailAddress());
        repository.save(entity);
        return ResponseEntity.ok("User saved: " + entity.getFullName());
    }
}
