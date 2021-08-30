package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import static io.cloudsoft.winrm4j.client.encryption.ByteArrayUtils.concat;
import static io.cloudsoft.winrm4j.client.encryption.ByteArrayUtils.getLittleEndianUnsignedInt;
import io.cloudsoft.winrm4j.client.ntlm.NTCredentialsWithEncryption;
import io.cloudsoft.winrm4j.client.ntlm.NtlmKeys.NegotiateFlags;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;
import java.util.zip.CRC32;
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

    public NtlmEncryptionUtils(NTCredentialsWithEncryption credentials) {
        this.credentials = credentials;
    }

    public static NtlmEncryptionUtils of(Credentials credentials, PayloadEncryptionMode payloadEncryptionMode) {
        if (!(credentials instanceof NTCredentialsWithEncryption)) {
            if (payloadEncryptionMode.isRequired()) {
                throw new IllegalStateException("NTCredentials required to use encryption; instead have " + credentials);
            } else {
                return null;
            }
        }
        return new NtlmEncryptionUtils((NTCredentialsWithEncryption) credentials);
    }

    public byte[] encryptAndSign(Message message, byte[] messageBody) {
        try {
            LOG.info("XXX-ENCRYPT "+credentials.getSequenceNumberOutgoing()+" "+ByteArrayUtils.formatHexDump(credentials.getClientSigningKey())+" "+new String(messageBody));
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
