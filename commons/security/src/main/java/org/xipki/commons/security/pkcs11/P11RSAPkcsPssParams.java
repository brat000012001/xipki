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

import java.math.BigInteger;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSASSAPSSparams;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.xipki.commons.security.HashAlgoType;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

// CHECKSTYLE:SKIP
public class P11RSAPkcsPssParams implements P11Params {

    private final long hashAlgorithm;

    private final long maskGenerationFunction;

    private final long saltLength;

    public P11RSAPkcsPssParams(final long hashAlgorithm, final long maskGenerationFunction,
            final long saltLength) {
        this.hashAlgorithm = hashAlgorithm;
        this.maskGenerationFunction = maskGenerationFunction;
        this.saltLength = saltLength;
    }

    public P11RSAPkcsPssParams(final RSASSAPSSparams asn1Params) {
        ASN1ObjectIdentifier asn1Oid = asn1Params.getHashAlgorithm().getAlgorithm();
        HashAlgoType contentHashAlgo = HashAlgoType.getHashAlgoType(asn1Oid);
        if (contentHashAlgo == null) {
            throw new IllegalArgumentException("unsupported hash algorithm " + asn1Oid.getId());
        }

        AlgorithmIdentifier mga = asn1Params.getMaskGenAlgorithm();
        asn1Oid = mga.getAlgorithm();
        if (!PKCSObjectIdentifiers.id_mgf1.equals(asn1Oid)) {
            throw new IllegalArgumentException("unsupported MGF algorithm " + asn1Oid.getId());
        }

        asn1Oid = AlgorithmIdentifier.getInstance(mga.getParameters()).getAlgorithm();
        HashAlgoType mgfHashAlgo = HashAlgoType.getHashAlgoType(asn1Oid);
        if (mgfHashAlgo == null) {
            throw new IllegalArgumentException("unsupported MGF hash algorithm " + asn1Oid.getId());
        }
        this.saltLength = asn1Params.getSaltLength().longValue();
        BigInteger trailerField = asn1Params.getTrailerField();
        if (!RSASSAPSSparams.DEFAULT_TRAILER_FIELD.getValue().equals(trailerField)) {
            throw new IllegalArgumentException("unsupported trailerField " + trailerField);
        }

        switch (contentHashAlgo) {
        case SHA1:
            this.hashAlgorithm = P11Constants.CKM_SHA_1;
            break;
        case SHA224:
            this.hashAlgorithm = P11Constants.CKM_SHA224;
            break;
        case SHA256:
            this.hashAlgorithm = P11Constants.CKM_SHA256;
            break;
        case SHA384:
            this.hashAlgorithm = P11Constants.CKM_SHA384;
            break;
        case SHA512:
            this.hashAlgorithm = P11Constants.CKM_SHA512;
            break;
        case SHA3_224:
            this.hashAlgorithm = P11Constants.CKM_SHA3_224;
            break;
        case SHA3_256:
            this.hashAlgorithm = P11Constants.CKM_SHA3_256;
            break;
        case SHA3_384:
            this.hashAlgorithm = P11Constants.CKM_SHA3_384;
            break;
        case SHA3_512:
            this.hashAlgorithm = P11Constants.CKM_SHA3_512;
            break;
        default:
            throw new RuntimeException("should not reach here");
        }

        switch (mgfHashAlgo) {
        case SHA1:
            this.maskGenerationFunction = P11Constants.CKG_MGF1_SHA1;
            break;
        case SHA224:
            this.maskGenerationFunction = P11Constants.CKG_MGF1_SHA224;
            break;
        case SHA256:
            this.maskGenerationFunction = P11Constants.CKG_MGF1_SHA256;
            break;
        case SHA384:
            this.maskGenerationFunction = P11Constants.CKG_MGF1_SHA384;
            break;
        case SHA512:
            this.maskGenerationFunction = P11Constants.CKG_MGF1_SHA512;
            break;
        case SHA3_224:
            this.maskGenerationFunction = P11Constants.CKG_MGF1_SHA3_224;
            break;
        case SHA3_256:
            this.maskGenerationFunction = P11Constants.CKG_MGF1_SHA3_256;
            break;
        case SHA3_384:
            this.maskGenerationFunction = P11Constants.CKG_MGF1_SHA3_384;
            break;
        case SHA3_512:
            this.maskGenerationFunction = P11Constants.CKG_MGF1_SHA3_512;
            break;
        default:
            throw new RuntimeException("should not reach here");
        }
    }

    public long getHashAlgorithm() {
        return hashAlgorithm;
    }

    public long getMaskGenerationFunction() {
        return maskGenerationFunction;
    }

    public long getSaltLength() {
        return saltLength;
    }

}
