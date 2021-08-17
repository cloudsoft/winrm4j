package io.cloudsoft.winrm4j.client.ntlm;

import static io.cloudsoft.winrm4j.client.encryption.ByteArrayUtils.concat;
import io.cloudsoft.winrm4j.client.encryption.CredentialsWithEncryption;
import io.cloudsoft.winrm4j.client.encryption.WinrmEncryptionUtils;
import io.cloudsoft.winrm4j.client.ntlm.forks.httpclient.NTLMEngineImpl.Type3Message;
import java.util.Arrays;

public class NtlmKeys {

    // adapted from python ntlm-auth

    // also see NTLMEngineImpl.Handle


//    # Copyright: (c) 2018, Jordan Borean (@jborean93) <jborean93@gmail.com>
//            # MIT License (see LICENSE or https://opensource.org/licenses/MIT)

//    CLIENT_SIGNING = b"session key to client-to-server signing key magic " \
//    b"constant\x00"
    public static final byte[] CLIENT_SIGNING = "session key to client-to-server signing key magic constant\0".getBytes();

//    SERVER_SIGNING = b"session key to server-to-client signing key magic " \
//    b"constant\x00"
    public static final byte[] SERVER_SIGNING = "session key to server-to-client signing key magic constant\0".getBytes();

//    CLIENT_SEALING = b"session key to client-to-server sealing key magic " \
//    b"constant\x00"
    public static final byte[] CLIENT_SEALING = "session key to client-to-server sealing key magic constant\0".getBytes();

//    SERVER_SEALING = b"session key to server-to-client sealing key magic " \
//    b"constant\x00"
    public static final byte[] SERVER_SEALING = "session key to server-to-client sealing key magic constant\0".getBytes();

    public NtlmKeys(Type3Message signAndSealData) {
        this(signAndSealData.getExportedSessionKey(), signAndSealData.getType2Flags());
    }

    public static class NegotiateFlags {
        // expanded set of what is in NTLMEngineImpl

        // 0b 10100010_10001010_10000010_00000101
        // 0b 10100010_00001000_10000010_00110001

        public static final long NTLMSSP_NEGOTIATE_56 = 0x80000000L;
        public static final long NTLMSSP_NEGOTIATE_KEY_EXCH = 0x40000000L;
        public static final long NTLMSSP_NEGOTIATE_128 = 0x20000000L;
        public static final long NTLMSSP_RESERVED_R1 = 0x10000000L;
        public static final long NTLMSSP_RESERVED_R2 = 0x08000000L;
        public static final long NTLMSSP_RESERVED_R3 = 0x04000000L;
        public static final long NTLMSSP_NEGOTIATE_VERSION = 0x02000000L;
        public static final long NTLMSSP_RESERVED_R4 = 0x01000000L;
        public static final long NTLMSSP_NEGOTIATE_TARGET_INFO = 0x00800000L;
        public static final long NTLMSSP_REQUEST_NON_NT_SESSION_KEY = 0x00400000L;
        public static final long NTLMSSP_RESERVED_R5 = 0x00200000L;
        public static final long NTLMSSP_NEGOTIATE_IDENTITY = 0x00100000L;
//        protected static final int FLAG_REQUEST_NTLM2_SESSION = 0x00080000; // From server in challenge, requesting NTLM2 session security
        public static final long NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY = 0x00080000L;
        public static final long NTLMSSP_RESERVED_R6 = 0x00040000L;
        public static final long NTLMSSP_TARGET_TYPE_SERVER = 0x00020000L;
        public static final long NTLMSSP_TARGET_TYPE_DOMAIN = 0x00010000L;
        public static final long NTLMSSP_NEGOTIATE_ALWAYS_SIGN = 0x00008000L;
        public static final long NTLMSSP_RESERVED_R7 = 0x00004000L;
        public static final long NTLMSSP_NEGOTIATE_OEM_WORKSTATION_SUPPLIED = 0x00002000L;
        public static final long NTLMSSP_NEGOTIATE_OEM_DOMAIN_SUPPLIED = 0x00001000L;
        public static final long NTLMSSP_ANOYNMOUS = 0x00000800L;
        public static final long NTLMSSP_RESERVED_R8 = 0x00000400L;
//        protected static final int FLAG_REQUEST_NTLMv1 = 0x00000200; // Request NTLMv1 security.  MUST be set in NEGOTIATE and CHALLENGE both
        public static final long NTLMSSP_NEGOTIATE_NTLM = 0x00000200L;
        public static final long NTLMSSP_RESERVED_R9 = 0x00000100L;
        public static final long NTLMSSP_NEGOTIATE_LM_KEY = 0x00000080L;
        public static final long NTLMSSP_NEGOTIATE_DATAGRAM = 0x00000040L;
        public static final long NTLMSSP_NEGOTIATE_SEAL = 0x00000020L;
        public static final long NTLMSSP_NEGOTIATE_SIGN = 0x00000010L;
        public static final long NTLMSSP_RESERVED_R10 = 0x00000008L;
        public static final long NTLMSSP_REQUEST_TARGET = 0x00000004L;
        public static final long NTLMSSP_NEGOTIATE_OEM = 0x00000002L;
        public static final long NTLMSSP_NEGOTIATE_UNICODE = 0x00000001L;
    }

