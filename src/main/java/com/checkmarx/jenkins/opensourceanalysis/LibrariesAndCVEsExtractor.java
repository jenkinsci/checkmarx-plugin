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

    public void getAndSetLibrariesAndCVEsToScanResult(OsaScanResult osaScanResult){
        List<Library> libraryList = osaScanClient.getScanResultLibraries(osaScanResult.getScanId());
        setLibrariesJson(libraryList, osaScanResult);

        List<CVE> cveList = osaScanClient.getScanResultCVEs(osaScanResult.getScanId());
        prepareAndSetCVEsObjects(cveList, libraryList, osaScanResult);
    }

    private void setLibrariesJson(List<Library> libraryList, OsaScanResult osaScanResult){
        String libraryListJson = turnListToJSON(libraryList);
        osaScanResult.setOsaFullLibraryList(libraryListJson);
    }

    private String turnListToJSON(List<?> list){
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(list);

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "[]";
    }

    private void prepareAndSetCVEsObjects(List<CVE> cveList, List<Library> libraryList, OsaScanResult osaScanResult){
        setCVEsLibraryName(cveList, libraryList, osaScanResult);
        formatCVEsDates(cveList);
        getAndSetCVEJsonByVulnerability(cveList, osaScanResult);
    }

    private void setCVEsLibraryName(List<CVE> cveList, List<Library> libraryList, OsaScanResult osaScanResult){
        for(CVE cve:cveList){
            String libraryName = getLibraryNameFromList(cve.getLibraryId(),libraryList);
            cve.setLibraryName(libraryName);
        }
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
    private void formatCVEsDates(List<CVE> cveList){
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
            String highJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(high);
            osaScanResult.setHighCvesList(highJson);
            String mediumJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(medium);
            osaScanResult.setMediumCvesList(mediumJson);
            String lowJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(low);
            osaScanResult.setLowCvesList(lowJson);
            osaScanResult.setOsaFullCVEsList("{ \"High\":"+ highJson +",\n \"Medium\":"+ mediumJson +",\n \"Low\":"+ lowJson +" }");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            osaScanResult.setOsaFullCVEsList("{}");
        }

    }
}
