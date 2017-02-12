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

package org.xipki.commons.security.pkcs11.proxy;

import java.io.IOException;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DERTaggedObject;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.exception.BadAsn1ObjectException;

/**
 *
 * <pre>
 * ASN1P11Params ::= CHOICE {
 *     rsaPkcsPssParams   [0]  RSA-PKCS-PSS-Parameters }
 * </pre>
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class Asn1P11Params extends ASN1Object {

    private final ASN1Encodable p11Params;

    public Asn1P11Params(final ASN1Encodable p11Params) {
        this.p11Params = ParamUtil.requireNonNull("p11Params", p11Params);
    }

    private Asn1P11Params(final ASN1TaggedObject taggedObject) throws BadAsn1ObjectException {
        int tagNo = taggedObject.getTagNo();
        if (tagNo == 0) {
            this.p11Params = Asn1RSAPkcsPssParams.getInstance(taggedObject.getObject());
        } else {
            throw new BadAsn1ObjectException("invalid tag " + tagNo);
        }
    }

    public static Asn1P11Params getInstance(final Object obj) throws BadAsn1ObjectException {
        if (obj == null || obj instanceof Asn1P11Params) {
            return (Asn1P11Params) obj;
        }

        try {
            if (obj instanceof ASN1TaggedObject) {
                return new Asn1P11Params((ASN1TaggedObject) obj);
            } else if (obj instanceof byte[]) {
                return getInstance(ASN1Primitive.fromByteArray((byte[]) obj));
            } else {
                throw new BadAsn1ObjectException("unknown object: " + obj.getClass().getName());
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new BadAsn1ObjectException("unable to parse encoded object: " + ex.getMessage(),
                    ex);
        }
    }

    @Override
    public ASN1Primitive toASN1Primitive() {
        int tagNo;
        if (p11Params instanceof Asn1RSAPkcsPssParams) {
            tagNo = 0;
        } else {
            throw new RuntimeException("invalid ASN1P11Param type "
                    + p11Params.getClass().getName());
        }

        return new DERTaggedObject(tagNo, p11Params);
    }

    public ASN1Encodable getP11Params() {
        return p11Params;
    }

}
