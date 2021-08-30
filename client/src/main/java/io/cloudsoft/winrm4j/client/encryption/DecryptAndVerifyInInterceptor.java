package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecryptAndVerifyInInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger LOG = LoggerFactory.getLogger(DecryptAndVerifyInInterceptor.class);

    public static final String APPLIED = DecryptAndVerifyInInterceptor.class.getSimpleName()+".APPLIED";

    private final PayloadEncryptionMode payloadEncryptionMode;

    public DecryptAndVerifyInInterceptor(PayloadEncryptionMode payloadEncryptionMode) {
        super(Phase.POST_STREAM);
        addBefore(StaxInInterceptor.class.getName());
        this.payloadEncryptionMode = payloadEncryptionMode;
    }

    public void handleMessage(Message message) {
        NtlmEncryptionUtils utils = NtlmEncryptionUtils.of(message, payloadEncryptionMode);
        if (utils!=null) utils.decrypt(message);
    }

}
