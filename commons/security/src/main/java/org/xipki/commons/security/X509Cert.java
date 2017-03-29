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

package org.xipki.commons.security;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.util.X509Util;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class X509Cert {

    private final X509Certificate cert;

    private final String subject;

    private final byte[] encodedCert;

    private final byte[] subjectKeyIdentifer;

    private final X500Name subjectAsX500Name;

    private X509CertificateHolder certHolder;

    public X509Cert(final X509Certificate cert) {
        this(cert, null);
    }

    public X509Cert(final X509Certificate cert, final byte[] encodedCert) {
        this.cert = ParamUtil.requireNonNull("cert", cert);

        X500Principal x500Subject = cert.getSubjectX500Principal();
        this.subject = X509Util.getRfc4519Name(x500Subject);
        this.subjectAsX500Name = X500Name.getInstance(x500Subject.getEncoded());
        try {
            this.subjectKeyIdentifer = X509Util.extractSki(cert);
        } catch (CertificateEncodingException ex) {
            throw new RuntimeException(String.format(
                    "CertificateEncodingException: %s", ex.getMessage()));
        }

        if (encodedCert != null) {
            this.encodedCert = encodedCert;
            return;
        }

        try {
            this.encodedCert = cert.getEncoded();
        } catch (CertificateEncodingException ex) {
            throw new RuntimeException(String.format(
                    "CertificateEncodingException: %s", ex.getMessage()));
        }
    }

    public X509Certificate getCert() {
        return cert;
    }

    public byte[] getEncodedCert() {
        return encodedCert;
    }

    public String getSubject() {
        return subject;
    }

    public X500Name getSubjectAsX500Name() {
        return subjectAsX500Name;
    }

    public byte[] getSubjectKeyIdentifier() {
        return Arrays.copyOf(subjectKeyIdentifer, subjectKeyIdentifer.length);
    }

    public X509CertificateHolder getCertHolder() {
        if (certHolder != null) {
            return certHolder;
        }

        synchronized (cert) {
            try {
                certHolder = new X509CertificateHolder(encodedCert);
            } catch (IOException ex) {
                throw new RuntimeException(
                        "should not happen, could not decode certificate: " + ex.getMessage());
            }
            return certHolder;
        }
    }

    @Override
    public String toString() {
        return cert.toString();
    }

    @Override
    public int hashCode() {
        return cert.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof X509Cert)) {
            return false;
        }

        return Arrays.equals(encodedCert, ((X509Cert) obj).encodedCert);
    }

}
