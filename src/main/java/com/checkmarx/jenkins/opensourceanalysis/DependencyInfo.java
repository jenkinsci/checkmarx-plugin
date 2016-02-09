package com.checkmarx.jenkins.opensourceanalysis;

import hudson.FilePath;

import java.io.Serializable;

/**
 * @author tsahi
 * @since 02/02/16
 */
public class DependencyInfo implements Serializable {
    private FilePath filePath;

    public void setFilePath(FilePath filePath) {
        this.filePath = filePath;
    }

    public FilePath getFilePath() {
        return filePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DependencyInfo that = (DependencyInfo) o;

        return !(filePath != null ? !filePath.equals(that.filePath) : that.filePath != null);

    }

    @Override
    public int hashCode() {
        return filePath != null ? filePath.hashCode() : 0;
    }
}
