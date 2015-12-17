package io.cloudsoft.winrm4j.winrm;

import java.util.List;

import io.cloudsoft.winrm4j.pywinrm.Response;
import io.cloudsoft.winrm4j.pywinrm.Session;
import io.cloudsoft.winrm4j.pywinrm.WinRMFactory;

/**
 * Tool for executing commands over WinRM.
 * 
 * It is the responsibility of the caller to retry on failure. This is strongly recommended,
 * because we regularly see temporary problems like:
 * <p>
 * {@code winrm.exceptions.WinRMTransportError: 500 WinRMTransport. [Errno -1] Unmapped exception: org.python.netty.channel.ConnectTimeoutException: connection timed out: /54.188.91.99:5985}
 */
public class WinRmTool {
    private final String address;
    private final String username;
    private final String password;
    private Session session;

    public static WinRmTool connect(String address, String username, String password) {
        WinRmTool tool = new WinRmTool(address, username, password);
        return tool;
    }

    private WinRmTool(String address, String username, String password) {
        this.address = address;
        this.username = username;
        this.password = password;
        session = WinRMFactory.INSTANCE.createSession(address, username, password);
    }

    public WinRmToolResponse executeScript(List<String> commands) {
        Response response = session.run_cmd(compileScript(commands));
        return responseToWinRmToolResponse(response);
    }

    public WinRmToolResponse executePs(List<String> commands) {
        Response response = session.run_ps(compileScript(commands));
        return responseToWinRmToolResponse(response);
    }

    private String compileScript(List<String> commands) {
        StringBuilder builder = new StringBuilder();
        for (String command : commands) {
            builder.append(command)
                .append("\r\n");

        }
        return builder.toString();
    }

    private WinRmToolResponse responseToWinRmToolResponse(Response response) {
        return new WinRmToolResponse(response.getStdOut(), response.getStdErr(), response.getStatusCode());
    }
}
