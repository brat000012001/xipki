/*
 *
 * Copyright (c) 2013 - 2016 Lijun Liao
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

package org.xipki.commons.security.pkcs11.provider;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;

import org.eclipse.jdt.annotation.Nullable;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.exception.P11TokenException;
import org.xipki.commons.security.exception.XiSecurityException;
import org.xipki.commons.security.pkcs11.P11CryptService;
import org.xipki.commons.security.pkcs11.P11EntityIdentifier;
import org.xipki.commons.security.pkcs11.P11Params;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class P11PrivateKey implements PrivateKey {

    private static final long serialVersionUID = 1L;

    private final P11CryptService p11CryptService;

    private final P11EntityIdentifier identityId;

    private final String algorithm;

    private final int keysize;

    public P11PrivateKey(final P11CryptService p11CryptService,
            final P11EntityIdentifier identityId) throws P11TokenException {
        this.p11CryptService = ParamUtil.requireNonNull("identityId", p11CryptService);
        this.identityId = ParamUtil.requireNonNull("entityId", identityId);

        PublicKey publicKey = p11CryptService.getIdentity(identityId).getPublicKey();

        if (publicKey instanceof RSAPublicKey) {
            algorithm = "RSA";
            keysize = ((RSAPublicKey) publicKey).getModulus().bitLength();
        } else if (publicKey instanceof DSAPublicKey) {
            algorithm = "DSA";
            keysize = ((DSAPublicKey) publicKey).getParams().getP().bitLength();
        } else if (publicKey instanceof ECPublicKey) {
            algorithm = "EC";
            keysize = ((ECPublicKey) publicKey).getParams().getCurve().getField().getFieldSize();
        } else {
            throw new P11TokenException("unknown public key: " + publicKey);
        }
    }

    boolean supportsMechanism(final long mechanism) {
        try {
            return p11CryptService.getSlot(identityId.getSlotId()).supportsMechanism(mechanism);
        } catch (P11TokenException ex) {
            return false;
        }
    }

    @Override
    public String getFormat() {
        return null;
    }

    @Override
    public byte[] getEncoded() {
        return null;
    }

    @Override
    public String getAlgorithm() {
        return algorithm;
    }

    public int getKeysize() {
        return keysize;
    }

    public byte[] sign(final long mechanism, @Nullable final P11Params parameters,
            final byte[] content) throws XiSecurityException, P11TokenException {
        return p11CryptService.getIdentity(identityId).sign(mechanism, parameters, content);
    }

    P11CryptService getP11CryptService() {
        return p11CryptService;
    }

    P11EntityIdentifier getIdentityId() {
        return identityId;
    }

}
