package io.cloudsoft.winrm4j.client.ntlm;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import io.cloudsoft.winrm4j.client.encryption.CredentialsWithEncryption;
import io.cloudsoft.winrm4j.client.ntlm.NtlmKeys.NegotiateFlags;
import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMEngine;
import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMEngineImpl;
import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMEngineImpl.Type3Message;
import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMScheme;
import java.util.function.Function;
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
        this(PayloadEncryptionMode.OPTIONAL);
    }

    public NtlmMasqAsSpnegoScheme(PayloadEncryptionMode payloadEncryptionMode) {
        this(newDefaultNtlmEngine(
                !payloadEncryptionMode.isPermitted()
                    ? null
                    : flags -> (flags
                        | NegotiateFlags.NTLMSSP_NEGOTIATE_SIGN
                        | NegotiateFlags.NTLMSSP_NEGOTIATE_SEAL
                        | NegotiateFlags.NTLMSSP_NEGOTIATE_KEY_EXCH

//                        // python ntlm-auth/ntlm.py adds this also (but seems to work without this)
//                        | NegotiateFlags.NTLMSSP_NEGOTIATE_TARGET_INFO
        )
//                        // super adds these which python seems not to (not sure, and works without removing them)
//                        & ~FLAG_REQUEST_NTLMv1
//                        & ~FLAG_REQUEST_NTLM2_SESSION
        ));
    }

    static NTLMEngine newDefaultNtlmEngine(Function<Long,Long> flagModifier) {
        return new NTLMEngineImpl() {
            @Override
            protected Integer getDefaultFlags() {
                Long flags = (long) super.getDefaultFlags();
                if (flagModifier!=null) {
                    flags = flagModifier.apply(flags);
                }
                return flags.intValue();
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
