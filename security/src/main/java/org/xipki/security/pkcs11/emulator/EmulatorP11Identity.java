/*
 *
 * Copyright (c) 2013 - 2017 Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.security.pkcs11.emulator;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.bouncycastle.crypto.macs.HMac;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.common.util.ParamUtil;
import org.xipki.security.HashAlgoType;
import org.xipki.security.XiSecurityConstants;
import org.xipki.security.exception.P11TokenException;
import org.xipki.security.exception.XiSecurityException;
import org.xipki.security.pkcs11.P11EntityIdentifier;
import org.xipki.security.pkcs11.P11Identity;
import org.xipki.security.pkcs11.P11Params;
import org.xipki.security.pkcs11.P11RSAPkcsPssParams;
import org.xipki.security.pkcs11.P11Slot;
import org.xipki.security.util.SignerUtil;

import iaik.pkcs.pkcs11.wrapper.Functions;
import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class EmulatorP11Identity extends P11Identity {

    private static final Logger LOG = LoggerFactory.getLogger(EmulatorP11Identity.class);

    private final Key signingKey;

    private final BlockingDeque<Cipher> rsaCiphers = new LinkedBlockingDeque<>();

    private final BlockingDeque<Signature> dsaSignatures = new LinkedBlockingDeque<>();

    private final SecureRandom random;

    public EmulatorP11Identity(final P11Slot slot, final P11EntityIdentifier identityId,
            final SecretKey signingKey, final int maxSessions, final SecureRandom random)
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException {
        super(slot, identityId, 0);
        this.signingKey = ParamUtil.requireNonNull("signingKey", signingKey);
        this.random = ParamUtil.requireNonNull("random", random);
    } // constructor

    public EmulatorP11Identity(final P11Slot slot, final P11EntityIdentifier identityId,
            final PrivateKey privateKey, final PublicKey publicKey,
            final X509Certificate[] certificateChain, final int maxSessions,
            final SecureRandom random)
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException {
        super(slot, identityId, publicKey, certificateChain);
        this.signingKey = ParamUtil.requireNonNull("privateKey", privateKey);
        this.random = ParamUtil.requireNonNull("random", random);

        if (this.publicKey instanceof RSAPublicKey) {
            String providerName =
                    (Security.getProvider(XiSecurityConstants.PROVIDER_NAME_NSS) != null)
                        ? XiSecurityConstants.PROVIDER_NAME_NSS : "BC";

            LOG.info("use provider {}", providerName);

            for (int i = 0; i < maxSessions; i++) {
                Cipher rsaCipher;
                try {
                    final String algo = "RSA/ECB/NoPadding";
                    rsaCipher = Cipher.getInstance(algo, providerName);
                    LOG.info("use cipher algorithm {}", algo);
                } catch (NoSuchPaddingException ex) {
                    throw new NoSuchAlgorithmException("NoSuchPadding", ex);
                } catch (NoSuchAlgorithmException ex) {
                    final String algo = "RSA/NONE/NoPadding";
                    try {
                        rsaCipher = Cipher.getInstance(algo, providerName);
                        LOG.info("use cipher algorithm {}", algo);
                    } catch (NoSuchPaddingException e1) {
                        throw new NoSuchAlgorithmException("NoSuchPadding", ex);
                    }
                }
                rsaCipher.init(Cipher.ENCRYPT_MODE, privateKey);
                rsaCiphers.add(rsaCipher);
            }
        } else {
            String algorithm;
            if (this.publicKey instanceof ECPublicKey) {
                algorithm = "NONEwithECDSA";
            } else if (this.publicKey instanceof DSAPublicKey) {
                algorithm = "NONEwithDSA";
            } else {
                throw new IllegalArgumentException(
                        "Currently only RSA, DSA and EC public key are supported, but not "
                        + this.publicKey.getAlgorithm()
                        + " (class: " + this.publicKey.getClass().getName() + ")");
            }

            for (int i = 0; i < maxSessions; i++) {
                Signature dsaSignature = Signature.getInstance(algorithm, "BC");
                dsaSignature.initSign(privateKey, random);
                dsaSignatures.add(dsaSignature);
            }
        }
    } // constructor

    @Override
    protected byte[] doDigestSecretKey(long mechanism)
            throws P11TokenException {
        if (!(signingKey instanceof SecretKey)) {
            throw new P11TokenException("digestSecretKey could not be applied to non-SecretKey");
        }

        HashAlgoType hashAlgo = HashAlgoType.getInstanceForPkcs11HashMech(mechanism);
        if (hashAlgo == null) {
            throw new P11TokenException(
                    "unknown mechanism " + Functions.mechanismCodeToString(mechanism));
        }
        return hashAlgo.hash(((SecretKey) signingKey).getEncoded());
    }

    @Override
    protected byte[] doSign(final long mechanism, final P11Params parameters, final byte[] content)
            throws P11TokenException {
        if (PKCS11Constants.CKM_ECDSA == mechanism) {
            return dsaAndEcdsaSign(content, null);
        } else if (PKCS11Constants.CKM_ECDSA_SHA1 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA1);
        } else if (PKCS11Constants.CKM_ECDSA_SHA224 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA224);
        } else if (PKCS11Constants.CKM_ECDSA_SHA256 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA256);
        } else if (PKCS11Constants.CKM_ECDSA_SHA384 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA384);
        } else if (PKCS11Constants.CKM_ECDSA_SHA512 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA512);
        } else if (PKCS11Constants.CKM_ECDSA_SHA3_224 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA3_224);
        } else if (PKCS11Constants.CKM_ECDSA_SHA3_256 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA3_256);
        } else if (PKCS11Constants.CKM_ECDSA_SHA3_384 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA3_384);
        } else if (PKCS11Constants.CKM_ECDSA_SHA3_512 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA3_512);
        } else if (PKCS11Constants.CKM_DSA == mechanism) {
            return dsaAndEcdsaSign(content, null);
        } else if (PKCS11Constants.CKM_DSA_SHA1 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA1);
        } else if (PKCS11Constants.CKM_DSA_SHA224 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA224);
        } else if (PKCS11Constants.CKM_DSA_SHA256 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA256);
        } else if (PKCS11Constants.CKM_DSA_SHA384 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA384);
        } else if (PKCS11Constants.CKM_DSA_SHA512 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA512);
        } else if (PKCS11Constants.CKM_DSA_SHA3_224 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA3_224);
        } else if (PKCS11Constants.CKM_DSA_SHA3_256 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA3_256);
        } else if (PKCS11Constants.CKM_DSA_SHA3_384 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA3_384);
        } else if (PKCS11Constants.CKM_DSA_SHA3_512 == mechanism) {
            return dsaAndEcdsaSign(content, HashAlgoType.SHA3_512);
        } else if (PKCS11Constants.CKM_RSA_X_509 == mechanism) {
            return rsaX509Sign(content);
        } else if (PKCS11Constants.CKM_RSA_PKCS == mechanism) {
            return rsaPkcsSign(content, null);
        } else if (PKCS11Constants.CKM_SHA1_RSA_PKCS == mechanism) {
            return rsaPkcsSign(content, HashAlgoType.SHA1);
        } else if (PKCS11Constants.CKM_SHA224_RSA_PKCS == mechanism) {
            return rsaPkcsSign(content, HashAlgoType.SHA224);
        } else if (PKCS11Constants.CKM_SHA256_RSA_PKCS == mechanism) {
            return rsaPkcsSign(content, HashAlgoType.SHA256);
        } else if (PKCS11Constants.CKM_SHA384_RSA_PKCS == mechanism) {
            return rsaPkcsSign(content, HashAlgoType.SHA384);
        } else if (PKCS11Constants.CKM_SHA512_RSA_PKCS == mechanism) {
            return rsaPkcsSign(content, HashAlgoType.SHA512);
        } else if (PKCS11Constants.CKM_SHA3_224_RSA_PKCS == mechanism) {
            return rsaPkcsSign(content, HashAlgoType.SHA3_224);
        } else if (PKCS11Constants.CKM_SHA3_256_RSA_PKCS == mechanism) {
            return rsaPkcsSign(content, HashAlgoType.SHA3_256);
        } else if (PKCS11Constants.CKM_SHA3_384_RSA_PKCS == mechanism) {
            return rsaPkcsSign(content, HashAlgoType.SHA3_384);
        } else if (PKCS11Constants.CKM_SHA3_512_RSA_PKCS == mechanism) {
            return rsaPkcsSign(content, HashAlgoType.SHA3_512);
        } else if (PKCS11Constants.CKM_RSA_PKCS_PSS == mechanism) {
            return rsaPkcsPssSign(parameters, content, null);
        } else if (PKCS11Constants.CKM_SHA1_RSA_PKCS_PSS == mechanism) {
            return rsaPkcsPssSign(parameters, content, HashAlgoType.SHA1);
        } else if (PKCS11Constants.CKM_SHA224_RSA_PKCS_PSS == mechanism) {
            return rsaPkcsPssSign(parameters, content, HashAlgoType.SHA224);
        } else if (PKCS11Constants.CKM_SHA256_RSA_PKCS_PSS == mechanism) {
            return rsaPkcsPssSign(parameters, content, HashAlgoType.SHA256);
        } else if (PKCS11Constants.CKM_SHA384_RSA_PKCS_PSS == mechanism) {
            return rsaPkcsPssSign(parameters, content, HashAlgoType.SHA384);
        } else if (PKCS11Constants.CKM_SHA512_RSA_PKCS_PSS == mechanism) {
            return rsaPkcsPssSign(parameters, content, HashAlgoType.SHA512);
        } else if (PKCS11Constants.CKM_SHA3_224_RSA_PKCS_PSS == mechanism) {
            return rsaPkcsPssSign(parameters, content, HashAlgoType.SHA3_224);
        } else if (PKCS11Constants.CKM_SHA3_256_RSA_PKCS_PSS == mechanism) {
            return rsaPkcsPssSign(parameters, content, HashAlgoType.SHA3_256);
        } else if (PKCS11Constants.CKM_SHA3_384_RSA_PKCS_PSS == mechanism) {
            return rsaPkcsPssSign(parameters, content, HashAlgoType.SHA3_384);
        } else if (PKCS11Constants.CKM_SHA3_512_RSA_PKCS_PSS == mechanism) {
            return rsaPkcsPssSign(parameters, content, HashAlgoType.SHA3_512);
        } else if (PKCS11Constants.CKM_SHA_1_HMAC == mechanism) {
            return hmac(content, HashAlgoType.SHA1);
        } else if (PKCS11Constants.CKM_SHA224_HMAC == mechanism) {
            return hmac(content, HashAlgoType.SHA224);
        } else if (PKCS11Constants.CKM_SHA256_HMAC == mechanism) {
            return hmac(content, HashAlgoType.SHA256);
        } else if (PKCS11Constants.CKM_SHA384_HMAC == mechanism) {
            return hmac(content, HashAlgoType.SHA384);
        } else if (PKCS11Constants.CKM_SHA512_HMAC == mechanism) {
            return hmac(content, HashAlgoType.SHA512);
        } else if (PKCS11Constants.CKM_SHA3_224_HMAC == mechanism) {
            return hmac(content, HashAlgoType.SHA3_224);
        } else if (PKCS11Constants.CKM_SHA3_256_HMAC == mechanism) {
            return hmac(content, HashAlgoType.SHA3_256);
        } else if (PKCS11Constants.CKM_SHA3_384_HMAC == mechanism) {
            return hmac(content, HashAlgoType.SHA3_384);
        } else if (PKCS11Constants.CKM_SHA3_512_HMAC == mechanism) {
            return hmac(content, HashAlgoType.SHA3_512);
        } else {
            throw new P11TokenException("unsupported mechanism " + mechanism);
        }
    }

    private byte[] hmac(final byte[] contentToSign,
            final HashAlgoType hashAlgo) {
        HMac hmac = new HMac(hashAlgo.createDigest());
        hmac.update(contentToSign, 0, contentToSign.length);
        byte[] signature = new byte[hmac.getMacSize()];
        hmac.doFinal(signature, 0);
        return signature;
    }

    private byte[] rsaPkcsPssSign(final P11Params parameters, final byte[] contentToSign,
            final HashAlgoType hashAlgo) throws P11TokenException {
        if (!(parameters instanceof P11RSAPkcsPssParams)) {
            throw new P11TokenException("the parameters is not of "
                    + P11RSAPkcsPssParams.class.getName());
        }

        P11RSAPkcsPssParams pssParam = (P11RSAPkcsPssParams) parameters;
        HashAlgoType contentHash = HashAlgoType.getInstanceForPkcs11HashMech(
                pssParam.hashAlgorithm());
        if (contentHash == null) {
            throw new P11TokenException(
                    "unsupported HashAlgorithm " + pssParam.hashAlgorithm());
        } else if (hashAlgo != null && contentHash != hashAlgo) {
            throw new P11TokenException("Invalid parameters: invalid hash algorithm");
        }

        HashAlgoType mgfHash = HashAlgoType.getInstanceForPkcs11MgfMech(
                pssParam.maskGenerationFunction());
        if (mgfHash == null) {
            throw new P11TokenException(
                    "unsupported MaskGenerationFunction " + pssParam.hashAlgorithm());
        }

        byte[] hashValue = (hashAlgo == null) ? contentToSign : hashAlgo.hash(contentToSign);
        byte[] encodedHashValue;
        try {
            encodedHashValue = SignerUtil.EMSA_PSS_ENCODE(contentHash, hashValue, mgfHash,
                    (int) pssParam.saltLength(), signatureKeyBitLength(), random);
        } catch (XiSecurityException ex) {
            throw new P11TokenException("XiSecurityException: " + ex.getMessage(), ex);
        }
        return rsaX509Sign(encodedHashValue);
    }

    private byte[] rsaPkcsSign(final byte[] contentToSign, final HashAlgoType hashAlgo)
            throws P11TokenException {
        int modulusBitLen = signatureKeyBitLength();
        byte[] paddedHash;
        try {
            if (hashAlgo == null) {
                paddedHash = SignerUtil.EMSA_PKCS1_v1_5_encoding(contentToSign, modulusBitLen);
            } else {
                byte[] hash = hashAlgo.hash(contentToSign);
                paddedHash = SignerUtil.EMSA_PKCS1_v1_5_encoding(hash, modulusBitLen, hashAlgo);
            }
        } catch (XiSecurityException ex) {
            throw new P11TokenException("XiSecurityException: " + ex.getMessage(), ex);
        }

        return rsaX509Sign(paddedHash);
    }

    private byte[] rsaX509Sign(final byte[] dataToSign) throws P11TokenException {
        Cipher cipher;
        try {
            cipher = rsaCiphers.takeFirst();
        } catch (InterruptedException ex) {
            throw new P11TokenException("could not take any idle signer");
        }

        try {
            return cipher.doFinal(dataToSign);
        } catch (BadPaddingException ex) {
            throw new P11TokenException("BadPaddingException: " + ex.getMessage(), ex);
        } catch (IllegalBlockSizeException ex) {
            throw new P11TokenException("IllegalBlockSizeException: " + ex.getMessage(), ex);
        } finally {
            rsaCiphers.add(cipher);
        }
    }

    private byte[] dsaAndEcdsaSign(final byte[] dataToSign, final HashAlgoType hashAlgo)
            throws P11TokenException {
        byte[] hash = (hashAlgo == null) ? dataToSign : hashAlgo.hash(dataToSign);

        Signature sig;
        try {
            sig = dsaSignatures.takeFirst();
        } catch (InterruptedException ex) {
            throw new P11TokenException(
                    "InterruptedException occurs while retrieving idle signature");
        }

        try {
            sig.update(hash);
            byte[] x962Signature = sig.sign();
            return SignerUtil.convertX962DSASigToPlain(x962Signature, signatureKeyBitLength());
        } catch (SignatureException ex) {
            throw new P11TokenException("SignatureException: " + ex.getMessage(), ex);
        } catch (XiSecurityException ex) {
            throw new P11TokenException("XiSecurityException: " + ex.getMessage(), ex);
        } finally {
            dsaSignatures.add(sig);
        }
    }

    Key signingKey() {
        return signingKey;
    }

}
