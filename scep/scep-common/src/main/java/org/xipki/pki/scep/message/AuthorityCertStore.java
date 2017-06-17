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

package org.xipki.pki.scep.message;

import java.security.cert.X509Certificate;

import org.xipki.common.util.ParamUtil;
import org.xipki.pki.scep.crypto.KeyUsage;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class AuthorityCertStore {

    private final X509Certificate caCert;

    private final X509Certificate signatureCert;

    private final X509Certificate encryptionCert;

    private AuthorityCertStore(final X509Certificate caCert, final X509Certificate signatureCert,
            final X509Certificate encryptionCert) {
        this.caCert = caCert;
        this.signatureCert = signatureCert;
        this.encryptionCert = encryptionCert;
    }

    public X509Certificate signatureCert() {
        return signatureCert;
    }

    public X509Certificate encryptionCert() {
        return encryptionCert;
    }

    public X509Certificate caCert() {
        return caCert;
    }

    public static AuthorityCertStore getInstance(final X509Certificate caCert,
            final X509Certificate... raCerts) {
        ParamUtil.requireNonNull("caCert", caCert);

        X509Certificate encryptionCert = null;
        X509Certificate signatureCert = null;

        if (raCerts == null || raCerts.length == 0) {
            signatureCert = caCert;
            encryptionCert = caCert;
        } else {
            for (X509Certificate cert : raCerts) {
                boolean[] keyusage = cert.getKeyUsage();
                if (hasKeyusage(keyusage, KeyUsage.keyEncipherment)) {
                    if (encryptionCert != null) {
                        throw new IllegalArgumentException(
                                "Could not determine RA certificate for encryption");
                    }
                    encryptionCert = cert;
                }

                if (hasKeyusage(keyusage, KeyUsage.digitalSignature)
                        || hasKeyusage(keyusage, KeyUsage.contentCommitment)) {
                    if (signatureCert != null) {
                        throw new IllegalArgumentException(
                                "Could not determine RA certificate for signature");
                    }
                    signatureCert = cert;
                }
            }

            if (encryptionCert == null) {
                throw new IllegalArgumentException(
                        "Could not determine RA certificate for encryption");
            }

            if (signatureCert == null) {
                throw new IllegalArgumentException(
                        "Could not determine RA certificate for signature");
            }
        }

        return new AuthorityCertStore(caCert, signatureCert, encryptionCert);
    } // method getInstance

    private static boolean hasKeyusage(final boolean[] keyusage, final KeyUsage usage) {
        if (keyusage != null && keyusage.length > usage.bit()) {
            return keyusage[usage.bit()];
        }
        return false;
    }

}
