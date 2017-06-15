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

package org.xipki.pki.ca.qa.internal;

import org.xipki.common.util.ParamUtil;
import org.xipki.pki.ca.certprofile.x509.jaxb.GeneralSubtreeBaseType;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class QaGeneralSubtree {

    private final GeneralSubtreeBaseType jaxb;

    public QaGeneralSubtree(final GeneralSubtreeBaseType jaxb) {
        this.jaxb = ParamUtil.requireNonNull("jaxb", jaxb);
        Integer min = jaxb.getMinimum();
        if (min != null) {
            ParamUtil.requireMin("jaxb.getMinimum()", min.intValue(), 0);
        }

        Integer max = jaxb.getMaximum();
        if (max != null) {
            ParamUtil.requireMin("jaxb.getMaximum()", max.intValue(), 0);
        }
    }

    public String getRfc822Name() {
        return jaxb.getRfc822Name();
    }

    public String getDnsName() {
        return jaxb.getDnsName();
    }

    public String getDirectoryName() {
        return jaxb.getDirectoryName();
    }

    public String getUri() {
        return jaxb.getUri();
    }

    public String getIpAddress() {
        return jaxb.getIpAddress();
    }

    public Integer getMinimum() {
        return jaxb.getMinimum();
    }

    public Integer getMaximum() {
        return jaxb.getMaximum();
    }

}
