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

package org.xipki.commons.security.shell;

import java.io.File;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.xipki.commons.common.util.IoUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.common.util.StringUtil;
import org.xipki.commons.console.karaf.completer.FilePathCompleter;
import org.xipki.commons.password.OBFPasswordService;
import org.xipki.commons.password.PBEPasswordService;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

@Command(scope = "xipki-tk", name = "pbe-enc",
        description = "encrypt password with master password")
@Service
// CHECKSTYLE:SKIP
public class PBEEncryptCmd extends SecurityCommandSupport {

    @Option(name = "--iteration-count", aliases = "-n",
            description = "iteration count, between 1 and 65535")
    private int iterationCount = 2000;

    @Option(name = "--out", description = "where to save the encrypted password")
    @Completion(FilePathCompleter.class)
    private String outFile;

    @Option(name = "-k", description = "quorum of the password parts")
    private Integer quorum = 1;

    @Option(name = "--mpassword-file",
            description = "file containing the (obfuscated) master password")
    @Completion(FilePathCompleter.class)
    private String masterPasswordFile;

    @Option(name = "--mk", description = "quorum of the master password parts")
    private Integer mquorum = 1;

    @Override
    protected Object doExecute() throws Exception {
        ParamUtil.requireRange("iterationCount", iterationCount, 1, 65535);
        ParamUtil.requireRange("k", quorum, 1, 10);
        ParamUtil.requireRange("mk", mquorum, 1, 10);

        char[] masterPassword;
        if (masterPasswordFile != null) {
            String str = new String(IoUtil.read(masterPasswordFile));
            if (str.startsWith("OBF:") || str.startsWith("obf:")) {
                str = OBFPasswordService.deobfuscate(str);
            }
            masterPassword = str.toCharArray();
        } else {
            if (mquorum == 1) {
                masterPassword = readPassword("Master password");
            } else {
                char[][] parts = new char[mquorum][];
                for (int i = 0; i < mquorum; i++) {
                    parts[i] = readPassword("Master password (part " + (i + 1) + "/" + mquorum
                            + ")");
                }
                masterPassword = StringUtil.merge(parts);
            }
        }

        char[] password;
        if (quorum == 1) {
            password = readPassword("Password");
        } else {
            char[][] parts = new char[quorum][];
            for (int i = 0; i < quorum; i++) {
                parts[i] = readPassword("Password (part " + (i + 1) + "/" + quorum + ")");
            }
            password = StringUtil.merge(parts);
        }

        String passwordHint = PBEPasswordService.encryptPassword(iterationCount, masterPassword,
                password);
        if (outFile != null) {
            saveVerbose("saved the encrypted password to file", new File(outFile),
                    passwordHint.getBytes());
        } else {
            println("the encrypted password is: '" + passwordHint + "'");
        }
        return null;
    }

}
