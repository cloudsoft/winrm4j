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

    public void setNumberOfReceiveCalls(int numberOfReceiveCalls) {
        this.numberOfReceiveCalls = numberOfReceiveCalls;
    }

    public int getNumberOfReceiveCalls() {
        return numberOfReceiveCalls;
    }
}
