package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import static io.cloudsoft.winrm4j.client.encryption.ByteArrayUtils.concat;
import static io.cloudsoft.winrm4j.client.encryption.ByteArrayUtils.getLittleEndianUnsignedInt;
import io.cloudsoft.winrm4j.client.ntlm.NTCredentialsWithEncryption;
import io.cloudsoft.winrm4j.client.ntlm.NtlmKeys.NegotiateFlags;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.function.Function;
import java.util.zip.CRC32;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.ProxyOutputStream;
import org.apache.http.auth.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignAndEncryptOutInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger LOG = LoggerFactory.getLogger(SignAndEncryptOutInterceptor.class);

    public static final String APPLIED = SignAndEncryptOutInterceptor.class.getSimpleName()+".APPLIED";

    public static final String ENCRYPTED_BOUNDARY_PREFIX = "--Encrypted Boundary";
    public static final String ENCRYPTED_BOUNDARY_CR = ENCRYPTED_BOUNDARY_PREFIX+"\r\n";
    public static final String ENCRYPTED_BOUNDARY_END = ENCRYPTED_BOUNDARY_PREFIX+"--\r\n";

    private final PayloadEncryptionMode payloadEncryptionMode;

    public SignAndEncryptOutInterceptor(PayloadEncryptionMode payloadEncryptionMode) {
        super(Phase.POST_LOGICAL);
        this.payloadEncryptionMode = payloadEncryptionMode;
    }

    public void handleMessage(Message message) {
        boolean hasApplied = message.containsKey(APPLIED);
        if (!hasApplied) {
            message.put(APPLIED, Boolean.TRUE);
            final OutputStream os = message.getContent(OutputStream.class);
            final EncryptAndSignProxyOutputStream newOut = new EncryptAndSignProxyOutputStream(message, os);
            message.setContent(OutputStream.class, newOut);
            message.setContent(ProxyOutputStream.class, newOut);
        }
    }

    class EncryptAndSignProxyOutputStream extends ProxyOutputStream {

        final CachedOutputStream unencrypted;
        final Message message;
        OutputStream finalOut;

        NTCredentialsWithEncryption credentials;

        public EncryptAndSignProxyOutputStream(Message message, OutputStream os) {
            this.message = message;
            unencrypted = new CachedOutputStream();
            super.setWrappedOutputStream(os);

            Object creds = message.get(Credentials.class.getName());
            if (creds instanceof NTCredentialsWithEncryption) {
                this.credentials = (NTCredentialsWithEncryption) creds;
            }
        }

        @Override
        public void setWrappedOutputStream(OutputStream os) {
            if (finalOut!=null) {
                LOG.debug("Target of "+this+" changing from "+finalOut+" to "+os);
            }
            this.finalOut = os;
        }

        @Override
        public void close() throws IOException {
            LOG.info("XXX-close "+finalOut);
            if (finalOut!=null) {
                processAndShip(finalOut);
                finalOut.close();
            } else {
                LOG.warn("No stream for writing encrypted message to");
            }
        }

        protected void processAndShip(OutputStream output) throws IOException {

            if (!payloadEncryptionMode.isPermitted() || credentials==null || !credentials.isAuthenticated()) {
                if (payloadEncryptionMode.isRequired() && credentials==null) {
                    // if required, we need a compatible credentials model; but don't need to (cannot) encrypt until authenticated
                    throw new IllegalStateException("Encryption required but unavailable");
                }
                if (credentials!=null && !credentials.isAuthenticated()) {
                    output.write(unencrypted.getBytes());
//                    output.write("AWAITING_KEYS".getBytes());
                } else {
                    output.write(unencrypted.getBytes());
                }

            } else {

                byte[] bytesEncryptedAndSigned = encryptAndSign(message, unencrypted.getBytes());
                output.write(bytesEncryptedAndSigned);
            }

            output.close();
        }


        protected byte[] encryptAndSign(Message message, byte[] messageBody) {
            try {
                LOG.info("XXX-ENCRYPT "+new String(messageBody));
                ByteArrayOutputStream out = new ByteArrayOutputStream();

                out.write(ENCRYPTED_BOUNDARY_CR.getBytes());
                out.write(("\tContent-Type: " + "application/HTTP-SPNEGO-session-encrypted" + "\r\n").getBytes());

                //message.get(Message.CONTENT_TYPE); - if we need the action // Content-Type -> application/soap+xml; action="http://schemas.xmlsoap.org/ws/2004/09/transfer/Create"
                out.write(("\tOriginalContent: type=application/soap+xml;charset=UTF-8;Length=" + messageBody.length + "\r\n").getBytes());

                out.write(ENCRYPTED_BOUNDARY_CR.getBytes());
                out.write("\tContent-Type: application/octet-stream\r\n".getBytes());

                // for credssh chunking might be needed, but not for ntlm

                writeNtlmEncrypted(messageBody, out);

                out.write(ENCRYPTED_BOUNDARY_END.getBytes());

                message.put(Message.CONTENT_TYPE, "multipart/encrypted;protocol=\"application/HTTP-SPNEGO-session-encrypted\";boundary=\"Encrypted Boundary\"");
                message.put(Message.ENCODING, null);
                return out.toByteArray();

            } catch (Exception e) {
                throw new IllegalStateException("Cannot encrypt WinRM message", e);
            }
        }

        private byte[] seal(byte[] in) {
            return credentials.getStatefulEncryptor().update(in);
        }

        private void writeNtlmEncrypted(byte[] messageBody, ByteArrayOutputStream encrypted) throws IOException {

            // ./pywinrm/winrm/encryption.py
            // ./ntlm_auth/session_security.py

            long seqNum = credentials.getSequenceNumberOutgoing().incrementAndGet();
            ByteArrayOutputStream signature = new ByteArrayOutputStream();
            ByteArrayOutputStream sealed = new ByteArrayOutputStream();

            // seal first, even though appended afterwards, because encryptor is stateful
            sealed.write(seal(messageBody));

            calculateSignature(messageBody, seqNum, signature, credentials, NTCredentialsWithEncryption::getClientSigningKey, this::seal);

            encrypted.write(getLittleEndianUnsignedInt(signature.size()));
            encrypted.write(signature.toByteArray());
            encrypted.write(sealed.toByteArray());
        }

    }

    static void calculateSignature(byte[] messageBody, long seqNum,
                                   ByteArrayOutputStream signature,
                                   NTCredentialsWithEncryption credentials,
                                   Function<NTCredentialsWithEncryption,byte[]> signingKeyFunction,
                                   Function<byte[],byte[]> sealer) throws IOException {
        //    sealed_message, signature = self.session.auth.session_security.wrap(message)
//            return signature_length + signature + sealed_message

//            seq_num = struct.pack("<I", seq_num)
//            if negotiate_flags & \
//            NegotiateFlags.NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY:
//            checksum_hmac = hmac.new(signing_key, seq_num + message,
//                    digestmod=hashlib.md5)
//            if negotiate_flags & NegotiateFlags.NTLMSSP_NEGOTIATE_KEY_EXCH:
//            checksum = handle.update(checksum_hmac.digest()[:8])
//        else:
//            checksum = checksum_hmac.digest()[:8]
//
//            signature = _NtlmMessageSignature2(checksum, seq_num)
//
//    else:
//            message_crc = binascii.crc32(message) % (1 << 32)
//            checksum = struct.pack("<I", message_crc)
//            random_pad = handle.update(struct.pack("<I", 0))
//            checksum = handle.update(checksum)
//            seq_num = handle.update(seq_num)
//            random_pad = struct.pack("<I", 0)
//
//            signature = _NtlmMessageSignature1(random_pad, checksum, seq_num)


        if (credentials.hasNegotiateFlag(NegotiateFlags.NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY)) {

//            checksum_hmac = hmac.new(signing_key, seq_num + message,
//                    digestmod=hashlib.md5)
//            if negotiate_flags & NegotiateFlags.NTLMSSP_NEGOTIATE_KEY_EXCH:
//            checksum = handle.update(checksum_hmac.digest()[:8])
//        else:
//            checksum = checksum_hmac.digest()[:8]

            // also see HMACMD5 in NTLMEngineIpml
            byte[] checksum = WinrmEncryptionUtils.hmacMd5(signingKeyFunction.apply(credentials), concat(getLittleEndianUnsignedInt(seqNum), messageBody));
            checksum = Arrays.copyOfRange(checksum, 0, 8);

            if (credentials.hasNegotiateFlag(NegotiateFlags.NTLMSSP_NEGOTIATE_KEY_EXCH)) {
                checksum = sealer.apply(checksum);
            }
//
//            signature = _NtlmMessageSignature2(checksum, seq_num)
//                self.version = b"\x01\x00\x00\x00"
//                signature = self.version
//                signature += self.checksum
//                signature += self.seq_num

            // version
            signature.write(new byte[]{1, 0, 0, 0});
            // checksum
            signature.write(checksum);
            // seq num
            signature.write(getLittleEndianUnsignedInt(seqNum));


        } else {

//            message_crc = binascii.crc32(message) % (1 << 32)
//            checksum = struct.pack("<I", message_crc)
//            random_pad = handle.update(struct.pack("<I", 0))
//            checksum = handle.update(checksum)
//            seq_num = handle.update(seq_num)
//            random_pad = struct.pack("<I", 0)
//
//            signature = _NtlmMessageSignature1(random_pad, checksum, seq_num)

//        message_crc = binascii.crc32(message) % (1 << 32)
            CRC32 crc = new CRC32();
            crc.update(messageBody);
            long messageCrc = crc.getValue();

//        signature = _NtlmMessageSignature1(random_pad, checksum, seq_num)
//            self.version = b"\x01\x00\x00\x00"
//            signature = self.version
//            signature += self.random_pad
//            signature += self.checksum
//            signature += self.seq_num

            // version
            signature.write(new byte[]{1, 0, 0, 0});
            // random pad
            signature.write(sealer.apply(getLittleEndianUnsignedInt(0)));
            // checksum
            signature.write(sealer.apply(getLittleEndianUnsignedInt(messageCrc)));
            // seq num
            signature.write(sealer.apply(getLittleEndianUnsignedInt(seqNum)));
        }
    }
}
