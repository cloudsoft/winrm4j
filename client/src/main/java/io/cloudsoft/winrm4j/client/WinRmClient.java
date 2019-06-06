package io.cloudsoft.winrm4j.client;

import java.io.Writer;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.Handler;
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
import org.apache.cxf.ws.addressing.policy.MetadataConstants;
import org.apache.cxf.ws.policy.PolicyConstants;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.neethi.Policy;
import org.apache.neethi.builders.PrimitiveAssertion;
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
public class WinRmClient implements AutoCloseable {
    static final int MAX_ENVELOPER_SIZE = 153600;
    static final String RESOURCE_URI = "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/cmd";

    private final String workingDirectory;
    private final Locale locale;
    private final Map<String, String> environment;

    // Can be changed throughout object's lifetime, but deprecated
    private String operationTimeout;

    private final WinRmClientContext context;
    private final boolean cleanupContext;

    private final WinRm winrm;
    private RetryingProxyHandler retryingHandler;

    private ShellCommand shellCommand;

    /**
     * Create a WinRmClient builder
     *
     * @param endpoint - the url of the WSMAN service in the format https://machine:5986/wsman
     */
    public static WinRmClientBuilder builder(URL endpoint) {
        return new WinRmClientBuilder(endpoint);
    }

    /**
     * Create a WinRmClient builder
     *
     * @param endpoint - the url of the WSMAN service in the format https://machine:5986/wsman
     */
    public static WinRmClientBuilder builder(String endpoint) {
        return new WinRmClientBuilder(endpoint);
    }

    /**
     * Create a WinRmClient builder
     *
     * @param endpoint - the url of the WSMAN service in the format https://machine:5986/wsman
     * @param authenticationScheme - one of Basic, NTLM, Kerberos. Default is NTLM (with Negotiate).
     * 
     * @deprecated since 0.6.0. Use {@link #builder(URL)} and {@link Builder#authenticationScheme(String)}.
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
     * @deprecated since 0.5.0. Use {@link #builder(String)} and {@link Builder#authenticationScheme(String)}.
     */
    @Deprecated
    public static Builder builder(String endpoint, String authenticationScheme) {
        return new Builder(endpoint, authenticationScheme);
    }

    /**
     * @deprecated since 0.6.0. Use {@link WinRmClientBuilder} instead.
     */
    @Deprecated
    public static class Builder extends WinRmClientBuilder {
        /** @deprecated since 0.6.0; will change to private */
        @Deprecated
        public static final Long DEFAULT_OPERATION_TIMEOUT = 60l * 1000l;

        /** @deprecated since 0.6.0. Use {@link WinRmClient#builder(URL, String)} instead. */
        @Deprecated
        public Builder(URL endpoint, String authenticationScheme) {
            this(endpoint);
            authenticationScheme(authenticationScheme);
        }

        /** @deprecated since 0.6.0. Use {@link WinRmClient#builder(String, String)} instead. */
        public Builder(String endpoint, String authenticationScheme) {
            this(WinRmClientBuilder.toUrlUnchecked(checkNotNull(endpoint, "endpoint")),
                    checkNotNull(authenticationScheme, "authenticationScheme"));
        }

        Builder(String endpoint) {
            super(endpoint);
        }

        Builder(URL endpoint) {
            super(endpoint);
        }
    }

    WinRmClient(WinRmClientBuilder builder) {
        this.workingDirectory = builder.workingDirectory;
        this.locale = builder.locale;
        this.operationTimeout = toDuration(builder.operationTimeout);
        this.environment = builder.environment;

        if (builder.context != null) {
            this.context = builder.context;
            this.cleanupContext = false;
        } else {
            this.context = WinRmClientContext.newInstance();
            this.cleanupContext = true;
        }

        WinRm service = getService(builder);
        retryingHandler = new RetryingProxyHandler(service, builder.retriesForConnectionFailures);
        this.winrm = (WinRm) Proxy.newProxyInstance(WinRm.class.getClassLoader(),
                new Class[] {WinRm.class, BindingProvider.class},
                retryingHandler);
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

    private WinRm getService(WinRmClientBuilder builder) {
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

    /** @deprecated since 0.6.0. Implementation detail, access will be removed in future versions. */
    @Deprecated
    public int getNumberOfReceiveCalls() {
        if (shellCommand == null) {
            return 0;
        } else {
            return shellCommand.getNumberOfReceiveCalls();
        }
    }

    private static void initializeClientAndService(WinRm winrm, WinRmClientBuilder builder) {
        String endpoint = builder.endpoint.toExternalForm();
        String authenticationScheme = builder.authenticationScheme;
        String username = builder.username;
        String password = builder.password;
        String domain = builder.domain;
        boolean disableCertificateChecks = builder.disableCertificateChecks;
        HostnameVerifier hostnameVerifier = builder.hostnameVerifier;
        SSLSocketFactory sslSocketFactory = builder.sslSocketFactory;
        SSLContext sslContext = builder.sslContext;
        long connectionTimeout = builder.connectionTimeout;
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

        Policy policy = new Policy();
        policy.addAssertion(new PrimitiveAssertion(MetadataConstants.USING_ADDRESSING_2004_QNAME));
        bp.getRequestContext().put(PolicyConstants.POLICY_OVERRIDE, policy);

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
                if (hostnameVerifier != null || sslSocketFactory != null || sslContext != null) {
                	TLSClientParameters tlsClientParameters = new TLSClientParameters();
                	tlsClientParameters.setHostnameVerifier(hostnameVerifier);
                	tlsClientParameters.setSSLSocketFactory(sslSocketFactory);
                	tlsClientParameters.setSslContext(sslContext);
                	httpClient.setTlsClientParameters(tlsClientParameters);
                }
                HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
                httpClientPolicy.setAllowChunking(false);
                httpClientPolicy.setConnectionTimeout(connectionTimeout);
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

        ResourceCreated resourceCreated = winrm.create(shell, RESOURCE_URI, MAX_ENVELOPER_SIZE, operationTimeout, locale, optSetCreate);
        String shellId = getShellId(resourceCreated);

        return new ShellCommand(winrm, shellId, operationTimeout, locale);
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

    @Override
    public void close() {
        if (context == null) return;
        boolean isBusRunning = context.getBus().getState() != BusState.SHUTDOWN;
        if (isBusRunning && cleanupContext) {
            context.getBus().shutdown(true);
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

    private static long operationToReceiveTimeout(long operationTimeout) {
        return operationTimeout + 60l * 1000l;
    }

    private static String toDuration(long operationTimeout) {
        BigDecimal bdMs = BigDecimal.valueOf(operationTimeout);
        BigDecimal bdSec = bdMs.divide(BigDecimal.valueOf(1000));
        DecimalFormat df = new DecimalFormat("PT#.###S", new DecimalFormatSymbols(java.util.Locale.ROOT));
        return df.format(bdSec);
    }

    public static <T> T checkNotNull(T check, String msg) {
        if (check == null) {
            throw new NullPointerException(msg);
        }
        return check;
    }

}
