package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import static io.cloudsoft.winrm4j.client.encryption.ByteArrayUtils.concat;
import static io.cloudsoft.winrm4j.client.encryption.ByteArrayUtils.getLittleEndianUnsignedInt;
import io.cloudsoft.winrm4j.client.ntlm.NTCredentialsWithEncryption;
import io.cloudsoft.winrm4j.client.ntlm.NtlmKeys.NegotiateFlags;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.function.Function;
import java.util.zip.CRC32;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.message.Message;
import org.apache.http.auth.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NtlmEncryptionUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NtlmEncryptionUtils.class);

    public static final String ENCRYPTED_BOUNDARY_PREFIX = "--Encrypted Boundary";
    public static final String ENCRYPTED_BOUNDARY_CR = ENCRYPTED_BOUNDARY_PREFIX+"\r\n";
    public static final String ENCRYPTED_BOUNDARY_END = ENCRYPTED_BOUNDARY_PREFIX+"--\r\n";

    protected final NTCredentialsWithEncryption credentials;
    protected final PayloadEncryptionMode payloadEncryptionMode;

    public NtlmEncryptionUtils(NTCredentialsWithEncryption credentials, PayloadEncryptionMode payloadEncryptionMode) {
        this.credentials = credentials;
        this.payloadEncryptionMode = payloadEncryptionMode;
    }

    public static NtlmEncryptionUtils of(Credentials credentials, PayloadEncryptionMode payloadEncryptionMode) {
        if (!(credentials instanceof NTCredentialsWithEncryption)) {
            if (payloadEncryptionMode.isRequired()) {
                throw new IllegalStateException("NTCredentials required to use encryption; instead have " + credentials);
            } else {
                return null;
            }
        }
        return new NtlmEncryptionUtils((NTCredentialsWithEncryption) credentials, payloadEncryptionMode);
    }

    public static NtlmEncryptionUtils of(Message message, PayloadEncryptionMode payloadEncryptionMode) {
        Credentials credentials = (Credentials) message.getExchange().get(Credentials.class.getName());
        return of(credentials, payloadEncryptionMode);
    }

    public byte[] encryptAndSign(Message message, byte[] messageBody) {
        try {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Encrypting message, seq="+credentials.getSequenceNumberOutgoing()+" key="+ByteArrayUtils.formatHexDump(credentials.getClientSigningKey())+"; body:\n"+new String(messageBody));
            }

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

            if (LOG.isTraceEnabled()) {
                LOG.trace("Encrypted message: "+ByteArrayUtils.formatHexDump(out.toByteArray()));
            }
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

    public void decrypt(Message message) {
        new Decryptor(credentials, payloadEncryptionMode).handle(message);
    }


    public static class Decryptor {

        private final PayloadEncryptionMode payloadEncryptionMode;

        NTCredentialsWithEncryption credentials;
        private byte[] rawBytes;
        private byte[] encryptedPayloadBytes;
        int index, lastBlockStart, lastBlockEnd;
        private byte[] signatureBytes;
        private byte[] sealedBytes;
        private String newHeaders;
        private byte[] unsealedBytes;

        public Decryptor(NTCredentialsWithEncryption credentials, PayloadEncryptionMode payloadEncryptionMode) {
            this.credentials = credentials;
            this.payloadEncryptionMode = payloadEncryptionMode;
        }

        public void handle(Message message) {
            Object contentType = message.get(Message.CONTENT_TYPE);
//        Map headers = (Map) message.get(Message.PROTOCOL_HEADERS);
//        if (headers!=null) {
//            contentType = headers.get(Message.CONTENT_TYPE);
//        }
//        if (contentType==null) {
//            throw new IllegalStateException("Invalid response; no content type");
//        }
//        if (contentType instanceof Iterable) contentType = ((Iterable)contentType).iterator().next();

            boolean isEncrypted = contentType==null ? false : contentType.toString().startsWith("multipart/encrypted");

            if (isEncrypted) {
                if (credentials==null) throw new IllegalStateException("Encrypted payload from server when no credentials with encryption known");
                if (!credentials.isAuthenticated()) throw new IllegalStateException("Encrypted payload from server when not authenticated");

                try {
                    decrypt(message);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            } else {
                if (payloadEncryptionMode.isRequired() && credentials!=null && credentials.isAuthenticated()) {
                    throw new IllegalStateException("Unencrypted payload from server when authenticated and encryption is required");
                }
            }
        }

        void decrypt(Message message) throws IOException {
            InputStream in = message.getContent(InputStream.class);
            rawBytes = IOUtils.readBytesFromStream(in);

            if (LOG.isTraceEnabled()) {
                LOG.trace("Decrypting message, seq="+credentials.getSequenceNumberIncoming()+" key="+ByteArrayUtils.formatHexDump(credentials.getServerSigningKey())+"; body:\n"+ByteArrayUtils.formatHexDump(rawBytes));
            }

            unwrap();

            int signatureLength = (int) ByteArrayUtils.readLittleEndianUnsignedInt(encryptedPayloadBytes, 0);
            signatureBytes = Arrays.copyOfRange(encryptedPayloadBytes, 4, 4+signatureLength);
            sealedBytes = Arrays.copyOfRange(encryptedPayloadBytes, 4+signatureLength, encryptedPayloadBytes.length);

            unseal();

            // should set length and type headers - but they don't seem to be needed!

            verify();

            if (LOG.isTraceEnabled()) {
                LOG.trace("Decrypted message: {}", new String(unsealedBytes));
            }

            message.setContent(InputStream.class, new ByteArrayInputStream(unsealedBytes));
        }

        private void verify() throws IOException {
//            if self.negotiate_flags & \
//            NegotiateFlags.NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY:
//            actual_checksum = signature[4:12]
//            actual_seq_num = struct.unpack("<I", signature[12:16])[0]
//        else:
//            actual_checksum = signature[8:12]
//            actual_seq_num = struct.unpack("<I", signature[12:16])[0]

            byte[] checksum;
            long seqNum = ByteArrayUtils.readLittleEndianUnsignedInt(signatureBytes, 12);
            int checkSumOffset;
            if (credentials.hasNegotiateFlag(NegotiateFlags.NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY)) {
                checkSumOffset = 4;
            } else {
                checkSumOffset = 8;
            }
            checksum = Arrays.copyOfRange(signatureBytes, checkSumOffset, 12);

//            expected_signature = calc_signature(message, self.negotiate_flags,
//                    self.incoming_signing_key,
//                    self.incoming_seq_num,
//                    self.incoming_handle)
//            expected_checksum = expected_signature.checksum
//            expected_seq_num = struct.unpack("<I", expected_signature.seq_num)[0]

            ByteArrayOutputStream signature = new ByteArrayOutputStream();
            NtlmEncryptionUtils.calculateSignature(unsealedBytes, seqNum, signature, credentials, NTCredentialsWithEncryption::getServerSigningKey, credentials.getStatefulDecryptor()::update);

            byte[] expected_checksum = Arrays.copyOfRange(signature.toByteArray(), checkSumOffset, 12);
            long expected_seq_num = ByteArrayUtils.readLittleEndianUnsignedInt(signature.toByteArray(), 12);

            if (!Arrays.equals(checksum, expected_checksum)) {
                throw new IllegalStateException("Checksum mismatch\n"+
                        ByteArrayUtils.formatHexDump(checksum)+"--\n"+ByteArrayUtils.formatHexDump(expected_checksum));
            }
            if (expected_seq_num!=seqNum) {
                throw new IllegalStateException("Sequence number mismatch: "+seqNum+" != "+expected_seq_num);
            }

            credentials.getSequenceNumberIncoming().incrementAndGet();

        }

        void unwrap() {
            index = 0;
            skipOver(NtlmEncryptionUtils.ENCRYPTED_BOUNDARY_CR);
            skipUntil("\n"+NtlmEncryptionUtils.ENCRYPTED_BOUNDARY_CR);

            newHeaders = new String(Arrays.copyOfRange(rawBytes, lastBlockStart, lastBlockEnd)).trim();
//                            "\tContent-Type: application/HTTP-SPNEGO-session-encrypted\r\n" +
//                            "\tOriginalContent: type=application/soap+xml;charset=UTF-8;Length=";

            skipUntil("\r\n");
            String secondHeaders = new String(Arrays.copyOfRange(rawBytes, lastBlockStart, lastBlockEnd)).trim();
//                    "\tContent-Type: application/octet-stream\r\n";

            // for credssh de-chunking might be needed, but not for ntlm

            lastBlockStart = index;
            lastBlockEnd = rawBytes.length - NtlmEncryptionUtils.ENCRYPTED_BOUNDARY_END.length();
            index = lastBlockEnd;
            skipOver(NtlmEncryptionUtils.ENCRYPTED_BOUNDARY_END);

            encryptedPayloadBytes = Arrays.copyOfRange(rawBytes, lastBlockStart, lastBlockEnd);
        }


        void skipOver(String s) {
            skipOver(s.getBytes());
        }

        void skipOver(byte[] expected) {
            int i=0;
            while (i<expected.length) {
                if (index>=rawBytes.length) {
                    throw new IllegalStateException("Invalid format for response from server; terminated early ("+i+") when expecting '"+new String(expected)+"'\n"+
                            ByteArrayUtils.formatHexDump(rawBytes));
                }
                if (expected[i++]!=rawBytes[index++]) {
                    throw new IllegalStateException("Invalid format for response from server; mismatch at position "+index+" ("+i+") when expecting '"+new String(expected)+"'\n"+
                            ByteArrayUtils.formatHexDump(rawBytes));
                }
            }
        }

        void skipUntil(String s) {
            skipUntil(s.getBytes());
        }

        void skipUntil(byte[] expected) {
            int nextBlock = index;
            outer: while (true) {
                for (int i = 0; i < expected.length && nextBlock+i < rawBytes.length; i++) {
                    if (nextBlock+i>=rawBytes.length) {
                        throw new IllegalStateException("Invalid format for response from server; terminated early ("+i+") when looking for '"+new String(expected)+"'\n"+
                                ByteArrayUtils.formatHexDump(rawBytes));
                    }
                    if (expected[i] != rawBytes[nextBlock+i]) {
                        nextBlock++;
                        continue outer;
                    }
                }
                lastBlockStart = index;
                lastBlockEnd = nextBlock;
                index = nextBlock + expected.length;
                return;
            }
        }

        private void unseal() {
            unsealedBytes = credentials.getStatefulDecryptor().update(sealedBytes);
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
