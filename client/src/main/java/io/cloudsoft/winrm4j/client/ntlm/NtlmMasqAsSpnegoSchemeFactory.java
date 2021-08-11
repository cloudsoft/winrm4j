package io.cloudsoft.winrm4j.client.ntlm;

import org.apache.http.auth.AuthScheme;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.protocol.HttpContext;

public class NtlmMasqAsSpnegoSchemeFactory extends NTLMSchemeFactory {

    @Override
    public AuthScheme create(HttpContext context) {
        return new NtlmMasqAsSpnegoScheme();
    }
}
