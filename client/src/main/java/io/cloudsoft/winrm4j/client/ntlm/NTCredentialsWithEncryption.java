package io.cloudsoft.winrm4j.client.ntlm;

import io.cloudsoft.winrm4j.client.encryption.AsyncHttpEncryptionAwareConduit.EncryptionAwareHttpEntity;
import io.cloudsoft.winrm4j.client.encryption.WinrmEncryptionUtils;
import io.cloudsoft.winrm4j.client.encryption.WinrmEncryptionUtils.CryptoHandler;
import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMEngineImpl.Type3Message;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.auth.NTCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NTCredentialsWithEncryption extends NTCredentials {

    private static final Logger LOG = LoggerFactory.getLogger(NTCredentialsWithEncryption.class);

    boolean isAuthenticated = false;
    long negotiateFlags;
    byte[] clientSigningKey, serverSigningKey, clientSealingKey, serverSealingKey;
    AtomicLong sequenceNumberIncoming = new AtomicLong(-1), sequenceNumberOutgoing = new AtomicLong(-1);

    public NTCredentialsWithEncryption(String userName, String password, String workstation, String domain) {
        super(userName, password, workstation, domain);
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    public void setIsAuthenticated(boolean isAuthenticated) {
        this.isAuthenticated = isAuthenticated;
    }

    public void setClientSigningKey(byte[] clientSigningKey) {
        this.clientSigningKey = clientSigningKey;
    }

    public void setServerSigningKey(byte[] serverSigningKey) {
        this.serverSigningKey = serverSigningKey;
    }

    public byte[] getClientSigningKey() {
        return clientSigningKey;
    }

    public byte[] getServerSigningKey() {
        return serverSigningKey;
    }

    public void setClientSealingKey(byte[] clientSealingKey) {
        this.clientSealingKey = clientSealingKey;
    }

    public void setServerSealingKey(byte[] serverSealingKey) {
        this.serverSealingKey = serverSealingKey;
    }

    public byte[] getClientSealingKey() {
        return clientSealingKey;
    }

    public byte[] getServerSealingKey() {
        return serverSealingKey;
    }

    public long getNegotiateFlags() {
        return negotiateFlags;
    }

    public boolean hasNegotiateFlag(long flag) {
        return (getNegotiateFlags() & flag) == flag;
    }

    public void setNegotiateFlags(long negotiateFlags) {
        this.negotiateFlags = negotiateFlags;
    }

    public AtomicLong getSequenceNumberIncoming() {
        return sequenceNumberIncoming;
    }

    public AtomicLong getSequenceNumberOutgoing() {
        return sequenceNumberOutgoing;
    }

    CryptoHandler encryptor = null;
    public CryptoHandler getStatefulEncryptor() {
        if (encryptor==null) encryptor = WinrmEncryptionUtils.encryptorArc4(getClientSealingKey());
        return encryptor;
    }

    CryptoHandler decryptor = null;
    public CryptoHandler getStatefulDecryptor() {
        if (decryptor==null) decryptor = WinrmEncryptionUtils.decryptorArc4(getServerSealingKey());
        return decryptor;
    }

    public void resetEncryption(String response, HttpRequest request) {
        if (isAuthenticated()) {
            LOG.debug("Resetting encryption for {}", request);
        } else {
            LOG.trace("Resetting encryption for {}", request);
        }

        setIsAuthenticated(false);
        clientSealingKey = null;
        clientSigningKey = null;
        serverSealingKey = null;
        serverSigningKey = null;
        encryptor = null;
        decryptor = null;
        sequenceNumberIncoming.set(-1);
        sequenceNumberOutgoing.set(-1);

        if (request instanceof HttpEntityEnclosingRequest && ((HttpEntityEnclosingRequest)request).getEntity() instanceof EncryptionAwareHttpEntity) {
            ((EncryptionAwareHttpEntity) ((HttpEntityEnclosingRequest)request).getEntity()).refreshHeaders((HttpEntityEnclosingRequest) request);
        }
    }

    public void initEncryption(Type3Message signAndSealData, HttpRequest request) {
        LOG.debug("Initializing encryption for {}", request);

        setIsAuthenticated(true);
        if (signAndSealData!=null && signAndSealData.getExportedSessionKey()!=null) {
            new NtlmKeys(signAndSealData).apply(this);
        }
        if (request instanceof HttpEntityEnclosingRequest && ((HttpEntityEnclosingRequest)request).getEntity() instanceof EncryptionAwareHttpEntity) {
            ((EncryptionAwareHttpEntity) ((HttpEntityEnclosingRequest) request).getEntity()).refreshHeaders((HttpEntityEnclosingRequest) request);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+super.toString()+"{auth="+isAuthenticated()+"}";
    }

}
