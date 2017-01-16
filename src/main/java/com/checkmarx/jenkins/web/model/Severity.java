package com.checkmarx.jenkins.web.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by zoharby on 09/01/2017.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Severity {
    @JsonProperty("id")
    Integer Id;
    @JsonProperty("name")
    String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getId() {
        return Id;
    }

    public void setId(Integer id) {
        Id = id;
    }
}
