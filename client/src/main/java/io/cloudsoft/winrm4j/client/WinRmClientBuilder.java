package io.cloudsoft.winrm4j.client;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLContext;

import org.apache.http.client.config.AuthSchemes;

import io.cloudsoft.winrm4j.client.wsman.Locale;

public class WinRmClientBuilder {
    private static final java.util.Locale DEFAULT_LOCALE = java.util.Locale.US;
    private static final Long DEFAULT_OPERATION_TIMEOUT = 60l * 1000l;
    private static final int DEFAULT_RETRIES_FOR_CONNECTION_FAILURES = 1;

    protected WinRmClientContext context;
    protected final URL endpoint;
    protected String authenticationScheme;
    protected String domain;
    protected String username;
    protected String password;
    protected String workingDirectory;
    protected Locale locale;
    protected long operationTimeout;
    protected Long receiveTimeout;
    protected int retriesForConnectionFailures;
    protected Map<String, String> environment;

    protected boolean disableCertificateChecks;
    protected HostnameVerifier hostnameVerifier;
    protected SSLSocketFactory sslSocketFactory;
    
    protected SSLContext sslContext;
    
    WinRmClientBuilder(String endpoint) {
        this(toUrlUnchecked(WinRmClient.checkNotNull(endpoint, "endpoint")));
    }

    WinRmClientBuilder(URL endpoint) {
        this.endpoint = WinRmClient.checkNotNull(endpoint, "endpoint");
        authenticationScheme(AuthSchemes.NTLM);
        locale(DEFAULT_LOCALE);
        operationTimeout(DEFAULT_OPERATION_TIMEOUT);
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
        this.operationTimeout = WinRmClient.checkNotNull(operationTimeout, "operationTimeout");
        return this;
    }

    /**
     * @param retriesConnectionFailures How many times to retry the command before giving up in case of failure (exception).
     *        Default is 16.
     */
    public WinRmClientBuilder retriesForConnectionFailures(int retriesConnectionFailures) {
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
    public WinRmClientBuilder disableCertificateChecks(boolean disableCertificateChecks) {
        this.disableCertificateChecks = disableCertificateChecks;
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
<<<<<<< HEAD
     * @param sslSocketFactory SSL Socket Factory to use
     */
    public WinRmClientBuilder sslSocketFactory(SSLSocketFactory sslSocketFactory) {
    	this.sslSocketFactory = sslSocketFactory;
    	return this;
    }
    
    /**
||||||| merged common ancestors
=======
     * @param sslContext override the default SSLContext
     */
    public WinRmClientBuilder sslContext(SSLContext sslContext) {
    	this.sslContext = sslContext;
    	return this;
    }
    
    /**
>>>>>>> a172a58a2fe5b6176690470b75ae5b30933abf7f
     * @param context is a shared {@link WinRmClientContext} object which allows connection
     *        reuse across {@link WinRmClient} invocations. If not set one will be created
     *        for each {@link WinRmClient} instance.
     */
    public WinRmClientBuilder context(WinRmClientContext context) {
        this.context = context;
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
}
