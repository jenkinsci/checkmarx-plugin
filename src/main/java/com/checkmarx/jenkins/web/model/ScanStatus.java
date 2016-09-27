package com.checkmarx.jenkins.web.model;

/**
 * Created by tsahib on 9/27/2016.
 */
public enum ScanStatus {
    NONE(0), InProgress(1), Finished(2), Failed(3);

    private int value;

    private ScanStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ScanStatus fromId(int id){
        for (ScanStatus type : ScanStatus.values()) {
            if (type.getValue() == id) {
                return type;
            }
        }
        return null;
    }
}
