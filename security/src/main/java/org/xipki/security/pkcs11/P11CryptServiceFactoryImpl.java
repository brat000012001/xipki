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

package org.xipki.security.pkcs11;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.common.InvalidConfException;
import org.xipki.common.util.LogUtil;
import org.xipki.common.util.StringUtil;
import org.xipki.password.PasswordResolver;
import org.xipki.security.exception.P11TokenException;
import org.xipki.security.exception.XiSecurityException;
import org.xipki.security.pkcs11.emulator.EmulatorP11Module;
import org.xipki.security.pkcs11.iaik.IaikP11Module;
import org.xipki.security.pkcs11.proxy.ProxyP11Module;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class P11CryptServiceFactoryImpl implements P11CryptServiceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(P11CryptServiceFactoryImpl.class);

    private static final Map<String, P11CryptService> services = new HashMap<>();

    private static final Map<String, P11Module> modules = new HashMap<>();

    private PasswordResolver passwordResolver;

    private P11Conf p11Conf;

    private String pkcs11ConfFile;

    public synchronized void init() throws InvalidConfException, IOException {
        if (p11Conf != null) {
            return;
        }
        if (StringUtil.isBlank(pkcs11ConfFile)) {
            LOG.error("no pkcs11ConfFile is configured, could not initialize");
            return;
        }

        this.p11Conf = new P11Conf(new FileInputStream(pkcs11ConfFile), passwordResolver);
    }

    public synchronized P11CryptService getP11CryptService(final String moduleName)
            throws XiSecurityException, P11TokenException {
        if (p11Conf == null) {
            throw new IllegalStateException("please set pkcs11ConfFile and then call init() first");
        }

        final String name = getModuleName(moduleName);
        P11ModuleConf conf = p11Conf.getModuleConf(name);
        if (conf == null) {
            throw new XiSecurityException("PKCS#11 module " + name + " is not defined");
        }

        P11CryptService instance = services.get(moduleName);
        if (instance != null) {
            return instance;
        }

        String nativeLib = conf.getNativeLibrary();
        P11Module p11Module = modules.get(nativeLib);
        if (p11Module == null) {
            if (StringUtil.startsWithIgnoreCase(nativeLib, ProxyP11Module.PREFIX)) {
                p11Module = ProxyP11Module.getInstance(conf);
            } else if (StringUtil.startsWithIgnoreCase(nativeLib, EmulatorP11Module.PREFIX)) {
                p11Module = EmulatorP11Module.getInstance(conf);
            } else {
                p11Module = IaikP11Module.getInstance(conf);
            }
        }

        modules.put(nativeLib, p11Module);
        instance = new P11CryptService(p11Module);
        services.put(moduleName, instance);

        return instance;
    }

    private String getModuleName(final String moduleName) {
        return (moduleName == null) ? DEFAULT_P11MODULE_NAME : moduleName;
    }

    public void setPkcs11ConfFile(final String confFile) {
        this.pkcs11ConfFile = StringUtil.isBlank(confFile) ? null : confFile;
    }

    public void setPasswordResolver(final PasswordResolver passwordResolver) {
        this.passwordResolver = passwordResolver;
    }

    public void shutdown() {
        for (String pk11Lib : modules.keySet()) {
            try {
                modules.get(pk11Lib).close();
            } catch (Throwable th) {
                LogUtil.error(LOG, th, "could not close PKCS11 Module " + pk11Lib);
            }
        }
        modules.clear();
        services.clear();
    }

    @Override
    public Set<String> getModuleNames() {
        if (p11Conf == null) {
            throw new IllegalStateException("pkcs11ConfFile is not set");
        }
        return p11Conf.getModuleNames();
    }

}
