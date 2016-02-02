package io.cloudsoft.winrm4j.winrm;

import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import io.cloudsoft.winrm4j.client.WinRmClient;
import org.apache.http.client.config.AuthSchemes;

/**
 * Tool for executing commands over WinRM.
 */
public class WinRmTool {
    private static final Logger LOG = Logger.getLogger(WinRmTool.class.getName());

    public static final int DEFAULT_WINRM_PORT = 5985;
    public static final int DEFAULT_WINRM_HTTPS_PORT = 5986;

    private String address;
    private String username;
    private String password;
    private String authenticationScheme;
    private boolean disableCertificateChecks;

    public static class Builder {
        private String authenticationScheme = AuthSchemes.BASIC;
        private Boolean useHttps;
        private Integer port = null;
        private boolean disableCertificateChecks = false;
        private String address;
        private String username;
        private String password;

        private static final Pattern matchPort = Pattern.compile(".*:\\d+$");

        public static Builder builder(String address, String username, String password) {
            return new Builder(address, username, password);
        }

        private Builder(String address, String username, String password) {
            this.address = address;
            this.username = username;
            this.password = password;
        }

        public Builder setAuthenticationScheme(String authenticationScheme) {
            this.authenticationScheme = authenticationScheme;
            return this;
        }

        public Builder disableCertificateChecks(boolean disableCertificateChecks) {
            this.disableCertificateChecks = disableCertificateChecks;
            return this;
        }

        public Builder useHttps(boolean useHttps) {
            this.useHttps = useHttps;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public WinRmTool build() {
            return new WinRmTool(getEndpointUrl(address, useHttps, port), username, password, authenticationScheme, disableCertificateChecks);
        }

        // TODO remove arguments when method WinRmTool.connect() is removed
        private static String getEndpointUrl(String address, Boolean useHttps, Integer port) {
            if (port == null && useHttps == null) {
                throw new IllegalArgumentException("No port number or protocol is set");
            }
            port = port != null ? port : (useHttps ? DEFAULT_WINRM_HTTPS_PORT : DEFAULT_WINRM_PORT);
            if (address.startsWith("http:") || address.startsWith("https:")) {
                if (useHttps != null) {
                    if (useHttps && address.startsWith("http:"))
                        throw new IllegalArgumentException("Invalid setting useHttps and address starting http://");
                    if (!useHttps && address.startsWith("https:"))
                        throw new IllegalArgumentException("Invalid setting useHttp and address starting https://");
                }
                return address;
            } else {
                if (matchPort.matcher(address).matches()) {
                    return (useHttps ? "https" : "http") + "://" + address + "/wsman";
                } else {
                    if (useHttps != null && useHttps) {
                        return "https://" + address + ":" + port + "/wsman";
                    } else {
                        return "http://" + address + ":" + port + "/wsman";
                    }
                }
            }
        }
    }

    @Deprecated
    public static WinRmTool connect(String address, String username, String password) {
        return new WinRmTool(WinRmTool.Builder.getEndpointUrl(address, false, DEFAULT_WINRM_PORT), username, password, AuthSchemes.BASIC, false);
    }

    private WinRmTool(String address, String username, String password, String authenticationScheme, boolean disableCertificateChecks) {
        this.disableCertificateChecks = disableCertificateChecks;
        this.address = address;
        this.username = username;
        this.password = password;
        this.authenticationScheme = authenticationScheme;
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
        WinRmClient.Builder builder = WinRmClient.builder(address, authenticationScheme);
        if (username != null && password != null) {
            builder.credentials(username, password);
        }
        if (disableCertificateChecks) {
            LOG.info("Disabled check for https connections " + this);
            builder.disableCertificateChecks(disableCertificateChecks);
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
