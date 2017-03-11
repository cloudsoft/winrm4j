package io.cloudsoft.winrm4j.client;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
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
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.soap.SOAPFaultException;
import javax.xml.ws.spi.Provider;
import javax.xml.ws.spi.ServiceDelegate;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.cxf.Bus;
import org.apache.cxf.Bus.BusState;
import org.apache.cxf.BusFactory;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.spi.ProviderImpl;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.ws.addressing.AddressingProperties;
import org.apache.cxf.ws.addressing.AttributedURIType;
import org.apache.cxf.ws.addressing.JAXWSAConstants;
import org.apache.cxf.ws.addressing.VersionTransformer;
import org.apache.cxf.ws.addressing.WSAddressingFeature;
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
 * TODO confirm if parallel commands can be called in parallel in one shell (probably not)!
 */
public class WinRmClient {
    private static final Logger LOG = LoggerFactory.getLogger(WinRmClient.class.getName());

    static final int MAX_ENVELOPER_SIZE = 153600;
    static final String RESOURCE_URI = "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/cmd";

    private final String authenticationScheme;
    private URL endpoint;
    private String domain;
    private String username;
    private String password;
    private String workingDirectory;
    private Locale locale;
    // Can be changed throughout object's lifetime
    private String operationTimeout;
    private Long receiveTimeout;
    private Integer retriesForConnectionFailures;
    private Map<String, String> environment;

    private WinRmClientContext context;
    private boolean cleanupContext;

    private WinRm winrm;
    private String shellId;

    private boolean disableCertificateChecks;
    private HostnameVerifier hostnameVerifier;

    private ShellCommand shellCommand;
    
    /**
     * Create a WinRmClient builder
     *
     * @param endpoint - the url of the WSMAN service in the format https://machine:5986/wsman
     */
    public static Builder builder(URL endpoint) {
        return new Builder(endpoint, AuthSchemes.NTLM);
    }

    /**
     * Create a WinRmClient builder
     *
     * @param endpoint - the url of the WSMAN service in the format https://machine:5986/wsman
     */
    public static Builder builder(String endpoint) {
        return new Builder(endpoint, AuthSchemes.NTLM);
    }

    /**
     * Create a WinRmClient builder
     *
     * @param endpoint - the url of the WSMAN service in the format https://machine:5986/wsman
     * @param authenticationScheme - one of Basic, NTLM, Kerberos. Default is NTLM (with Negotiate).
     */
    public static Builder builder(URL endpoint, String authenticationScheme) {
        return new Builder(endpoint, authenticationScheme);
    }

    /**
     * Create a WinRmClient builder
     *
     * @param endpoint - the url of the WSMAN service in the format https://machine:5986/wsman
     * @param authenticationScheme - one of Basic, NTLM, Kerberos. Default is NTLM (with Negotiate).
     */
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

        /**
         * Credentials to use for authentication
         */
        public Builder credentials(String domain, String username, String password) {
            client.domain = domain;
            client.username = checkNotNull(username, "username");
            client.password = checkNotNull(password, "password");
            return this;
        }

        /**
         * @param locale The locale to run the process in
         */
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

        /**
         * @param retriesConnectionFailures How many times to retry the command before giving up in case of failure (exception).
         *        Default is 16.
         */
        public Builder retriesForConnectionFailures(Integer retriesConnectionFailures) {
            if (retriesConnectionFailures < 1) {
                throw new IllegalArgumentException("retriesConnectionFailure should be one or more");
            }
            client.retriesForConnectionFailures = retriesConnectionFailures;
            return this;
        }


        /**
         * @param disableCertificateChecks Skip trusted certificate and domain (CN) checks.
         *        Used when working with self-signed certificates. Use {@link #hostnameVerifier(HostnameVerifier)}
         *        for a more precise white-listing of server certificates.
         */
        public Builder disableCertificateChecks(boolean disableCertificateChecks) {
            client.disableCertificateChecks = disableCertificateChecks;
            return this;
        }

