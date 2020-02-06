package com.checkmarx.jenkins;

import com.cx.restclient.dto.DependencyScannerType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * The format of the data used by this plugin has changed after adding SCA support.
 * This class allows to migrate the data from the old (pre-SCA) format to the current format.
 */
class PluginDataMigration {
    private final Logger log;
    private static final ObjectMapper jsonWriter = new ObjectMapper();

    PluginDataMigration(Logger log) {
        this.log = log;
    }

    /**
     * @param builder object that will be modified during migration.
     */
    void migrate(@NotNull CxScanBuilder builder) {
        if (needToMigrate(builder)) {
            // The changes below are persisted only after user explicitly saves the job.
            // As a result, this migration may occur several times (when job wasn't saved and Jenkins is restarting),
            // but that's OK.
            log.debug(this.getClass().getName() + ": migrating plugin data to the new format.");
            DependencyScanConfig config = extractDependencyScanConfig(builder);
            writeToLog(config);
            builder.setDependencyScanConfig(config);
        }
    }

    private boolean needToMigrate(CxScanBuilder builder) {
        // osaEnabled == true in the old format corresponds to dependencyScanConfig != null in the new format.
        // If this is not the case, a conversion is needed.
        return builder.isOsaEnabled() && builder.getDependencyScanConfig() == null;
    }

    @NotNull
    private DependencyScanConfig extractDependencyScanConfig(CxScanBuilder builder) {
        DependencyScanConfig config = new DependencyScanConfig();
        config.overrideGlobalConfig = true;
        config.dependencyScannerType = DependencyScannerType.OSA;
        config.dependencyScanPatterns = builder.getIncludeOpenSourceFolders();
        config.dependencyScanExcludeFolders = builder.getExcludeOpenSourceFolders();
        config.osaArchiveIncludePatterns = builder.getOsaArchiveIncludePatterns();
        config.osaInstallBeforeScan = builder.isOsaInstallBeforeScan();
        return config;
    }

    private void writeToLog(DependencyScanConfig config) {
        try {
            log.debug("Dependency scan config after migration: " + jsonWriter.writeValueAsString(config));
        } catch (JsonProcessingException e) {
            log.warn("Failed to convert object to JSON.");
        }
    }
}
