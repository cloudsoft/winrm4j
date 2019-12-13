package io.cloudsoft.winrm4j.client.spnego;

import org.apache.http.auth.AuthScheme;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.protocol.HttpContext;

public class WsmanSPNegoSchemeFactory extends SPNegoSchemeFactory {
    
    public WsmanSPNegoSchemeFactory() {
        super(true, true);
    }
    
    @Override
    public AuthScheme create(final HttpContext context) {
        return new WsmanSPNegoScheme(isStripPort(), isUseCanonicalHostname());
    }    
}
