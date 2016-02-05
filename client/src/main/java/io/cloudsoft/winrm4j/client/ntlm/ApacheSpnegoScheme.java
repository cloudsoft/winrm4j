package io.cloudsoft.winrm4j.client.ntlm;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.impl.auth.NTLMScheme;
import org.apache.http.message.BasicHeader;

public class ApacheSpnegoScheme extends NTLMScheme{

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
