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

package org.xipki.commons.security.pkcs11;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.RuntimeCryptoException;
import org.bouncycastle.operator.ContentSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.common.util.LogUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.HashAlgoType;
import org.xipki.commons.security.exception.P11TokenException;
import org.xipki.commons.security.exception.XiSecurityException;
import org.xipki.commons.security.util.SignerUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */
// CHECKSTYLE:SKIP
class P11DSAContentSigner implements ContentSigner {

    private static final Logger LOG = LoggerFactory.getLogger(P11DSAContentSigner.class);

    private static final Map<String, HashAlgoType> sigAlgHashMap = new HashMap<>();

    private static final Map<HashAlgoType, Long> hashMechMap = new HashMap<>();

    private final P11CryptService cryptService;

    private final P11EntityIdentifier identityId;

    private final AlgorithmIdentifier algorithmIdentifier;

    private final long mechanism;

    private final OutputStream outputStream;

    private final boolean plain;

    static {
        sigAlgHashMap.put(X9ObjectIdentifiers.id_dsa_with_sha1.getId(), HashAlgoType.SHA1);
        sigAlgHashMap.put(NISTObjectIdentifiers.dsa_with_sha224.getId(), HashAlgoType.SHA224);
        sigAlgHashMap.put(NISTObjectIdentifiers.dsa_with_sha256.getId(), HashAlgoType.SHA256);
        sigAlgHashMap.put(NISTObjectIdentifiers.dsa_with_sha384.getId(), HashAlgoType.SHA384);
        sigAlgHashMap.put(NISTObjectIdentifiers.dsa_with_sha512.getId(), HashAlgoType.SHA512);
        sigAlgHashMap.put(NISTObjectIdentifiers.id_dsa_with_sha3_224.getId(),
                HashAlgoType.SHA3_224);
        sigAlgHashMap.put(NISTObjectIdentifiers.id_dsa_with_sha3_256.getId(),
                HashAlgoType.SHA3_256);
        sigAlgHashMap.put(NISTObjectIdentifiers.id_dsa_with_sha3_384.getId(),
                HashAlgoType.SHA3_384);
        sigAlgHashMap.put(NISTObjectIdentifiers.id_dsa_with_sha3_512.getId(),
                HashAlgoType.SHA3_512);

        hashMechMap.put(HashAlgoType.SHA1, P11Constants.CKM_DSA_SHA1);
        hashMechMap.put(HashAlgoType.SHA224, P11Constants.CKM_DSA_SHA224);
        hashMechMap.put(HashAlgoType.SHA256, P11Constants.CKM_DSA_SHA256);
        hashMechMap.put(HashAlgoType.SHA384, P11Constants.CKM_DSA_SHA384);
        hashMechMap.put(HashAlgoType.SHA512, P11Constants.CKM_DSA_SHA512);
        hashMechMap.put(HashAlgoType.SHA3_224, P11Constants.CKM_DSA_SHA3_224);
        hashMechMap.put(HashAlgoType.SHA3_256, P11Constants.CKM_DSA_SHA3_256);
        hashMechMap.put(HashAlgoType.SHA3_384, P11Constants.CKM_DSA_SHA3_384);
        hashMechMap.put(HashAlgoType.SHA3_512, P11Constants.CKM_DSA_SHA3_512);
    }

    P11DSAContentSigner(final P11CryptService cryptService, final P11EntityIdentifier identityId,
            final AlgorithmIdentifier signatureAlgId, final boolean plain)
            throws XiSecurityException, P11TokenException {
        this.identityId = ParamUtil.requireNonNull("identityId", identityId);
        this.cryptService = ParamUtil.requireNonNull("cryptService", cryptService);
        this.algorithmIdentifier = ParamUtil.requireNonNull("signatureAlgId", signatureAlgId);
        this.plain = plain;

        String algOid = signatureAlgId.getAlgorithm().getId();
        HashAlgoType hashAlgo = sigAlgHashMap.get(algOid);
        if (hashAlgo == null) {
            throw new XiSecurityException("unsupported signature algorithm " + algOid);
        }

        P11SlotIdentifier slotId = identityId.getSlotId();
        P11Slot slot = cryptService.getSlot(slotId);
        if (slot.supportsMechanism(P11Constants.CKM_DSA)) {
            this.mechanism = P11Constants.CKM_DSA;
            Digest digest = SignerUtil.getDigest(hashAlgo);
            this.outputStream = new DigestOutputStream(digest);
        } else {
            this.mechanism = hashMechMap.get(hashAlgo).longValue();
            if (!slot.supportsMechanism(this.mechanism)) {
                throw new XiSecurityException("unsupported signature algorithm " + algOid);
            }

            this.outputStream = new ByteArrayOutputStream();
        }
    }

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return algorithmIdentifier;
    }

    @Override
    public OutputStream getOutputStream() {
        if (outputStream instanceof ByteArrayOutputStream) {
            ((ByteArrayOutputStream) outputStream).reset();
        } else {
            ((DigestOutputStream) outputStream).reset();
        }
        return outputStream;
    }

    @Override
    public byte[] getSignature() {
        try {
            byte[] plainSignature = getPlainSignature();
            return plain ? plainSignature : SignerUtil.convertPlainDSASigToX962(plainSignature);
        } catch (XiSecurityException ex) {
            LogUtil.warn(LOG, ex);
            throw new RuntimeCryptoException("XiSecurityException: " + ex.getMessage());
        } catch (Throwable th) {
            LogUtil.warn(LOG, th);
            throw new RuntimeCryptoException(th.getClass().getName() + ": " + th.getMessage());
        }
    }

    private byte[] getPlainSignature() throws XiSecurityException, P11TokenException {
        byte[] dataToSign;
        if (outputStream instanceof ByteArrayOutputStream) {
            dataToSign = ((ByteArrayOutputStream) outputStream).toByteArray();
            ((ByteArrayOutputStream) outputStream).reset();
        } else {
            dataToSign = ((DigestOutputStream) outputStream).digest();
            ((DigestOutputStream) outputStream).reset();
        }

        return cryptService.getIdentity(identityId).sign(mechanism, null, dataToSign);
    }

}
