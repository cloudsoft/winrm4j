package io.cloudsoft.winrm4j.client;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.soap.SOAPFaultException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.cxf.Bus.BusState;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.addressing.AddressingProperties;
import org.apache.cxf.ws.addressing.AttributedURIType;
import org.apache.cxf.ws.addressing.JAXWSAConstants;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import io.cloudsoft.winrm4j.client.ntlm.SpNegoNTLMSchemeFactory;
import io.cloudsoft.winrm4j.client.shell.EnvironmentVariable;
import io.cloudsoft.winrm4j.client.shell.EnvironmentVariableList;
import io.cloudsoft.winrm4j.client.shell.Shell;
import io.cloudsoft.winrm4j.client.transfer.ResourceCreated;
import io.cloudsoft.winrm4j.client.wsman.Locale;
import io.cloudsoft.winrm4j.client.wsman.OptionSetType;
import io.cloudsoft.winrm4j.client.wsman.OptionType;

/**
 * TODO confirm if commands can be called in parallel in one shell (probably not)!
 */
public class WinRmClient {
    private static final Logger LOG = LoggerFactory.getLogger(WinRmClient.class.getName());

    static final int MAX_ENVELOPER_SIZE = 153600;
    static final String RESOURCE_URI = "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/cmd";

    private final String workingDirectory;
    private final Locale locale;
    private final Map<String, String> environment;

    // Can be changed throughout object's lifetime, but deprecated
    private String operationTimeout;
    private final int retriesForConnectionFailures;

    private final WinRmClientContext context;
    private final boolean cleanupContext;

    private final WinRm winrm;

    private ShellCommand shellCommand;

    /**
     * Create a WinRmClient builder
     *
     * @param endpoint - the url of the WSMAN service in the format https://machine:5986/wsman
     */
    public static Builder builder(URL endpoint) {
        return new Builder(endpoint);
    }

    /**
     * Create a WinRmClient builder
     *
     * @param endpoint - the url of the WSMAN service in the format https://machine:5986/wsman
     */
    public static Builder builder(String endpoint) {
        return new Builder(endpoint);
    }

    /**
     * Create a WinRmClient builder
     *
     * @param endpoint - the url of the WSMAN service in the format https://machine:5986/wsman
     * @param authenticationScheme - one of Basic, NTLM, Kerberos. Default is NTLM (with Negotiate).
     * 
     * @deprecated since 0.6.0. Use {@link #builder(URL)} and {@link #authenticationScheme(String)}.
     */
    @Deprecated
    public static Builder builder(URL endpoint, String authenticationScheme) {
        return new Builder(endpoint, authenticationScheme);
    }

    /**
     * Create a WinRmClient builder
     *
     * @param endpoint - the url of the WSMAN service in the format https://machine:5986/wsman
     * @param authenticationScheme - one of Basic, NTLM, Kerberos. Default is NTLM (with Negotiate).
     * 
     * @deprecated since 0.5.0. Use {@link #builder(String)} and {@link #authenticationScheme(String)}.
     */
    @Deprecated
    public static Builder builder(String endpoint, String authenticationScheme) {
        return new Builder(endpoint, authenticationScheme);
    }

    public static class Builder {
        private static final java.util.Locale DEFAULT_LOCALE = java.util.Locale.US;

        /** @deprecated since 0.6.0; will change to private */
        @Deprecated
        public static final Long DEFAULT_OPERATION_TIMEOUT = 60l * 1000l;
        private static final int DEFAULT_RETRIES_FOR_CONNECTION_FAILURES = 1;

        private WinRmClientContext context;
        private final URL endpoint;
        private String authenticationScheme;
        private String domain;
        private String username;
        private String password;
        private String workingDirectory;
        private Locale locale;
        private long operationTimeout;
        private Long receiveTimeout;
        private int retriesForConnectionFailures;
        private Map<String, String> environment;

        private boolean disableCertificateChecks;
        private HostnameVerifier hostnameVerifier;

        /** @deprecated since 0.6.0. Use {@link WinRmClient#builder(URL, String)} instead. */
        @Deprecated
        public Builder(URL endpoint, String authenticationScheme) {
            this(endpoint);
            authenticationScheme(authenticationScheme);
        }

