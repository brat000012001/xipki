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

package org.xipki.pki.ca.common.cmp;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xipki.common.util.ParamUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CmpUtf8Pairs {

    public static final String KEY_CERT_PROFILE = "CERT-PROFILE";

    public static final String KEY_NOT_BEFORE = "NOT-BEFORE";

    public static final String KEY_NOT_AFTER = "NOT-AFTER";

    private static final char NAME_TERM = '?';

    private static final char TOKEN_TERM = '%';

    private final Map<String, String> pairs = new HashMap<>();

    public CmpUtf8Pairs(final String name, final String value) {
        putUtf8Pair(name, value);
    }

    public CmpUtf8Pairs() {
    }

    public CmpUtf8Pairs(final String encodedCmpUtf8Pairs) {
        String encoded = ParamUtil.requireNonBlank("encodedCmpUtf8Pairs", encodedCmpUtf8Pairs);
        // remove the ending '%'-symbols
        while (encoded.charAt(encoded.length() - 1) == TOKEN_TERM) {
            encoded = encoded.substring(0, encoded.length() - 1);
        }

        // find the position of terminators
        List<Integer> positions = new LinkedList<>();

        int idx = 1;
        int len = encoded.length();
        while (idx < len) {
            char ch = encoded.charAt(idx++);
            if (ch == TOKEN_TERM) {
                char ch2 = encoded.charAt(idx);
                if (ch2 < '0' || ch2 > '9') {
                    positions.add(idx - 1);
                }
            }
        }
        positions.add(encoded.length());

        // parse the token
        int beginIndex = 0;
        for (int i = 0; i < positions.size(); i++) {
            int endIndex = positions.get(i);
            String token = encoded.substring(beginIndex, endIndex);

            int sepIdx = token.indexOf(NAME_TERM);
            if (sepIdx == -1 || sepIdx == token.length() - 1) {
                throw new IllegalArgumentException("invalid token: " + token);
            }
            String name = token.substring(0, sepIdx);
            name = decodeNameOrValue(name);
            String value = token.substring(sepIdx + 1);
            value = decodeNameOrValue(value);
            pairs.put(name, value);

            beginIndex = endIndex + 1;
        }
    } // constructor

    public void putUtf8Pair(final String name, final String value) {
        ParamUtil.requireNonNull("name", name);
        ParamUtil.requireNonNull("value", value);

        char ch = name.charAt(0);
        if (ch >= '0' && ch <= '9') {
            throw new IllegalArgumentException("name must not begin with " + ch);
        }
        pairs.put(name, value);
    }

    public void removeUtf8Pair(final String name) {
        ParamUtil.requireNonNull("name", name);
        pairs.remove(name);
    }

    public String getValue(final String name) {
        ParamUtil.requireNonNull(name, name);
        return pairs.get(name);
    }

    public Set<String> getNames() {
        return Collections.unmodifiableSet(pairs.keySet());
    }

    public String getEncoded() {
        StringBuilder sb = new StringBuilder();
        List<String> names = new LinkedList<>();
        for (String name : pairs.keySet()) {
            String value = pairs.get(name);
            if (value.length() <= 100) {
                names.add(name);
            }
        }
        Collections.sort(names);

        for (String name : pairs.keySet()) {
            if (!names.contains(name)) {
                names.add(name);
            }
        }

        for (String name : names) {
            String value = pairs.get(name);
            sb.append(encodeNameOrValue(name));
            sb.append(NAME_TERM);
            if (value != null) {
                sb.append(encodeNameOrValue(value));
            }
            sb.append(TOKEN_TERM);
        }

        return sb.toString();
    } // method getEncoded

    @Override
    public String toString() {
        return getEncoded();
    }

    @Override
    public int hashCode() {
        return getEncoded().hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof CmpUtf8Pairs)) {
            return false;
        }

        CmpUtf8Pairs objB = (CmpUtf8Pairs) obj;
        return pairs.equals(objB.pairs);
    }

    private static String encodeNameOrValue(final String str) {
        String tmpStr = str;
        if (tmpStr.indexOf("%") != -1) {
            tmpStr = tmpStr.replaceAll("%", "%25");
        }

        if (tmpStr.indexOf("?") != -1) {
            tmpStr = tmpStr.replaceAll("\\?", "%3f");
        }

        return tmpStr;
    }

    private static String decodeNameOrValue(final String str) {
        int idx = str.indexOf(TOKEN_TERM);
        if (idx == -1) {
            return str;
        }

        StringBuilder newS = new StringBuilder();

        for (int i = 0; i < str.length();) {
            char ch = str.charAt(i);
            if (ch != TOKEN_TERM) {
                newS.append(ch);
                i++;
            } else {
                if (i + 3 <= str.length()) {
                    String hex = str.substring(i + 1, i + 3);
                    ch = (char) Byte.parseByte(hex, 16);
                    newS.append(ch);
                    i += 3;
                } else {
                    newS.append(str.substring(i));
                    break;
                }
            }
        }

        return newS.toString();
    } // method decodeNameOrValue

}
