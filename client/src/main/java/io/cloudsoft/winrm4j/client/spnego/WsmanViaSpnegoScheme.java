package io.cloudsoft.winrm4j.client.spnego;

import org.apache.http.auth.Credentials;
import org.apache.http.auth.KerberosCredentials;
import org.apache.http.impl.auth.SPNegoScheme;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

/**
 * The {@link SPNegoScheme} use the scheme of the URI (HTTP or HTTPS) to build the SPN of WinRM.
 * Thus when we access to the HTTPS listener of the WinRM service the SPN built is HTTPS/HOSTNAME which doesn't exist.
 * When WinRM is configured the SPN automatically added is: WSMAN/HOSTNAME.
 */
public class WsmanViaSpnegoScheme extends SPNegoScheme {

    public WsmanViaSpnegoScheme(final boolean stripPort, final boolean useCanonicalHostname) {
        super(stripPort, useCanonicalHostname);
    }
    
    /**
     * Copied form {@link org.apache.http.impl.auth.GGSSchemeBase#generateGSSToken}.
     * The variable "service" must be set to "WSMAN" but this variable is private.
     */
    @Override
    protected byte[] generateGSSToken(
            final byte[] input, final Oid oid, final String authServer,
            final Credentials credentials) throws GSSException {
        byte[] inputBuff = input;
        if (inputBuff == null) {
            inputBuff = new byte[0];
        }
        final GSSManager manager = getManager();

        // only seems to work if default realm is available on the system (?)
        final GSSName serverName = manager.createName("WSMAN" + "@" + authServer, GSSName.NT_HOSTBASED_SERVICE);

        final GSSCredential gssCredential;
        if (credentials instanceof KerberosCredentials) {
            gssCredential = ((KerberosCredentials) credentials).getGSSCredential();
        } else {
            gssCredential = null;
        }

        final GSSContext gssContext = manager.createContext(
                serverName.canonicalize(oid), oid, gssCredential, GSSContext.DEFAULT_LIFETIME);
        gssContext.requestMutualAuth(true);
        gssContext.requestCredDeleg(true);
        return gssContext.initSecContext(inputBuff, 0, inputBuff.length);
    }

}
