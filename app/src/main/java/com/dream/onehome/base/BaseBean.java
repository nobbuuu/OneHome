package com.dream.onehome.base;

import java.io.Serializable;

/**
 * Created by Administrator on 2017/12/13 0013.
 */

public class BaseBean<T> implements Serializable {
    private String errorCode;
    private String errorMsg;
    private boolean success;
    private T results;

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getResults() {
        return results;
    }

    public void setResults(T results) {
        this.results = results;
    }
}
