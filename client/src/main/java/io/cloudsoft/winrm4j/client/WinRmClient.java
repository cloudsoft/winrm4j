package io.cloudsoft.winrm4j.client;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.soap.SOAPFaultException;

import io.cloudsoft.winrm4j.client.ntlm.SpNegoNTLMSchemeFactory;
import io.cloudsoft.winrm4j.client.wsman.CommandResponse;
import io.cloudsoft.winrm4j.client.wsman.DeleteResponse;
import io.cloudsoft.winrm4j.client.wsman.Locale;
import io.cloudsoft.winrm4j.client.wsman.OptionSetType;
import io.cloudsoft.winrm4j.client.wsman.OptionType;
import io.cloudsoft.winrm4j.client.wsman.SelectorSetType;
import io.cloudsoft.winrm4j.client.wsman.SelectorType;
import io.cloudsoft.winrm4j.client.wsman.Signal;
import io.cloudsoft.winrm4j.client.wsman.SignalResponse;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduit;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduitFactory;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.addressing.AddressingProperties;
import org.apache.cxf.ws.addressing.AttributedURIType;
import org.apache.cxf.ws.addressing.JAXWSAConstants;
import org.apache.cxf.ws.addressing.WSAddressingFeature;
import org.apache.cxf.ws.addressing.VersionTransformer;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;

import io.cloudsoft.winrm4j.client.shell.CommandLine;
import io.cloudsoft.winrm4j.client.shell.CommandStateType;
import io.cloudsoft.winrm4j.client.shell.DesiredStreamType;
import io.cloudsoft.winrm4j.client.shell.EnvironmentVariable;
import io.cloudsoft.winrm4j.client.shell.EnvironmentVariableList;
import io.cloudsoft.winrm4j.client.shell.Receive;
import io.cloudsoft.winrm4j.client.shell.ReceiveResponse;
import io.cloudsoft.winrm4j.client.shell.Shell;
import io.cloudsoft.winrm4j.client.shell.StreamType;
import io.cloudsoft.winrm4j.client.transfer.ResourceCreated;

/**
 * TODO confirm if parallel commands can be called in parallel in one shell (probably not)!
 */
public class WinRmClient {
    private static final Logger LOG = LoggerFactory.getLogger(WinRmClient.class.getName());

    private static final String COMMAND_STATE_DONE = "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/CommandState/Done";
    private static final int MAX_ENVELOPER_SIZE = 153600;
    private static final String RESOURCE_URI = "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/cmd";

    /**
     * If no output is available before the wsman:OperationTimeout expires, the server MUST return a WSManFault with the Code attribute equal to "2150858793"
     * https://msdn.microsoft.com/en-us/library/cc251676.aspx
     */
    private static final String WSMAN_FAULT_CODE_OPERATION_TIMEOUT_EXPIRED = "2150858793";

    /**
     * Example response:
     *   [truncated]The request for the Windows Remote Shell with ShellId xxxx-yyyy-ccc... failed because the shell was not found on the server.
     *   Possible causes are: the specified ShellId is incorrect or the shell no longer exi
     */
    private static final String WSMAN_FAULT_CODE_SHELL_WAS_NOT_FOUND = "2150858843";

    private final String authenticationScheme;
    private URL endpoint;
    private String domain;
    private String username;
    private String password;
    private String workingDirectory;
    private Locale locale;
    private String operationTimeout;
    private Long receiveTimeout;
    private Integer retriesForConnectionFailures;
    private Map<String, String> environment;

    private WinRm winrm;
    private String shellId;
    private SelectorSetType shellSelector;

    private int numberOfReceiveCalls;

    private boolean disableCertificateChecks;

    public static Builder builder(URL endpoint) {
        return new Builder(endpoint, AuthSchemes.NTLM);
    }

    public static Builder builder(String endpoint) {
        return new Builder(endpoint, AuthSchemes.NTLM);
    }

    public static Builder builder(URL endpoint, String authenticationScheme) {
        return new Builder(endpoint, authenticationScheme);
    }

    public static Builder builder(String endpoint, String authenticationScheme) {
        return new Builder(endpoint, authenticationScheme);
    }

