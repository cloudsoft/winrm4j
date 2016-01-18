package io.cloudsoft.winrm4j.winrm;

import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.List;

import io.cloudsoft.winrm4j.client.WinRmClient;
import io.cloudsoft.winrm4j.client.WinRmClient.Builder;

/**
 * Tool for executing commands over WinRM.
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

    /**
     * Executes a Native Windows commands.
     * 
     * Current implementation is to concatenate the commands using <code>" & "</code>.
     * 
     * Consider instead uploading a script file, and then executing that as a one-line command.
     * 
     * @see {@link #executeCommand(String)} for limitations, e.g. about command length.
     * 
     * @since 0.2
     */
    public WinRmToolResponse executeCommand(List<String> commands) {
    	return executeCommand(joinCommands(commands));
    }

    /**
     * Executes a Native Windows command.
     * It is creating a new Shell on the destination host each time it is being called.
     * @param command The command is limited to 8096 bytes.
     *                Maximum length of the command can be even smaller depending on the platform.
     *                https://support.microsoft.com/en-us/kb/830473
     * @since 0.2
     */
    public WinRmToolResponse executeCommand(String command) {
        Builder builder = WinRmClient.builder(getEndpointUrl());
        if (username != null && password != null) {
            builder.credentials(username, password);
        }
        WinRmClient client = builder.build();

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        try {
            int code = client.command(command, out, err);
            return new WinRmToolResponse(out.toString(), err.toString(), code);
        } finally {
            client.disconnect();
        }
    }

    /**
     * Executes a Power Shell command.
     * It is creating a new Shell on the destination host each time it is being called.
     * @since 0.2
     */
    public WinRmToolResponse executePs(String psCommand) {
        return executeCommand(compilePs(psCommand));
    }

    /**
     * Execute a list of Power Shell commands as one command.
     * The method translates the list of commands to a single String command with a 
     * <code>"\r\n"</code> delimiter and a terminating one.
     * 
     * Consider instead uploading a script file, and then executing that as a one-line command.
     */
    public WinRmToolResponse executePs(List<String> commands) {
        return executeCommand(compilePs(joinPs(commands)));
    }

    private String compilePs(String psScript) {
        byte[] cmd = psScript.getBytes(Charset.forName("UTF-16LE"));
        String arg = javax.xml.bind.DatatypeConverter.printBase64Binary(cmd);
        return "powershell -encodedcommand " + arg;
    }

    /**
     * Execute a list of Windows Native commands as one command.
     * The method translates the list of commands to a single String command with a <code>" & "</code> 
     * delimiter and a terminating one.
     * 
     * @deprecated since 0.2; instead use {@link #executeCommand(List)} to remove ambiguity
     *             between native commands and powershell.
     */
    @Deprecated
    public WinRmToolResponse executeScript(List<String> commands) {
        return executeCommand(commands);
    }

    /**
     * @deprecated since 0.2; instead use {@link #executeCommand(String)} to remove ambiguity
     *             between native commands and powershell.
     */
    @Deprecated
    public WinRmToolResponse executeScript(String commands) {
        return executeCommand(commands);
    }

    //TODO support https transport
    private String getEndpointUrl() {
        if (address.startsWith("http:") || address.startsWith("https:")) {
            return address;
        } else if (address.contains(":")) {
            return "http://" + address + "/wsman";
        } else {
            return "http://" + address + ":5985/wsman";
        }
    }

    private String joinCommands(List<String> commands) {
        return join(commands, " & ", false);
    }

    /**
     * PS commands are base64 encoded so we can use the normal new line
     * Windows delimiter here.
     */
    private String joinPs(List<String> commands) {
        return join(commands, "\r\n", true);
    }

    private String join(List<String> commands, String delim, boolean endWithDelim) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String command : commands) {
        	if (first) {
        		first = false;
        	} else {
                builder.append(delim);
        	}
            builder.append(command);
        }
        if (endWithDelim) {
        	builder.append(delim);
        }
        return builder.toString();
    }
}
