package io.cloudsoft.winrm4j.winrm;

import io.cloudsoft.winrm4j.pywinrm.Response;

public class WinRmToolResponse implements Response {
    private final String stdout;
    private final String stderr;
    private final int statusCode;

    public WinRmToolResponse(String stdout, String stderr, int statusCode) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.statusCode = statusCode;
    }

    @Override
    public String getStdOut() {
        return stdout;
    }

    @Override
    public String getStdErr() {
        return stderr;
    }

    @Override
    public int getStatusCode() {
        return statusCode;
    }
}
