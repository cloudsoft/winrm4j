package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import io.cloudsoft.winrm4j.client.ntlm.NTCredentialsWithEncryption;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.cxf.interceptor.StaxOutInterceptor;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.http.auth.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Not only encrypts if necessary, but must track the payload and make it available to
 * {@link AsyncHttpEncryptionAwareConduit} in case we need to subsequently encrypt.
 */
public class SignAndEncryptOutInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger LOG = LoggerFactory.getLogger(SignAndEncryptOutInterceptor.class);

    public static final String APPLIED = SignAndEncryptOutInterceptor.class.getSimpleName()+".APPLIED";

    private final PayloadEncryptionMode payloadEncryptionMode;

    public SignAndEncryptOutInterceptor(PayloadEncryptionMode payloadEncryptionMode) {
        super(Phase.PRE_STREAM);
        // we need to be set before various other output devices, so they write to us
        addBefore(StaxOutInterceptor.class.getName());
        this.payloadEncryptionMode = payloadEncryptionMode;
    }

    public void handleMessage(Message message) {
        boolean hasApplied = message.containsKey(APPLIED);
        if (!hasApplied) {
            message.put(APPLIED, Boolean.TRUE);
            final OutputStream os = message.getContent(OutputStream.class);
            final EncryptAndSignOutputStream newOut = new EncryptAndSignOutputStream(message, os);
            message.setContent(OutputStream.class, newOut);
            message.setContent(EncryptAndSignOutputStream.class, newOut);
        }
    }

    class EncryptAndSignOutputStream extends CachedOutputStream {

        final CachedOutputStream unencrypted;
        ContentWithType unencryptedResult = null;
        ContentWithType encrypted = null;
        final Message message;

        OutputStream wrapped;

        NTCredentialsWithEncryption credentials;

        public EncryptAndSignOutputStream(Message message, OutputStream os) {
            super();

            this.message = message;
            wrapped = os;
            unencrypted = new CachedOutputStream();

            Object creds = message.get(Credentials.class.getName());
            if (creds instanceof NTCredentialsWithEncryption) {
                this.credentials = (NTCredentialsWithEncryption) creds;
            }
        }

        @Override
        public void resetOut(OutputStream out, boolean copyOldContent) throws IOException {
            super.resetOut(out, copyOldContent);
        }

        @Override
        public void close() throws IOException {
            LOG.trace("Closing stream {}", wrapped);
            super.close();
            unencrypted.write(getBytes());
            currentStream = new NullOutputStream();

            if (wrapped!=null) {
                processAndShip(wrapped);
                wrapped.close();
            } else {
                LOG.warn("No stream for writing encrypted message to");
            }
        }

        protected synchronized ContentWithType getEncrypted() {
            try {
                if (encrypted==null) {
                    byte[] bytesEncryptedAndSigned = new NtlmEncryptionUtils(credentials, payloadEncryptionMode).encryptAndSign(message, unencrypted.getBytes());
                    encrypted = ContentWithType.of(message, bytesEncryptedAndSigned);
                }

                return encrypted;

            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        protected byte[] getUnencrypted() {
            try {
                return unencrypted.getBytes();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public synchronized ContentWithType getAppropriate() {
            return getAppropriate(false);
        }

        public synchronized ContentWithType getAppropriate(boolean isFirstInvocation) {
            if (unencryptedResult==null) {
                unencryptedResult = ContentWithType.of(message, null);
            }

            if (!payloadEncryptionMode.isPermitted() || credentials==null || !credentials.isAuthenticated()) {
                if (encrypted!=null) {
                    // clear any previous encryption if no longer valid
                    encrypted = null;
                    LOG.debug("Clearing previously encrypted message becasue credentials no longer authenticated");
                }

                if (payloadEncryptionMode.isRequired() && credentials==null) {
                    // if required, we need a compatible credentials model; but don't need to (cannot) encrypt until authenticated
                    throw new IllegalStateException("Encryption required but unavailable");
                }
                if (credentials!=null && !credentials.isAuthenticated()) {
                    return unencryptedResult.with(AsyncHttpEncryptionAwareConduit.PRE_AUTH_BOGUS_PAYLOAD);

                } else {
                    return unencryptedResult.with(getUnencrypted());

                }

            } else {
                return getEncrypted();
            }
        }

        protected void processAndShip(OutputStream output) throws IOException {
            output.write(getAppropriate(true).payload);
            output.close();
        }

    }

    public static class ContentWithType {
        public String contentType;
        public String encoding;
        public byte[] payload;

        static ContentWithType of(Message message, byte[] payload) {
            return of((String)message.get(Message.CONTENT_TYPE), (String)message.get(Message.ENCODING), payload);
        }

        static ContentWithType of(String contentType, String encoding, byte[] payload) {
            ContentWithType result = new ContentWithType();
            result.contentType = contentType;
            result.encoding = encoding;
            result.payload = payload;
            return result;
        }

        public ContentWithType with(byte[] payload) {
            return ContentWithType.of(this.contentType, this.encoding, payload);
        }
    }

}
