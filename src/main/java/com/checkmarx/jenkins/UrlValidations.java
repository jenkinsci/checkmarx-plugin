package com.checkmarx.jenkins;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by tsahib on 7/5/2016.
 *
 */
public class UrlValidations {

    public boolean urlHasPaths(String spec) throws MalformedURLException {
        URL url = new URL(spec);
        return url.getPath().length() > 0;
    }
}
