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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceFeature;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import io.cloudsoft.winrm4j.client.ntlm.SpNegoNTLMSchemeFactory;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.ClientImpl;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsClientFactoryBean;
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
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.w3c.dom.Element;

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
    private static final String COMMAND_STATE_DONE = "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/CommandState/Done";
    private static final int MAX_ENVELOPER_SIZE = 153600;
    private static final String RESOURCE_URI = "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/cmd";

    private final String authenticationScheme;
    private URL endpoint;
    private String username;
    private String password;
    private String workingDirectory;
    private Locale locale;
    private String operationTimeout;
    private Map<String, String> environment;

    private WinRm winrm;
    private String shellId;
    private SelectorSetType shellSelector;

    private boolean disableCertificatesChecks;

    public static Builder builder(URL endpoint) {
        return new Builder(endpoint, AuthSchemes.BASIC);
    }

    public static Builder builder(String endpoint) {
        return new Builder(endpoint, AuthSchemes.BASIC);
    }

    public static Builder builder(URL endpoint, String authenticationScheme) {
        return new Builder(endpoint, authenticationScheme);
    }

    public static Builder builder(String endpoint, String authenticationScheme) {
        return new Builder(endpoint, authenticationScheme);
    }

    public static class Builder {
        private static final java.util.Locale DEFAULT_LOCALE = java.util.Locale.US;
        private static final int DEFAULT_OPERATION_TIMEOUT = 60000;
        private WinRmClient client;
        public Builder(URL endpoint, String authenticationScheme) {
            client = new WinRmClient(endpoint, authenticationScheme);
        }
        public Builder(String endpoint, String authenticationScheme) {
            this(toUrlUnchecked(checkNotNull(endpoint, "endpoint")), authenticationScheme);
        }
        public Builder credentials(String username, String password) {
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
        public Builder operationTimeout(long operationTimeout) {
            client.operationTimeout = toDuration(operationTimeout);
            return this;
        }
        public Builder setDisableCertificatesChecks(boolean disableCertificatesChecks) {
            client.disableCertificatesChecks = disableCertificatesChecks;
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
                operationTimeout(DEFAULT_OPERATION_TIMEOUT);
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
        this.authenticationScheme = authenticationScheme != null ? authenticationScheme : AuthSchemes.BASIC;
        this.endpoint = endpoint;

        // Needed to be async client according to http://cxf.apache.org/docs/asynchronous-client-http-transport.html
        // TODO consider using async client for Basic authentication
        if (this.authenticationScheme.equals(AuthSchemes.NTLM)) {
            Bus bus = BusFactory.getDefaultBus();
            bus.getProperties().put(AsyncHTTPConduit.USE_ASYNC, Boolean.TRUE);
            bus.getProperties().put(AsyncHTTPConduitFactory.USE_POLICY, "ALWAYS");
        }
    }

    public int command(String cmd, Writer out, Writer err) {
        checkNotNull(cmd, "command");
        WinRm service = getService();

        CommandLine cmdLine = new CommandLine();
        cmdLine.setCommand(cmd);
        OptionSetType optSetCmd = new OptionSetType();
        OptionType optConsolemodeStdin = new OptionType();
        optConsolemodeStdin.setName("WINRS_CONSOLEMODE_STDIN");
        optConsolemodeStdin.setValue("TRUE");
        optSetCmd.getOption().add(optConsolemodeStdin);
        OptionType optSkipCmdShell = new OptionType();
        optSkipCmdShell.setName("WINRS_SKIP_CMD_SHELL");
        optSkipCmdShell.setValue("FALSE");
        optSetCmd.getOption().add(optSkipCmdShell);

        //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
        setActionToContext((BindingProvider) service, "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/Command");
        CommandResponse cmdResponse = service.command(cmdLine, RESOURCE_URI, MAX_ENVELOPER_SIZE, operationTimeout, locale, shellSelector, optSetCmd);
        String commandId = cmdResponse.getCommandId();

        try {
            return receiveCommand(commandId, out, err);
        } finally {
            releaseCommand(commandId);
        }
    }

    // TODO fix CXF to not set a wrong action https://issues.apache.org/jira/browse/CXF-4647
    private void setActionToContext(BindingProvider bp, String action) {
        AttributedURIType attrUri = new AttributedURIType();
        attrUri.setValue(action);
        ((AddressingProperties)bp.getRequestContext().get("javax.xml.ws.addressing.context")).setAction(attrUri);
    }

    private void releaseCommand(String commandId) {
        Signal signal = new Signal();
        signal.setCommandId(commandId);
        signal.setCode("http://schemas.microsoft.com/wbem/wsman/1/windows/shell/signal/terminate");

        //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
        setActionToContext((BindingProvider) winrm, "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/Signal");
        winrm.signal(signal, RESOURCE_URI, MAX_ENVELOPER_SIZE, operationTimeout, locale, shellSelector);
    }

    private int receiveCommand(String commandId, Writer out, Writer err) {
        while(true) {
            Receive receive = new Receive();
            DesiredStreamType stream = new DesiredStreamType();
            stream.setCommandId(commandId);
            stream.setValue("stdout stderr");
            receive.setDesiredStream(stream);

            //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
            setActionToContext((BindingProvider) winrm, "http://schemas.microsoft.com/wbem/wsman/1/windows/shell/Receive");
            ReceiveResponse receiveResponse = winrm.receive(receive, RESOURCE_URI, MAX_ENVELOPER_SIZE, operationTimeout, locale, shellSelector);
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
            CommandStateType state = receiveResponse.getCommandState();
            if (COMMAND_STATE_DONE.equals(state.getState())) {
                return state.getExitCode().intValue();
            }
        }
    }
    
    private WinRm getService() {
        if (winrm != null) {
            return winrm;
        } else {
            return createService();
        }
    }

    private class JaxWsClientWithNegotiateFactoryBean extends JaxWsClientFactoryBean {
        @Override
        protected Client createClient(Endpoint ep) {
            return new ClientImpl(getBus(), ep, getConduitSelector());
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

        // Needed to be async according to http://cxf.apache.org/docs/asynchronous-client-http-transport.html
//        Bus bus = BusFactory.getDefaultBus();
//        bus.setProperty(AsyncHTTPConduit.USE_ASYNC, Boolean.TRUE);

        Client client = ClientProxy.getClient(winrm);
        ServiceInfo si = client.getEndpoint().getEndpointInfo().getService();
        si.setProperty("soap.force.doclit.bare", true);
        si.setProperty("soap.no.validate.parts", true);

        BindingProvider bp = (BindingProvider)winrm;

        Map<String, Object> requestContext = bp.getRequestContext();
        AddressingProperties maps = new AddressingProperties(VersionTransformer.Names200408.WSA_NAMESPACE_NAME);
        requestContext.put(JAXWSAConstants.CLIENT_ADDRESSING_PROPERTIES, maps);

        bp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint.toExternalForm());

        switch(authenticationScheme) {
            case AuthSchemes.BASIC:
                bp.getRequestContext().put(BindingProvider.USERNAME_PROPERTY, username);
                bp.getRequestContext().put(BindingProvider.PASSWORD_PROPERTY, password);
            case AuthSchemes.NTLM:
                Credentials creds = new NTCredentials(username, password, null, null);

                CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                        new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                        creds);

                Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
                        .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                        // Uncomment to support Negotiate(SPNEGO)+Kerberos, only one of the next two items allowed
                        // .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory())
                        .register(AuthSchemes.SPNEGO, new SpNegoNTLMSchemeFactory())
                        .register(AuthSchemes.KERBEROS, new KerberosSchemeFactory())//
                        .build();

                bp.getRequestContext().put(Credentials.class.getName(), creds);
                bp.getRequestContext().put("http.autoredirect", true);

                bp.getRequestContext().put(AuthSchemeProvider.class.getName(), authSchemeRegistry);
                HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
//            httpClientPolicy.setConnectionTimeout(36000);
                httpClientPolicy.setAllowChunking(false);

                AsyncHTTPConduit httpClient = (AsyncHTTPConduit) client.getConduit();

                TLSClientParameters tlsClientParameters = new TLSClientParameters();
                if (disableCertificatesChecks) {
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
                }
                httpClient.setTlsClientParameters(tlsClientParameters);

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

        OptionSetType optSetCreate = new OptionSetType();
        OptionType optNoProfile = new OptionType();
        optNoProfile.setName("WINRS_NOPROFILE");
        optNoProfile.setValue("FALSE");
        optSetCreate.getOption().add(optNoProfile);
        OptionType optCodepage = new OptionType();
        optCodepage.setName("WINRS_CODEPAGE");
        optCodepage.setValue("437");
        optSetCreate.getOption().add(optCodepage);

        //TODO use different instances of service http://cxf.apache.org/docs/developing-a-consumer.html#DevelopingaConsumer-SettingConnectionPropertieswithContexts
        setActionToContext((BindingProvider) winrm, "http://schemas.xmlsoap.org/ws/2004/09/transfer/Create");
        Holder<Shell> holder = new Holder<>(shell);
        ResourceCreated resourceCreated = winrm.create(holder, RESOURCE_URI, MAX_ENVELOPER_SIZE, operationTimeout, locale, optSetCreate);
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
            winrm.delete(new Delete(), RESOURCE_URI, MAX_ENVELOPER_SIZE, operationTimeout, locale, shellSelector);
        }
    }

    private static <T> T checkNotNull(T check, String msg) {
        if (check == null) {
            throw new NullPointerException(msg);
        }
        return check;
    }
}
