package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import io.cloudsoft.winrm4j.client.encryption.NtlmEncryptionUtils.Decryptor;
import io.cloudsoft.winrm4j.client.ntlm.NTCredentialsWithEncryption;
import io.cloudsoft.winrm4j.client.ntlm.NtlmKeys.NegotiateFlags;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.http.auth.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecryptAndVerifyInInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger LOG = LoggerFactory.getLogger(DecryptAndVerifyInInterceptor.class);

    public static final String APPLIED = DecryptAndVerifyInInterceptor.class.getSimpleName()+".APPLIED";

    public static final String ENCRYPTED_BOUNDARY_PREFIX = "--Encrypted Boundary";
    public static final String ENCRYPTED_BOUNDARY_CR = ENCRYPTED_BOUNDARY_PREFIX+"\r\n";
    public static final String ENCRYPTED_BOUNDARY_END = ENCRYPTED_BOUNDARY_PREFIX+"--\r\n";

    private final PayloadEncryptionMode payloadEncryptionMode;

    public DecryptAndVerifyInInterceptor(PayloadEncryptionMode payloadEncryptionMode) {
        super(Phase.POST_STREAM);
        addBefore(StaxInInterceptor.class.getName());
        this.payloadEncryptionMode = payloadEncryptionMode;
    }

    public void handleMessage(Message message) {
        NtlmEncryptionUtils.of(message, payloadEncryptionMode).decrypt(message);
    }

}