    public static class Builder {
        private static final java.util.Locale DEFAULT_LOCALE = java.util.Locale.US;
        public static final Long DEFAULT_OPERATION_TIMEOUT = 60l * 1000l;
        private WinRmClient client;
        public Builder(URL endpoint, String authenticationScheme) {
            client = new WinRmClient(endpoint, authenticationScheme);
        }
        public Builder(String endpoint, String authenticationScheme) {
            this(toUrlUnchecked(checkNotNull(endpoint, "endpoint")), authenticationScheme);
        }
        public Builder credentials(String username, String password) {
            return credentials(null, username, password);
        }
        public Builder credentials(String domain, String username, String password) {
            client.domain = domain;
            client.username = checkNotNull(username, "username");
            client.password = checkNotNull(password, "password");
            return this;
        }
        public Builder locale(java.util.Locale locale) {
            Locale l = new Locale();
            l.setLang(checkNotNull(locale, "locale").toLanguageTag());
            client.locale = l;
            return this;
        }

        /**
         * If operations cannot be completed in a specified time,
         * the service returns a fault so that a client can comply with its obligations.
         * http://www.dmtf.org/sites/default/files/standards/documents/DSP0226_1.2.0.pdf
         *
         * @param operationTimeout in milliseconds
         *                         default value {@link WinRmClient.Builder#DEFAULT_OPERATION_TIMEOUT}
         */
        public Builder operationTimeout(long operationTimeout) {
            client.operationTimeout = toDuration(operationTimeout);
            client.receiveTimeout = operationTimeout + 60l * 1000l;
            return this;
        }

        public Builder retriesForConnectionFailures(Integer retriesConnectionFailures) {
            if (retriesConnectionFailures < 1) {
                throw new IllegalArgumentException("retriesConnectionFailure should be one or more");
            }
            client.retriesForConnectionFailures = retriesConnectionFailures;
            return this;
        }

        public Builder disableCertificateChecks(boolean disableCertificateChecks) {
            client.disableCertificateChecks = disableCertificateChecks;
            return this;
        }
        public Builder workingDirectory(String workingDirectory) {
            client.workingDirectory = checkNotNull(workingDirectory, "workingDirectory");
            return this;
        }
        public Builder environment(Map<String, String> environment) {
            client.environment = checkNotNull(environment, "environment");
            return this;
        }

        public WinRmClient build() {
            if (client.locale == null) {
                locale(DEFAULT_LOCALE);
            }
            if (client.operationTimeout == null) {
                client.operationTimeout = toDuration(DEFAULT_OPERATION_TIMEOUT);
                client.receiveTimeout = DEFAULT_OPERATION_TIMEOUT + 30l * 1000l;
            }
            WinRmClient ret = client;
            client = null;
            return ret;
        }
        private static URL toUrlUnchecked(String endpoint) {
            try {
                return new URL(endpoint);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }
        private static String toDuration(long operationTimeout) {
            BigDecimal bdMs = BigDecimal.valueOf(operationTimeout);
            BigDecimal bdSec = bdMs.divide(BigDecimal.valueOf(1000));
            DecimalFormat df = new DecimalFormat("PT#.###S", new DecimalFormatSymbols(java.util.Locale.ROOT));
            return df.format(bdSec);
        }
    }

    private WinRmClient(URL endpoint, String authenticationScheme) {
        this.authenticationScheme = authenticationScheme != null ? authenticationScheme : AuthSchemes.NTLM;
        this.endpoint = endpoint;

        if (!this.authenticationScheme.equals(AuthSchemes.BASIC)) {
            // TODO consider using async client for Basic authentication
            // Needed to be async according to http://cxf.apache.org/docs/asynchronous-client-http-transport.html
            Bus bus = BusFactory.getDefaultBus();
            bus.getProperties().put(AsyncHTTPConduit.USE_ASYNC, Boolean.TRUE);
            bus.getProperties().put(AsyncHTTPConduitFactory.USE_POLICY, "ALWAYS");
        }
    }

