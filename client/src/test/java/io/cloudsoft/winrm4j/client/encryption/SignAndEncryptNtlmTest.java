package io.cloudsoft.winrm4j.client.encryption;

import io.cloudsoft.winrm4j.client.PayloadEncryptionMode;
import static io.cloudsoft.winrm4j.client.encryption.ByteArrayUtils.bytes;
import static io.cloudsoft.winrm4j.client.encryption.ByteArrayUtils.getLittleEndianUnsignedInt;
import static io.cloudsoft.winrm4j.client.encryption.ByteArrayUtils.repeated;
import io.cloudsoft.winrm4j.client.encryption.WinrmEncryptionUtils.CryptoHandler;
import static io.cloudsoft.winrm4j.client.encryption.WinrmEncryptionUtils.encryptorArc4;
import io.cloudsoft.winrm4j.client.ntlm.NTCredentialsWithEncryption;
import io.cloudsoft.winrm4j.client.ntlm.NtlmKeys;
import io.cloudsoft.winrm4j.client.ntlm.NtlmKeys.NegotiateFlags;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.http.auth.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SignAndEncryptNtlmTest {

    private static final Logger LOG = LoggerFactory.getLogger(SignAndEncryptNtlmTest.class);

    @Test
    public void testEncryptionOff() throws IOException {
        String body = "hello";
        LOG.info("hex:\n"+ByteArrayUtils.formatHexDump(body.getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals(wrap(PayloadEncryptionMode.OFF, creds -> {}, body.getBytes()), body.getBytes());
    }

    @Test
    public void getSigningKey() {
        byte[] expected = bytes(0x47, 0x88, 0xdc, 0x86, 0x1b, 0x47, 0x82, 0xf3, 0x5d, 0x43, 0xfd, 0x98, 0xfe, 0x1a, 0x2d, 0x39);
        byte[] session_base_key = ByteArrayUtils.repeated(16, new byte[]{0x55});

        byte[] actual = new NtlmKeys(session_base_key, 0).getSignKey(NtlmKeys.CLIENT_SIGNING);
        assertEquals(actual, expected);
    }

//    def test_get_seal_key_no_flag(self):
//    test_flags = NegotiateFlags.NTLMSSP_NEGOTIATE_SIGN | \
//    NegotiateFlags.NTLMSSP_NEGOTIATE_SEAL
//            expected = b"\x55" * 16
//    exported_session_key = expected
//
//            actual = compute_keys.get_seal_key(test_flags,
//            exported_session_key,
//            SignSealConstants.CLIENT_SEALING)
//        assert actual == expected

    @Test
    public void getSealingKeyNoFlag() {
        byte[] expected = ByteArrayUtils.repeated(16, new byte[]{0x55});
        byte[] session_base_key = expected;

        byte[] actual = new NtlmKeys(session_base_key, NegotiateFlags.NTLMSSP_NEGOTIATE_SIGN | NegotiateFlags.NTLMSSP_NEGOTIATE_SEAL)
                .getSealKey(NtlmKeys.CLIENT_SEALING);
        assertEquals(actual, expected);
    }

//    @Test
//    public void getSealingKeyNtlm2_56() {
//        //    def test_get_seal_key_ntlm2_56(self):
//
//        byte[] expected         = bytes(0x04, 0xdd, 0x7f, 0x01, 0x4d, 0x85, 0x04, 0xd2, 0x65, 0xa2, 0x5c, 0xc8, 0x6a, 0x3a, 0x7c, 0x06);
//        byte[] session_base_key = bytes(0xd8, 0x72, 0x62, 0xb0, 0xcd, 0xe4, 0xb1, 0xcb, 0x74, 0x99, 0xbe, 0xcc, 0xcd, 0xf1, 0x07, 0x84);
//
//        exported_session_key = ... ?
//
//        byte[] actual = new NtlmKeys(exported_session_key, 2181726771L)
//                .getSealKey(NtlmKeys.CLIENT_SEALING);
//        assertEquals(actual, expected);
//    }

    @Test
    public void getSealingKeyNtlm2_128() {
        // def test_get_seal_key_ntlm2_128(self):

        byte[] expected = bytes(0x59, 0xf6, 0x00, 0x97, 0x3c, 0xc4, 0x96, 0x0a, 0x25, 0x48, 0x0a, 0x7c, 0x19, 0x6e, 0x4c, 0x58);
        byte[] session_base_key = ByteArrayUtils.repeated(16, new byte[]{0x55});

        byte[] actual = new NtlmKeys(session_base_key, 3800728115L)
                .getSealKey(NtlmKeys.CLIENT_SEALING);
        assertEquals(actual, expected);
    }

    @Test
    public void testEncryptArc4() throws Exception {
//        def test_encrypt_40bit_key(self, rc4):
        byte[] key = bytes(0x01, 0x02, 0x03, 0x04, 0x05);
        byte[] expected1 = bytes(0xb2, 0x39, 0x63, 0x05, 0xf0, 0x3d, 0xc0, 0x27, 0xcc, 0xc3, 0x52, 0x4a, 0x0a, 0x11, 0x18, 0xa8);
        byte[] expected2 = bytes(0x69, 0x82, 0x94, 0x4f, 0x18, 0xfc, 0x82, 0xd5, 0x89, 0xc4, 0x03, 0xa4, 0x7a, 0x0d, 0x09, 0x19);
        CryptoHandler encryptor = encryptorArc4(key);
        byte[] actual1 = encryptor.update(repeated(16, new byte[]{0}));
        byte[] actual2 = encryptor.update(repeated(16, new byte[]{0}));

        assertEquals(actual1, expected1);
        assertEquals(actual2, expected2);
    }

    @Test
    public void testEncryption1() throws IOException {
        byte[] sessionKey = bytes(0xeb,0x93,0x42,0x9a,0x8b,0xd9,0x52,0xf8,0xb8,0x9c,0x55,0xb8,0x7f,0x47,0x5e,0xdc);
        long flags = 2181726771L;

        byte[] expected_seal = bytes(0xa0, 0x23, 0x72, 0xf6, 0x53, 0x02, 0x73, 0xf3, 0xaa, 0x1e, 0xb9, 0x01, 0x90, 0xce, 0x52, 0x00, 0xc9, 0x9d);
        byte[] expected_sign = bytes(0x01,0x00,0x00,0x00,0xff,0x2a,0xeb,0x52,0xf6,0x81,0x79,0x3a,0x00,0x00,0x00,0x00);
        byte[] plaintext_data = bytes(0x50,0x00,0x6c,0x00,0x61,0x00,0x69,0x00,0x6e,0x00,0x74,0x00,0x65,0x00,0x78,0x00,0x74,0x00);

        CryptoHandler encryptor = WinrmEncryptionUtils.encryptorArc4( new NtlmKeys(sessionKey, flags).getSealKey(NtlmKeys.CLIENT_SEALING) );
        assertEquals(encryptor.update(plaintext_data), expected_seal);

        byte[] unwrapped = unwrapped(wrap(PayloadEncryptionMode.REQUIRED, new NtlmKeys(sessionKey, flags)::apply, plaintext_data), plaintext_data.length);

        assertEquals(unwrapped, ByteArrayUtils.concat(getLittleEndianUnsignedInt(16), expected_sign, expected_seal));
    }

    private byte[] unwrapped(byte[] bytes) {
        return unwrapped(bytes, null);
    }

    private byte[] unwrapped(byte[] bytes, Integer originalLenExpected) {
        String prefix1 =
                SignAndEncryptOutInterceptor.ENCRYPTED_BOUNDARY_CR+
                "\tContent-Type: application/HTTP-SPNEGO-session-encrypted\r\n" +
                "\tOriginalContent: type=application/soap+xml;charset=UTF-8;Length=";
        String prefix2 = "\r\n" +
                SignAndEncryptOutInterceptor.ENCRYPTED_BOUNDARY_CR+
                "\tContent-Type: application/octet-stream\r\n";
        String suffix = SignAndEncryptOutInterceptor.ENCRYPTED_BOUNDARY_END;

        assertEquals(Arrays.copyOfRange(bytes, 0, prefix1.length()), prefix1.getBytes());

        int offset = prefix1.length();
        while (offset<bytes.length && bytes[offset]>='0') offset++;
        int originalLen = Integer.parseInt(new String( Arrays.copyOfRange(bytes, prefix1.length(), offset) ));

        if (originalLenExpected!=null) Assert.assertEquals(originalLen, (int)originalLenExpected, "original length mismatch");

        assertEquals(Arrays.copyOfRange(bytes, offset, offset+prefix2.length()), prefix2.getBytes());

        assertEquals(Arrays.copyOfRange(bytes, bytes.length-suffix.length(), bytes.length), suffix.getBytes());

        return Arrays.copyOfRange(bytes, offset+prefix2.length(), bytes.length-suffix.length());
    }

    static void assertEquals(byte[] b1, byte[] b2) {
        if (b1==b2) return;

        String errmsg = null;

        if (b1==null || b2==null) {
            errmsg = "Null mismatch";
        } else if (b1.length != b2.length) {
            errmsg = "Different lengths";
        } else {
            for (int i=0; i<b1.length; i++) {
                if (b1[i]!=b2[i]) {
                    errmsg = "Mismatch at index "+i;
                    break;
                }
            }
        }
        if (errmsg==null) return;
        Assert.fail(errmsg+"\n--\n"+ByteArrayUtils.formatHexDump(b1)+"--\n"+ByteArrayUtils.formatHexDump(b2)+"--\n");
    }

    private byte[] wrap(PayloadEncryptionMode mode, Consumer<CredentialsWithEncryption> keyInjector, byte[] body) throws IOException {
        SignAndEncryptOutInterceptor interceptor = new SignAndEncryptOutInterceptor(mode);
        Message msg1 = new MessageImpl();
        Message msg2 = new SoapMessage(msg1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        msg2.setContent(OutputStream.class, out);

        NTCredentialsWithEncryption creds = new NTCredentialsWithEncryption("user", "password", "workstation", "domain");
        creds.setIsAuthenticated(true);
        keyInjector.accept(creds);

        msg2.put(Credentials.class.getName(), creds);

        interceptor.handleMessage(msg2);
        OutputStream out0 = msg2.getContent(OutputStream.class);

        out0.write(body);
        out0.close();

        return out.toByteArray();
    }

    @Test
    public void testEncryption2() {
//        def test_encrypt_message():
//        test_session = SessionTest()
//        test_message = b"unencrypted message"
//        test_endpoint = b"endpoint"
//
//        encryption = Encryption(test_session, 'ntlm')
//
//        actual = encryption.prepare_encrypted_request(test_session, test_endpoint, test_message)
//        expected_encrypted_message = b"dW5lbmNyeXB0ZWQgbWVzc2FnZQ=="
//        expected_signature = b"1234"
//        signature_length = struct.pack("<i", len(expected_signature))
//
//        assert actual.headers == {
//                "Content-Length": "272",
//        "Content-Type": 'multipart/encrypted;protocol="application/HTTP-SPNEGO-session-encrypted";boundary="Encrypted Boundary"'
//    }
//        assert actual.body == b"--Encrypted Boundary\r\n" \
//        b"\tContent-Type: application/HTTP-SPNEGO-session-encrypted\r\n" \
//        b"\tOriginalContent: type=application/soap+xml;charset=UTF-8;Length=19\r\n" \
//        b"--Encrypted Boundary\r\n" \
//        b"\tContent-Type: application/octet-stream\r\n" + \
//        signature_length + expected_signature + expected_encrypted_message + \
//        b"--Encrypted Boundary--\r\n"

    }
}
