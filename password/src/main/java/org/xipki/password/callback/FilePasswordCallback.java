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

package org.xipki.password.callback;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.common.ConfPairs;
import org.xipki.common.util.ParamUtil;
import org.xipki.common.util.StringUtil;
import org.xipki.password.OBFPasswordService;
import org.xipki.password.PasswordResolverException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class FilePasswordCallback implements PasswordCallback {

    private static final Logger LOG = LoggerFactory.getLogger(FilePasswordCallback.class);

    private String passwordFile;

    @Override
    public char[] getPassword(final String prompt, final String testToken)
            throws PasswordResolverException {
        if (passwordFile == null) {
            throw new PasswordResolverException("please initialize me first");
        }

        String passwordHint = null;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(expandFilepath(passwordFile)));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (StringUtil.isNotBlank(line) && !line.startsWith("#")) {
                    passwordHint = line;
                    break;
                }
            }
        } catch (IOException ex) {
            throw new PasswordResolverException("could not read file " + passwordFile, ex);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ex) {
                    LOG.error("could not close reader: {}", ex.getMessage());
                }
            }
        }

        if (passwordHint == null) {
            throw new PasswordResolverException("no password is specified in file " + passwordFile);
        }

        if (StringUtil.startsWithIgnoreCase(passwordHint, OBFPasswordService.OBFUSCATE)) {
            return OBFPasswordService.deobfuscate(passwordHint).toCharArray();
        } else {
            return passwordHint.toCharArray();
        }
    } // method getPassword

    @Override
    public void init(final String conf) throws PasswordResolverException {
        ParamUtil.requireNonBlank("conf", conf);
        ConfPairs pairs = new ConfPairs(conf);
        passwordFile = pairs.getValue("file");
        if (StringUtil.isBlank(passwordFile)) {
            throw new PasswordResolverException("invalid configuration " + conf
                    + ", no file is specified");
        }
        passwordFile = expandFilepath(passwordFile);
    }

    private static String expandFilepath(final String path) {
        return (path.startsWith("~" + File.separator))
                ? System.getProperty("user.home") + path.substring(1)
                : path;
    }

}
