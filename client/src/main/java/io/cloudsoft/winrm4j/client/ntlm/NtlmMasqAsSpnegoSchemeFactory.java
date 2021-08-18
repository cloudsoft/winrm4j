package io.cloudsoft.winrm4j.client.ntlm;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import org.apache.http.auth.AuthScheme;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.protocol.HttpContext;

public class NtlmMasqAsSpnegoSchemeFactory extends NTLMSchemeFactory {

    private final PayloadEncryptionMode payloadEncryptionMode;

    public NtlmMasqAsSpnegoSchemeFactory() {
        this(PayloadEncryptionMode.OPTIONAL);
    }

    public NtlmMasqAsSpnegoSchemeFactory(PayloadEncryptionMode payloadEncryptionMode) {
        this.payloadEncryptionMode = payloadEncryptionMode;
    }

    @Override
    public AuthScheme create(HttpContext context) {
        return new NtlmMasqAsSpnegoScheme(payloadEncryptionMode);
    }
}
