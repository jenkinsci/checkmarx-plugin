package com.checkmarx.jenkins.web.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by: dorg.
 * Date: 28/01/2018.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class State {

    @JsonProperty("id")
    Integer id;

    @JsonProperty("actionType")
    String actionType;

    @JsonProperty("name")
    String name;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}