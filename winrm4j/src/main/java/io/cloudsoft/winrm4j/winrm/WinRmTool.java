package io.cloudsoft.winrm4j.winrm;

import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.List;

import io.cloudsoft.winrm4j.client.WinRmClient;
import io.cloudsoft.winrm4j.client.WinRmClient.Builder;

/**
 * Tool for executing commands over WinRM.
 * 
 * It is the responsibility of the caller to retry on failure. This is strongly recommended,
 * because we regularly see temporary problems like:
 * <p>
 * {@code winrm.exceptions.WinRMTransportError: 500 WinRMTransport. [Errno -1] Unmapped exception: org.python.netty.channel.ConnectTimeoutException: connection timed out: /54.188.91.99:5985}
 */
public class WinRmTool {
    private String address;
    private String username;
    private String password;

    public static WinRmTool connect(String address, String username, String password) {
        return new WinRmTool(address, username, password);
    }

    private WinRmTool(String address, String username, String password) {
        this.address = address;
        this.username = username;
        this.password = password;
    }

    public WinRmToolResponse executeScript(List<String> commands) {
        return executeScript(compileScript(commands));
    }

    public WinRmToolResponse executeScript(String commands) {
        // TODO better support for address formats, host:port required
        Builder builder = WinRmClient.builder("http://" + address + "/wsman");
        if (username != null && password != null) {
            builder.credentials(username, password);
        }
        WinRmClient client = builder.build();

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        try {
            int code = client.command(commands, out, err);
            return new WinRmToolResponse(out.toString(), err.toString(), code);
        } finally {
            client.disconnect();
        }
    }

    public WinRmToolResponse executePs(List<String> commands) {
        return executeScript(compilePs(compileScript(commands)));
    }

    private String compilePs(String psScript) {
        byte[] cmd = psScript.getBytes(Charset.forName("UTF-16LE"));
        String arg = javax.xml.bind.DatatypeConverter.printBase64Binary(cmd);
        return "powershell -encodedcommand " + arg;
    }

    private String compileScript(List<String> commands) {
        StringBuilder builder = new StringBuilder();
        for (String command : commands) {
            builder.append(command)
                .append("\r\n");

        }
        return builder.toString();
    }
}
