package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.encryption.WinrmEncryptionUtils.CryptoHandler;
import java.util.concurrent.atomic.AtomicLong;

public interface CredentialsWithEncryption {

    boolean isAuthenticated();
    void setIsAuthenticated(boolean isAuthenticated);

    void setClientSigningKey(byte[] key);
    void setServerSigningKey(byte[] key);

    byte[] getClientSigningKey();
    byte[] getServerSigningKey();

    void setClientSealingKey(byte[] key);
    void setServerSealingKey(byte[] key);

    byte[] getClientSealingKey();
    byte[] getServerSealingKey();

    void setNegotiateFlags(long negotiateFlags);
    long getNegotiateFlags();
    default boolean hasNegotiateFlag(long flag) {
        return (getNegotiateFlags() & flag) == flag;
    }

    AtomicLong getSequenceNumberIncoming();
    AtomicLong getSequenceNumberOutgoing();

    CryptoHandler getStatefulEncryptor();
    CryptoHandler getStatefulDecryptor();
}
