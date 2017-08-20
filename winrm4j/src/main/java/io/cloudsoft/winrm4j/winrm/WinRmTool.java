package io.cloudsoft.winrm4j.winrm;

import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;

import org.apache.http.client.config.AuthSchemes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudsoft.winrm4j.client.ShellCommand;
import io.cloudsoft.winrm4j.client.WinRmClient;
import io.cloudsoft.winrm4j.client.WinRmClientBuilder;
import io.cloudsoft.winrm4j.client.WinRmClientContext;

/**
 * Tool for executing commands over WinRM.
 * 
 * This class is not guaranteed to be thread safe.
 */
// Current implementation is thread safe because it creates a client per execute call
// but future implementations might re-use the client.
// TODO Create a client per WinRmTool
public class WinRmTool {
    private static final Logger LOG = LoggerFactory.getLogger(WinRmTool.class.getName());

    public static final int DEFAULT_WINRM_PORT = 5985;
    public static final int DEFAULT_WINRM_HTTPS_PORT = 5986;

    // TODO consider make them non-final and accessing the properties directly from builder.
    // This impose moving getEndpointUrl() to the WinRmTool.
    private final String address;
    private final String domain;
    private final String username;
    private final String password;
    private final String authenticationScheme;
    private Long operationTimeout;
    private Integer retriesForConnectionFailures;
    private final boolean disableCertificateChecks;
    private final String workingDirectory;
    private final Map<String, String> environment;
    private final HostnameVerifier hostnameVerifier;
    private final WinRmClientContext context;

    public static class Builder {
        private String authenticationScheme = AuthSchemes.NTLM;
        private Boolean useHttps;
        private Integer port = null;
        private boolean disableCertificateChecks = false;
        private String address;
        private String domain;
        private String username;
        private String password;
        private String workingDirectory;
        private Map<String, String> environment;
        private HostnameVerifier hostnameVerifier;
        private WinRmClientContext context;

        private static final Pattern matchPort = Pattern.compile(".*:(\\d+)$");

        public static Builder builder(String address, String username, String password) {
            return builder(address, null, username, password);
        }
        public static Builder builder(String address, String domain, String username, String password) {
            return new Builder(address, domain, username, password);
        }

        private Builder(String address, String domain, String username, String password) {
            this.address = address;
            this.domain = domain;
            this.username = username;
            this.password = password;
        }

        public Builder workingDirectory(String workingDirectory) {
            this.workingDirectory = WinRmClient.checkNotNull(workingDirectory, "workingDirectory");
            return this;
        }
        public Builder environment(Map<String, String> environment) {
            this.environment = WinRmClient.checkNotNull(environment, "environment");
            return this;
        }

        /**
         * @deprecated since 0.6.0
         */
        @Deprecated
        public Builder setAuthenticationScheme(String authenticationScheme) {
            this.authenticationScheme = authenticationScheme;
            return this;
        }

        public void authenticationScheme(String authenticationScheme) {
            this.authenticationScheme = authenticationScheme;
        }

        public Builder disableCertificateChecks(boolean disableCertificateChecks) {
            this.disableCertificateChecks = disableCertificateChecks;
            return this;
        }

        public Builder useHttps(boolean useHttps) {
            this.useHttps = useHttps;
            return this;
        }
        
        public Builder hostnameVerifier(HostnameVerifier hostnameVerifier) {
        	this.hostnameVerifier = hostnameVerifier;
        	return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }
        
        public Builder context(WinRmClientContext context) {
            this.context = context;
            return this;
        }

        public WinRmTool build() {
            return new WinRmTool(getEndpointUrl(address, useHttps, port),
                    domain, username, password, authenticationScheme,
                    disableCertificateChecks, workingDirectory,
                    environment, hostnameVerifier,
                    context);
        }

        // TODO remove arguments when method WinRmTool.connect() is removed
        private static String getEndpointUrl(String address, Boolean useHttps, Integer port) {
            if (address.startsWith("http:") || address.startsWith("https:")) {
                if (useHttps != null) {
                    if (useHttps && address.startsWith("http:"))
                        throw new IllegalArgumentException("Invalid setting useHttps and address starting http://");
                    if (!useHttps && address.startsWith("https:"))
                        throw new IllegalArgumentException("Invalid setting useHttp and address starting https://");
                }
                return address;
            } else {
                Matcher matcher = matchPort.matcher(address);

                if (matcher.matches()) {
                    if (useHttps == null) {
                        useHttps = matcher.group(1).equals("5986");
                    }
                    return (useHttps ? "https" : "http") + "://" + address + "/wsman";
                } else {
                    if (useHttps != null) {
                        port = port != null ? port : (useHttps ? DEFAULT_WINRM_HTTPS_PORT : DEFAULT_WINRM_PORT);
                    }
                    if (useHttps != null && useHttps) {
                        return "https://" + address + ":" + port + "/wsman";
                    } else {
                        return "http://" + address + ":" + port + "/wsman";
                    }
                }
            }
        }
    }

    private WinRmTool(String address, String domain, String username,
            String password, String authenticationScheme,
            boolean disableCertificateChecks, String workingDirectory,
            Map<String, String> environment, HostnameVerifier hostnameVerifier,
            WinRmClientContext context) {
        this.disableCertificateChecks = disableCertificateChecks;
        this.address = address;
        this.domain = domain;
        this.username = username;
        this.password = password;
        this.authenticationScheme = authenticationScheme;
        this.workingDirectory = workingDirectory;
        this.environment = environment;
        this.hostnameVerifier = hostnameVerifier;
        this.context = context;
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
     * Updates operationTimeout for the next <code>executeXxx</code> call
     *
     * @see <a href="http://www.dmtf.org/sites/default/files/standards/documents/DSP0226_1.2.0.pdf>DSP0226_1.2.0.pdf</a>
     * @param operationTimeout in milliseconds
     *                         default value {@link WinRmClient.Builder#DEFAULT_OPERATION_TIMEOUT}
     *                         If operations cannot be completed in a specified time,
     *                         the service returns a fault so that a client can comply with its obligations.
     */
    public void setOperationTimeout(Long operationTimeout) {
        this.operationTimeout = operationTimeout;
    }

    public void setRetriesForConnectionFailures(Integer retriesForConnectionFailures) {
        this.retriesForConnectionFailures = retriesForConnectionFailures;
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
        WinRmClientBuilder builder = WinRmClient.builder(address);
        builder.authenticationScheme(authenticationScheme);
        if (operationTimeout != null) {
            builder.operationTimeout(operationTimeout);
        }
        if (username != null && password != null) {
            builder.credentials(domain, username, password);
        }
        if (disableCertificateChecks) {
            LOG.trace("Disabled check for https connections " + this);
            builder.disableCertificateChecks(disableCertificateChecks);
        }
        if (hostnameVerifier != null) {
        	builder.hostnameVerifier(hostnameVerifier);
        }
        if (workingDirectory != null) {
            builder.workingDirectory(workingDirectory);
        }
        if (environment != null) {
            builder.environment(environment);
        }
        if (retriesForConnectionFailures != null) {
            builder.retriesForConnectionFailures(retriesForConnectionFailures);
        }
        if (context != null) {
            builder.context(context);
        }

        WinRmClient client = builder.build();

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        try (ShellCommand shell = client.createShell()) {
            int code = shell.execute(command, out, err);
            WinRmToolResponse winRmToolResponse = new WinRmToolResponse(out.toString(), err.toString(), code);
            winRmToolResponse.setNumberOfReceiveCalls(shell.getNumberOfReceiveCalls());
            return winRmToolResponse;
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
