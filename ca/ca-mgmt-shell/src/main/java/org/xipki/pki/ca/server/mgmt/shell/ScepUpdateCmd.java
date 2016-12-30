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
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.bouncycastle.util.encoders.Base64;
import org.xipki.commons.common.util.IoUtil;
import org.xipki.commons.console.karaf.IllegalCmdParamException;
import org.xipki.commons.console.karaf.completer.FilePathCompleter;
import org.xipki.commons.console.karaf.completer.SignerTypeCompleter;
import org.xipki.commons.password.PasswordResolver;
import org.xipki.commons.security.util.X509Util;
import org.xipki.pki.ca.server.mgmt.api.CaManager;
import org.xipki.pki.ca.server.mgmt.api.ChangeScepEntry;
import org.xipki.pki.ca.server.mgmt.api.x509.ScepEntry;
import org.xipki.pki.ca.server.mgmt.shell.completer.ScepNameCompleter;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

@Command(scope = "xipki-ca", name = "scep-up",
        description = "Update SCEP")
@Service
public class ScepUpdateCmd extends CaCommandSupport {

    @Option(name = "--ca",
            required = true,
            description = "CA name\n"
                    + "(required)")
    @Completion(ScepNameCompleter.class)
    private String caName;

    @Option(name = "--resp-type",
            description = "type of the responder")
    @Completion(SignerTypeCompleter.class)
    private String responderType;

    @Option(name = "--resp-conf",
            description = "conf of the responder")
    private String responderConf;

    @Option(name = "--resp-cert",
            description = "responder certificate file or 'NULL'")
    @Completion(FilePathCompleter.class)
    private String certFile;

    @Option(name = "--control",
            description = "SCEP control or 'NULL'")
    private String control;

    @Reference
    private PasswordResolver passwordResolver;

    private String getResponderConf() throws Exception {
        if (responderConf == null) {
            return responderConf;
        }
        String tmpRespType = responderType;
        if (tmpRespType == null) {
            ScepEntry entry = caManager.getScepEntry(caName);
            if (entry == null) {
                throw new IllegalCmdParamException("please specify the responderType");
            }
            tmpRespType = entry.getResponderType();
        }

        return ShellUtil.canonicalizeSignerConf(tmpRespType, responderConf, passwordResolver,
                securityFactory);
    }

    @Override
    protected Object doExecute() throws Exception {
        String certConf = null;
        if (CaManager.NULL.equalsIgnoreCase(certFile)) {
            certConf = CaManager.NULL;
        } else if (certFile != null) {
            byte[] certBytes = IoUtil.read(certFile);
            X509Util.parseCert(new ByteArrayInputStream(certBytes));
            certConf = Base64.toBase64String(certBytes);
        }

        ChangeScepEntry entry = new ChangeScepEntry(caName);
        if (responderType != null) {
            entry.setResponderType(responderType);
        }

        String conf = getResponderConf();
        if (conf != null) {
            entry.setResponderConf(conf);
        }

        if (certConf != null) {
            entry.setBase64Cert(certConf);
        }

        if (control != null) {
            entry.setControl(control);
        }

        boolean bo = caManager.changeScep(entry);
        output(bo, "updated", "could not update", "SCEP responder " + caName);
        return null;
    } // method doExecute

}
