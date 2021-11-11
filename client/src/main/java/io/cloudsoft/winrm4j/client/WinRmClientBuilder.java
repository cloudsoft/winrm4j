package io.cloudsoft.winrm4j.client;

import io.cloudsoft.winrm4j.client.encryption.AsyncHttpEncryptionAwareConduitFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLContext;

import org.apache.cxf.transport.http.HTTPConduitFactory;
import org.apache.http.auth.AuthScheme;
import org.apache.http.client.config.AuthSchemes;

import io.cloudsoft.winrm4j.client.retry.RetryPolicy;
import io.cloudsoft.winrm4j.client.retry.SimpleCounterRetryPolicy;
import io.cloudsoft.winrm4j.client.wsman.Locale;

public class WinRmClientBuilder {
    private static final java.util.Locale DEFAULT_LOCALE = java.util.Locale.US;
    /**
     * Timeout applied by default on client side for the opening of the socket (0 meaning infinite waiting).
     */
    // Default matches org.apache.cxf.transports.http.configuration.HTTPClientPolicy.getConnectionTimeout()
    public static final long DEFAULT_CONNECTION_TIMEOUT = 30L * 1000L;
    
    // Default matches org.apache.cxf.transports.http.configuration.HTTPClientPolicy.getConnectionRequestTimeout()
    public static final long DEFAULT_CONNECTION_REQUEST_TIMEOUT = 60L * 1000L;
    
    /**
     * Timeout applied by default on client side for the reading of the socket ({@code null} meaning automatically calculated from
     * the{@link #operationTimeout} by adding to it one minute).
     */
    public static final Long DEFAULT_RECEIVE_TIMEOUT = null;
    public static final Long DEFAULT_OPERATION_TIMEOUT = 60L * 1000L;
    public static final int DEFAULT_RETRIES_FOR_CONNECTION_FAILURES = 1;

    /**
     * Duration in seconds applied by default for the sleep between 2 retries after a connection failure.
     */
    public static final long DEFAULT_PAUSE_BETWEEN_RETRIES = 5;

    protected WinRmClientContext context;
    protected final URL endpoint;
    protected String authenticationScheme;
    protected String domain;
    protected String username;
    protected String password;
    protected String workingDirectory;
    protected Locale locale;
    protected long operationTimeout;
    protected Predicate<String> retryReceiveAfterOperationTimeout;
    protected long connectionTimeout;
    protected long connectionRequestTimeout;
    protected Long receiveTimeout;
    protected RetryPolicy failureRetryPolicy;
    protected Map<String, String> environment;

    protected boolean disableCertificateChecks;
    protected boolean allowChunking;
    protected HostnameVerifier hostnameVerifier;
    protected SSLSocketFactory sslSocketFactory;

    protected SSLContext sslContext;
    protected boolean requestNewKerberosTicket;
    protected PayloadEncryptionMode payloadEncryptionMode;
    protected Collection<String> targetAuthSchemes;
    protected AsyncHttpEncryptionAwareConduitFactory endpointConduitFactory;

    WinRmClientBuilder(String endpoint) {
        this(toUrlUnchecked(WinRmClient.checkNotNull(endpoint, "endpoint")));
    }

    WinRmClientBuilder(URL endpoint) {
        this.endpoint = WinRmClient.checkNotNull(endpoint, "endpoint");
        authenticationScheme(AuthSchemes.NTLM);
        locale(DEFAULT_LOCALE);
        operationTimeout(DEFAULT_OPERATION_TIMEOUT);
        retryReceiveAfterOperationTimeout(alwaysRetryReceiveAfterOperationTimeout());
        connectionTimeout(DEFAULT_CONNECTION_TIMEOUT);
        connectionRequestTimeout(DEFAULT_CONNECTION_REQUEST_TIMEOUT);
        receiveTimeout(DEFAULT_RECEIVE_TIMEOUT);
        retriesForConnectionFailures(DEFAULT_RETRIES_FOR_CONNECTION_FAILURES);
    }

