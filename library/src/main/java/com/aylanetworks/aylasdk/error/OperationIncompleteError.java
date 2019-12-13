package com.aylanetworks.aylasdk.error;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */
import java.util.List;

/**
 * An OperationIncompleteError is returned to the caller when a batch of requests is performed,
 * such as updating a set of schedule actions, and some of them succeed. The caller may obtain
 * the list of operations that were successful as well as the error that caused the batch
 * operation to terminate via calls to {@link #getSuccessList} and {@link #getFailedError}.
 */
public class OperationIncompleteError extends AylaError{
    List successList =null;

    public OperationIncompleteError(List successList,String detailMessage, Throwable cause) {
        super(ErrorType.OperationIncompleteError, detailMessage, cause);
        this.successList=successList;
    }
    public List getSuccessList(){
        return successList;
    }
    public Throwable getFailedError() { return this.getCause(); }
}
