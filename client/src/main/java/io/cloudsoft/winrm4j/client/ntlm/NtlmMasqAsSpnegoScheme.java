package io.cloudsoft.winrm4j.client.ntlm;

import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMEngine;
import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMEngineImpl;
import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMScheme;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.message.BasicHeader;

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
            protected int getFlagsForType3Msg(Type2Message t2m) {
                return super.getFlagsForType3Msg(t2m)
                        | FLAG_REQUEST_SIGN | FLAG_REQUEST_EXPLICIT_KEY_EXCH
                        //| FLAG_REQUEST_NTLM2_SESSION
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
}