    public WinRmClientBuilder authenticationScheme(String authenticationScheme) {
        this.authenticationScheme = WinRmClient.checkNotNull(authenticationScheme, "authenticationScheme");
        return this;
    }

    public WinRmClientBuilder credentials(String username, String password) {
        return credentials(null, username, password);
    }

    /**
     * Credentials to use for authentication
     */
    public WinRmClientBuilder credentials(String domain, String username, String password) {
        this.domain = domain;
        this.username = WinRmClient.checkNotNull(username, "username");
        this.password = WinRmClient.checkNotNull(password, "password");
        return this;
    }

    /**
     * @param locale The locale to run the process in
     */
    public WinRmClientBuilder locale(java.util.Locale locale) {
        Locale l = new Locale();
        l.setLang(WinRmClient.checkNotNull(locale, "locale").toLanguageTag());
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
    public WinRmClientBuilder operationTimeout(long operationTimeout) {
        this.operationTimeout = operationTimeout;
        return this;
    }

   /**
    * Timeout applied to connect the socket.
    *
    * @param connectionTimeout in milliseconds
    *                         default value {@link WinRmClientBuilder#DEFAULT_CONNECTION_TIMEOUT}
    * @see <a href="https://cxf.apache.org/javadoc/latest/org/apache/cxf/transports/http/configuration/HTTPClientPolicy.html#setConnectionTimeout-long-">HTTPClientPolicy#setConnectionTimeout</a>
    */
    public WinRmClientBuilder connectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    /**
     * Timeout applied to requesting a connection from the connection manager.
     *
     * @param connectionRequestTimeout in milliseconds
     *                                 default value {@link WinRmClientBuilder#DEFAULT_CONNECTION_REQUEST_TIMEOUT}
     * @see <a href="https://cxf.apache.org/javadoc/latest/org/apache/cxf/transports/http/configuration/HTTPClientPolicy.html#setConnectionRequestTimeout-long-">HTTPClientPolicy#setConnectionRequestTimeout</a>
     */
     public WinRmClientBuilder connectionRequestTimeout(long connectionRequestTimeout) {
         this.connectionRequestTimeout = connectionRequestTimeout;
         return this;
     }

    /**
     * Timeout applied to read the socket.
     *
     * @param receiveTimeout in milliseconds
     *                         default value {@link WinRmClientBuilder#DEFAULT_RECEIVE_TIMEOUT}
     * @see <a href="https://cxf.apache.org/javadoc/latest/org/apache/cxf/transports/http/configuration/HTTPClientPolicy.html#setReceiveTimeout-long-">HTTPClientPolicy#setReceiveTimeout</a>
     */
     public WinRmClientBuilder receiveTimeout(Long receiveTimeout) {
         this.receiveTimeout = receiveTimeout;
         return this;
     }

    /**
     * @param retryReceiveAfterOperationTimeout define if a new Receive request will be send when the server returns
     *      a fault with the code {@link ShellCommand#WSMAN_FAULT_CODE_OPERATION_TIMEOUT_EXPIRED}.
     *        Default value {@link #alwaysRetryReceiveAfterOperationTimeout()}.
     */
    public WinRmClientBuilder retryReceiveAfterOperationTimeout(Predicate<String> retryReceiveAfterOperationTimeout) {
        this.retryReceiveAfterOperationTimeout = retryReceiveAfterOperationTimeout;
        return this;
    }

    public static Predicate<String> alwaysRetryReceiveAfterOperationTimeout() {
        return x -> true;
    }

    public static Predicate<String> neverRetryReceiveAfterOperationTimeout() {
        return x -> false;
    }

    /**
     * @param maxRetries How many times to retry the command before giving up in case of failure (exception).
     *                   Default is 1.
     * @deprecated since 0.8.0; replaced by {@link #failureRetryPolicy(RetryPolicy)}
     */
    @Deprecated
    public WinRmClientBuilder retriesForConnectionFailures(int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("retriesConnectionFailure should be zero or more");
        }
        failureRetryPolicy(simpleCounterRetryPolicy(maxRetries));
        return this;
    }

