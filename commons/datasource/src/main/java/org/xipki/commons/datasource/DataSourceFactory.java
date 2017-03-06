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

package org.xipki.commons.datasource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.datasource.springframework.dao.DataAccessException;
import org.xipki.commons.password.PasswordResolver;
import org.xipki.commons.password.PasswordResolverException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class DataSourceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceFactory.class);

    public DataSourceWrapper createDataSourceForFile(final String name, final String confFile,
            final PasswordResolver passwordResolver)
            throws DataAccessException, PasswordResolverException, IOException {
        ParamUtil.requireNonNull("confFile", confFile);
        FileInputStream fileIn = new FileInputStream(expandFilepath(confFile));
        return createDataSource(name, fileIn, passwordResolver);
    }

    public DataSourceWrapper createDataSource(final String name, final InputStream conf,
            final PasswordResolver passwordResolver)
            throws DataAccessException, PasswordResolverException, IOException {
        ParamUtil.requireNonNull("conf", conf);
        Properties config = new Properties();
        try {
            config.load(conf);
        } finally {
            try {
                conf.close();
            } catch (Exception ex) {
                LOG.error("could not close stream: {}", ex.getMessage());
            }
        }

        return createDataSource(name, config, passwordResolver);
    } // method createDataSource

    public DataSourceWrapper createDataSource(final String name, final Properties conf,
            final PasswordResolver passwordResolver)
            throws DataAccessException, PasswordResolverException {
        ParamUtil.requireNonNull("conf", conf);
        DatabaseType databaseType;
        String className = conf.getProperty("dataSourceClassName");
        if (className != null) {
            databaseType = DatabaseType.forDataSourceClass(className);
        } else {
            className = conf.getProperty("driverClassName");
            databaseType = DatabaseType.forDriver(className);
        }

        String password = conf.getProperty("password");
        if (password != null) {
            if (passwordResolver != null) {
                password = new String(passwordResolver.resolvePassword(password));
            }
            conf.setProperty("password", password);
        }

        password = conf.getProperty("dataSource.password");
        if (password != null) {
            if (passwordResolver != null) {
                password = new String(passwordResolver.resolvePassword(password));
            }
            conf.setProperty("dataSource.password", password);
        }

        Set<Object> keySet = new HashSet<>(conf.keySet());
        for (Object key : keySet) {
            if (((String) key).startsWith("liquibase")) {
                conf.remove(key);
            }
        }

        return DataSourceWrapper.createDataSource(name, conf, databaseType);
    } // method createDataSource

    private static String expandFilepath(final String path) {
        return path.startsWith("~" + File.separator)
                ? System.getProperty("user.home") + path.substring(1) : path;
    }

}