        /** @deprecated since 0.6.0. Use {@link WinRmClient#builder(String, String)} instead. */
        public Builder(String endpoint, String authenticationScheme) {
            this(toUrlUnchecked(checkNotNull(endpoint, "endpoint")),
                    checkNotNull(authenticationScheme, "authenticationScheme"));
        }

        private Builder(String endpoint) {
            this(toUrlUnchecked(checkNotNull(endpoint, "endpoint")));
        }

        private Builder(URL endpoint) {
            this.endpoint = checkNotNull(endpoint, "endpoint");
            authenticationScheme(AuthSchemes.NTLM);
            locale(DEFAULT_LOCALE);
            operationTimeout(DEFAULT_OPERATION_TIMEOUT);
            retriesForConnectionFailures(DEFAULT_RETRIES_FOR_CONNECTION_FAILURES);
        }

        public Builder authenticationScheme(String authenticationScheme) {
            this.authenticationScheme = checkNotNull(authenticationScheme, "authenticationScheme");
            return this;
        }

        public Builder credentials(String username, String password) {
            return credentials(null, username, password);
        }

        /**
         * Credentials to use for authentication
         */
        public Builder credentials(String domain, String username, String password) {
            this.domain = domain;
            this.username = checkNotNull(username, "username");
            this.password = checkNotNull(password, "password");
            return this;
        }

        /**
         * @param locale The locale to run the process in
         */
        public Builder locale(java.util.Locale locale) {
            Locale l = new Locale();
            l.setLang(checkNotNull(locale, "locale").toLanguageTag());
            this.locale = l;
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
            this.operationTimeout = checkNotNull(operationTimeout, "operationTimeout");
            return this;
        }

        /**
         * @param retriesConnectionFailures How many times to retry the command before giving up in case of failure (exception).
         *        Default is 16.
         */
        public Builder retriesForConnectionFailures(int retriesConnectionFailures) {
            if (retriesConnectionFailures < 1) {
                throw new IllegalArgumentException("retriesConnectionFailure should be one or more");
            }
            this.retriesForConnectionFailures = retriesConnectionFailures;
            return this;
        }


        /**
         * @param disableCertificateChecks Skip trusted certificate and domain (CN) checks.
         *        Used when working with self-signed certificates. Use {@link #hostnameVerifier(HostnameVerifier)}
         *        for a more precise white-listing of server certificates.
         */
        public Builder disableCertificateChecks(boolean disableCertificateChecks) {
            this.disableCertificateChecks = disableCertificateChecks;
            return this;
        }

        /**
         * @param workingDirectory the working directory of the process
         */
        public Builder workingDirectory(String workingDirectory) {
            this.workingDirectory = checkNotNull(workingDirectory, "workingDirectory");
            return this;
        }

        /**
         * @param environment variables to pass to the command
         */
        public Builder environment(Map<String, String> environment) {
            this.environment = checkNotNull(environment, "environment");
            return this;
        }

        /**
         * @param hostnameVerifier override the default HostnameVerifier allowing
         *        users to add custom validation logic. Used when the default rules for URL
         *        hostname verification fail. Use {@link #disableCertificateChecks(boolean)} to
         *        disable certificate checks for all host names.
         */
        public Builder hostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
            return this;
        }

        /**
         * @param context is a shared {@link WinRmClientContext} object which allows connection
         *        reuse across {@link WinRmClient} invocations. If not set one will be created
         *        for each {@link WinRmClient} instance.
         */
        public Builder context(WinRmClientContext context) {
            this.context = context;
            return this;
        }

        /**
         * Create a WinRmClient
         */
        public WinRmClient build() {
            return new WinRmClient(this);
        }

