package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.OsaScanResult;
import com.checkmarx.jenkins.web.client.OsaScanClient;
import com.checkmarx.jenkins.web.model.CVE;
import com.checkmarx.jenkins.web.model.Library;

import java.util.List;

/**
 * Created by zoharby on 09/01/2017.
 */
public class LibrariesAndCVEsExtractor {

    private OsaScanClient osaScanClient;

    public LibrariesAndCVEsExtractor(OsaScanClient osaScanClient) {
        this.osaScanClient = osaScanClient;
    }

    public void getAndSetLibrariesAndCVEs(OsaScanResult osaScanResult){
        List<Library> libraryList = osaScanClient.getScanResultLibraries(osaScanResult.getScanId());
        osaScanResult.setOsaLibrariesList(libraryList);

        List<CVE> cveList = osaScanClient.getScanResultCVEs(osaScanResult.getScanId());
        for(CVE cve:cveList){
            Library library = findLibraryInList(cve.getLibraryId(),libraryList);
            cve.setLibrary(library);
        }
        osaScanResult.setOsaCveList(cveList);
    }

    private Library findLibraryInList(String libraryId, List<Library> libraryList){
        for (Library library:libraryList){
            if(library.getId().equals(libraryId)){
                return library;
            }
        }
        return null;
    }

}
