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

package org.xipki.console.karaf.completer;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.bouncycastle.asn1.nist.NISTNamedCurves;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.teletrust.TeleTrusTNamedCurves;
import org.bouncycastle.asn1.x9.X962NamedCurves;
import org.xipki.console.karaf.AbstractDynamicEnumCompleter;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

@Service
// CHECKSTYLE:SKIP
public class ECCurveNameCompleter extends AbstractDynamicEnumCompleter {

    @Override
    protected Set<String> getEnums() {
        Set<String> curveNames = new HashSet<>();
        Enumeration<?> names = X962NamedCurves.getNames();
        while (names.hasMoreElements()) {
            curveNames.add((String) names.nextElement());
        }

        names = SECNamedCurves.getNames();
        while (names.hasMoreElements()) {
            curveNames.add((String) names.nextElement());
        }

        names = TeleTrusTNamedCurves.getNames();
        while (names.hasMoreElements()) {
            curveNames.add((String) names.nextElement());
        }

        names = NISTNamedCurves.getNames();
        while (names.hasMoreElements()) {
            curveNames.add((String) names.nextElement());
        }

        return curveNames;
    }

}
