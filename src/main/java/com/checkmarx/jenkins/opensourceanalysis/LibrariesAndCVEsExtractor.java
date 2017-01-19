package com.checkmarx.jenkins.opensourceanalysis;

import com.checkmarx.jenkins.OsaScanResult;
import com.checkmarx.jenkins.web.client.OsaScanClient;
import com.checkmarx.jenkins.web.model.CVE;
import com.checkmarx.jenkins.web.model.Library;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedList;
import java.util.List;

/**
 *  Created by zoharby on 09/01/2017.
 */
public class LibrariesAndCVEsExtractor {

    private OsaScanClient osaScanClient;
    private ObjectMapper mapper = new ObjectMapper();

    public LibrariesAndCVEsExtractor(OsaScanClient osaScanClient) {
        this.osaScanClient = osaScanClient;
    }

    public void getAndSetLibrariesAndCVEs(OsaScanResult osaScanResult){
        getAndSetLibraries(osaScanResult);
        List<CVE> cveList = getAndSetCVEsObjects(osaScanResult);
        formatDate(cveList);
        getAndSetCVEJsonByVulnerability(cveList, osaScanResult);
    }

    private void getAndSetLibraries(OsaScanResult osaScanResult){
        List<Library> libraryList = osaScanClient.getScanResultLibraries(osaScanResult.getScanId());
        osaScanResult.setOsaLibrariesList(libraryList);
    }

    private List<CVE> getAndSetCVEsObjects(OsaScanResult osaScanResult){
        List<CVE> cveList = osaScanClient.getScanResultCVEs(osaScanResult.getScanId());
        for(CVE cve:cveList){
            String libraryName = getLibraryNameFromList(cve.getLibraryId(),osaScanResult.getOsaLibrariesList());
            cve.setLibraryName(libraryName);
        }
        return cveList;
    }

    private String getLibraryNameFromList(String libraryId, List<Library> libraryList){
        for (Library library:libraryList){
            if(library.getId().equals(libraryId)){
                return library.getName();
            }
        }
        return null;
    }

    //change time format from "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" to "dd-MM-yyyy"
    private void formatDate(List<CVE> cveList){
        for (CVE cve: cveList){
            String[] timeParts = cve.getPublishDate().split("T");
            String[] partsOfTimePart = timeParts[0].split("-");
            String metricSimpleTime = partsOfTimePart[2]+"-"+partsOfTimePart[1]+"-"+partsOfTimePart[0];
            cve.setPublishDate(metricSimpleTime);
        }
    }

    private void getAndSetCVEJsonByVulnerability(List<CVE> cveList, OsaScanResult osaScanResult) {
        List<CVE> high = new LinkedList<>();
        List<CVE> medium = new LinkedList<>();
        List<CVE> low = new LinkedList<>();

        for (CVE cve: cveList){
            switch(cve.getSeverity().getId()){
                case 2: high.add(cve);
                    continue;
                case 1: medium.add(cve);
                    continue;
                case 0: low.add(cve);
            }
        }

        try {
            String highJson = mapper.writeValueAsString(high);
            osaScanResult.setHighCvesList(highJson);
            String mediumJson = mapper.writeValueAsString(medium);
            osaScanResult.setMediumCvesList(mediumJson);
            String lowJson = mapper.writeValueAsString(low);
            osaScanResult.setLowCvesList(lowJson);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

    }
}
