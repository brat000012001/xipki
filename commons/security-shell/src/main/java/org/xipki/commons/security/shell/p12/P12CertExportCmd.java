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

package org.xipki.commons.security.shell.p12;

import java.io.File;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.xipki.commons.console.karaf.CmdFailure;
import org.xipki.commons.console.karaf.completer.FilePathCompleter;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

@Command(scope = "xipki-tk", name = "export-cert-p12",
        description = "export certificate from PKCS#12 keystore")
@Service
public class P12CertExportCmd extends P12SecurityCommandSupport {

    @Option(name = "--out", aliases = "-o",
            required = true,
            description = "where to save the certificate\n"
                    + "(required)")
    @Completion(FilePathCompleter.class)
    private String outFile;

    @Override
    protected Object doExecute() throws Exception {
        KeyStore ks = getKeyStore();

        String keyname = null;
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (ks.isKeyEntry(alias)) {
                keyname = alias;
                break;
            }
        }

        if (keyname == null) {
            throw new CmdFailure("could not find private key");
        }

        X509Certificate cert = (X509Certificate) ks.getCertificate(keyname);
        saveVerbose("saved certificate to file", new File(outFile), cert.getEncoded());

        return null;
    }

}