        /**
         * @param workingDirectory the working directory of the process
         */
        public Builder workingDirectory(String workingDirectory) {
            client.workingDirectory = checkNotNull(workingDirectory, "workingDirectory");
            return this;
        }

        /**
         * @param environment variables to pass to the command
         */
        public Builder environment(Map<String, String> environment) {
            client.environment = checkNotNull(environment, "environment");
            return this;
        }

        /**
         * @param hostnameVerifier override the default HostnameVerifier allowing
         *        users to add custom validation logic. Used when the default rules for URL
         *        hostname verification fail. Use {@link #disableCertificateChecks(boolean)} to
         *        disable certificate checks for all host names.
         */
        public Builder hostnameVerifier(HostnameVerifier hostnameVerifier) {
            client.hostnameVerifier = hostnameVerifier;
            return this;
        }

        /**
         * @param context is a shared {@link WinRmClientContext} object which allows connection
         *        reuse across {@link WinRmClient} invocations. If not set one will be created
         *        for each {@link WinRmClient} instance.
         */
        public Builder context(WinRmClientContext context) {
            client.context = context;
            return this;
        }

        /**
         * Create a WinRmClient
         */
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
    }

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

    @Deprecated
    public int getNumberOfReceiveCalls() {
        return shellCommand.getNumberOfReceiveCalls();
    }

    private synchronized WinRm initService() {
        if (winrm != null) {
            return winrm;
        } else {

            if (context != null) {
                cleanupContext = false;
            } else {
                context = WinRmClientContext.newInstance();
                cleanupContext = true;
            }

            Bus prevBus = BusFactory.getAndSetThreadDefaultBus(context.getBus());
            try {
                // The default thread bus is set on the ClientImpl and used for further requests
                return createService();
            } finally {
                if (BusFactory.getThreadDefaultBus(false) != prevBus) {
                    BusFactory.setThreadDefaultBus(prevBus);
                }
            }
        }
    }

    private synchronized WinRm createService() {
        if (winrm != null) return winrm;
    
        RuntimeException lastException = null;
        
        try {
            winrm = null;
            doCreateServiceWithBean();
            return winrm;
        } catch (RuntimeException e) {
            LOG.warn("Error creating WinRm service with mbean strategy (trying other strategies): "+e, e);
            lastException = e;
        }
        
        /*
         * It's tedious getting the right Provider esp in OSGi.
         * 
         * We've tried a bunch of strategies, with the most promising tried here,
         * and detailed notes below.
         */
        
        try {
            winrm = null;
            doCreateServiceWithReflectivelySetDelegate();
            getAddressingProperties((BindingProvider) winrm);
            return winrm;
        } catch (RuntimeException e) {
            LOG.warn("Error creating WinRm service with reflective delegate (trying other strategies): "+e, e);
            lastException = e;
        }
        
        try {
            winrm = null;
            doCreateServiceNormal();
            return winrm;
        } catch (RuntimeException e) {
            LOG.warn("Error creating WinRm service with many strategies (giving up): "+e, e);
            lastException = e;
        }
        
        throw lastException;

        // works, but addressing context might be null
//        doCreateServiceWithReflectivelySetDelegate();
        
        // fails with NPE setting up feature (Bus is null)
        // but it works if you install into karaf:  <feature>cxf-ws-addr</feature>
//        doCreateServiceWithBean();
        // also fails with NPE without that feature installed; untested with it
//        doCreateServiceInSpecialClassLoader( ProviderImpl.class.getClassLoader() );
//        doCreateServiceInSpecialClassLoader( JaxWsProxyFactoryBean.class.getClassLoader() );
        
        // fails in OSGi with CNF error when FactoryFinder tries to load the CXF impl 
//        doCreateServiceWithSystemPropertySet();
        
        // fails in OSGi, with original error:
        // com.sun.xml.internal.ws.client.sei.SEIStub cannot be cast to org.apache.cxf.frontend.ClientProxy
        // at: ClientProxy.getClient(...);
//        doCreateServiceNormal();
//        doCreateServiceInSpecialClassLoader( WinRmClient.class.getClassLoader() );
    }
    
    // normal approach
    private synchronized void doCreateServiceNormal() {
        WinRmService service = doCreateService_1_CreateMinimalServiceInstance();
        Client client = doCreateService_2_GetClient(service);
        doCreateService_3_InitializeClientAndService(client);
    }

    //  sys prop approach
    @SuppressWarnings("unused")
    private void doCreateServiceWithSystemPropertySet() {
        System.setProperty("javax.xml.ws.spi.Provider", ProviderImpl.class.getName());
        doCreateServiceNormal();
    }
    
    // force delegate
    // based on http://stackoverflow.com/a/31892206/109079
    private void doCreateServiceWithReflectivelySetDelegate() {
        WinRmService service = doCreateService_1_CreateMinimalServiceInstance();

        try {
            Field delegateField = javax.xml.ws.Service.class.getDeclaredField("delegate"); //ALLOW CXF SPECIFIC SERVICE DELEGATE ONLY!
            delegateField.setAccessible(true);
            ServiceDelegate previousDelegate = (ServiceDelegate) delegateField.get(service);
            if (!previousDelegate.getClass().getName().contains("cxf")) {
                ServiceDelegate serviceDelegate = ((Provider) Class.forName("org.apache.cxf.jaxws.spi.ProviderImpl").newInstance())
                        .createServiceDelegate(WinRmService.WSDL_LOCATION, WinRmService.SERVICE, service.getClass());
                delegateField.set(service, serviceDelegate);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reflectively setting CXF WS service delegate", e);
        }
        Client client = doCreateService_2_GetClient(service);
        doCreateService_3_InitializeClientAndService(client);
    }
    
    // approach using JaxWsProxyFactoryBean
    
    private synchronized void doCreateServiceWithBean() {
        Client client = doCreateServiceWithBean_Part1();
        doCreateService_3_InitializeClientAndService(client);
    }
    
    private synchronized Client doCreateServiceWithBean_Part1() {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.getClientFactoryBean().getServiceFactory().setWsdlURL(WinRmService.WSDL_LOCATION);
        factory.setServiceName(WinRmService.SERVICE);
        factory.setEndpointName(WinRmService.WinRmPort);
        factory.setFeatures(Arrays.asList((Feature)newMemberSubmissionAddressingFeature()));
        factory.setBus(context.getBus());
        winrm = factory.create(WinRm.class);
        
        return ClientProxy.getClient(winrm);
    }

    
    // approach using CCL

    @SuppressWarnings("unused")
    private synchronized void doCreateServiceInSpecialClassLoader(ClassLoader cl) {
        
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        
        Client client;
        try {
            // use CXF classloader in order to avoid errors in osgi
            // as described at http://stackoverflow.com/questions/24289151/eclipse-rcp-and-apache-cxf
            // do this for as short a time as possible to prevent other potential issues
            Thread.currentThread().setContextClassLoader(cl);
            
            WinRmService service = doCreateService_1_CreateMinimalServiceInstance();
            client = doCreateService_2_GetClient(service);
            
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
        
        doCreateService_3_InitializeClientAndService(client);
    }
        
    private synchronized WinRmService doCreateService_1_CreateMinimalServiceInstance() {
        return new WinRmService();
    }
    private synchronized Client doCreateService_2_GetClient(WinRmService service) {
//        JaxWsDynamicClientFactory dcf = JaxWsDynamicClientFactory.newInstance();
//        Client client = dcf.createClient("people.wsdl", classLoader);
        winrm = service.getWinRmPort(
                // * Adds WS-Addressing headers and uses the submission spec namespace
                //   http://schemas.xmlsoap.org/ws/2004/08/addressing
                newMemberSubmissionAddressingFeature());

        return ClientProxy.getClient(winrm);
    }
    
    private synchronized void doCreateService_3_InitializeClientAndService(Client client) {
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

    public ShellCommand createShell() {
        initService();

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
        shellId = getShellId(resourceCreated);

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
                context = null;
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
