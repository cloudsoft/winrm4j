package io.cloudsoft.winrm4j.client.spnego;

import org.apache.http.auth.AuthScheme;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.protocol.HttpContext;

public class WsmanViaSpnegoSchemeFactory extends SPNegoSchemeFactory {
    
    public WsmanViaSpnegoSchemeFactory() {
        super(true, true);
    }
    
    @Override
    public AuthScheme create(final HttpContext context) {
        return new WsmanViaSpnegoScheme(isStripPort(), isUseCanonicalHostname());
    }    
}
