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

package org.xipki.pki.ocsp.server.impl;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.ocsp.ResponderID;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSASSAPSSparams;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.RespID;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.ConcurrentContentSigner;
import org.xipki.commons.security.HashAlgoType;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

class ResponderSigner {

    private final Map<String, ConcurrentContentSigner> algoSignerMap;

    private final List<ConcurrentContentSigner> signers;

    private final X509CertificateHolder bcCertificate;

    private final X509Certificate certificate;

    private final X509CertificateHolder[] bcCertificateChain;

    private final X509Certificate[] certificateChain;

    private final RespID responderIdByName;

    private final RespID responderIdByKey;

    ResponderSigner(final List<ConcurrentContentSigner> signers)
            throws CertificateException, IOException {
        this.signers = ParamUtil.requireNonEmpty("signers", signers);
        X509Certificate[] tmpCertificateChain = signers.get(0).getCertificateChain();
        if (tmpCertificateChain == null || tmpCertificateChain.length == 0) {
            throw new CertificateException("no certificate is bound with the signer");
        }
        int len = tmpCertificateChain.length;
        if (len > 1) {
            X509Certificate cert = tmpCertificateChain[len - 1];
            if (cert.getIssuerX500Principal().equals(cert.getSubjectX500Principal())) {
                len--;
            }
        }
        this.certificateChain = new X509Certificate[len];
        System.arraycopy(tmpCertificateChain, 0, this.certificateChain, 0, len);

        this.certificate = certificateChain[0];

        this.bcCertificate = new X509CertificateHolder(this.certificate.getEncoded());
        this.bcCertificateChain = new X509CertificateHolder[this.certificateChain.length];
        for (int i = 0; i < certificateChain.length; i++) {
            this.bcCertificateChain[i] = new X509CertificateHolder(
                    this.certificateChain[i].getEncoded());
        }

        this.responderIdByName = new RespID(this.bcCertificate.getSubject());
        byte[] keySha1 = HashAlgoType.SHA1.hash(
                this.bcCertificate.getSubjectPublicKeyInfo().getPublicKeyData().getBytes());
        this.responderIdByKey = new RespID(new ResponderID(new DEROctetString(keySha1)));

        algoSignerMap = new HashMap<>();
        for (ConcurrentContentSigner signer : signers) {
            String algoName = getSignatureAlgorithmName(signer.getAlgorithmIdentifier());
            algoSignerMap.put(algoName, signer);
        }
    } // constructor

    public ConcurrentContentSigner getFirstSigner() {
        return signers.get(0);
    }

    public ConcurrentContentSigner getSignerForPreferredSigAlgs(
            final ASN1Sequence preferredSigAlgs) {
        if (preferredSigAlgs == null) {
            return signers.get(0);
        }

        int size = preferredSigAlgs.size();
        for (int i = 0; i < size; i++) {
            ASN1Sequence algObj = ASN1Sequence.getInstance(preferredSigAlgs.getObjectAt(i));
            AlgorithmIdentifier sigAlgId = AlgorithmIdentifier.getInstance(algObj.getObjectAt(0));
            String algoName = getSignatureAlgorithmName(sigAlgId);
            if (algoSignerMap.containsKey(algoName)) {
                return algoSignerMap.get(algoName);
            }
        }
        return null;
    }

    public RespID getResponder(final boolean byName) {
        return byName ? responderIdByName :  responderIdByKey;
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    public X509Certificate[] getCertificateChain() {
        return certificateChain;
    }

    public X509CertificateHolder getBcCertificate() {
        return bcCertificate;
    }

    public X509CertificateHolder[] getBcCertificateChain() {
        return bcCertificateChain;
    }

    public boolean isHealthy() {
        for (ConcurrentContentSigner signer : signers) {
            if (!signer.isHealthy()) {
                return false;
            }
        }

        return true;
    }

    private static String getSignatureAlgorithmName(final AlgorithmIdentifier sigAlgId) {
        ASN1ObjectIdentifier algOid = sigAlgId.getAlgorithm();
        if (!PKCSObjectIdentifiers.id_RSASSA_PSS.equals(algOid)) {
            return algOid.getId();
        }

        ASN1Encodable asn1Encodable = sigAlgId.getParameters();
        RSASSAPSSparams param = RSASSAPSSparams.getInstance(asn1Encodable);
        ASN1ObjectIdentifier digestAlgOid = param.getHashAlgorithm().getAlgorithm();
        return digestAlgOid.getId() + "WITHRSAANDMGF1";
    }

}
