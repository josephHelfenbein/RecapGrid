package com.recapgrid.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClerkUser {
    @JsonProperty("data")
    private Data data;

    public Data getData() {
        return data;
    }
    public void setData(Data data) {
        this.data = data;
    }
}