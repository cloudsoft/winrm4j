package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
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
        new Decryptor(message).handle();
    }

    public class Decryptor {

        private final Message message;

        NTCredentialsWithEncryption credentials;
        private byte[] rawBytes;
        private byte[] encryptedPayloadBytes;
        int index, lastBlockStart, lastBlockEnd;
        private byte[] signatureBytes;
        private byte[] sealedBytes;
        private String newHeaders;
        private byte[] unsealedBytes;

        public Decryptor(Message message) {
            this.message = message;
        }

        public void handle() {
            Object creds = message.getExchange().get(Credentials.class.getName());

            credentials = null;
            if (creds instanceof NTCredentialsWithEncryption) {
                credentials = (NTCredentialsWithEncryption) creds;
            }

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
                if (!payloadEncryptionMode.isPermitted()) throw new IllegalStateException("Encrypted payload from server when encryption not permitted");
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
            unwrap();

            int signatureLength = (int) ByteArrayUtils.readLittleEndianUnsignedInt(encryptedPayloadBytes, 0);
            signatureBytes = Arrays.copyOfRange(encryptedPayloadBytes, 4, 4+signatureLength);
            sealedBytes = Arrays.copyOfRange(encryptedPayloadBytes, 4+signatureLength, encryptedPayloadBytes.length);

            unseal();

            // should set length and type headers - but they don't seem to be needed!

            verify();

            LOG.info("XXX-decrypt "+new String(unsealedBytes));

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

}