        private static URL toUrlUnchecked(String endpoint) {
            try {
                return new URL(endpoint);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    private WinRmClient(Builder builder) {
        this.workingDirectory = builder.workingDirectory;
        this.locale = builder.locale;
        this.operationTimeout = toDuration(builder.operationTimeout);
        this.environment = builder.environment;
        this.retriesForConnectionFailures = builder.retriesForConnectionFailures;

        if (builder.context != null) {
            this.context = builder.context;
            this.cleanupContext = false;
        } else {
            this.context = WinRmClientContext.newInstance();
            this.cleanupContext = true;
        }

        this.winrm = getService(builder);
    }

    /** 
     * @deprecated since 0.6.0. Re-build the client for a new operation timeout.
     * Note that the {@code receiveTimeout} is not changed.
     * {@code operationTimeout} is not a command time out but the polling
     * timeout so doesn't make much sense to change it mid-use.
     */
    @Deprecated
    public void setOperationTimeout(long timeout) {
        this.operationTimeout = toDuration(timeout);
    }

    public WinRm getService(Builder builder) {
        WinRm service = WinRmFactory.newInstance(context.getBus());
        initializeClientAndService(service, builder);
        return service;
    }

    /**
     * @deprecated since 0.6.0. Use {@link #createShell()} and {@link ShellCommand#execute(String, Writer, Writer)} instead.
     */
    @Deprecated
    public int command(String cmd, Writer out, Writer err) {
        return initInstanceShell().execute(cmd, out, err);
    }

    // TODO fix CXF to not set a wrong action https://issues.apache.org/jira/browse/CXF-4647
    static void setActionToContext(BindingProvider bp, String action) {
        AttributedURIType attrUri = new AttributedURIType();
        attrUri.setValue(action);
        AddressingProperties addrProps = getAddressingProperties(bp);
        addrProps.setAction(attrUri);
    }

    static AddressingProperties getAddressingProperties(BindingProvider bp) {
        String ADDR_CONTEXT = "javax.xml.ws.addressing.context";
        Map<String, Object> reqContext = bp.getRequestContext();
        if (reqContext==null) {
            throw new NullPointerException("Unable to load request context; delegate load failed");
        }
        
        AddressingProperties addrProps = ((AddressingProperties)reqContext.get(ADDR_CONTEXT));
        if (addrProps==null) {
            throw new NullPointerException("Unable to load request context "+ADDR_CONTEXT+"; are the addressing classes installed (you may need <feature>cxf-ws-addr</feature> if running in osgi)");
        }
        return addrProps;
    }

    /**
     * @deprecated since 0.6.0. Exposed for tests only. Will be removed.
     */
    @Deprecated
    public int getNumberOfReceiveCalls() {
        return shellCommand.getNumberOfReceiveCalls();
    }

    private static void initializeClientAndService(WinRm winrm, Builder builder) {
        String endpoint = builder.endpoint.toExternalForm();
        String authenticationScheme = builder.authenticationScheme;
        String username = builder.username;
        String password = builder.password;
        String domain = builder.domain;
        boolean disableCertificateChecks = builder.disableCertificateChecks;
        HostnameVerifier hostnameVerifier = builder.hostnameVerifier;
        long receiveTimeout;
        if (builder.receiveTimeout != null) {
            receiveTimeout = builder.receiveTimeout;
        } else {
            receiveTimeout = operationToReceiveTimeout(builder.operationTimeout);
        }

        Client client = ClientProxy.getClient(winrm);
        ServiceInfo si = client.getEndpoint().getEndpointInfo().getService();
        
        // when client.command is executed if doclit.bare is not set then this exception occurs:
        // Unexpected element {http://schemas.microsoft.com/wbem/wsman/1/windows/shell}CommandResponse found.
        // Expected {http://schemas.dmtf.org/wbem/wsman/1/wsman.xsd}CommandResponse
        si.setProperty("soap.force.doclit.bare", true);
//        si.setProperty("soap.no.validate.parts", true);

        BindingProvider bp = (BindingProvider)winrm;
        
        @SuppressWarnings("rawtypes")
        List<Handler> handlerChain = Arrays.<Handler>asList(new StripShellResponseHandler());
        bp.getBinding().setHandlerChain(handlerChain);

        Map<String, Object> requestContext = bp.getRequestContext();
        AddressingProperties maps = new AddressingProperties(VersionTransformer.Names200408.WSA_NAMESPACE_NAME);
        requestContext.put(JAXWSAConstants.CLIENT_ADDRESSING_PROPERTIES, maps);

        bp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint);

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
                if (hostnameVerifier != null) {
                	TLSClientParameters tlsClientParameters = new TLSClientParameters();
                	tlsClientParameters.setHostnameVerifier(hostnameVerifier);
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
    }

    /**
     * Creates a Shell resource on the server, available for executing commands through the {@link ShellCommand} object.
     * {@link ShellCommand#close()} the returned object after usage.
     */
    public ShellCommand createShell() {
        final Shell shell = new Shell();
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

        ResourceCreated resourceCreated = winrmCallRetryConnFailure(new CallableFunction<ResourceCreated>() {
            @Override
            public ResourceCreated call() {
                //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
                setActionToContext((BindingProvider) winrm, "http://schemas.xmlsoap.org/ws/2004/09/transfer/Create");
                return winrm.create(shell, RESOURCE_URI, MAX_ENVELOPER_SIZE, operationTimeout, locale, optSetCreate);
            }
        }, retriesForConnectionFailures);
        String shellId = getShellId(resourceCreated);

        return new ShellCommand(winrm, shellId, operationTimeout, locale, retriesForConnectionFailures);
    }

    private static String getShellId(ResourceCreated resourceCreated) {
        XPath xpath = XPathFactory.newInstance().newXPath();
        for (Element el : resourceCreated.getAny()) {
            String shellId;
            try {
                shellId = xpath.evaluate("//*[local-name()='Selector' and @Name='ShellId']", el);
            } catch (XPathExpressionException e) {
                throw new IllegalStateException(e);
            }
            if (shellId != null && !shellId.isEmpty()) {
                return shellId;
            }
        }
        throw new IllegalStateException("Shell ID not fount in " + resourceCreated);
    }

    /**
     * @deprecated since 0.6.0. Use {@link ShellCommand#close()} instead.
     */
    @Deprecated
    public void disconnect() {
        if (context == null) return;
        boolean isBusRunning = context.getBus().getState() != BusState.SHUTDOWN;
        if (!isBusRunning) return;
        try {
            ShellCommand oldShellCmd = cleanupInstanceShell();
            if (oldShellCmd != null) {
                oldShellCmd.close();
            }
        } finally {
            if (cleanupContext) {
                context.getBus().shutdown(true);
            }
        }
    }

    private synchronized ShellCommand initInstanceShell() {
        if (shellCommand == null) {
            shellCommand = createShell();
        }
        return shellCommand;
    }

    private synchronized ShellCommand cleanupInstanceShell() {
        ShellCommand cmd = shellCommand;
        shellCommand = null;
        return cmd;
    }

    public static <T> T checkNotNull(T check, String msg) {
        if (check == null) {
            throw new NullPointerException(msg);
        }
        return check;
    }

    private static long operationToReceiveTimeout(long operationTimeout) {
        return operationTimeout + 60l * 1000l;
    }

    private static String toDuration(long operationTimeout) {
        BigDecimal bdMs = BigDecimal.valueOf(operationTimeout);
        BigDecimal bdSec = bdMs.divide(BigDecimal.valueOf(1000));
        DecimalFormat df = new DecimalFormat("PT#.###S", new DecimalFormatSymbols(java.util.Locale.ROOT));
        return df.format(bdSec);
    }

    static <V> V winrmCallRetryConnFailure(CallableFunction<V> winrmCall, Integer retriesForConnectionFailures) throws SOAPFaultException {
        int retries = retriesForConnectionFailures != null ? retriesForConnectionFailures : 16;
        List<Throwable> exceptions = new ArrayList<>();

        for (int i = 0; i < retries + 1; i++) {
            try {
                return winrmCall.call();
            } catch (SOAPFaultException soapFault) {
                throw soapFault;
            } catch (javax.xml.ws.WebServiceException wsException) {
                if (!(wsException.getCause() instanceof IOException)) {
                    throw new RuntimeException("Exception occurred while making winrm call", wsException);
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
    interface CallableFunction<V> {
        V call();
    }
}
