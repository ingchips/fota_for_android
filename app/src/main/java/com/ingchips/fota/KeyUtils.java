package com.ingchips.fota;

import org.bouncycastle.asn1.nist.NISTNamedCurves;
import org.bouncycastle.asn1.sec.SECObjectIdentifiers;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.agreement.ECDHCBasicAgreement;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;

import java.math.BigInteger;
import java.security.SecureRandom;

public class KeyUtils {
    public byte[] root_sk = new byte[] {
            (byte)0x5c, (byte)0x77, (byte)0x17, (byte)0x11, (byte)0x67, (byte)0xd6, (byte)0x40, (byte)0xa3, (byte)0x36, (byte)0x0d, (byte)0xe2,
            (byte)0x69, (byte)0xfe, (byte)0x0b, (byte)0xb7, (byte)0x8f, (byte)0x5e, (byte)0x94, (byte)0xd8, (byte)0xf2, (byte)0xf4, (byte)0x80,
            (byte)0x94, (byte)0x0a, (byte)0xc2, (byte)0xf2, (byte)0x6e, (byte)0x43, (byte)0xbb, (byte)0x69, (byte)0x5f, (byte)0xa7
    };
    public byte[] root_pk = new byte[] {
            (byte)0x14, (byte)0x1b, (byte)0x0b, (byte)0x28, (byte)0x46, (byte)0xc4, (byte)0xaf, (byte)0x97, (byte)0x41, (byte)0x59, (byte)0x97,
            (byte)0x4f, (byte)0x17, (byte)0x52, (byte)0xe0, (byte)0x1c, (byte)0x9a, (byte)0xea, (byte)0x21, (byte)0xc7, (byte)0xc6, (byte)0xe3,
            (byte)0x04, (byte)0x30, (byte)0x4f, (byte)0x8d, (byte)0x9c, (byte)0xf0, (byte)0x7f, (byte)0x1d, (byte)0x1f, (byte)0x0a, (byte)0x83,
            (byte)0xaf, (byte)0x76, (byte)0xe0, (byte)0x4d, (byte)0xc1, (byte)0xcc, (byte)0x96, (byte)0xb4, (byte)0xb8, (byte)0x3f, (byte)0xbb,
            (byte)0x73, (byte)0x6c, (byte)0x66, (byte)0x3f, (byte)0x0b, (byte)0xdf, (byte)0x52, (byte)0x86, (byte)0xbf, (byte)0x60, (byte)0xe8,
            (byte)0x91, (byte)0x27, (byte)0x00, (byte)0x85, (byte)0xc8, (byte)0xbf, (byte)0x55, (byte)0xa8, (byte)0x96
    };
    public byte[] session_pk;
    public byte[] session_sk;
    public byte[] peer_pk;
    public byte[] shared_secret;
    public byte[] xor_key;

    public boolean is_secure_fota;

    public static byte[] trimToUnsignedByteArray(byte[] array) {
        if (array.length == 33) {
            byte[] temp = new byte[array.length - 1];
            System.arraycopy(array, 1, temp, 0, temp.length);
            array = temp;
        }
        return array;
    }

    public KeyUtils() {
        X9ECParameters curve = NISTNamedCurves.getByOID(SECObjectIdentifiers.secp256r1);
        ECDomainParameters ecParam = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH(), curve.getSeed());
        ECKeyGenerationParameters ecKeyGenerationParameters = new ECKeyGenerationParameters(ecParam, new SecureRandom());
        ECKeyPairGenerator ecKeyPairGenerator = new ECKeyPairGenerator();
        ecKeyPairGenerator.init(ecKeyGenerationParameters);

        AsymmetricCipherKeyPair keyPair = ecKeyPairGenerator.generateKeyPair();

        ECPrivateKeyParameters sk = (ECPrivateKeyParameters)keyPair.getPrivate();
        ECPublicKeyParameters pk = (ECPublicKeyParameters)keyPair.getPublic();

        byte[] xa = trimToUnsignedByteArray(pk.getQ().getXCoord().toBigInteger().toByteArray());
        byte[] ya = trimToUnsignedByteArray(pk.getQ().getYCoord().toBigInteger().toByteArray());

        session_sk = trimToUnsignedByteArray(sk.getD().toByteArray());
        session_pk = new byte[xa.length + ya.length];
        System.arraycopy(xa, 0, session_pk, 0, xa.length);
        System.arraycopy(ya, 0, session_pk, xa.length, ya.length);
        is_secure_fota = false;
    }

    public byte[] signData(byte[] sk, byte[] data) {
        X9ECParameters curve = NISTNamedCurves.getByName("P-256");
        ECDomainParameters ecParam = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH(), curve.getSeed());
        ECPrivateKeyParameters privKey = new ECPrivateKeyParameters(new BigInteger(1, sk), ecParam);

        byte[] hash = SHA256(data);
        ECDSASigner sa = new ECDSASigner();
        sa.init(true, privKey);
        BigInteger[] sig = sa.generateSignature(hash);
        byte[] b1 = trimToUnsignedByteArray(sig[0].toByteArray());
        byte[] b2 = trimToUnsignedByteArray(sig[1].toByteArray());

        byte[] r = new byte[b1.length + b2.length];
        System.arraycopy(b1, 0, r, 0, b1.length);
        System.arraycopy(b2, 0, r, b1.length, b2.length);
        return r;
    }

    public void encrypt(byte[] data) {
        for (int i = 0; i < data.length; i++)
            data[i] ^= xor_key[i & 0x1f];
    }

    public static byte[] SHA256(byte []data) {
        SHA256Digest bcsha256a =  new SHA256Digest();
        bcsha256a.update(data, 0, data.length);

        byte[] checksum = new byte[32];
        bcsha256a.doFinal(checksum, 0);
        return checksum;
    }

    public static byte[] getSharedSecret(byte[] privateKeyIn, byte[] publicKeyIn) {
        ECDHCBasicAgreement agreement = new ECDHCBasicAgreement();

        X9ECParameters curve = NISTNamedCurves.getByName("P-256");
        ECDomainParameters ecParam = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH(), curve.getSeed());
        ECPrivateKeyParameters privKey = new ECPrivateKeyParameters(new BigInteger(1, privateKeyIn), ecParam);

        byte[] publicKeyX = new byte[32];
        byte[] publicKeyY = new byte[32];
        System.arraycopy(publicKeyIn, 0, publicKeyX, 0, 32);
        System.arraycopy(publicKeyIn, 32, publicKeyY, 0, 32);

        BigInteger x = new BigInteger(1, publicKeyX);
        BigInteger y = new BigInteger(1, publicKeyY);

        ECPublicKeyParameters pubKey = new ECPublicKeyParameters(curve.getCurve().validatePoint(x, y), ecParam);

        agreement.init(privKey);
        BigInteger secret = agreement.calculateAgreement(pubKey);

        return secret.toByteArray();
    }
}
