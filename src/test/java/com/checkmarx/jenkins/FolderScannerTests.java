package com.checkmarx.jenkins;

import com.checkmarx.jenkins.opensourceanalysis.DependencyInfo;
import com.checkmarx.jenkins.filesystem.FoldersScanner;
import hudson.FilePath;
import mockit.Mock;
import mockit.MockUp;
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author tsahi
 * @since 02/02/16
 */
@RunWith(JMockit.class)
public class FolderScannerTests {

    @Test
    public void folderScanner_noFiles_collectionShouldBeEmpty() throws IOException, InterruptedException {

        new MockUp<FilePath>() {
            @Mock
            void $init(File file) { }

            @Mock
            FilePath[] list(final String includes, final String excludes) throws IOException, InterruptedException {
                return new FilePath[0];
            }
        };

        FoldersScanner foldersScanner = new FoldersScanner(null, null);
        Collection<DependencyInfo> collection = foldersScanner.invoke(null, null);
        assertTrue(collection.isEmpty());
    }

    @Test
    public void folderScanner_oneFile_collectionShouldBeOne() throws IOException, InterruptedException {

        DependencyInfo info = new DependencyInfo();
        final FilePath testFilePath = new FilePath(new File("test"));
        info.setFilePath(testFilePath);

        new MockUp<FilePath>() {
            @Mock
            void $init(File file) { }

            @Mock
            FilePath[] list(final String includes, final String excludes) throws IOException, InterruptedException {
                return new FilePath[]{testFilePath};
            }
        };

        FoldersScanner foldersScanner = new FoldersScanner(null, null);
        Collection<DependencyInfo> collection = foldersScanner.invoke(null, null);
        assertEquals(1, collection.size());
        assertTrue(collection.contains(info));
    }


}
