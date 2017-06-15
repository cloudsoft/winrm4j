package io.cloudsoft.winrm4j.winrm;

public class WinRmToolResponse {
    private final String stdout;
    private final String stderr;
    private final int statusCode;
    private int numberOfReceiveCalls;

    public WinRmToolResponse(String stdout, String stderr, int statusCode) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.statusCode = statusCode;
    }

    public String getStdOut() {
        return stdout;
    }

    public String getStdErr() {
        return stderr;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /** @deprecated since 0.6.0. Implementation detail, access will be removed in future versions */
    public void setNumberOfReceiveCalls(int numberOfReceiveCalls) {
        this.numberOfReceiveCalls = numberOfReceiveCalls;
    }

    /** @deprecated since 0.6.0. Implementation detail, access will be removed in future versions */
    @Deprecated
    public int getNumberOfReceiveCalls() {
        return numberOfReceiveCalls;
    }
}
