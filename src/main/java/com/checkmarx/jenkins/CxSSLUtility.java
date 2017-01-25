package com.checkmarx.jenkins;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Allows skipping SSL certificate validation
 *
 * @author denis
 * @since 23/3/14
 */
public class CxSSLUtility {

    private static final Logger logger = Logger.getLogger(CxSSLUtility.class);

    @Nullable
    private static HostnameVerifier originalHostnameVerifier = null;
    @Nullable
    private static SSLSocketFactory originalSSLSocketFactory = null;

    /*
     * Prevents instantiation
     */
    private CxSSLUtility() {

    }

    public static void enableSSLCertificateVerification() {
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(SSLContext.getDefault().getSocketFactory());
        } catch (NoSuchAlgorithmException e) {
            logger.error(e);
        }

        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return false;
            }
        });
    }


    public static void disableSSLCertificateVerification() {
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        });

        trustAllCertificates();
    }

    private static void trustAllCertificates() {
        // Create a trust manager that does not validate certificate chains
        final TrustManager[] trustManagers = new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};


        // Install the fake trust manager
        try {
            final SSLContext context = SSLContext.getInstance("TLSv1");
            context.init(null, trustManagers, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        } catch (KeyManagementException e) {
            // In case of exception, do not install fake trust manager
            logger.warn("Failed to disable SSL/TLS certificate validation", e);
        } catch (NoSuchAlgorithmException e) {
            // In case of exception, do not install fake trust manager
            logger.warn("Failed to disable SSL/TLS certificate validation", e);
        }
    }
}
