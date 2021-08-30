package io.cloudsoft.winrm4j.client.ntlm;

import io.cloudsoft.winrm4j.client.encryption.WinrmEncryptionUtils;
import io.cloudsoft.winrm4j.client.encryption.WinrmEncryptionUtils.CryptoHandler;
import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMEngineImpl.Type3Message;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.http.auth.NTCredentials;

public class NTCredentialsWithEncryption extends NTCredentials {

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
        if (decryptor==null) decryptor = WinrmEncryptionUtils.encryptorArc4(getServerSealingKey());
        return decryptor;
    }

    public void resetEncryption() {
        setIsAuthenticated(false);
        sequenceNumberIncoming.set(-1);
        sequenceNumberOutgoing.set(-1);
    }

    public void initEncryption(Type3Message signAndSealData) {
        setIsAuthenticated(true);
        if (signAndSealData!=null && signAndSealData.getExportedSessionKey()!=null) {
            new NtlmKeys(signAndSealData).apply(this);
        }
    }
}
