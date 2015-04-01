package io.cloudsoft.winrm4j.pywinrm;

public interface ResponseType {
    String getStdOut();
    String getStdErr();
    int getStatusCode();
}
