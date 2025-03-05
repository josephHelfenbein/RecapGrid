package com.recapgrid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
    public String createClerkUser(@RequestBody ClerkUser clerkUser) {
        UserEntity entity = new UserEntity();
        entity.setId(clerkUser.getId());
        entity.setFullName(clerkUser.getFirstName() + " " + clerkUser.getLastName());
        entity.setProfilePicture(clerkUser.getImageUrl());
        if(!clerkUser.getEmailAddresses().isEmpty()) {
            entity.setEmail(clerkUser.getEmailAddresses().get(0).getEmailAddress());
        } else {
            entity.setEmail(null);
        }

        repository.save(entity);
        return "User saved with name: " + entity.getFullName();
    }

}