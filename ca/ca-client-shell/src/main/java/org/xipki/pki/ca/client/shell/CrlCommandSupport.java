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

package org.xipki.pki.ca.client.shell;

import java.io.File;
import java.security.cert.X509CRL;
import java.util.Set;

import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.xipki.commons.console.karaf.CmdFailure;
import org.xipki.commons.console.karaf.IllegalCmdParamException;
import org.xipki.commons.console.karaf.completer.FilePathCompleter;
import org.xipki.pki.ca.client.api.CaClientException;
import org.xipki.pki.ca.client.api.PkiErrorException;
import org.xipki.pki.ca.client.shell.completer.CaNameCompleter;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public abstract class CrlCommandSupport extends ClientCommandSupport {

    @Option(name = "--ca",
            description = "CA name\n"
                    + "(required if multiple CAs are configured)")
    @Completion(CaNameCompleter.class)
    protected String caName;

    @Option(name = "--out", aliases = "-o",
            required = true,
            description = "where to save the CRL\n"
                    + "(required)")
    @Completion(FilePathCompleter.class)
    protected String outFile;

    protected abstract X509CRL retrieveCrl() throws CaClientException, PkiErrorException;

    @Override
    protected Object doExecute() throws Exception {
        Set<String> caNames = caClient.getCaNames();
        if (isEmpty(caNames)) {
            throw new CmdFailure("no CA is configured");
        }

        if (caName != null && !caNames.contains(caName)) {
            throw new IllegalCmdParamException("CA " + caName
                    + " is not within the configured CAs " + caNames);
        }

        if (caName == null) {
            if (caNames.size() == 1) {
                caName = caNames.iterator().next();
            } else {
                throw new IllegalCmdParamException("no CA is specified, one of " + caNames
                        + " is required");
            }
        }

        X509CRL crl = null;
        try {
            crl = retrieveCrl();
        } catch (PkiErrorException ex) {
            throw new CmdFailure("received no CRL from server: " + ex.getMessage());
        }

        if (crl == null) {
            throw new CmdFailure("received no CRL from server");
        }

        saveVerbose("saved CRL to file", new File(outFile), crl.getEncoded());
        return null;
    } // method doExecute

}