    private final byte[] exportedSessionKey;
    private final long negotiateFlags;

//            def _get_exchange_key_ntlm_v1(negotiate_flags, session_base_key,
//                           server_challenge, lm_challenge_response,
//                           lm_hash):
//            """

    public NtlmKeys(byte[] exportedSessionKey, long negotiateFlags) {
        this.exportedSessionKey = exportedSessionKey;
        this.negotiateFlags = negotiateFlags;
    }

    public byte[] getExportedSessionKey() {
        return exportedSessionKey;
    }

    public long getNegotiateFlags() {
        return negotiateFlags;
    }

    //    [MS-NLMP] v28.0 2016-07-14
//
//    3.4.5.1 KXKEY
//    Calculates the Key Exchange Key for NTLMv1 authentication. Used for signing
//    and sealing messages
//
//    :param negotiate_flags: The negotiated NTLM flags
//    :param session_base_key: A session key calculated from the user password
//        challenge
//    :param server_challenge: A random 8-byte response generated by the server
//        in the CHALLENGE_MESSAGE
//    :param lm_challenge_response: The LmChallengeResponse value computed in
//        ComputeResponse
//    :param lm_hash: The LMOWF computed in Compute Response
//    :return: The Key Exchange Key (KXKEY) used to sign and seal messages and
//        compute the ExportedSessionKey
//    """
//            if negotiate_flags & \
//    NegotiateFlags.NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY:
//    key_exchange_key = hmac.new(
//    session_base_key, server_challenge + lm_challenge_response[:8],
//    digestmod=hashlib.md5
//        ).digest()
//    elif negotiate_flags & NegotiateFlags.NTLMSSP_NEGOTIATE_LM_KEY:
//    des_handler = DES(DES.key56_to_key64(lm_hash[:7]))
//    first_des = des_handler.encrypt(lm_challenge_response[:8])
//
//    second_des_key = lm_hash[7:8] + b"\xbd\xbd\xbd\xbd\xbd\xbd"
//    des_handler = DES(DES.key56_to_key64(second_des_key))
//    second_des = des_handler.encrypt(lm_challenge_response[:8])
//
//    key_exchange_key = first_des + second_des
//    elif negotiate_flags & NegotiateFlags.NTLMSSP_REQUEST_NON_NT_SESSION_KEY:
//    key_exchange_key = lm_hash[:8] + b'\0' * 8
//            else:
//    key_exchange_key = session_base_key
//
//    return key_exchange_key
//
//
//    def _get_exchange_key_ntlm_v2(session_base_key):
//            """
//    [MS-NLMP] v28.0 2016-07-14
//
//    4.3.5.1 KXKEY
//    Calculates the Key Exchange Key for NTLMv2 authentication. Used for signing
//    and sealing messages. According to docs, 'If NTLM v2 is used,
//    KeyExchangeKey MUST be set to the given 128-bit SessionBaseKey
//
//    :param session_base_key: A session key calculated from the user password
//        challenge
//    :return key_exchange_key: The Key Exchange Key (KXKEY) used to sign and
//        seal messages
//    """
//            return session_base_key
//
//

    public boolean hasNegotiateFlag(long flag) {
        return (negotiateFlags & flag)==flag;
    }

    public byte[] getSignKey(byte[] magicConstant) {
//    def get_sign_key(exported_session_key, magic_constant):
//            """
//    3.4.5.2 SIGNKEY
//
//    :param exported_session_key: A 128-bit session key used to derive signing
//        and sealing keys
//    :param magic_constant: A constant value set in the MS-NLMP documentation
//        (constants.SignSealConstants)
//    :return sign_key: Key used to sign messages
//    """
//
//    sign_key = hashlib.md5(exported_session_key + magic_constant).digest()
//
//    return sign_key
        return WinrmEncryptionUtils.md5digest(concat(exportedSessionKey, magicConstant));
    }

