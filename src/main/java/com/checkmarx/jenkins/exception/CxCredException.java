package com.checkmarx.jenkins.exception;

import com.cx.restclient.exception.CxClientException;

/**
 * Created by shaulv on 6/20/2018.
 */
public class CxCredException extends CxClientException {

    public CxCredException() {
        super();
    }

    public CxCredException(String message) {
        super(message);
    }

    public CxCredException(String message, Throwable cause) {
        super(message, cause);
    }

    public CxCredException(Throwable cause) {
        super(cause);
    }
}
