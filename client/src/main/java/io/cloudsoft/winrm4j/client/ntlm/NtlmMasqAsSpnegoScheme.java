package io.cloudsoft.winrm4j.client.ntlm;

import io.cloudsoft.winrm4j.client.encryption.CredentialsWithEncryption;
import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMEngine;
import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMEngineImpl;
import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMEngineImpl.Type3Message;
import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMScheme;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;

public class NtlmMasqAsSpnegoScheme extends NTLMScheme {

    public NtlmMasqAsSpnegoScheme(NTLMEngine engine) {
        super(engine);
    }

    public NtlmMasqAsSpnegoScheme() {
        this(newDefaultNtlmEngine());
    }

    static NTLMEngine newDefaultNtlmEngine() {
        return new NTLMEngineImpl() {
            @Override
            protected Integer getDefaultFlags() {
                return (super.getDefaultFlags()
                        | FLAG_REQUEST_SIGN | FLAG_REQUEST_SEAL
                        | FLAG_REQUEST_EXPLICIT_KEY_EXCH
//                        // python ntlm-auth/ntlm.py adds this also (but seems to work without this)
//                        | NegotiateFlags.NTLMSSP_NEGOTIATE_TARGET_INFO
                        )
//                        // super adds these which python seems not to (not sure, and works without removing them)
//                        & ~FLAG_REQUEST_NTLMv1
//                        & ~FLAG_REQUEST_NTLM2_SESSION
                        ;
            }
        };
    }

    @Override
    public String getSchemeName() {
        return AuthSchemes.SPNEGO;
    }

    @Override
    public Header authenticate(Credentials credentials, HttpRequest request)
            throws AuthenticationException {
        Header hdr = super.authenticate(credentials, request);
        return new BasicHeader(hdr.getName(), hdr.getValue().replace("NTLM", getSchemeName()));
    }

    @Override
    protected void handleSignAndSealData(final Credentials credentials,
                                         final HttpRequest request,
                                         final HttpContext context,
                                         final Type3Message signAndSealData) {

        if (credentials instanceof CredentialsWithEncryption) {
            ((CredentialsWithEncryption)credentials).setIsAuthenticated(true);
            if (signAndSealData!=null && signAndSealData.getExportedSessionKey()!=null) {
                new NtlmKeys(signAndSealData).apply((CredentialsWithEncryption) credentials);
            }
        }
    }

}
