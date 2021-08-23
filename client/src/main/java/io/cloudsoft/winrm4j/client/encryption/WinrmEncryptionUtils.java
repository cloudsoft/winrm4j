package io.cloudsoft.winrm4j.client.encryption;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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

    public static CryptoHandler encryptorArc4(byte[] key) {
        return cryptorArc4(true, key);
    }

    public static CryptoHandler decryptorArc4(byte[] key) {
        return cryptorArc4(false, key);
    }

    public static CryptoHandler cryptorArc4(boolean forEncryption, byte[] key) {
        // engine needs to be stateful
        try {
            final Cipher rc4 = Cipher.getInstance("RC4");
            rc4.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "RC4"));

            return new CryptoHandler() {
                @Override
                public byte[] update(byte[] input) {
                    try {
                        return rc4.update(input);

                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            };

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

            // using bouncycastle - but brings in unnecessary deps
//        RC4Engine engine = new RC4Engine();
//        engine.init(forEncryption, new KeyParameter(key));
//
//        return new CryptoHandler() {
//            @Override
//            public byte[] update(byte[] input) {
//                try {
//                    ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
//                    CipherOutputStream cos = new CipherOutputStream(outBytes, engine);
//                    cos.write(input);
//
//                    return outBytes.toByteArray();
//                } catch (Exception e) {
//                    throw new IllegalStateException(e);
//                }
//            }
//        };
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

    public interface CryptoHandler {
        byte[] update(byte[] input);
    }

}
