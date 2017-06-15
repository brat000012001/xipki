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

package org.xipki.security.pkcs11.provider;

import org.xipki.security.HashAlgoType;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */
// CHECKSTYLE:SKIP
public class P11PlainECDSASignatureSpi extends AbstractP11ECDSASignatureSpi {

    // CHECKSTYLE:SKIP
    public static class SHA1 extends P11PlainECDSASignatureSpi {

        public SHA1() {
            super(HashAlgoType.SHA1);
        }

    } // class SHA1

    // CHECKSTYLE:SKIP
    public static class NONE extends P11PlainECDSASignatureSpi {

        public NONE() {
            super(null);
        }

    } // class NONE

    // CHECKSTYLE:SKIP
    public static class SHA224 extends P11PlainECDSASignatureSpi {

        public SHA224() {
            super(HashAlgoType.SHA224);
        }

    } // class SHA224

    // CHECKSTYLE:SKIP
    public static class SHA256 extends P11PlainECDSASignatureSpi {

        public SHA256() {
            super(HashAlgoType.SHA256);
        }

    } // class SHA256

    // CHECKSTYLE:SKIP
    static class SHA384 extends P11PlainECDSASignatureSpi {

        SHA384() {
            super(HashAlgoType.SHA384);
        }

    } // class SHA384

    // CHECKSTYLE:SKIP
    public static class SHA512 extends P11PlainECDSASignatureSpi {

        public SHA512() {
            super(HashAlgoType.SHA512);
        }

    } // class SHA512

    private P11PlainECDSASignatureSpi(final HashAlgoType hashAlgo) {
        super(hashAlgo, true);
    }

}
