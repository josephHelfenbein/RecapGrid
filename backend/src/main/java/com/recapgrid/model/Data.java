package com.recapgrid.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class Data {
    private String id;
    
    @JsonProperty("first_name")
    private String firstName;
    
    @JsonProperty("last_name")
    private String lastName;
    
    @JsonProperty("email_addresses")
    private List<EmailAddress> emailAddresses = new ArrayList<>();
    
    @JsonProperty("image_url")
    private String imageUrl;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public List<EmailAddress> getEmailAddresses() { return emailAddresses; }
    public void setEmailAddresses(List<EmailAddress> emailAddresses) { 
        this.emailAddresses = emailAddresses != null ? emailAddresses : new ArrayList<>(); 
    }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