    public int command(String cmd, Writer out, Writer err) {
        checkNotNull(cmd, "command");
        final WinRm service = getService();

        final CommandLine cmdLine = new CommandLine();
        cmdLine.setCommand(cmd);
        final OptionSetType optSetCmd = new OptionSetType();
        OptionType optConsolemodeStdin = new OptionType();
        optConsolemodeStdin.setName("WINRS_CONSOLEMODE_STDIN");
        optConsolemodeStdin.setValue("TRUE");
        optSetCmd.getOption().add(optConsolemodeStdin);
        OptionType optSkipCmdShell = new OptionType();
        optSkipCmdShell.setName("WINRS_SKIP_CMD_SHELL");
        optSkipCmdShell.setValue("FALSE");
        optSetCmd.getOption().add(optSkipCmdShell);

        numberOfReceiveCalls = 0;
        //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
        setActionToContext((BindingProvider) service, "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/Command");
        CommandResponse cmdResponse = service.command(cmdLine, RESOURCE_URI, MAX_ENVELOPER_SIZE, operationTimeout, locale, shellSelector, optSetCmd);

        String commandId = cmdResponse.getCommandId();

        try {
            return receiveCommand(commandId, out, err);
        } finally {
            try {
                releaseCommand(commandId);
            } catch (SOAPFaultException soapFault) {
                assertFaultCode(soapFault, WSMAN_FAULT_CODE_SHELL_WAS_NOT_FOUND);
            }
        }
    }

    // TODO fix CXF to not set a wrong action https://issues.apache.org/jira/browse/CXF-4647
    private void setActionToContext(BindingProvider bp, String action) {
        AttributedURIType attrUri = new AttributedURIType();
        attrUri.setValue(action);
        ((AddressingProperties)bp.getRequestContext().get("javax.xml.ws.addressing.context")).setAction(attrUri);
    }

    private void releaseCommand(String commandId) {
        final Signal signal = new Signal();
        signal.setCommandId(commandId);
        signal.setCode("http://schemas.microsoft.com/wbem/wsman/1/windows/shell/signal/terminate");

        winrmCallRetryConnFailure(new CallableFunction<SignalResponse>() {
            @Override
            public SignalResponse call() {
                //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
                setActionToContext((BindingProvider) winrm, "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/Signal");
                return winrm.signal(signal, RESOURCE_URI, MAX_ENVELOPER_SIZE, operationTimeout, locale, shellSelector);
            }
        });

    }

    private int receiveCommand(String commandId, Writer out, Writer err) {
        while(true) {
            final Receive receive = new Receive();
            DesiredStreamType stream = new DesiredStreamType();
            stream.setCommandId(commandId);
            stream.setValue("stdout stderr");
            receive.setDesiredStream(stream);


            try {
                numberOfReceiveCalls++;
                ReceiveResponse receiveResponse = winrmCallRetryConnFailure(new CallableFunction<ReceiveResponse>() {
                    @Override
                    public ReceiveResponse call() {
                        //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
                        setActionToContext((BindingProvider) winrm, "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/Receive");
                        return winrm.receive(receive, RESOURCE_URI, MAX_ENVELOPER_SIZE, operationTimeout, locale, shellSelector);
                    }
                });
                getStreams(receiveResponse, out, err);

                CommandStateType state = receiveResponse.getCommandState();
                if (COMMAND_STATE_DONE.equals(state.getState())) {
                    return state.getExitCode().intValue();
                } else {
                    LOG.debug("{} is not done. Response it received: {}", this, receiveResponse);
                }
            } catch (SOAPFaultException soapFault) {
                /**
                 * If such Exception which has a code 2150858793 the client is expected to again trigger immediately a receive request.
                 * https://msdn.microsoft.com/en-us/library/cc251676.aspx
                 */
                assertFaultCode(soapFault, WSMAN_FAULT_CODE_OPERATION_TIMEOUT_EXPIRED);
            }
        }
    }

    private void assertFaultCode(SOAPFaultException soapFault, String code) {
        try {
            if (soapFault.getFault().getDetail().getFirstChild().getAttributes().getNamedItem("Code").getNodeValue().equals(code)) {
                LOG.trace("winrm client {} received error 500 response with code {}, response {}", this, code, soapFault);
            } else {
                throw soapFault;
            }
        } catch (NullPointerException e) {
            LOG.debug("Error reading Fault Code {}", soapFault.getFault());
            throw soapFault;
        }
    }

    public int getNumberOfReceiveCalls() {
        return numberOfReceiveCalls;
    }

