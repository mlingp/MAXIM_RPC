package maxim.rpc;

import java.io.Serializable;

public class RpcResponse implements Serializable {

    private final String uuid;
    private Object returnObject;
    private String returnType;
    private String exception;


    public RpcResponse(String uuid) {
        this.uuid = uuid;
    }

    public Object getReturnObject() {
        return returnObject;
    }

    public void setReturnObject(Object returnObject) {
        this.returnObject = returnObject;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

    public String getUuid() {
        return uuid;
    }

}
