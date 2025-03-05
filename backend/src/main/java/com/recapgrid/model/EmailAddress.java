package com.recapgrid.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EmailAddress {
    @JsonProperty("email_address")
    private String emailAddress;
    
    private String id;
    
    private Verification verification;

    public String getEmailAddress() {
        return emailAddress;
    }
    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }
    
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    
    public Verification getVerification() {
        return verification;
    }
    public void setVerification(Verification verification) {
        this.verification = verification;
    }
}
