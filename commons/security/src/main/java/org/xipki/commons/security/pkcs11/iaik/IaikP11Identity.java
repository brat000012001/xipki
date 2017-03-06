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

package org.xipki.commons.security.pkcs11.iaik;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;

import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.exception.P11TokenException;
import org.xipki.commons.security.pkcs11.P11EntityIdentifier;
import org.xipki.commons.security.pkcs11.P11Identity;
import org.xipki.commons.security.pkcs11.P11Params;

import iaik.pkcs.pkcs11.objects.PrivateKey;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

class IaikP11Identity extends P11Identity {

    private final PrivateKey privateKey;

    private final int expectedSignatureLen;

    IaikP11Identity(final IaikP11Slot slot, final P11EntityIdentifier identityId,
            final PrivateKey privateKey, final PublicKey publicKey,
            final X509Certificate[] certificateChain) {
        super(slot, identityId, publicKey, certificateChain);
        this.privateKey = ParamUtil.requireNonNull("privateKey", privateKey);

        int keyBitLen = getSignatureKeyBitLength();
        if (publicKey instanceof RSAPublicKey) {
            expectedSignatureLen = (keyBitLen + 7) / 8;
        } else if (publicKey instanceof ECPublicKey) {
            expectedSignatureLen = (keyBitLen + 7) / 8 * 2;
        } else if (publicKey instanceof DSAPublicKey) {
            expectedSignatureLen = (keyBitLen + 7) / 8 * 2;
        } else {
            throw new IllegalArgumentException(
                    "currently only RSA, DSA and EC public key are supported, but not "
                    + this.publicKey.getAlgorithm()
                    + " (class: " + publicKey.getClass().getName() + ")");
        }
    }

    @Override
    protected byte[] doSign(final long mechanism, final P11Params parameters, final byte[] content)
            throws P11TokenException {
        return ((IaikP11Slot) slot).sign(mechanism, parameters, content, this);
    }

    PrivateKey getPrivateKey() {
        return privateKey;
    }

    int getExpectedSignatureLen() {
        return expectedSignatureLen;
    }

}
