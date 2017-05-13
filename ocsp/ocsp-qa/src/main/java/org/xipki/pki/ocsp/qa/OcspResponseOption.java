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

package org.xipki.pki.ocsp.qa;

import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.xipki.commons.common.util.StringUtil;
import org.xipki.commons.security.util.AlgorithmUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class OcspResponseOption {

    private X509Certificate respIssuer;

    private Occurrence nonceOccurrence;

    private Occurrence certhashOccurrence;

    private Occurrence nextUpdateOccurrence;

    private ASN1ObjectIdentifier certhashAlgId;

    private String signatureAlgName;

    public OcspResponseOption() {
    }

    public X509Certificate getRespIssuer() {
        return respIssuer;
    }

    public void setRespIssuer(final X509Certificate respIssuer) {
        this.respIssuer = respIssuer;
    }

    public Occurrence getNonceOccurrence() {
        return nonceOccurrence;
    }

    public void setNonceOccurrence(final Occurrence nonceOccurrence) {
        this.nonceOccurrence = nonceOccurrence;
    }

    public Occurrence getCerthashOccurrence() {
        return certhashOccurrence;
    }

    public void setCerthashOccurrence(final Occurrence certhashOccurrence) {
        this.certhashOccurrence = certhashOccurrence;
    }

    public Occurrence getNextUpdateOccurrence() {
        return nextUpdateOccurrence;
    }

    public void setNextUpdateOccurrence(final Occurrence nextUpdateOccurrence) {
        this.nextUpdateOccurrence = nextUpdateOccurrence;
    }

    public ASN1ObjectIdentifier getCerthashAlgId() {
        return certhashAlgId;
    }

    public void setCerthashAlgId(final ASN1ObjectIdentifier certhashAlgId) {
        this.certhashAlgId = certhashAlgId;
    }

    public String getSignatureAlgName() {
        return signatureAlgName;
    }

    public void setSignatureAlgName(final String signatureAlgName) throws NoSuchAlgorithmException {
        if (StringUtil.isBlank(signatureAlgName)) {
            this.signatureAlgName = null;
        } else {
            this.signatureAlgName = AlgorithmUtil.canonicalizeSignatureAlgo(signatureAlgName);
        }
    }

}
