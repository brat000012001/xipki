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

package org.xipki.security;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class TlsExtensionType implements Comparable<TlsExtensionType> {

    public static final TlsExtensionType SERVER_NAME = new TlsExtensionType(0, "server_name");
    public static final TlsExtensionType MAX_FRAGMENT_LENGTH
            = new TlsExtensionType(1, "max_fragment_length");
    public static final TlsExtensionType CLIENT_CERTIFICATE_URL
            = new TlsExtensionType(2, "client_certificate_url");
    public static final TlsExtensionType TRUSTED_CA_KEYS
            = new TlsExtensionType(3, "trusted_ca_keys");
    public static final TlsExtensionType TRUCATED_HMAC = new TlsExtensionType(4, "truncated_hmac");
    public static final TlsExtensionType STATUS_REQUEST = new TlsExtensionType(5, "status_request");

    private final int code;
    private final String name;

    private TlsExtensionType(final int code, final String name) {
        this.code = code;
        this.name = name;
    }

    public int code() {
        return code;
    }

    public String name() {
        return name;
    }

    @Override
    public int compareTo(final TlsExtensionType obj) {
        return Integer.valueOf(code).compareTo(obj.code);
    }

}
