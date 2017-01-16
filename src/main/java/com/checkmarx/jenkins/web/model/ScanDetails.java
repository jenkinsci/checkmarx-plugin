package com.checkmarx.jenkins.web.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by zoharby on 09/01/2017.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanDetails {

    @JsonProperty("id")
    private String id;//"cc5ce4a8-daeb-43d3-a594-83ace96df8e2",
    @JsonProperty("startAnalyzeTime")
    private String startAnalyzeTime;//:"2016-12-19T10:16:06.1196743Z",
    @JsonProperty("endAnalyzeTime")
    private String endAnalyzeTime;//:"2016-12-19T10:29:06.1196743Z",
    @JsonProperty("origin")
    private String origin;//:"Eclipse",
    @JsonProperty("state")
    private State state;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStartAnalyzeTime() {
        return startAnalyzeTime;
    }

    public void setStartAnalyzeTime(String startAnalyzeTime) {
        this.startAnalyzeTime = startAnalyzeTime;
    }

    public String getEndAnalyzeTime() {
        return endAnalyzeTime;
    }

    public void setEndAnalyzeTime(String endAnalyzeTime) {
        this.endAnalyzeTime = endAnalyzeTime;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public static class State{
        @JsonProperty("id")
        int id;
        @JsonProperty("name")
        String name;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