    private void getStreams(ReceiveResponse receiveResponse, Writer out, Writer err) {
        List<StreamType> streams = receiveResponse.getStream();
        for (StreamType s : streams) {
            byte[] value = s.getValue();
            if (value == null) continue;
            if (out != null && "stdout".equals(s.getName())) {
                try {
                    //TODO use passed locale?
                    if (value.length > 0) {
                        out.write(new String(value));
                    }
                    if (Boolean.TRUE.equals(s.isEnd())) {
                        out.close();
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            if (err != null && "stderr".equals(s.getName())) {
                try {
                    //TODO use passed locale?
                    if (value.length > 0) {
                        err.write(new String(value));
                    }
                    if (Boolean.TRUE.equals(s.isEnd())) {
                        err.close();
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        try {
            out.close();
            err.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private WinRm getService() {
        if (winrm != null) {
            return winrm;
        } else {
            return createService();
        }
    }

    private synchronized WinRm createService() {
        if (winrm != null) return winrm;

        WinRmService service = new WinRmService();
//        JaxWsDynamicClientFactory dcf = JaxWsDynamicClientFactory.newInstance();
//        Client client = dcf.createClient("people.wsdl", classLoader);
        winrm = service.getWinRmPort(
                // * Adds WS-Addressing headers and uses the submission spec namespace
                //   http://schemas.xmlsoap.org/ws/2004/08/addressing
                newMemberSubmissionAddressingFeature());

        Client client = ClientProxy.getClient(winrm);
        ServiceInfo si = client.getEndpoint().getEndpointInfo().getService();

        // when client.command is executed if doclit.bare is not set then this exception occurs:
        // Unexpected element {http://schemas.microsoft.com/wbem/wsman/1/windows/shell}CommandResponse found.
        // Expected {http://schemas.dmtf.org/wbem/wsman/1/wsman.xsd}CommandResponse
        si.setProperty("soap.force.doclit.bare", true);
//        si.setProperty("soap.no.validate.parts", true);

        BindingProvider bp = (BindingProvider)winrm;

        Map<String, Object> requestContext = bp.getRequestContext();
        AddressingProperties maps = new AddressingProperties(VersionTransformer.Names200408.WSA_NAMESPACE_NAME);
        requestContext.put(JAXWSAConstants.CLIENT_ADDRESSING_PROPERTIES, maps);

        bp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint.toExternalForm());

        switch (authenticationScheme) {
            case AuthSchemes.BASIC:
                bp.getRequestContext().put(BindingProvider.USERNAME_PROPERTY, username);
                bp.getRequestContext().put(BindingProvider.PASSWORD_PROPERTY, password);
                break;
            case AuthSchemes.NTLM: case AuthSchemes.KERBEROS:
                Credentials creds = new NTCredentials(username, password, null, domain);

                Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                        .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                        .register(AuthSchemes.SPNEGO,
                                authenticationScheme.equals(AuthSchemes.NTLM) ? new SpNegoNTLMSchemeFactory() : new SPNegoSchemeFactory())
                        .register(AuthSchemes.KERBEROS, new KerberosSchemeFactory())//
                        .build();

                bp.getRequestContext().put(Credentials.class.getName(), creds);
                bp.getRequestContext().put("http.autoredirect", true);
                bp.getRequestContext().put(AuthSchemeProvider.class.getName(), authSchemeRegistry);

                AsyncHTTPConduit httpClient = (AsyncHTTPConduit) client.getConduit();

                if (disableCertificateChecks) {
                    TLSClientParameters tlsClientParameters = new TLSClientParameters();
                    tlsClientParameters.setDisableCNCheck(true);
                    tlsClientParameters.setTrustManagers(new TrustManager[]{new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }});
                    httpClient.setTlsClientParameters(tlsClientParameters);
                }
                HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
                httpClientPolicy.setAllowChunking(false);
                httpClientPolicy.setReceiveTimeout(receiveTimeout);

                httpClient.setClient(httpClientPolicy);
                httpClient.getClient().setAutoRedirect(true);
                break;
            default:
                throw new UnsupportedOperationException("No such authentication scheme " + authenticationScheme);
        }

        Shell shell = new Shell();
        shell.getInputStreams().add("stdin");
        shell.getOutputStreams().add("stdout");
        shell.getOutputStreams().add("stderr");
        if (workingDirectory != null) {
            shell.setWorkingDirectory(workingDirectory);
        }
        if (environment != null && !environment.isEmpty()) {
            EnvironmentVariableList env = new EnvironmentVariableList();
            List<EnvironmentVariable> vars = env.getVariable();
            for (Entry<String, String> entry : environment.entrySet()) {
                EnvironmentVariable var = new EnvironmentVariable();
                var.setName(entry.getKey());
                var.setValue(entry.getValue());
                vars.add(var);
            }
            shell.setEnvironment(env);
        }

        final OptionSetType optSetCreate = new OptionSetType();
        OptionType optNoProfile = new OptionType();
        optNoProfile.setName("WINRS_NOPROFILE");
        optNoProfile.setValue("FALSE");
        optSetCreate.getOption().add(optNoProfile);
        OptionType optCodepage = new OptionType();
        optCodepage.setName("WINRS_CODEPAGE");
        optCodepage.setValue("437");
        optSetCreate.getOption().add(optCodepage);

        final Holder<Shell> holder = new Holder<>(shell);
        ResourceCreated resourceCreated = winrmCallRetryConnFailure(new CallableFunction<ResourceCreated>() {
            @Override
            public ResourceCreated call() {
                //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
                setActionToContext((BindingProvider) winrm, "http://schemas.xmlsoap.org/ws/2004/09/transfer/Create");
                return winrm.create(holder, RESOURCE_URI, MAX_ENVELOPER_SIZE, operationTimeout, locale, optSetCreate);
            }
        });
        shellId = holder.value.getShellId();

        shellSelector = new SelectorSetType();
        SelectorType sel = new SelectorType();
        sel.setName("ShellId");
        sel.getContent().add(shellId);
        shellSelector.getSelector().add(sel);

        return winrm;
    }

    // TODO
    private static WebServiceFeature newMemberSubmissionAddressingFeature() {
        /*
         * Requires the following dependency so the feature is visible to maven.
         * But is it included in the IBM dist?
<dependency>
    <groupId>com.sun.xml.ws</groupId>
    <artifactId>jaxws-rt</artifactId>
    <version>2.2.10</version>
</dependency>
         */
        try {
            // com.ibm.websphere.wsaddressing.jaxws21.SubmissionAddressingFeature for IBM java (available only in WebSphere?)

            WSAddressingFeature webServiceFeature = new WSAddressingFeature();
//            webServiceFeature.setResponses(WSAddressingFeature.AddressingResponses.ANONYMOUS);
            webServiceFeature.setAddressingRequired(true);

            return webServiceFeature;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void disconnect() {
        if (winrm != null && shellSelector != null) {
            try {
                winrmCallRetryConnFailure(new CallableFunction<DeleteResponse>() {
                    @Override
                    public DeleteResponse call() {
                        //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
                        setActionToContext((BindingProvider) winrm, "http://schemas.xmlsoap.org/ws/2004/09/transfer/Delete");
                        return winrm.delete(null, RESOURCE_URI, MAX_ENVELOPER_SIZE, operationTimeout, locale, shellSelector);
                    }
                });
            } catch (SOAPFaultException soapFault) {
                assertFaultCode(soapFault, WSMAN_FAULT_CODE_SHELL_WAS_NOT_FOUND);
            }
        }
    }

    public static <T> T checkNotNull(T check, String msg) {
        if (check == null) {
            throw new NullPointerException(msg);
        }
        return check;
    }

    private <V> V winrmCallRetryConnFailure(CallableFunction<V> winrmCall) throws SOAPFaultException {
        int retries = retriesForConnectionFailures != null ? retriesForConnectionFailures : 8;
        List<Throwable> exceptions = new ArrayList<>();

        for (int i = 0; i < retries + 1; i++) {
            try {
                return winrmCall.call();
            } catch (SOAPFaultException soapFault) {
                throw soapFault;
            } catch (javax.xml.ws.WebServiceException wsException) {
                if (!(wsException.getCause() instanceof ConnectException || wsException.getCause() instanceof SocketTimeoutException || wsException.getCause() instanceof SocketException)) {
                    throw new RuntimeException("Exception occured while making winrm call", wsException);
                }
                LOG.debug("Ignoring exception and retrying (attempt " + (i + 1) + " of " + (retries + 1) + ") {}", wsException);
                try {
                    Thread.sleep(5 * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Exception occured while making winrm call", e.initCause(wsException));
                }
                exceptions.add(wsException);
            }
        }
        throw new RuntimeException("failed task " + winrmCall, exceptions.get(0));
    }

    /**
     * A {@link java.util.concurrent.Callable} like but without <code>throws Exception</code> signature
     *
     * @param <V> the result type of method {@code call}
     */
    private interface CallableFunction<V> {
        V call();
    }
}
