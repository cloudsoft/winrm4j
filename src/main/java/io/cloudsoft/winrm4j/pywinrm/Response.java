package io.cloudsoft.winrm4j.pywinrm;

public interface Response {
    String getStdOut();
    String getStdErr();
    int getStatusCode();
}