    public WinRmClientBuilder failureRetryPolicy(RetryPolicy failureRetryPolicy) {
        this.failureRetryPolicy = failureRetryPolicy;
        return this;
    }

    public static RetryPolicy simpleCounterRetryPolicy(int maxRetries) {
        return new SimpleCounterRetryPolicy(maxRetries, DEFAULT_PAUSE_BETWEEN_RETRIES, TimeUnit.SECONDS);
    }

    /**
     * @param disableCertificateChecks Skip trusted certificate and domain (CN) checks.
     *        Used when working with self-signed certificates. Use {@link #hostnameVerifier(HostnameVerifier)}
     *        for a more precise white-listing of server certificates.
     */
    public WinRmClientBuilder disableCertificateChecks(boolean disableCertificateChecks) {
        this.disableCertificateChecks = disableCertificateChecks;
        return this;
    }

    public WinRmClientBuilder allowChunking(boolean allowChunking) {
        this.allowChunking = allowChunking;
        return this;
    }

    /**
     * @param workingDirectory the working directory of the process
     */
    public WinRmClientBuilder workingDirectory(String workingDirectory) {
        this.workingDirectory = WinRmClient.checkNotNull(workingDirectory, "workingDirectory");
        return this;
    }

    /**
     * @param environment variables to pass to the command
     */
    public WinRmClientBuilder environment(Map<String, String> environment) {
        this.environment = WinRmClient.checkNotNull(environment, "environment");
        return this;
    }

    /**
     * @param hostnameVerifier override the default HostnameVerifier allowing
     *        users to add custom validation logic. Used when the default rules for URL
     *        hostname verification fail. Use {@link #disableCertificateChecks(boolean)} to
     *        disable certificate checks for all host names.
     */
    public WinRmClientBuilder hostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
        return this;
    }

    /**
     * @param sslSocketFactory SSL Socket Factory to use
     */
    public WinRmClientBuilder sslSocketFactory(SSLSocketFactory sslSocketFactory) {
    	this.sslSocketFactory = sslSocketFactory;
    	return this;
    }
    
    /**
     * @param sslContext override the default SSLContext
     */
    public WinRmClientBuilder sslContext(SSLContext sslContext) {
    	this.sslContext = sslContext;
    	return this;
    }
    
    /**
     * @param context is a shared {@link WinRmClientContext} object which allows connection
     *        reuse across {@link WinRmClient} invocations. If not set one will be created
     *        for each {@link WinRmClient} instance.
     */
    public WinRmClientBuilder context(WinRmClientContext context) {
        this.context = context;
        return this;
    }

    /**
     * Set this parameter to {@code true} for requesting from the KDC a fresh Kerberos TGT with credentials set to the builder.
     * In this case the configuration defined in the JAAS configuration file will be ignored.
     * By default this parameter is set to {@code false}. 
     */
    public WinRmClientBuilder requestNewKerberosTicket(boolean requestNewKerberosTicket) {
        this.requestNewKerberosTicket = requestNewKerberosTicket;
        return this;
    }

    /**
     * Create a WinRmClient
     */
    public WinRmClient build() {
        return new WinRmClient(this);
    }

    protected static URL toUrlUnchecked(String endpoint) {
        try {
            return new URL(endpoint);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public WinRmClientBuilder payloadEncryptionMode(PayloadEncryptionMode payloadEncryptionMode) {
        this.payloadEncryptionMode = payloadEncryptionMode;
        return this;
    }

    public PayloadEncryptionMode payloadEncryptionMode() {
        return payloadEncryptionMode!=null ? payloadEncryptionMode : PayloadEncryptionMode.OPTIONAL;
    }

    public WinRmClientBuilder targetAuthSchemes(Collection<String> targetAuthSchemes) {
        this.targetAuthSchemes = targetAuthSchemes;
        return this;
    }

    public Collection<String> targetAuthSchemes() {
        return targetAuthSchemes;
    }

}
