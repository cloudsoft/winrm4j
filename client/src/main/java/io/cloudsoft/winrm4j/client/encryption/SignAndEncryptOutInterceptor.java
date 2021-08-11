package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import org.apache.cxf.interceptor.StaxOutInterceptor;
import org.apache.cxf.io.WriteOnCloseOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.http.auth.Credentials;
import org.bouncycastle.crypto.engines.RC4Engine;
import org.bouncycastle.crypto.io.CipherOutputStream;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jcajce.provider.symmetric.ARC4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignAndEncryptOutInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger LOG = LoggerFactory.getLogger(SignAndEncryptOutInterceptor.class);

    public static final String APPLIED = SignAndEncryptOutInterceptor.class.getSimpleName()+".APPLIED";
    private final PayloadEncryptionMode payloadEncryptionMode;

    public SignAndEncryptOutInterceptor(PayloadEncryptionMode payloadEncryptionMode) {
        super(Phase.PRE_STREAM);
        addBefore(StaxOutInterceptor.class.getName());
        this.payloadEncryptionMode = payloadEncryptionMode;
    }

    public void handleMessage(Message message) {
        final OutputStream os = message.getContent(OutputStream.class);
//        final Writer iowriter = message.getContent(Writer.class);
//        if (os == null && iowriter == null) {
//            return;
//        }

        boolean hasApplied = message.containsKey(APPLIED);
        if (!hasApplied) {
            message.put(APPLIED, Boolean.TRUE);
            if (os != null) {
                final EncryptAndSignOutputStream newOut = new EncryptAndSignOutputStream(message, os);
                message.setContent(OutputStream.class, newOut);
            } else {
                throw new IllegalStateException("Encryption only supported with output streams");
            }
        }
    }

    class EncryptAndSignOutputStream extends WriteOnCloseOutputStream {
        protected final Message message;
        private final OutputStream stream;

        CredentialsWithEncryption credentials;

        public EncryptAndSignOutputStream(Message message, OutputStream stream) {
            super(stream);
            this.message = message;
            this.stream = stream;

            Object creds = message.get(Credentials.class.getName());
            if (creds instanceof CredentialsWithEncryption) {
                this.credentials = (CredentialsWithEncryption) creds;
            }
        }

        @Override
        public void resetOut(OutputStream out, boolean copyOldContent) throws IOException {
            if (!payloadEncryptionMode.isPermitted() || credentials==null || !credentials.isAuthenticated()) {
                if (payloadEncryptionMode.isRequired() && credentials==null) {
                    // if required, we need a compatible credentials model; but don't need to (cannot) encrypt until authenticated
                    throw new IllegalStateException("Encryption required but unavailable");
                }
                super.resetOut(out, copyOldContent);

            } else {

                ByteArrayOutputStream out2 = new ByteArrayOutputStream();
                super.resetOut(out2, copyOldContent);

                byte[] bytesEncryptedAndSigned = encryptAndSign(message, out2.toByteArray());

                out.write(bytesEncryptedAndSigned);
                super.resetOut(out, false);
            }
        }


        protected byte[] encryptAndSign(Message message, byte[] messageBody) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();

                out.write("--Encrypted Boundary\r\n".getBytes());
                out.write(("\tContent-Type: " + "application/HTTP-SPNEGO-session-encrypted" + "\r\n").getBytes());

                //message.get(Message.CONTENT_TYPE); // Content-Type -> application/soap+xml; action="http://schemas.xmlsoap.org/ws/2004/09/transfer/Create"
                // from python
                out.write(("\tOriginalContent: type=application/soap+xml;charset=UTF-8;Length=" + messageBody.length + "\r\n").getBytes());

                out.write("--Encrypted Boundary\r\n".getBytes());
                out.write("\tContent-Type: application/octet-stream\r\n".getBytes());

                //encrypted_stream = self._build_message(message, host)
                writeNtlmEncrypted(messageBody, out);

                out.write("--\r\n".getBytes());

                message.put(Message.CONTENT_TYPE, "multipart/encrypted");
                return out.toByteArray();

            } catch (IOException e) {
                throw new IllegalStateException("Cannot encrypt WinRM message", e);
            }
        }

        private byte[] encrypt(byte[] in) throws IOException {

            // ntlm_auth session_security:
//        csk = self._client_sealing_key
//        ssk = self._server_sealing_key
//        if outgoing:
//        self.outgoing_handle = ARC4(csk if self._source == 'client' else ssk)
//        else:
//        self.incoming_handle = ARC4(ssk if self._source == 'client' else csk)

            RC4Engine engine = new RC4Engine();
            engine.init(true, new KeyParameter(credentials.getClientKey()));

            ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
            CipherOutputStream cos = new CipherOutputStream(outBytes, engine);
            cos.write(in);

            return outBytes.toByteArray();
        }

        private void writeNtlmEncrypted(byte[] messageBody, ByteArrayOutputStream encrypted) throws IOException {

//    sealed_message, signature = self.session.auth.session_security.wrap(message)
//            return signature_length + signature + sealed_message

//        message_crc = binascii.crc32(message) % (1 << 32)
            CRC32 crc = new CRC32();
            crc.update(messageBody);
            long messageCrc = crc.getValue();

            long seqNum = -1;

            ByteArrayOutputStream signature = new ByteArrayOutputStream();
            ;
//        random_pad = handle.update(struct.pack("<I", 0))
            signature.write(new byte[]{1, 0, 0, 0});
            // version
            signature.write(getLittleEndianUnsignedInt(0));
//        checksum = handle.update(checksum)
            signature.write(encrypt(getLittleEndianUnsignedInt(messageCrc)));
//        seq_num = handle.update(seq_num)
            signature.write(encrypt(getLittleEndianUnsignedInt(seqNum)));

//        signature = _NtlmMessageSignature1(random_pad, checksum, seq_num)
            encrypted.write(getLittleEndianUnsignedInt(signature.size()));
            encrypted.write(signature.toByteArray());

            encrypted.write(encrypt(messageBody));
        }
    }

    private static byte[] getLittleEndianUnsignedInt(long x) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putInt( (int) (x & 0xFFFFFFFF) );
        return byteBuffer.array();
    }

}
