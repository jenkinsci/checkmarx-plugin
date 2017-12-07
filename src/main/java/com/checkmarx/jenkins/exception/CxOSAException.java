package com.checkmarx.jenkins.exception;

public class CxOSAException extends Exception {

    public CxOSAException() {
        super();
    }

    public CxOSAException(String message) {
        super(message);
    }

    public CxOSAException(String message, Throwable cause) {
        super(message, cause);
    }

    public CxOSAException(Throwable cause) {
        super(cause);
    }


}

