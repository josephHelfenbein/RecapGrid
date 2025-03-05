package com.recapgrid.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ClerkUser {
    private String id;
    
    @JsonProperty("first_name")
    private String firstName;
    
    @JsonProperty("last_name")
    private String lastName;
    
    @JsonProperty("email_addresses")
    private List<EmailAddress> emailAddresses;
    
    @JsonProperty("external_accounts")
    private List<ExternalAccount> externalAccounts;
    
    @JsonProperty("image_url")
    private String imageUrl;
    
    @JsonProperty("last_active_at")
    private long lastActiveAt;
    
    @JsonProperty("last_sign_in_at")
    private Long lastSignInAt;
    
    private boolean locked;
    private String username;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    
    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public List<EmailAddress> getEmailAddresses() {
        return emailAddresses;
    }
    public void setEmailAddresses(List<EmailAddress> emailAddresses) {
        this.emailAddresses = emailAddresses;
    }
    
    public List<ExternalAccount> getExternalAccounts() {
        return externalAccounts;
    }
    public void setExternalAccounts(List<ExternalAccount> externalAccounts) {
        this.externalAccounts = externalAccounts;
    }
    
    public String getImageUrl() {
        return imageUrl;
    }
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
    
    public long getLastActiveAt() {
        return lastActiveAt;
    }
    public void setLastActiveAt(long lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
    
    public Long getLastSignInAt() {
        return lastSignInAt;
    }
    public void setLastSignInAt(Long lastSignInAt) {
        this.lastSignInAt = lastSignInAt;
    }
    
    public boolean isLocked() {
        return locked;
    }
    public void setLocked(boolean locked) {
        this.locked = locked;
    }
    
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
}
