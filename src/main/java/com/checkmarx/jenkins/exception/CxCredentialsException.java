package com.checkmarx.jenkins.exception;

import com.cx.restclient.exception.CxClientException;

/**
 * Created by shaulv on 6/20/2018.
 */
public class CxCredentialsException extends CxClientException {

    public CxCredentialsException() {
        super();
    }

    public CxCredentialsException(String message) {
        super(message);
    }

    public CxCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }

    public CxCredentialsException(Throwable cause) {
        super(cause);
    }
}
