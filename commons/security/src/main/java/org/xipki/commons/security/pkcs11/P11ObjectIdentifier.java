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

package org.xipki.commons.security.pkcs11;

import java.math.BigInteger;
import java.util.Arrays;

import org.bouncycastle.util.encoders.Hex;
import org.eclipse.jdt.annotation.NonNull;
import org.xipki.commons.common.util.ParamUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class P11ObjectIdentifier implements Comparable<P11ObjectIdentifier> {

    private final byte[] id;

    private final String idHex;

    private final String label;

    public P11ObjectIdentifier(@NonNull final byte[] id, @NonNull final String label) {
        this.id = ParamUtil.requireNonNull("id", id);
        this.label = ParamUtil.requireNonNull("label", label);
        this.idHex = Hex.toHexString(id).toUpperCase();
    }

    public byte[] getId() {
        return id;
    }

    public boolean matchesId(final byte[] id) {
        return Arrays.equals(id, this.id);
    }

    public String getIdHex() {
        return idHex;
    }

    public String getLabel() {
        return label;
    }

    public char[] getLabelChars() {
        return label.toCharArray();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(50);
        sb.append("(id = ").append(idHex).append(", label = ").append(label).append(")");
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int hashCode = new BigInteger(1, id).hashCode();
        hashCode += 31 * label.hashCode();
        return hashCode;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof P11ObjectIdentifier)) {
            return false;
        }

        P11ObjectIdentifier another = (P11ObjectIdentifier) obj;
        return Arrays.equals(id, another.id) && label.equals(another.label);
    }

    @Override
    public int compareTo(final P11ObjectIdentifier obj) {
        ParamUtil.requireNonNull("obj", obj);
        if (this == obj) {
            return 0;
        }

        return label.compareTo(obj.label);
    }

}
