package org.apache.brooklyn.windows.pywinrm;

public interface ResponseType {
    String getStdOut();
    String getStdErr();
    int getStatusCode();
}
