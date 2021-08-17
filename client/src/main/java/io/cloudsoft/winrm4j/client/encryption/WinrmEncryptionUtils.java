package io.cloudsoft.winrm4j.client.encryption;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.engines.RC4Engine;
import org.bouncycastle.crypto.io.CipherOutputStream;
import org.bouncycastle.crypto.params.KeyParameter;

public class WinrmEncryptionUtils {


    public static byte[] md5digest(byte[] bytes) {
        try {
            MessageDigest handle = MessageDigest.getInstance("MD5");
            handle.update(bytes);
            return handle.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // don't use this -- we need the pseudoRNG to be stateful
//    public static byte[] encryptArc4(byte[] in, byte[] key) throws IOException {
//        // ntlm_auth session_security:
////        csk = self._client_sealing_key
////        ssk = self._server_sealing_key
////        if outgoing:
////        self.outgoing_handle = ARC4(csk if self._source == 'client' else ssk)
////        else:
////        self.incoming_handle = ARC4(ssk if self._source == 'client' else csk)
//
//        RC4Engine engine = new RC4Engine();
//        engine.init(true, new KeyParameter(key));
//
//        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
//        CipherOutputStream cos = new CipherOutputStream(outBytes, engine);
//        cos.write(in);
//
//        return outBytes.toByteArray();
//    }

    public static StatefulEncryption encryptorArc4(byte[] key) {
        RC4Engine engine = new RC4Engine();
        engine.init(true, new KeyParameter(key));

        return new StatefulEncryption() {
            @Override
            public byte[] update(byte[] input) {
                try {
                    ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
                    CipherOutputStream cos = new CipherOutputStream(outBytes, engine);
                    cos.write(input);

                    return outBytes.toByteArray();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    public static byte[] hmacMd5(byte[] key, byte[] body) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, "HmacMD5");
            Mac mac = Mac.getInstance("HmacMD5");
            mac.init(keySpec);
            return mac.doFinal( body );
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    public interface StatefulEncryption {
        byte[] update(byte[] input);
    }

}