    public byte[] getSealKey(byte[] magicConstant) {
//
//
//    def get_seal_key(negotiate_flags, exported_session_key, magic_constant):
//            """
//    3.4.5.3. SEALKEY
//    Main method to use to calculate the seal_key used to seal (encrypt)
//    messages. This will determine the correct method below to use based on the
//    compatibility flags set and should be called instead of the others
//
//    :param exported_session_key: A 128-bit session key used to derive signing
//        and sealing keys
//    :param negotiate_flags: The negotiate_flags structure sent by the server
//    :param magic_constant: A constant value set in the MS-NLMP documentation
//        (constants.SignSealConstants)
//    :return seal_key: Key used to seal messages
//    """
//
//            if negotiate_flags & \
//    NegotiateFlags.NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY:
//    seal_key = _get_seal_key_ntlm2(negotiate_flags,
//                                   exported_session_key,
//                                   magic_constant)
//    elif negotiate_flags & NegotiateFlags.NTLMSSP_NEGOTIATE_LM_KEY:
//    seal_key = _get_seal_key_ntlm1(negotiate_flags, exported_session_key)
//    else:
//    seal_key = exported_session_key
//
//    return seal_key
        if (hasNegotiateFlag(NegotiateFlags.NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY)) {
            return getSealKeyNtlm2(magicConstant);
        } else if (hasNegotiateFlag(NegotiateFlags.NTLMSSP_NEGOTIATE_LM_KEY)) {
            return getSealKeyNtlm1(magicConstant);
        } else {
            return exportedSessionKey;
        }
    }

    private byte[] getSealKeyNtlm1(byte[] magicConstant) {
//
//
//    def _get_seal_key_ntlm1(negotiate_flags, exported_session_key):
//            """
//    3.4.5.3 SEALKEY
//    Calculates the seal_key used to seal (encrypt) messages. This for
//    authentication where NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY has not
//    been negotiated. Will weaken the keys if NTLMSSP_NEGOTIATE_56 is not
//    negotiated it will default to the 40-bit key
//
//    :param negotiate_flags: The negotiate_flags structure sent by the server
//    :param exported_session_key: A 128-bit session key used to derive signing
//        and sealing keys
//    :return seal_key: Key used to seal messages
//    """
//            if negotiate_flags & NegotiateFlags.NTLMSSP_NEGOTIATE_56:
//    seal_key = exported_session_key[:7] + b"\xa0"
//            else:
//    seal_key = exported_session_key[:5] + b"\xe5\x38\xb0"
//
//            return seal_key
        throw new UnsupportedOperationException("LM KEY negotiate mode not implemented; use extended session security instead");
    }

    private byte[] getSealKeyNtlm2(byte[] magicConstant) {
//
//    def _get_seal_key_ntlm2(negotiate_flags, exported_session_key, magic_constant):
//            """
//    3.4.5.3 SEALKEY
//    Calculates the seal_key used to seal (encrypt) messages. This for
//    authentication where NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY has been
//    negotiated. Will weaken the keys if NTLMSSP_NEGOTIATE_128 is not
//    negotiated, will try NEGOTIATE_56 and then will default to the 40-bit key
//
//    :param negotiate_flags: The negotiate_flags structure sent by the server
//    :param exported_session_key: A 128-bit session key used to derive signing
//        and sealing keys
//    :param magic_constant: A constant value set in the MS-NLMP documentation
//        (constants.SignSealConstants)
//    :return seal_key: Key used to seal messages
//    """
//            if negotiate_flags & NegotiateFlags.NTLMSSP_NEGOTIATE_128:
//    seal_key = exported_session_key
//    elif negotiate_flags & NegotiateFlags.NTLMSSP_NEGOTIATE_56:
//    seal_key = exported_session_key[:7]
//            else:
//    seal_key = exported_session_key[:5]
//
//    seal_key = hashlib.md5(seal_key + magic_constant).digest()
//
//    return seal_key

        byte[] key1;
        if (hasNegotiateFlag(NegotiateFlags.NTLMSSP_NEGOTIATE_128)) {
            key1 = exportedSessionKey;
        } else if (hasNegotiateFlag(NegotiateFlags.NTLMSSP_NEGOTIATE_56)) {
            key1 = Arrays.copyOfRange(exportedSessionKey, 0, 7);
        } else {
            key1 = Arrays.copyOfRange(exportedSessionKey, 0, 5);
        }

        return WinrmEncryptionUtils.md5digest(concat(key1, magicConstant));

    }

    public void apply(CredentialsWithEncryption credentials) {
        credentials.setNegotiateFlags(getNegotiateFlags());

        credentials.setClientSigningKey( this.getSignKey(NtlmKeys.CLIENT_SIGNING) );
        credentials.setServerSigningKey( this.getSignKey(NtlmKeys.SERVER_SIGNING) );
        credentials.setClientSealingKey( this.getSealKey(NtlmKeys.CLIENT_SEALING) );
        credentials.setServerSealingKey( this.getSealKey(NtlmKeys.SERVER_SEALING) );
    }

}
