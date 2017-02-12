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

package org.xipki.commons.password;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */
// CHECKSTYLE:SKIP
public class OBFPasswordService {

    public static final String OBFUSCATE = "OBF:";

    public static String obfuscate(final String str) {
        Objects.requireNonNull(str, "str must not be null");
        StringBuilder buf = new StringBuilder();
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);

        buf.append(OBFUSCATE);
        for (int i = 0; i < bytes.length; i++) {
            byte b1 = bytes[i];
            byte b2 = bytes[bytes.length - (i + 1)];
            if (b1 < 0 || b2 < 0) {
                int i0 = (0xff & b1) * 256 + (0xff & b2);
                String sx = Integer.toString(i0, 36).toLowerCase();
                buf.append("U0000", 0, 5 - sx.length());
                buf.append(sx);
            } else {
                int i1 = 127 + b1 + b2;
                int i2 = 127 + b1 - b2;
                int i0 = i1 * 256 + i2;
                String sx = Integer.toString(i0, 36).toLowerCase();

                buf.append("000", 0, 4 - sx.length());
                buf.append(sx);
            }
        } // end for
        return buf.toString();
    }

    public static String deobfuscate(final String str) {
        Objects.requireNonNull(str, "str must not be null");
        String tmpStr = str;

        if (startsWithIgnoreCase(tmpStr, OBFUSCATE)) {
            tmpStr = tmpStr.substring(4);
        }

        byte[] bytes = new byte[tmpStr.length() / 2];
        int idx = 0;
        for (int i = 0; i < tmpStr.length(); i += 4) {
            if (tmpStr.charAt(i) == 'U') {
                i++;
                String sx = tmpStr.substring(i, i + 4);
                int i0 = Integer.parseInt(sx, 36);
                byte bx = (byte) (i0 >> 8);
                bytes[idx++] = bx;
            } else {
                String sx = tmpStr.substring(i, i + 4);
                int i0 = Integer.parseInt(sx, 36);
                int i1 = (i0 / 256);
                int i2 = (i0 % 256);
                byte bx = (byte) ((i1 + i2 - 254) / 2);
                bytes[idx++] = bx;
            }
        } // end for

        return new String(bytes, 0, idx, StandardCharsets.UTF_8);
    }

    private static boolean startsWithIgnoreCase(final String str, final String prefix) {
        if (str.length() < prefix.length()) {
            return false;
        }

        return prefix.equalsIgnoreCase(str.substring(0, prefix.length()));
    }
}
