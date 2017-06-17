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

package org.xipki.pki.scep.serveremulator;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.bouncycastle.asn1.pkcs.CertificationRequestInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.CertificateList;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.jce.X509KeyUsage;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcContentVerifierProviderBuilder;
import org.bouncycastle.operator.bc.BcDSAContentVerifierProviderBuilder;
import org.bouncycastle.operator.bc.BcECContentVerifierProviderBuilder;
import org.bouncycastle.operator.bc.BcRSAContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.common.util.LogUtil;
import org.xipki.common.util.ParamUtil;
import org.xipki.pki.scep.crypto.ScepHashAlgoType;
import org.xipki.pki.scep.util.ScepUtil;
import org.xipki.security.util.KeyUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CaEmulator {

    public static final long MIN_IN_MS = 60L * 1000;

    public static final long DAY_IN_MS = 24L * 60 * MIN_IN_MS;

    private static final Logger LOG = LoggerFactory.getLogger(CaEmulator.class);

    private static final DefaultDigestAlgorithmIdentifierFinder DFLT_DIGESTALG_IDENTIFIER_FINDER =
            new DefaultDigestAlgorithmIdentifierFinder();

    private static final Map<String, BcContentVerifierProviderBuilder> VERIFIER_PROVIDER_BUILDER
        = new HashMap<>();

    private final PrivateKey caKey;

    private final Certificate caCert;

    private final X500Name caSubject;

    private final byte[] caCertBytes;

    private final boolean generateCrl;

    private final Map<BigInteger, Certificate> serialCertMap
        = new HashMap<BigInteger, Certificate>();

    private final Map<X500Name, Certificate> reqSubjectCertMap
        = new HashMap<X500Name, Certificate>();

    private final AtomicLong serialNumber = new AtomicLong(2);

    private final AtomicLong crlNumber = new AtomicLong(2);

    private CertificateList crl;

    public CaEmulator(final PrivateKey caKey, final Certificate caCert, final boolean generateCrl)
            throws CertificateEncodingException {
        this.caKey = ParamUtil.requireNonNull("caKey", caKey);
        this.caCert = ParamUtil.requireNonNull("caCert", caCert);
        this.caSubject = caCert.getSubject();
        this.generateCrl = generateCrl;
        try {
            this.caCertBytes = caCert.getEncoded();
        } catch (IOException ex) {
            throw new CertificateEncodingException(ex.getMessage(), ex);
        }
    }

    public PrivateKey caKey() {
        return caKey;
    }

    public Certificate caCert() {
        return caCert;
    }

    public byte[] caCertBytes() {
        return Arrays.copyOf(caCertBytes, caCertBytes.length);
    }

    public boolean isGenerateCrl() {
        return generateCrl;
    }

    public Certificate generateCert(final CertificationRequest csr) throws Exception {
        if (!verifyPopo(csr)) {
            throw new Exception("CSR invalid");
        }
        CertificationRequestInfo reqInfo = csr.getCertificationRequestInfo();
        return generateCert(reqInfo.getSubjectPublicKeyInfo(), reqInfo.getSubject());
    }

    public Certificate generateCert(final SubjectPublicKeyInfo pubKeyInfo, final X500Name subjectDn)
            throws Exception {
        return generateCert(pubKeyInfo, subjectDn,
                new Date(System.currentTimeMillis() - 10 * CaEmulator.MIN_IN_MS));
    }

    public Certificate generateCert(final SubjectPublicKeyInfo pubKeyInfo, final X500Name subjectDn,
            final Date notBefore) throws Exception {
        ParamUtil.requireNonNull("pubKeyInfo", pubKeyInfo);
        ParamUtil.requireNonNull("subjectDn", subjectDn);
        ParamUtil.requireNonNull("notBefore", notBefore);

        Date notAfter = new Date(notBefore.getTime() + 730 * DAY_IN_MS);
        BigInteger tmpSerialNumber = BigInteger.valueOf(serialNumber.getAndAdd(1));
        X509v3CertificateBuilder certGenerator = new X509v3CertificateBuilder(caSubject,
                tmpSerialNumber, notBefore, notAfter, subjectDn, pubKeyInfo);

        X509KeyUsage ku = new X509KeyUsage(X509KeyUsage.digitalSignature
                    | X509KeyUsage.dataEncipherment | X509KeyUsage.keyAgreement
                    | X509KeyUsage.keyEncipherment);
        certGenerator.addExtension(Extension.keyUsage, true, ku);
        BasicConstraints bc = new BasicConstraints(false);
        certGenerator.addExtension(Extension.basicConstraints, true, bc);

        String signatureAlgorithm = ScepUtil.getSignatureAlgorithm(caKey, ScepHashAlgoType.SHA256);
        ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(caKey);
        Certificate asn1Cert = certGenerator.build(contentSigner).toASN1Structure();

        serialCertMap.put(tmpSerialNumber, asn1Cert);
        reqSubjectCertMap.put(subjectDn, asn1Cert);
        return asn1Cert;
    }

    public Certificate getCert(final X500Name issuer, final BigInteger serialNumber) {
        if (!caSubject.equals(issuer)) {
            return null;
        }

        return serialCertMap.get(serialNumber);
    }

    public Certificate pollCert(final X500Name issuer, final X500Name subject) {
        ParamUtil.requireNonNull("issuer", issuer);
        ParamUtil.requireNonNull("subject", subject);
        if (!caSubject.equals(issuer)) {
            return null;
        }

        return reqSubjectCertMap.get(subject);
    }

    public synchronized CertificateList getCrl(final X500Name issuer, final BigInteger serialNumber)
            throws Exception {
        if (crl != null) {
            return crl;
        }

        Date thisUpdate = new Date();
        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(caSubject, thisUpdate);
        Date nextUpdate = new Date(thisUpdate.getTime() + 30 * DAY_IN_MS);
        crlBuilder.setNextUpdate(nextUpdate);
        Date caStartTime = caCert.getTBSCertificate().getStartDate().getDate();
        Date revocationTime = new Date(caStartTime.getTime() + 1);
        if (revocationTime.after(thisUpdate)) {
            revocationTime = caStartTime;
        }
        crlBuilder.addCRLEntry(BigInteger.valueOf(2), revocationTime, CRLReason.keyCompromise);
        crlBuilder.addExtension(Extension.cRLNumber, false,
                new ASN1Integer(crlNumber.getAndAdd(1)));

        String signatureAlgorithm = ScepUtil.getSignatureAlgorithm(caKey, ScepHashAlgoType.SHA256);
        ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(caKey);
        X509CRLHolder crl = crlBuilder.build(contentSigner);
        return crl.toASN1Structure();
    }

    private boolean verifyPopo(final CertificationRequest csr) {
        ParamUtil.requireNonNull("csr", csr);
        try {
            PKCS10CertificationRequest p10Req = new PKCS10CertificationRequest(csr);
            SubjectPublicKeyInfo pkInfo = p10Req.getSubjectPublicKeyInfo();
            PublicKey pk = KeyUtil.generatePublicKey(pkInfo);

            ContentVerifierProvider cvp = getContentVerifierProvider(pk);
            return p10Req.isSignatureValid(cvp);
        } catch (InvalidKeyException | PKCSException | NoSuchAlgorithmException
                | InvalidKeySpecException ex) {
            LogUtil.error(LOG, ex, "could not validate POPO of CSR");
            return false;
        }
    }

    public ContentVerifierProvider getContentVerifierProvider(final PublicKey publicKey)
            throws InvalidKeyException {
        ParamUtil.requireNonNull("publicKey", publicKey);

        String keyAlg = publicKey.getAlgorithm().toUpperCase();
        if ("EC".equals(keyAlg)) {
            keyAlg = "ECDSA";
        }

        BcContentVerifierProviderBuilder builder = VERIFIER_PROVIDER_BUILDER.get(keyAlg);
        if (builder == null) {
            if ("RSA".equals(keyAlg)) {
                builder = new BcRSAContentVerifierProviderBuilder(DFLT_DIGESTALG_IDENTIFIER_FINDER);
            } else if ("DSA".equals(keyAlg)) {
                builder = new BcDSAContentVerifierProviderBuilder(DFLT_DIGESTALG_IDENTIFIER_FINDER);
            } else if ("ECDSA".equals(keyAlg)) {
                builder = new BcECContentVerifierProviderBuilder(DFLT_DIGESTALG_IDENTIFIER_FINDER);
            } else {
                throw new InvalidKeyException("unknown key algorithm of the public key " + keyAlg);
            }
            VERIFIER_PROVIDER_BUILDER.put(keyAlg, builder);
        }

        AsymmetricKeyParameter keyParam = KeyUtil.generatePublicKeyParameter(publicKey);
        try {
            return builder.build(keyParam);
        } catch (OperatorCreationException ex) {
            throw new InvalidKeyException("could not build ContentVerifierProvider: "
                    + ex.getMessage(), ex);
        }
    }

}
