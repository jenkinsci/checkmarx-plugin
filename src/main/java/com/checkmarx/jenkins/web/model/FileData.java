package com.checkmarx.jenkins.web.model;

/**
 * Created by ehuds.
 * Date: 3/24/2016.
 */
public class FileData {
    public String hashedValue;
    public String fullName;

    public FileData(String hashedValue, String fullName) {
        this.hashedValue = hashedValue;
        this.fullName = fullName;
    }
}
