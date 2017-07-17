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

package org.xipki.ocsp.client.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSASSAPSSparams;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.xipki.common.util.ParamUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class RequestOptions {

    private static final Map<String, AlgorithmIdentifier> SIGALGS_MAP = new HashMap<>();

    static {
        String algoName = "SHA1withRSA";
        SIGALGS_MAP.put(algoName.toUpperCase(), createAlgId(algoName));

        algoName = "SHA256withRSA";
        SIGALGS_MAP.put(algoName.toUpperCase(), createAlgId(algoName));

        algoName = "SHA384withRSA";
        SIGALGS_MAP.put(algoName.toUpperCase(), createAlgId(algoName));

        algoName = "SHA512withRSA";
        SIGALGS_MAP.put(algoName.toUpperCase(), createAlgId(algoName));

        algoName = "SHA1withECDSA";
        SIGALGS_MAP.put(algoName.toUpperCase(), createAlgId(algoName));

        algoName = "SHA256withECDSA";
        SIGALGS_MAP.put(algoName.toUpperCase(), createAlgId(algoName));

        algoName = "SHA384withECDSA";
        SIGALGS_MAP.put(algoName.toUpperCase(), createAlgId(algoName));

        algoName = "SHA512withECDSA";
        SIGALGS_MAP.put(algoName.toUpperCase(), createAlgId(algoName));

        algoName = "SHA1withRSAandMGF1";
        SIGALGS_MAP.put(algoName.toUpperCase(), createAlgId(algoName));

        algoName = "SHA256withRSAandMGF1";
        SIGALGS_MAP.put(algoName.toUpperCase(), createAlgId(algoName));

        algoName = "SHA384withRSAandMGF1";
        SIGALGS_MAP.put(algoName.toUpperCase(), createAlgId(algoName));

        algoName = "SHA512withRSAandMGF1";
        SIGALGS_MAP.put(algoName.toUpperCase(), createAlgId(algoName));

    }

    private boolean signRequest;

    private boolean useNonce = true;

    private int nonceLen = 8;

    private boolean useHttpGetForRequest;

    private ASN1ObjectIdentifier hashAlgorithmId = NISTObjectIdentifiers.id_sha256;

    private List<AlgorithmIdentifier> preferredSignatureAlgorithms;

    public RequestOptions() {
    }

    public boolean isUseNonce() {
        return useNonce;
    }

    public void setUseNonce(final boolean useNonce) {
        this.useNonce = useNonce;
    }

    public int nonceLen() {
        return nonceLen;
    }

    public void setNonceLen(final int nonceLen) {
        this.nonceLen = ParamUtil.requireMin("nonceLen", nonceLen, 1);
    }

    public ASN1ObjectIdentifier hashAlgorithmId() {
        return hashAlgorithmId;
    }

    public void setHashAlgorithmId(final ASN1ObjectIdentifier hashAlgorithmId) {
        this.hashAlgorithmId = hashAlgorithmId;
    }

    public List<AlgorithmIdentifier> preferredSignatureAlgorithms() {
        return preferredSignatureAlgorithms;
    }

    public void setPreferredSignatureAlgorithms(
            final AlgorithmIdentifier[] preferredSignatureAlgorithms) {
        this.preferredSignatureAlgorithms = Arrays.asList(preferredSignatureAlgorithms);
    }

    public void setPreferredSignatureAlgorithms(final String[] preferredSignatureAlgoNames) {
        if (preferredSignatureAlgoNames == null || preferredSignatureAlgoNames.length == 0) {
            this.preferredSignatureAlgorithms = null;
            return;
        }

        for (String algoName : preferredSignatureAlgoNames) {
            AlgorithmIdentifier sigAlgId = SIGALGS_MAP.get(algoName.toUpperCase());
            if (sigAlgId == null) {
                // ignore it
                continue;
            }

            if (this.preferredSignatureAlgorithms == null) {
                this.preferredSignatureAlgorithms = new ArrayList<>(
                        preferredSignatureAlgoNames.length);
            }
            this.preferredSignatureAlgorithms.add(sigAlgId);
        }
    }

    public boolean isUseHttpGetForRequest() {
        return useHttpGetForRequest;
    }

    public void setUseHttpGetForRequest(final boolean useHttpGetForRequest) {
        this.useHttpGetForRequest = useHttpGetForRequest;
    }

    public boolean isSignRequest() {
        return signRequest;
    }

    public void setSignRequest(final boolean signRequest) {
        this.signRequest = signRequest;
    }

    private static AlgorithmIdentifier createAlgId(final String algoName) {
        ASN1ObjectIdentifier algOid = null;
        if ("SHA1withRSA".equalsIgnoreCase(algoName)) {
            algOid = PKCSObjectIdentifiers.sha1WithRSAEncryption;
        } else if ("SHA256withRSA".equalsIgnoreCase(algoName)) {
            algOid = PKCSObjectIdentifiers.sha256WithRSAEncryption;
        } else if ("SHA384withRSA".equalsIgnoreCase(algoName)) {
            algOid = PKCSObjectIdentifiers.sha384WithRSAEncryption;
        } else if ("SHA512withRSA".equalsIgnoreCase(algoName)) {
            algOid = PKCSObjectIdentifiers.sha512WithRSAEncryption;
        } else if ("SHA1withECDSA".equalsIgnoreCase(algoName)) {
            algOid = X9ObjectIdentifiers.ecdsa_with_SHA1;
        } else if ("SHA256withECDSA".equalsIgnoreCase(algoName)) {
            algOid = X9ObjectIdentifiers.ecdsa_with_SHA256;
        } else if ("SHA384withECDSA".equalsIgnoreCase(algoName)) {
            algOid = X9ObjectIdentifiers.ecdsa_with_SHA384;
        } else if ("SHA512withECDSA".equalsIgnoreCase(algoName)) {
            algOid = X9ObjectIdentifiers.ecdsa_with_SHA512;
        } else if ("SHA1withRSAandMGF1".equalsIgnoreCase(algoName)
                || "SHA256withRSAandMGF1".equalsIgnoreCase(algoName)
                || "SHA384withRSAandMGF1".equalsIgnoreCase(algoName)
                || "SHA512withRSAandMGF1".equalsIgnoreCase(algoName)) {
            algOid = PKCSObjectIdentifiers.id_RSASSA_PSS;
        } else {
            throw new RuntimeException("Unsupported algorithm " + algoName); // should not happen
        }

        ASN1Encodable params;
        if (PKCSObjectIdentifiers.id_RSASSA_PSS.equals(algOid)) {
            ASN1ObjectIdentifier digestAlgOid = null;
            if ("SHA1withRSAandMGF1".equalsIgnoreCase(algoName)) {
                digestAlgOid = X509ObjectIdentifiers.id_SHA1;
            } else if ("SHA256withRSAandMGF1".equalsIgnoreCase(algoName)) {
                digestAlgOid = NISTObjectIdentifiers.id_sha256;
            } else if ("SHA384withRSAandMGF1".equalsIgnoreCase(algoName)) {
                digestAlgOid = NISTObjectIdentifiers.id_sha384;
            } else { // if ("SHA512withRSAandMGF1".equalsIgnoreCase(algoName))
                digestAlgOid = NISTObjectIdentifiers.id_sha512;
            }
            params = createPSSRSAParams(digestAlgOid);
        } else {
            params = DERNull.INSTANCE;
        }

        return new AlgorithmIdentifier(algOid, params);
    } // method createAlgId

    // CHECKSTYLE:SKIP
    public static RSASSAPSSparams createPSSRSAParams(final ASN1ObjectIdentifier digestAlgOid) {
        int saltSize;
        if (X509ObjectIdentifiers.id_SHA1.equals(digestAlgOid)) {
            saltSize = 20;
        } else if (NISTObjectIdentifiers.id_sha224.equals(digestAlgOid)) {
            saltSize = 28;
        } else if (NISTObjectIdentifiers.id_sha256.equals(digestAlgOid)) {
            saltSize = 32;
        } else if (NISTObjectIdentifiers.id_sha384.equals(digestAlgOid)) {
            saltSize = 48;
        } else if (NISTObjectIdentifiers.id_sha512.equals(digestAlgOid)) {
            saltSize = 64;
        } else {
            throw new RuntimeException("unknown digest algorithm " + digestAlgOid);
        }

        AlgorithmIdentifier digAlgId = new AlgorithmIdentifier(digestAlgOid, DERNull.INSTANCE);
        return new RSASSAPSSparams(digAlgId,
                new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, digAlgId),
                new ASN1Integer(saltSize), RSASSAPSSparams.DEFAULT_TRAILER_FIELD);
    } // method createPSSRSAParams

}
