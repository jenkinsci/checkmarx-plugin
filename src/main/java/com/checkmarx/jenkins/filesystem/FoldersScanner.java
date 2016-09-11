package com.checkmarx.jenkins.filesystem;

import com.checkmarx.jenkins.opensourceanalysis.DependencyInfo;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author tsahi
 * @since 02/02/16
 */
public class FoldersScanner implements FilePath.FileCallable<Collection<DependencyInfo>> {
    private List<String> libIncludes;
    private List<String> libExcludes;
    private Collection<DependencyInfo> dependencies;

    public FoldersScanner(List<String> libIncludes, List<String> libExcludes) {
        this.libIncludes = libIncludes;
        this.libExcludes = libExcludes;
        dependencies = new ArrayList<>();
    }

    @Override
    public Collection<DependencyInfo> invoke(File f, VirtualChannel channel)
            throws IOException, InterruptedException {

        String includes = StringUtils.join(libIncludes, ",");
        String excludes = StringUtils.join(libExcludes, ",");
        FilePath[] libraries = new FilePath(f).list(includes, excludes);
        for (FilePath file : libraries) {
            dependencies.add(collectDependencyInfo(file));
        }
        return dependencies;
    }

    private DependencyInfo collectDependencyInfo(FilePath filePath) {
        DependencyInfo info = new DependencyInfo();
        info.setFilePath(filePath);
        return info;
    }
}
