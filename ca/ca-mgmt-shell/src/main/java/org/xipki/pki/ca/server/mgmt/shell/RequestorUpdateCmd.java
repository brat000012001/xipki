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

package org.xipki.pki.ca.server.mgmt.shell;

import java.io.ByteArrayInputStream;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.bouncycastle.util.encoders.Base64;
import org.xipki.commons.common.util.IoUtil;
import org.xipki.commons.console.karaf.completer.FilePathCompleter;
import org.xipki.commons.security.util.X509Util;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

@Command(scope = "xipki-ca", name = "requestor-up",
        description = "update requestor")
@Service
public class RequestorUpdateCmd extends CaCommandSupport {

    @Option(name = "--name", aliases = "-n",
            required = true,
            description = "requestor name\n"
                    + "(required)")
    protected String name;

    @Option(name = "--cert",
            required = true,
            description = "requestor certificate file\n"
                    + "(required)")
    @Completion(FilePathCompleter.class)
    protected String certFile;

    @Override
    protected Object doExecute() throws Exception {
        // check if the certificate is valid
        byte[] certBytes = IoUtil.read(certFile);
        X509Util.parseCert(new ByteArrayInputStream(certBytes));
        boolean bo = caManager.changeCmpRequestor(name, Base64.toBase64String(certBytes));
        output(bo, "updated", "could not update", "CMP requestor " + name);
        return null;
    }

}
