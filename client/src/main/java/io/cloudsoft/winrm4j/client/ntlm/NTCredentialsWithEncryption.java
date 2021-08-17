package io.cloudsoft.winrm4j.client.ntlm;

import io.cloudsoft.winrm4j.client.encryption.CredentialsWithEncryption;
import io.cloudsoft.winrm4j.client.encryption.WinrmEncryptionUtils;
import io.cloudsoft.winrm4j.client.encryption.WinrmEncryptionUtils.StatefulEncryption;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.http.auth.NTCredentials;

public class NTCredentialsWithEncryption extends NTCredentials implements CredentialsWithEncryption {

    boolean isAuthenticated = false;
    long negotiateFlags;
    byte[] clientSigningKey, serverSigningKey, clientSealingKey, serverSealingKey;
    AtomicLong sequenceNumberIncoming = new AtomicLong(-1), sequenceNumberOutgoing = new AtomicLong(-1);

    public NTCredentialsWithEncryption(String userName, String password, String workstation, String domain) {
        super(userName, password, workstation, domain);
    }

    @Override
    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    @Override
    public void setIsAuthenticated(boolean isAuthenticated) {
        this.isAuthenticated = isAuthenticated;
    }

    @Override
    public void setClientSigningKey(byte[] clientSigningKey) {
        this.clientSigningKey = clientSigningKey;
    }

    @Override
    public void setServerSigningKey(byte[] serverSigningKey) {
        this.serverSigningKey = serverSigningKey;
    }

    @Override
    public byte[] getClientSigningKey() {
        return clientSigningKey;
    }

    @Override
    public byte[] getServerSigningKey() {
        return serverSigningKey;
    }

    @Override
    public void setClientSealingKey(byte[] clientSealingKey) {
        this.clientSealingKey = clientSealingKey;
    }

    @Override
    public void setServerSealingKey(byte[] serverSealingKey) {
        this.serverSealingKey = serverSealingKey;
    }

    @Override
    public byte[] getClientSealingKey() {
        return clientSealingKey;
    }

    @Override
    public byte[] getServerSealingKey() {
        return serverSealingKey;
    }

    @Override
    public long getNegotiateFlags() {
        return negotiateFlags;
    }

    @Override
    public void setNegotiateFlags(long negotiateFlags) {
        this.negotiateFlags = negotiateFlags;
    }

    @Override
    public AtomicLong getSequenceNumberIncoming() {
        return sequenceNumberIncoming;
    }

    @Override
    public AtomicLong getSequenceNumberOutgoing() {
        return sequenceNumberOutgoing;
    }

    StatefulEncryption encryptor = null;
    @Override
    public StatefulEncryption getStatefulEncryptor() {
        if (encryptor==null) encryptor = WinrmEncryptionUtils.encryptorArc4(getClientSealingKey());
        return encryptor;
    }

}
