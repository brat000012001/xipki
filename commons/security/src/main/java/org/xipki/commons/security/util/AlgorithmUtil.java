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

package org.xipki.commons.security.util;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.bsi.BSIObjectIdentifiers;
import org.bouncycastle.asn1.nist.NISTNamedCurves;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSASSAPSSparams;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.teletrust.TeleTrusTNamedCurves;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.asn1.x9.X962NamedCurves;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.common.util.StringUtil;
import org.xipki.commons.security.HashAlgoType;
import org.xipki.commons.security.SignatureAlgoControl;
import org.xipki.commons.security.SignerConf;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class AlgorithmUtil {

    // CHECKSTYLE:SKIP
    private static final List<String> curveNames;

    private static final Map<String, ASN1ObjectIdentifier> curveNameOidMap;

    static {
        List<String> nameList = new LinkedList<>();
        Map<String, ASN1ObjectIdentifier> nameOidMap = new HashMap<>();

        Enumeration<?> names = ECNamedCurveTable.getNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            ASN1ObjectIdentifier oid = org.bouncycastle.asn1.x9.ECNamedCurveTable.getOID(name);
            if (oid == null) {
                continue;
            }

            nameList.add(name);
            nameOidMap.put(name.toLowerCase(), oid);
        }

        Collections.sort(nameList);
        curveNames = Collections.unmodifiableList(nameList);
        curveNameOidMap = Collections.unmodifiableMap(nameOidMap);
    }

    private AlgorithmUtil() {
    }

    public static ASN1ObjectIdentifier getHashAlg(final String hashAlgName)
            throws NoSuchAlgorithmException {
        ParamUtil.requireNonNull("hashAlgName", hashAlgName);
        HashAlgoType hashAlgo = HashAlgoType.getHashAlgoType(hashAlgName);
        if (hashAlgo == null) {
            throw new NoSuchAlgorithmException("Unsupported hash algorithm " + hashAlgName);
        }
        return hashAlgo.getOid();
    } // method getHashAlg

    public static int getHashOutputSizeInOctets(final ASN1ObjectIdentifier hashAlgo)
            throws NoSuchAlgorithmException {
        ParamUtil.requireNonNull("hashAlgo", hashAlgo);
        HashAlgoType hashAlgoType = HashAlgoType.getHashAlgoType(hashAlgo);
        if (hashAlgoType == null) {
            throw new NoSuchAlgorithmException("Unsupported hash algorithm " + hashAlgo.getId());
        }
        return hashAlgoType.getLength();
    } // method getHashOutputSizeInOctets

    public static String getSignatureAlgoName(final AlgorithmIdentifier sigAlgId)
            throws NoSuchAlgorithmException {
        ParamUtil.requireNonNull("sigAlgId", sigAlgId);
        ASN1ObjectIdentifier algOid = sigAlgId.getAlgorithm();

        if (X9ObjectIdentifiers.ecdsa_with_SHA1.equals(algOid)) {
            return "SHA1withECDSA";
        } else if (X9ObjectIdentifiers.ecdsa_with_SHA224.equals(algOid)) {
            return "SHA224withECDSA";
        } else if (X9ObjectIdentifiers.ecdsa_with_SHA256.equals(algOid)) {
            return "SHA256withECDSA";
        } else if (X9ObjectIdentifiers.ecdsa_with_SHA384.equals(algOid)) {
            return "SHA384withECDSA";
        } else if (X9ObjectIdentifiers.ecdsa_with_SHA512.equals(algOid)) {
            return "SHA512withECDSA";
        } else if (NISTObjectIdentifiers.id_ecdsa_with_sha3_224.equals(algOid)) {
            return "SHA3-224withECDSA";
        } else if (NISTObjectIdentifiers.id_ecdsa_with_sha3_256.equals(algOid)) {
            return "SHA3-256withECDSA";
        } else if (NISTObjectIdentifiers.id_ecdsa_with_sha3_384.equals(algOid)) {
            return "SHA3-384withECDSA";
        } else if (NISTObjectIdentifiers.id_ecdsa_with_sha3_512.equals(algOid)) {
            return "SHA3-512withECDSA";
        } else if (BSIObjectIdentifiers.ecdsa_plain_SHA1.equals(algOid)) {
            return "SHA1withPLAIN-ECDSA";
        } else if (BSIObjectIdentifiers.ecdsa_plain_SHA224.equals(algOid)) {
            return "SHA224withPLAIN-ECDSA";
        } else if (BSIObjectIdentifiers.ecdsa_plain_SHA256.equals(algOid)) {
            return "SHA256withPLAIN-ECDSA";
        } else if (BSIObjectIdentifiers.ecdsa_plain_SHA384.equals(algOid)) {
            return "SHA384withPLAIN-ECDSA";
        } else if (BSIObjectIdentifiers.ecdsa_plain_SHA512.equals(algOid)) {
            return "SHA512withPLAIN-ECDSA";
        } else if (X9ObjectIdentifiers.id_dsa_with_sha1.equals(algOid)) {
            return "SHA1withDSA";
        } else if (X9ObjectIdentifiers.id_dsa_with_sha1.equals(algOid)) {
            return "SHA1withDSA";
        } else if (NISTObjectIdentifiers.dsa_with_sha224.equals(algOid)) {
            return "SHA224withDSA";
        } else if (NISTObjectIdentifiers.dsa_with_sha256.equals(algOid)) {
            return "SHA256withDSA";
        } else if (NISTObjectIdentifiers.dsa_with_sha384.equals(algOid)) {
            return "SHA384withDSA";
        } else if (NISTObjectIdentifiers.dsa_with_sha512.equals(algOid)) {
            return "SHA512withDSA";
        } else if (NISTObjectIdentifiers.id_dsa_with_sha3_224.equals(algOid)) {
            return "SHA3-224withDSA";
        } else if (NISTObjectIdentifiers.id_dsa_with_sha3_256.equals(algOid)) {
            return "SHA3-256withDSA";
        } else if (NISTObjectIdentifiers.id_dsa_with_sha3_384.equals(algOid)) {
            return "SHA3-384withDSA";
        } else if (NISTObjectIdentifiers.id_dsa_with_sha3_512.equals(algOid)) {
            return "SHA3-512withDSA";
        } else if (PKCSObjectIdentifiers.sha1WithRSAEncryption.equals(algOid)) {
            return "SHA1withRSA";
        } else if (PKCSObjectIdentifiers.sha224WithRSAEncryption.equals(algOid)) {
            return "SHA224withRSA";
        } else if (PKCSObjectIdentifiers.sha256WithRSAEncryption.equals(algOid)) {
            return "SHA256withRSA";
        } else if (PKCSObjectIdentifiers.sha384WithRSAEncryption.equals(algOid)) {
            return "SHA384withRSA";
        } else if (PKCSObjectIdentifiers.sha512WithRSAEncryption.equals(algOid)) {
            return "SHA512withRSA";
        } else if (NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_224.equals(algOid)) {
            return "SHA3-224withRSA";
        } else if (NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_256.equals(algOid)) {
            return "SHA3-256withRSA";
        } else if (NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_384.equals(algOid)) {
            return "SHA3-384withRSA";
        } else if (NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_512.equals(algOid)) {
            return "SHA3-512withRSA";
        } else if (PKCSObjectIdentifiers.id_RSASSA_PSS.equals(algOid)) {
            RSASSAPSSparams param = RSASSAPSSparams.getInstance(sigAlgId.getParameters());
            ASN1ObjectIdentifier digestAlgOid = param.getHashAlgorithm().getAlgorithm();
            if (X509ObjectIdentifiers.id_SHA1.equals(digestAlgOid)) {
                return "SHA1withRSAandMGF1";
            } else if (NISTObjectIdentifiers.id_sha256.equals(digestAlgOid)) {
                return "SHA256withRSAandMGF1";
            } else if (NISTObjectIdentifiers.id_sha384.equals(digestAlgOid)) {
                return "SHA384withRSAandMGF1";
            } else if (NISTObjectIdentifiers.id_sha512.equals(digestAlgOid)) {
                return "SHA512withRSAandMGF1";
            } else if (NISTObjectIdentifiers.id_sha3_224.equals(digestAlgOid)) {
                return "SHA3-224withRSAandMGF1";
            } else if (NISTObjectIdentifiers.id_sha3_256.equals(digestAlgOid)) {
                return "SHA3-256withRSAandMGF1";
            } else if (NISTObjectIdentifiers.id_sha3_384.equals(digestAlgOid)) {
                return "SHA3-384withRSAandMGF1";
            } else if (NISTObjectIdentifiers.id_sha3_512.equals(digestAlgOid)) {
                return "SHA3-512withRSAandMGF1";
            } else {
                throw new NoSuchAlgorithmException("unsupported digest algorithm "
                        + digestAlgOid.getId());
            }
        } else {
            throw new NoSuchAlgorithmException("unsupported signature algorithm "
                    + algOid.getId());
        }
    } // method getSignatureAlgoName

    // CHECKSTYLE:SKIP
    public static boolean isDSAPlainSigAlg(final AlgorithmIdentifier algId) {
        return isPlainECDSASigAlg(algId);
    }

    public static String canonicalizeSignatureAlgo(final String algoName)
            throws NoSuchAlgorithmException {
        return getSignatureAlgoName(getSigAlgId(algoName));
    }

    public static AlgorithmIdentifier getSigAlgId(final String sigAlgName)
            throws NoSuchAlgorithmException {
        ParamUtil.requireNonNull("sigAlgName", sigAlgName);
        String algoS = sigAlgName.toUpperCase();
        if (algoS.indexOf('-') != -1 && algoS.indexOf("SHA3-") == -1) {
            algoS = algoS.replaceAll("-", "");
        }

        AlgorithmIdentifier signatureAlgId;
        if ("SHA1withRSAandMGF1".equalsIgnoreCase(algoS)
                || "SHA224withRSAandMGF1".equalsIgnoreCase(algoS)
                || "SHA256withRSAandMGF1".equalsIgnoreCase(algoS)
                || "SHA384withRSAandMGF1".equalsIgnoreCase(algoS)
                || "SHA512withRSAandMGF1".equalsIgnoreCase(algoS)
                || "SHA3-224withRSAandMGF1".equalsIgnoreCase(algoS)
                || "SHA3-256withRSAandMGF1".equalsIgnoreCase(algoS)
                || "SHA3-384withRSAandMGF1".equalsIgnoreCase(algoS)
                || "SHA3-512withRSAandMGF1".equalsIgnoreCase(algoS)) {
            HashAlgoType hashAlgo;
            if ("SHA1withRSAandMGF1".equalsIgnoreCase(algoS)) {
                hashAlgo = HashAlgoType.SHA1;
            } else if ("SHA224withRSAandMGF1".equalsIgnoreCase(algoS)) {
                hashAlgo = HashAlgoType.SHA224;
            } else if ("SHA256withRSAandMGF1".equalsIgnoreCase(algoS)) {
                hashAlgo = HashAlgoType.SHA256;
            } else if ("SHA384withRSAandMGF1".equalsIgnoreCase(algoS)) {
                hashAlgo = HashAlgoType.SHA384;
            } else if ("SHA512withRSAandMGF1".equalsIgnoreCase(algoS)) {
                hashAlgo = HashAlgoType.SHA512;
            } else if ("SHA3-224withRSAandMGF1".equalsIgnoreCase(algoS)) {
                hashAlgo = HashAlgoType.SHA3_224;
            } else if ("SHA3-256withRSAandMGF1".equalsIgnoreCase(algoS)) {
                hashAlgo = HashAlgoType.SHA3_256;
            } else if ("SHA3-384withRSAandMGF1".equalsIgnoreCase(algoS)) {
                hashAlgo = HashAlgoType.SHA3_384;
            } else if ("SHA3-512withRSAandMGF1".equalsIgnoreCase(algoS)) {
                hashAlgo = HashAlgoType.SHA3_512;
            } else {
                throw new NoSuchAlgorithmException("should not reach here, unknown algorithm "
                        + algoS);
            }

            signatureAlgId = buildRSAPSSAlgId(hashAlgo);
        } else {
            boolean withNullParam = false;
            ASN1ObjectIdentifier algOid;
            if ("SHA1withRSA".equalsIgnoreCase(algoS) || "RSAwithSHA1".equalsIgnoreCase(algoS)
                    || PKCSObjectIdentifiers.sha1WithRSAEncryption.getId().equals(algoS)) {
                algOid = PKCSObjectIdentifiers.sha1WithRSAEncryption;
                withNullParam = true;
            } else if ("SHA224withRSA".equalsIgnoreCase(algoS)
                    || "RSAwithSHA224".equalsIgnoreCase(algoS)
                    || PKCSObjectIdentifiers.sha224WithRSAEncryption.getId().equals(algoS)) {
                algOid = PKCSObjectIdentifiers.sha224WithRSAEncryption;
                withNullParam = true;
            } else if ("SHA256withRSA".equalsIgnoreCase(algoS)
                    || "RSAwithSHA256".equalsIgnoreCase(algoS)
                    || PKCSObjectIdentifiers.sha256WithRSAEncryption.getId().equals(algoS)) {
                algOid = PKCSObjectIdentifiers.sha256WithRSAEncryption;
                withNullParam = true;
            } else if ("SHA384withRSA".equalsIgnoreCase(algoS)
                    || "RSAwithSHA384".equalsIgnoreCase(algoS)
                    || PKCSObjectIdentifiers.sha384WithRSAEncryption.getId().equals(algoS)) {
                algOid = PKCSObjectIdentifiers.sha384WithRSAEncryption;
                withNullParam = true;
            } else if ("SHA512withRSA".equalsIgnoreCase(algoS)
                    || "RSAwithSHA512".equalsIgnoreCase(algoS)
                    || PKCSObjectIdentifiers.sha512WithRSAEncryption.getId().equals(algoS)) {
                algOid = PKCSObjectIdentifiers.sha512WithRSAEncryption;
                withNullParam = true;
            } else if ("SHA512withRSA".equalsIgnoreCase(algoS)
                    || "RSAwithSHA512".equalsIgnoreCase(algoS)
                    || PKCSObjectIdentifiers.sha512WithRSAEncryption.getId().equals(algoS)) {
                algOid = PKCSObjectIdentifiers.sha512WithRSAEncryption;
                withNullParam = true;
            } else if ("SHA3-224withRSA".equalsIgnoreCase(algoS)
                    || "RSAwithSHA3-224".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_224.getId()
                        .equals(algoS)) {
                algOid = NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_224;
                withNullParam = true;
            } else if ("SHA3-256withRSA".equalsIgnoreCase(algoS)
                    || "RSAwithSHA3-256".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_256.getId()
                        .equals(algoS)) {
                algOid = NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_256;
                withNullParam = true;
            } else if ("SHA3-384withRSA".equalsIgnoreCase(algoS)
                    || "RSAwithSHA3-384".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_384.getId()
                        .equals(algoS)) {
                algOid = NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_384;
                withNullParam = true;
            } else if ("SHA3-512withRSA".equalsIgnoreCase(algoS)
                    || "RSAwithSHA3-512".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_512.getId()
                        .equals(algoS)) {
                algOid = NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_512;
                withNullParam = true;
            } else if ("SHA1withECDSA".equalsIgnoreCase(algoS)
                    || "ECDSAwithSHA1".equalsIgnoreCase(algoS)
                    || X9ObjectIdentifiers.ecdsa_with_SHA1.getId().equals(algoS)) {
                algOid = X9ObjectIdentifiers.ecdsa_with_SHA1;
            } else if ("SHA224withECDSA".equalsIgnoreCase(algoS)
                    || "ECDSAwithSHA224".equalsIgnoreCase(algoS)
                    || X9ObjectIdentifiers.ecdsa_with_SHA224.getId().equals(algoS)) {
                algOid = X9ObjectIdentifiers.ecdsa_with_SHA224;
            } else if ("SHA256withECDSA".equalsIgnoreCase(algoS)
                    || "ECDSAwithSHA256".equalsIgnoreCase(algoS)
                    || X9ObjectIdentifiers.ecdsa_with_SHA256.getId().equals(algoS)) {
                algOid = X9ObjectIdentifiers.ecdsa_with_SHA256;
            } else if ("SHA384withECDSA".equalsIgnoreCase(algoS)
                    || "ECDSAwithSHA384".equalsIgnoreCase(algoS)
                    || X9ObjectIdentifiers.ecdsa_with_SHA384.getId().equals(algoS)) {
                algOid = X9ObjectIdentifiers.ecdsa_with_SHA384;
            } else if ("SHA512withECDSA".equalsIgnoreCase(algoS)
                    || "ECDSAwithSHA512".equalsIgnoreCase(algoS)
                    || X9ObjectIdentifiers.ecdsa_with_SHA512.getId().equals(algoS)) {
                algOid = X9ObjectIdentifiers.ecdsa_with_SHA512;
            } else if ("SHA3-224withECDSA".equalsIgnoreCase(algoS)
                    || "ECDSAwithSHA3-224".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.id_ecdsa_with_sha3_224.getId().equals(algoS)) {
                algOid = NISTObjectIdentifiers.id_ecdsa_with_sha3_224;
            } else if ("SHA3-256withECDSA".equalsIgnoreCase(algoS)
                    || "ECDSAwithSHA3-256".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.id_ecdsa_with_sha3_256.getId().equals(algoS)) {
                algOid = NISTObjectIdentifiers.id_ecdsa_with_sha3_256;
            } else if ("SHA3-384withECDSA".equalsIgnoreCase(algoS)
                    || "ECDSAwithSHA3-384".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.id_ecdsa_with_sha3_384.getId().equals(algoS)) {
                algOid = NISTObjectIdentifiers.id_ecdsa_with_sha3_384;
            } else if ("SHA3-512withECDSA".equalsIgnoreCase(algoS)
                    || "ECDSAwithSHA3-512".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.id_ecdsa_with_sha3_512.getId().equals(algoS)) {
                algOid = NISTObjectIdentifiers.id_ecdsa_with_sha3_512;
            } else if ("SHA1withPlainECDSA".equalsIgnoreCase(algoS)
                    || "PlainECDSAwithSHA1".equalsIgnoreCase(algoS)
                    || BSIObjectIdentifiers.ecdsa_plain_SHA1.getId().equals(algoS)) {
                algOid = BSIObjectIdentifiers.ecdsa_plain_SHA1;
            } else if ("SHA224withPlainECDSA".equalsIgnoreCase(algoS)
                    || "PlainECDSAwithSHA224".equalsIgnoreCase(algoS)
                    || BSIObjectIdentifiers.ecdsa_plain_SHA224.getId().equals(algoS)) {
                algOid = BSIObjectIdentifiers.ecdsa_plain_SHA224;
            } else if ("SHA256withPlainECDSA".equalsIgnoreCase(algoS)
                    || "PlainECDSAwithSHA256".equalsIgnoreCase(algoS)
                    || BSIObjectIdentifiers.ecdsa_plain_SHA256.getId().equals(algoS)) {
                algOid = BSIObjectIdentifiers.ecdsa_plain_SHA256;
            } else if ("SHA384withPlainECDSA".equalsIgnoreCase(algoS)
                    || "PlainECDSAwithSHA384".equalsIgnoreCase(algoS)
                    || BSIObjectIdentifiers.ecdsa_plain_SHA384.getId().equals(algoS)) {
                algOid = BSIObjectIdentifiers.ecdsa_plain_SHA384;
            } else if ("SHA512withPlainECDSA".equalsIgnoreCase(algoS)
                    || "PlainECDSAwithSHA512".equalsIgnoreCase(algoS)
                    || BSIObjectIdentifiers.ecdsa_plain_SHA512.getId().equals(algoS)) {
                algOid = BSIObjectIdentifiers.ecdsa_plain_SHA512;
            } else if ("SHA1withDSA".equalsIgnoreCase(algoS)
                    || "DSAwithSHA1".equalsIgnoreCase(algoS)
                    || X9ObjectIdentifiers.id_dsa_with_sha1.getId().equals(algoS)) {
                algOid = X9ObjectIdentifiers.id_dsa_with_sha1;
            } else if ("SHA224withDSA".equalsIgnoreCase(algoS)
                    || "DSAwithSHA224".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.dsa_with_sha224.getId().equals(algoS)) {
                algOid = NISTObjectIdentifiers.dsa_with_sha224;
            } else if ("SHA256withDSA".equalsIgnoreCase(algoS)
                    || "DSAwithSHA256".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.dsa_with_sha256.getId().equals(algoS)) {
                algOid = NISTObjectIdentifiers.dsa_with_sha256;
            } else if ("SHA384withDSA".equalsIgnoreCase(algoS)
                    || "DSAwithSHA384".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.dsa_with_sha384.getId().equals(algoS)) {
                algOid = NISTObjectIdentifiers.dsa_with_sha384;
            } else if ("SHA512withDSA".equalsIgnoreCase(algoS)
                    || "DSAwithSHA512".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.dsa_with_sha512.getId().equals(algoS)) {
                algOid = NISTObjectIdentifiers.dsa_with_sha512;
            } else if ("SHA3-224withDSA".equalsIgnoreCase(algoS)
                    || "DSAwithSHA3-224".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.id_dsa_with_sha3_224.getId().equals(algoS)) {
                algOid = NISTObjectIdentifiers.id_dsa_with_sha3_224;
            } else if ("SHA3-256withDSA".equalsIgnoreCase(algoS)
                    || "DSAwithSHA3-256".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.id_dsa_with_sha3_256.getId().equals(algoS)) {
                algOid = NISTObjectIdentifiers.id_dsa_with_sha3_256;
            } else if ("SHA3-384withDSA".equalsIgnoreCase(algoS)
                    || "DSAwithSHA3-384".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.id_dsa_with_sha3_384.getId().equals(algoS)) {
                algOid = NISTObjectIdentifiers.id_dsa_with_sha3_384;
            } else if ("SHA3-512withDSA".equalsIgnoreCase(algoS)
                    || "DSAwithSHA3-512".equalsIgnoreCase(algoS)
                    || NISTObjectIdentifiers.id_dsa_with_sha3_512.getId().equals(algoS)) {
                algOid = NISTObjectIdentifiers.id_dsa_with_sha3_512;
            } else {
                throw new NoSuchAlgorithmException("unsupported signature algorithm " + algoS);
            }

            signatureAlgId = withNullParam ? new AlgorithmIdentifier(algOid, DERNull.INSTANCE)
                    : new AlgorithmIdentifier(algOid);
        }

        return signatureAlgId;
    } // method getSigAlgId

    public static AlgorithmIdentifier getSigAlgId(final PublicKey pubKey,
            final SignerConf signerConf) throws NoSuchAlgorithmException {
        ParamUtil.requireNonNull("signerConf", signerConf);
        if (signerConf.getHashAlgo() == null) {
            return getSigAlgId(signerConf.getConfValue("algo"));
        } else {
            SignatureAlgoControl algoControl = signerConf.getSignatureAlgoControl();
            HashAlgoType hashAlgo = signerConf.getHashAlgo();
            boolean rsaMgf1 = (algoControl == null) ? false : algoControl.isRsaMgf1();
            boolean dsaPlain = (algoControl == null) ? false : algoControl.isDsaPlain();

            if (pubKey instanceof RSAPublicKey) {
                return getRSASigAlgId(hashAlgo, rsaMgf1);
            } else if (pubKey instanceof ECPublicKey) {
                return getECDSASigAlgId(hashAlgo, dsaPlain);
            } else if (pubKey instanceof DSAPublicKey) {
                return getDSASigAlgId(hashAlgo);
            } else {
                throw new NoSuchAlgorithmException("Unknown public key '"
                        + pubKey.getClass().getName());
            }
        }
    }

    public static AlgorithmIdentifier getSigAlgId(final PublicKey pubKey,
            final HashAlgoType hashAlgo, final SignatureAlgoControl algoControl)
            throws NoSuchAlgorithmException {
        ParamUtil.requireNonNull("hashAlgo", hashAlgo);
        boolean rsaMgf1 = (algoControl == null) ? false : algoControl.isRsaMgf1();
        boolean dsaPlain = (algoControl == null) ? false : algoControl.isDsaPlain();

        if (pubKey instanceof RSAPublicKey) {
            return getRSASigAlgId(hashAlgo, rsaMgf1);
        } else if (pubKey instanceof ECPublicKey) {
            return getECDSASigAlgId(hashAlgo, dsaPlain);
        } else if (pubKey instanceof DSAPublicKey) {
            return getDSASigAlgId(hashAlgo);
        } else {
            throw new NoSuchAlgorithmException("Unknown public key '"
                    + pubKey.getClass().getName());
        }
    }

    // CHECKSTYLE:SKIP
    public static boolean isRSASigAlgId(final AlgorithmIdentifier algId) {
        ParamUtil.requireNonNull("algId", algId);
        ASN1ObjectIdentifier oid = algId.getAlgorithm();
        if (PKCSObjectIdentifiers.sha1WithRSAEncryption.equals(oid)
                || PKCSObjectIdentifiers.sha224WithRSAEncryption.equals(oid)
                || PKCSObjectIdentifiers.sha256WithRSAEncryption.equals(oid)
                || PKCSObjectIdentifiers.sha384WithRSAEncryption.equals(oid)
                || PKCSObjectIdentifiers.sha512WithRSAEncryption.equals(oid)
                || NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_224.equals(oid)
                || NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_256.equals(oid)
                || NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_384.equals(oid)
                || NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_512.equals(oid)
                || PKCSObjectIdentifiers.id_RSASSA_PSS.equals(oid)) {
            return true;
        }

        return false;
    }

    // CHECKSTYLE:SKIP
    public static boolean isECSigAlg(final AlgorithmIdentifier algId) {
        return isECDSASigAlg(algId) || isPlainECDSASigAlg(algId);
    }

    // CHECKSTYLE:SKIP
    public static boolean isECDSASigAlg(final AlgorithmIdentifier algId) {
        ParamUtil.requireNonNull("algId", algId);

        ASN1ObjectIdentifier oid = algId.getAlgorithm();
        if (X9ObjectIdentifiers.ecdsa_with_SHA1.equals(oid)
                || X9ObjectIdentifiers.ecdsa_with_SHA224.equals(oid)
                || X9ObjectIdentifiers.ecdsa_with_SHA256.equals(oid)
                || X9ObjectIdentifiers.ecdsa_with_SHA384.equals(oid)
                || X9ObjectIdentifiers.ecdsa_with_SHA512.equals(oid)
                || NISTObjectIdentifiers.id_ecdsa_with_sha3_224.equals(oid)
                || NISTObjectIdentifiers.id_ecdsa_with_sha3_256.equals(oid)
                || NISTObjectIdentifiers.id_ecdsa_with_sha3_384.equals(oid)
                || NISTObjectIdentifiers.id_ecdsa_with_sha3_512.equals(oid)) {
            return true;
        }

        return false;
    }

    // CHECKSTYLE:SKIP
    public static boolean isPlainECDSASigAlg(final AlgorithmIdentifier algId) {
        ParamUtil.requireNonNull("algId", algId);

        ASN1ObjectIdentifier oid = algId.getAlgorithm();
        if (BSIObjectIdentifiers.ecdsa_plain_SHA1.equals(oid)
                || BSIObjectIdentifiers.ecdsa_plain_SHA224.equals(oid)
                || BSIObjectIdentifiers.ecdsa_plain_SHA256.equals(oid)
                || BSIObjectIdentifiers.ecdsa_plain_SHA384.equals(oid)
                || BSIObjectIdentifiers.ecdsa_plain_SHA512.equals(oid)) {
            return true;
        }

        return false;
    }

    // CHECKSTYLE:SKIP
    public static boolean isDSASigAlg(final AlgorithmIdentifier algId) {
        ParamUtil.requireNonNull("algId", algId);

        ASN1ObjectIdentifier oid = algId.getAlgorithm();
        if (X9ObjectIdentifiers.id_dsa_with_sha1.equals(oid)
                || NISTObjectIdentifiers.dsa_with_sha224.equals(oid)
                || NISTObjectIdentifiers.dsa_with_sha256.equals(oid)
                || NISTObjectIdentifiers.dsa_with_sha384.equals(oid)
                || NISTObjectIdentifiers.dsa_with_sha512.equals(oid)
                || NISTObjectIdentifiers.id_dsa_with_sha3_224.equals(oid)
                || NISTObjectIdentifiers.id_dsa_with_sha3_256.equals(oid)
                || NISTObjectIdentifiers.id_dsa_with_sha3_384.equals(oid)
                || NISTObjectIdentifiers.id_dsa_with_sha3_512.equals(oid)) {
            return true;
        }

        return false;
    }

    // CHECKSTYLE:SKIP
    public static AlgorithmIdentifier getRSASigAlgId(final HashAlgoType hashAlgo,
            final boolean mgf1)
            throws NoSuchAlgorithmException {
        ParamUtil.requireNonNull("hashAlgo", hashAlgo);
        if (mgf1) {
            return buildRSAPSSAlgId(hashAlgo);
        }

        ASN1ObjectIdentifier sigAlgOid;
        switch (hashAlgo) {
        case SHA1:
            sigAlgOid = PKCSObjectIdentifiers.sha1WithRSAEncryption;
            break;
        case SHA224:
            sigAlgOid = PKCSObjectIdentifiers.sha224WithRSAEncryption;
            break;
        case SHA256:
            sigAlgOid = PKCSObjectIdentifiers.sha256WithRSAEncryption;
            break;
        case SHA384:
            sigAlgOid = PKCSObjectIdentifiers.sha384WithRSAEncryption;
            break;
        case SHA512:
            sigAlgOid = PKCSObjectIdentifiers.sha512WithRSAEncryption;
            break;
        case SHA3_224:
            sigAlgOid = NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_224;
            break;
        case SHA3_256:
            sigAlgOid = NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_256;
            break;
        case SHA3_384:
            sigAlgOid = NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_384;
            break;
        case SHA3_512:
            sigAlgOid = NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_512;
            break;
        default:
            throw new RuntimeException("unknown HashAlgoType: " + hashAlgo);
        }

        return new AlgorithmIdentifier(sigAlgOid, DERNull.INSTANCE);
    } // method getRSASigAlgId

    // CHECKSTYLE:SKIP
    public static AlgorithmIdentifier getDSASigAlgId(final HashAlgoType hashAlgo)
            throws NoSuchAlgorithmException {
        ParamUtil.requireNonNull("hashAlgo", hashAlgo);

        ASN1ObjectIdentifier sigAlgOid;
        switch (hashAlgo) {
        case SHA1:
            sigAlgOid = X9ObjectIdentifiers.id_dsa_with_sha1;
            break;
        case SHA224:
            sigAlgOid = NISTObjectIdentifiers.dsa_with_sha224;
            break;
        case SHA256:
            sigAlgOid = NISTObjectIdentifiers.dsa_with_sha256;
            break;
        case SHA384:
            sigAlgOid = NISTObjectIdentifiers.dsa_with_sha384;
            break;
        case SHA512:
            sigAlgOid = NISTObjectIdentifiers.dsa_with_sha512;
            break;
        case SHA3_224:
            sigAlgOid = NISTObjectIdentifiers.id_dsa_with_sha3_224;
            break;
        case SHA3_256:
            sigAlgOid = NISTObjectIdentifiers.id_dsa_with_sha3_256;
            break;
        case SHA3_384:
            sigAlgOid = NISTObjectIdentifiers.id_dsa_with_sha3_384;
            break;
        case SHA3_512:
            sigAlgOid = NISTObjectIdentifiers.id_dsa_with_sha3_512;
            break;
        default:
            throw new RuntimeException("unknown HashAlgoType: " + hashAlgo);
        }

        return new AlgorithmIdentifier(sigAlgOid);
    } // method getDSASigAlgId

    // CHECKSTYLE:SKIP
    public static AlgorithmIdentifier getECDSASigAlgId(final HashAlgoType hashAlgo,
            final boolean plainSignature)
            throws NoSuchAlgorithmException {
        ParamUtil.requireNonNull("hashAlgo", hashAlgo);

        ASN1ObjectIdentifier sigAlgOid;

        if (plainSignature) {
            switch (hashAlgo) {
            case SHA1:
                sigAlgOid = BSIObjectIdentifiers.ecdsa_plain_SHA1;
                break;
            case SHA224:
                sigAlgOid = BSIObjectIdentifiers.ecdsa_plain_SHA224;
                break;
            case SHA256:
                sigAlgOid = BSIObjectIdentifiers.ecdsa_plain_SHA256;
                break;
            case SHA384:
                sigAlgOid = BSIObjectIdentifiers.ecdsa_plain_SHA384;
                break;
            case SHA512:
                sigAlgOid = BSIObjectIdentifiers.ecdsa_plain_SHA512;
                break;
            default:
                throw new RuntimeException("unknown HashAlgoType: " + hashAlgo);
            }
        } else {
            switch (hashAlgo) {
            case SHA1:
                sigAlgOid = X9ObjectIdentifiers.ecdsa_with_SHA1;
                break;
            case SHA224:
                sigAlgOid = X9ObjectIdentifiers.ecdsa_with_SHA224;
                break;
            case SHA256:
                sigAlgOid = X9ObjectIdentifiers.ecdsa_with_SHA256;
                break;
            case SHA384:
                sigAlgOid = X9ObjectIdentifiers.ecdsa_with_SHA384;
                break;
            case SHA512:
                sigAlgOid = X9ObjectIdentifiers.ecdsa_with_SHA512;
                break;
            case SHA3_224:
                sigAlgOid = NISTObjectIdentifiers.id_ecdsa_with_sha3_224;
                break;
            case SHA3_256:
                sigAlgOid = NISTObjectIdentifiers.id_ecdsa_with_sha3_256;
                break;
            case SHA3_384:
                sigAlgOid = NISTObjectIdentifiers.id_ecdsa_with_sha3_384;
                break;
            case SHA3_512:
                sigAlgOid = NISTObjectIdentifiers.id_ecdsa_with_sha3_512;
                break;
            default:
                throw new RuntimeException("unknown HashAlgoType: " + hashAlgo);
            }
        }

        return new AlgorithmIdentifier(sigAlgOid);
    } // method getECDSASigAlgId

    public static AlgorithmIdentifier extractDigesetAlgId(final AlgorithmIdentifier sigAlgId)
            throws NoSuchAlgorithmException {
        ASN1ObjectIdentifier algOid = sigAlgId.getAlgorithm();

        ASN1ObjectIdentifier digestAlgOid;
        if (PKCSObjectIdentifiers.id_RSASSA_PSS.equals(algOid)) {
            ASN1Encodable asn1Encodable = sigAlgId.getParameters();
            RSASSAPSSparams param = RSASSAPSSparams.getInstance(asn1Encodable);
            digestAlgOid = param.getHashAlgorithm().getAlgorithm();
        } else {
            HashAlgoType digestAlg;

            if (X9ObjectIdentifiers.ecdsa_with_SHA1.equals(algOid)) {
                digestAlg = HashAlgoType.SHA1;
            } else if (X9ObjectIdentifiers.ecdsa_with_SHA224.equals(algOid)) {
                digestAlg = HashAlgoType.SHA224;
            } else if (X9ObjectIdentifiers.ecdsa_with_SHA256.equals(algOid)) {
                digestAlg = HashAlgoType.SHA256;
            } else if (X9ObjectIdentifiers.ecdsa_with_SHA384.equals(algOid)) {
                digestAlg = HashAlgoType.SHA384;
            } else if (X9ObjectIdentifiers.ecdsa_with_SHA512.equals(algOid)) {
                digestAlg = HashAlgoType.SHA512;
            } else if (NISTObjectIdentifiers.id_ecdsa_with_sha3_224.equals(algOid)) {
                digestAlg = HashAlgoType.SHA3_224;
            } else if (NISTObjectIdentifiers.id_ecdsa_with_sha3_256.equals(algOid)) {
                digestAlg = HashAlgoType.SHA3_256;
            } else if (NISTObjectIdentifiers.id_ecdsa_with_sha3_384.equals(algOid)) {
                digestAlg = HashAlgoType.SHA3_384;
            } else if (NISTObjectIdentifiers.id_ecdsa_with_sha3_512.equals(algOid)) {
                digestAlg = HashAlgoType.SHA3_512;
            } else if (BSIObjectIdentifiers.ecdsa_plain_SHA1.equals(algOid)) {
                digestAlg = HashAlgoType.SHA1;
            } else if (BSIObjectIdentifiers.ecdsa_plain_SHA224.equals(algOid)) {
                digestAlg = HashAlgoType.SHA224;
            } else if (BSIObjectIdentifiers.ecdsa_plain_SHA256.equals(algOid)) {
                digestAlg = HashAlgoType.SHA256;
            } else if (BSIObjectIdentifiers.ecdsa_plain_SHA384.equals(algOid)) {
                digestAlg = HashAlgoType.SHA384;
            } else if (BSIObjectIdentifiers.ecdsa_plain_SHA512.equals(algOid)) {
                digestAlg = HashAlgoType.SHA512;
            } else if (X9ObjectIdentifiers.id_dsa_with_sha1.equals(algOid)) {
                digestAlg = HashAlgoType.SHA1;
            } else if (NISTObjectIdentifiers.dsa_with_sha224.equals(algOid)) {
                digestAlg = HashAlgoType.SHA224;
            } else if (NISTObjectIdentifiers.dsa_with_sha256.equals(algOid)) {
                digestAlg = HashAlgoType.SHA256;
            } else if (NISTObjectIdentifiers.dsa_with_sha384.equals(algOid)) {
                digestAlg = HashAlgoType.SHA384;
            } else if (NISTObjectIdentifiers.dsa_with_sha512.equals(algOid)) {
                digestAlg = HashAlgoType.SHA512;
            } else if (NISTObjectIdentifiers.id_dsa_with_sha3_224.equals(algOid)) {
                digestAlg = HashAlgoType.SHA3_224;
            } else if (NISTObjectIdentifiers.id_dsa_with_sha3_256.equals(algOid)) {
                digestAlg = HashAlgoType.SHA3_256;
            } else if (NISTObjectIdentifiers.id_dsa_with_sha3_384.equals(algOid)) {
                digestAlg = HashAlgoType.SHA3_384;
            } else if (NISTObjectIdentifiers.id_dsa_with_sha3_512.equals(algOid)) {
                digestAlg = HashAlgoType.SHA3_512;
            } else if (PKCSObjectIdentifiers.sha1WithRSAEncryption.equals(algOid)) {
                digestAlg = HashAlgoType.SHA1;
            } else if (PKCSObjectIdentifiers.sha224WithRSAEncryption.equals(algOid)) {
                digestAlg = HashAlgoType.SHA224;
            } else if (PKCSObjectIdentifiers.sha256WithRSAEncryption.equals(algOid)) {
                digestAlg = HashAlgoType.SHA256;
            } else if (PKCSObjectIdentifiers.sha384WithRSAEncryption.equals(algOid)) {
                digestAlg = HashAlgoType.SHA384;
            } else if (PKCSObjectIdentifiers.sha512WithRSAEncryption.equals(algOid)) {
                digestAlg = HashAlgoType.SHA512;
            } else if (NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_224.equals(algOid)) {
                digestAlg = HashAlgoType.SHA3_224;
            } else if (NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_256.equals(algOid)) {
                digestAlg = HashAlgoType.SHA3_256;
            } else if (NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_384.equals(algOid)) {
                digestAlg = HashAlgoType.SHA3_384;
            } else if (NISTObjectIdentifiers.id_rsassa_pkcs1_v1_5_with_sha3_512.equals(algOid)) {
                digestAlg = HashAlgoType.SHA3_512;
            } else {
                throw new NoSuchAlgorithmException("unknown signature algorithm" + algOid.getId());
            }

            digestAlgOid = digestAlg.getOid();
        }

        return new AlgorithmIdentifier(digestAlgOid, DERNull.INSTANCE);
    } // method extractDigesetAlgorithmIdentifier

    public static boolean equalsAlgoName(final String algoNameA, final String algoNameB) {
        ParamUtil.requireNonBlank("algoNameA", algoNameA);
        ParamUtil.requireNonBlank("algoNameB", algoNameB);
        if (algoNameA.equalsIgnoreCase(algoNameB)) {
            return true;
        }

        String tmpA = algoNameA;
        if (tmpA.indexOf('-') != -1) {
            tmpA = tmpA.replace("-", "");
        }

        String tmpB = algoNameB;
        if (tmpB.indexOf('-') != -1) {
            tmpB = tmpB.replace("-", "");
        }

        if (tmpA.equalsIgnoreCase(tmpB)) {
            return true;
        }

        return splitAlgoNameTokens(tmpA).equals(splitAlgoNameTokens(tmpB));
    }

    private static Set<String> splitAlgoNameTokens(final String algoName) {
        ParamUtil.requireNonNull("algoName", algoName);
        String tmpAlgoName = algoName.toUpperCase();
        int idx = tmpAlgoName.indexOf("AND");
        Set<String> set = new HashSet<>();

        if (idx == -1) {
            set.add(tmpAlgoName);
            return set;
        }

        final int len = tmpAlgoName.length();

        int beginIndex = 0;
        int endIndex = idx;
        while (true) {
            String token = tmpAlgoName.substring(beginIndex, endIndex);
            if (StringUtil.isNotBlank(token)) {
                set.add(token);
            }

            if (endIndex >= len) {
                return set;
            }
            beginIndex = endIndex + 3; // 3 = "AND".length()
            endIndex = tmpAlgoName.indexOf("AND", beginIndex);
            if (endIndex == -1) {
                endIndex = len;
            }
        }
    }

    // CHECKSTYLE:SKIP
    public static AlgorithmIdentifier buildRSAPSSAlgId(final HashAlgoType digestAlg)
            throws NoSuchAlgorithmException {
        RSASSAPSSparams params = createPSSRSAParams(digestAlg);
        return new AlgorithmIdentifier(PKCSObjectIdentifiers.id_RSASSA_PSS, params);
    }

    // CHECKSTYLE:SKIP
    public static AlgorithmIdentifier buildDSASigAlgId(final HashAlgoType digestAlg)
            throws NoSuchAlgorithmException {
        ParamUtil.requireNonNull("digestAlg", digestAlg);

        ASN1ObjectIdentifier sid;

        switch (digestAlg) {
        case SHA1:
            sid = X9ObjectIdentifiers.id_dsa_with_sha1;
            break;
        case SHA224:
            sid = NISTObjectIdentifiers.dsa_with_sha224;
            break;
        case SHA256:
            sid = NISTObjectIdentifiers.dsa_with_sha256;
            break;
        case SHA384:
            sid = NISTObjectIdentifiers.dsa_with_sha384;
            break;
        case SHA512:
            sid = NISTObjectIdentifiers.dsa_with_sha512;
            break;
        case SHA3_224:
            sid = NISTObjectIdentifiers.id_dsa_with_sha3_224;
            break;
        case SHA3_256:
            sid = NISTObjectIdentifiers.id_dsa_with_sha3_256;
            break;
        case SHA3_384:
            sid = NISTObjectIdentifiers.id_dsa_with_sha3_384;
            break;
        case SHA3_512:
            sid = NISTObjectIdentifiers.id_dsa_with_sha3_512;
            break;
        default:
            throw new RuntimeException("unknown HashAlgoType: " + digestAlg);
        }
        return new AlgorithmIdentifier(sid);
    } // method buildRSAPSSAlgorithmIdentifier

    // CHECKSTYLE:SKIP
    public static RSASSAPSSparams createPSSRSAParams(final HashAlgoType digestAlg)
            throws NoSuchAlgorithmException {
        ParamUtil.requireNonNull("digestAlg", digestAlg);
        int saltSize = digestAlg.getLength();
        AlgorithmIdentifier digAlgId = new AlgorithmIdentifier(digestAlg.getOid(),
                DERNull.INSTANCE);
        return new RSASSAPSSparams(digAlgId,
            new AlgorithmIdentifier(PKCSObjectIdentifiers.id_mgf1, digAlgId),
            new ASN1Integer(saltSize), RSASSAPSSparams.DEFAULT_TRAILER_FIELD);
    } // method createPSSRSAParams

    public static ASN1ObjectIdentifier getCurveOidForName(final String curveName) {
        ParamUtil.requireNonBlank("curveName", curveName);
        return curveNameOidMap.get(curveName.toLowerCase());
    }

    // CHECKSTYLE:SKIP
    public static List<String> getECCurveNames() {
        return curveNames;
    }

    public static String getCurveName(final ASN1ObjectIdentifier curveOid) {
        ParamUtil.requireNonNull("curveOid", curveOid);

        String curveName = X962NamedCurves.getName(curveOid);
        if (curveName == null) {
            curveName = SECNamedCurves.getName(curveOid);
        }
        if (curveName == null) {
            curveName = TeleTrusTNamedCurves.getName(curveOid);
        }
        if (curveName == null) {
            curveName = NISTNamedCurves.getName(curveOid);
        }

        return curveName;
    }

    public static ASN1ObjectIdentifier getCurveOidForCurveNameOrOid(final String curveNameOrOid) {
        ParamUtil.requireNonBlank("curveNameOrOid", curveNameOrOid);
        ASN1ObjectIdentifier oid;
        try {
            oid = new ASN1ObjectIdentifier(curveNameOrOid);
        } catch (Exception ex) {
            oid = getCurveOidForName(curveNameOrOid);
        }
        return oid;
    }

}
