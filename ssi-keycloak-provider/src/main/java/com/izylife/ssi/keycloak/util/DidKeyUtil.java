/*
 * SSI Keycloak Provider
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.izylife.ssi.keycloak.util;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Generates EC P-256 key pairs and derives did:key identifiers.
 * Implements base58btc multibase encoding inline (no external dependencies).
 *
 * DID:key derivation for P-256:
 *   multicodec prefix = [0x80, 0x24]  (varint encoding of 0x1200)
 *   payload = prefix || compressed_public_key (33 bytes)
 *   did = "did:key:z" + base58btc(payload)
 */
public final class DidKeyUtil {

    private static final char[] BASE58_ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    // P-256 multicodec prefix: varint(0x1200) = [0x80, 0x24]
    private static final byte[] P256_MULTICODEC_PREFIX = {(byte) 0x80, 0x24};

    private DidKeyUtil() {}

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate EC P-256 key pair", e);
        }
    }

    public static String deriveDid(ECPublicKey publicKey) {
        byte[] compressed = toCompressedPublicKey(publicKey);
        byte[] multicodec = concat(P256_MULTICODEC_PREFIX, compressed);
        return "did:key:z" + base58Encode(multicodec);
    }

    public static String toPublicJwkJson(ECPublicKey key, String kid) {
        String x = encodeCoordinate(key.getW().getAffineX().toByteArray());
        String y = encodeCoordinate(key.getW().getAffineY().toByteArray());
        return String.format(
                "{\"kty\":\"EC\",\"crv\":\"P-256\",\"kid\":\"%s\",\"x\":\"%s\",\"y\":\"%s\"}",
                kid, x, y);
    }

    public static String toPrivateJwkJson(ECPrivateKey privKey, ECPublicKey pubKey, String kid) {
        String x = encodeCoordinate(pubKey.getW().getAffineX().toByteArray());
        String y = encodeCoordinate(pubKey.getW().getAffineY().toByteArray());
        String d = encodeCoordinate(privKey.getS().toByteArray());
        return String.format(
                "{\"kty\":\"EC\",\"crv\":\"P-256\",\"kid\":\"%s\",\"x\":\"%s\",\"y\":\"%s\",\"d\":\"%s\"}",
                kid, x, y, d);
    }

    private static byte[] toCompressedPublicKey(ECPublicKey key) {
        byte[] x = padTo32(key.getW().getAffineX().toByteArray());
        byte[] y = padTo32(key.getW().getAffineY().toByteArray());
        byte prefix = (y[31] & 0x01) == 0 ? (byte) 0x02 : (byte) 0x03;
        byte[] compressed = new byte[33];
        compressed[0] = prefix;
        System.arraycopy(x, 0, compressed, 1, 32);
        return compressed;
    }

    private static String encodeCoordinate(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(padTo32(bytes));
    }

    /** Normalises a BigInteger byte array to exactly 32 bytes (strips sign byte or left-pads). */
    static byte[] padTo32(byte[] bytes) {
        if (bytes.length == 32) return bytes;
        if (bytes.length > 32) {
            return Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length);
        }
        byte[] padded = new byte[32];
        System.arraycopy(bytes, 0, padded, 32 - bytes.length, bytes.length);
        return padded;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static String base58Encode(byte[] input) {
        int leadingZeros = 0;
        for (byte b : input) {
            if (b != 0) break;
            leadingZeros++;
        }
        BigInteger n = new BigInteger(1, input);
        BigInteger base = BigInteger.valueOf(58);
        StringBuilder sb = new StringBuilder();
        while (n.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] dm = n.divideAndRemainder(base);
            n = dm[0];
            sb.append(BASE58_ALPHABET[dm[1].intValue()]);
        }
        for (int i = 0; i < leadingZeros; i++) {
            sb.append(BASE58_ALPHABET[0]);
        }
        return sb.reverse().toString();
    }
}
