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

package org.xipki.scep.client;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.xipki.common.util.ParamUtil;
import org.xipki.scep.crypto.ScepHashAlgoType;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public final class PreprovisionedHashCaCertValidator implements CaCertValidator {

    private final ScepHashAlgoType hashAlgo;

    private final Set<byte[]> hashValues;

    public PreprovisionedHashCaCertValidator(final ScepHashAlgoType hashAlgo,
            final Set<byte[]> hashValues) {
        this.hashAlgo = ParamUtil.requireNonNull("hashAlgo", hashAlgo);
        ParamUtil.requireNonEmpty("hashValues", hashValues);

        final int hLen = hashAlgo.length();
        for (byte[] m : hashValues) {
            if (m.length != hLen) {
                throw new IllegalArgumentException("invalid the length of hashValue: "
                        + m.length + " != " + hLen);
            }
        }

        this.hashValues = new HashSet<byte[]>(hashValues.size());
        for (byte[] m : hashValues) {
            this.hashValues.add(Arrays.copyOf(m, m.length));
        }
    }

    @Override
    public boolean isTrusted(final X509Certificate cert) {
        ParamUtil.requireNonNull("cert", cert);
        byte[] actual;
        try {
            actual = hashAlgo.digest(cert.getEncoded());
        } catch (CertificateEncodingException ex) {
            return false;
        }

        for (byte[] m : hashValues) {
            if (Arrays.equals(actual, m)) {
                return true;
            }
        }

        return false;
    }

}
