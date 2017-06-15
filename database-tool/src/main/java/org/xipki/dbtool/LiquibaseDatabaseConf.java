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

package org.xipki.dbtool;

import java.util.Objects;
import java.util.Properties;

import org.xipki.password.PasswordResolver;
import org.xipki.password.PasswordResolverException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class LiquibaseDatabaseConf {

    private final String driver;

    private final String username;

    private final String password;

    private final String url;

    private final String schema;

    private LiquibaseDatabaseConf(final String driver, final String username, final String password,
            final String url, final String schema) {
        this.driver = Objects.requireNonNull(driver, "driver must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.password = password;
        this.url = Objects.requireNonNull(url, "url must not be null");
        this.schema = schema;
    }

    public String getDriver() {
        return driver;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getUrl() {
        return url;
    }

    public String getSchema() {
        return schema;
    }

    public static LiquibaseDatabaseConf getInstance(final Properties dbProps,
            final PasswordResolver passwordResolver) throws PasswordResolverException {
        Objects.requireNonNull(dbProps, "dbProps must no be null");

        String schema = dbProps.getProperty("liquibase.schema");
        if (schema != null) {
            schema = schema.trim();
            if (schema.isEmpty()) {
                schema = null;
            }
        }

        String datasourceClassName = dbProps.getProperty("dataSourceClassName");
        if (datasourceClassName == null) {
            throw new IllegalArgumentException("unsupported configuration");
        }

        StringBuilder urlBuilder = new StringBuilder();

        datasourceClassName = datasourceClassName.toLowerCase();
        String driverClassName;
        String url;

        if (datasourceClassName.contains("org.h2.")) {
            driverClassName = "org.h2.Driver";
            urlBuilder.append(dbProps.getProperty("dataSource.url"));
            if (schema != null) {
                urlBuilder.append(";INIT=CREATE SCHEMA IF NOT EXISTS ").append(schema);
            }
        } else if (datasourceClassName.contains("mysql.")) {
            driverClassName = "com.mysql.jdbc.Driver";
            urlBuilder.append("jdbc:mysql://");
            urlBuilder.append(dbProps.getProperty("dataSource.serverName"));
            urlBuilder.append(":");
            urlBuilder.append(dbProps.getProperty("dataSource.port"));
            urlBuilder.append("/");
            urlBuilder.append(dbProps.getProperty("dataSource.databaseName"));
        } else if (datasourceClassName.contains("mariadb.")) {
            driverClassName = "org.mariadb.jdbc.Driver";
            String str = dbProps.getProperty("dataSource.URL");
            if (isNotBlank(str)) {
                urlBuilder.append(str);
            } else {
                urlBuilder.append("jdbc:mariadb://");
                urlBuilder.append(dbProps.getProperty("dataSource.serverName"));
                urlBuilder.append(":");
                urlBuilder.append(dbProps.getProperty("dataSource.port"));
                urlBuilder.append("/");
                urlBuilder.append(dbProps.getProperty("dataSource.databaseName"));
            }
        } else if (datasourceClassName.contains("oracle.")) {
            driverClassName = "oracle.jdbc.driver.OracleDriver";
            String str = dbProps.getProperty("dataSource.URL");
            if (isNotBlank(str)) {
                urlBuilder.append(str);
            } else {
                urlBuilder.append("jdbc:oracle:thin:@");
                urlBuilder.append(dbProps.getProperty("dataSource.serverName"));
                urlBuilder.append(":");
                urlBuilder.append(dbProps.getProperty("dataSource.portNumber"));
                urlBuilder.append(":");
                urlBuilder.append(dbProps.getProperty("dataSource.databaseName"));
            }
        } else if (datasourceClassName.contains("com.ibm.db2.")) {
            driverClassName = "com.ibm.db2.jcc.DB2Driver";
            schema = dbProps.getProperty("dataSource.currentSchema");

            urlBuilder.append("jdbc:db2://");
            urlBuilder.append(dbProps.getProperty("dataSource.serverName"));
            urlBuilder.append(":");
            urlBuilder.append(dbProps.getProperty("dataSource.portNumber"));
            urlBuilder.append("/");
            urlBuilder.append(dbProps.getProperty("dataSource.databaseName"));
        } else if (datasourceClassName.contains("postgresql.")
                || datasourceClassName.contains("impossibl.postgres.")) {
            String serverName;
            String portNumber;
            String databaseName;
            if (datasourceClassName.contains("postgresql.")) {
                serverName = dbProps.getProperty("dataSource.serverName");
                portNumber = dbProps.getProperty("dataSource.portNumber");
                databaseName = dbProps.getProperty("dataSource.databaseName");
            } else {
                serverName = dbProps.getProperty("dataSource.host");
                portNumber = dbProps.getProperty("dataSource.port");
                databaseName = dbProps.getProperty("dataSource.database");
            }
            driverClassName = "org.postgresql.Driver";
            urlBuilder.append("jdbc:postgresql://");
            urlBuilder.append(serverName).append(":").append(portNumber).append("/")
                .append(databaseName);
        } else if (datasourceClassName.contains("hsqldb.")) {
            driverClassName = "org.hsqldb.jdbc.JDBCDriver";
            urlBuilder.append(dbProps.getProperty("dataSource.url"));
        } else {
            throw new IllegalArgumentException("unsupported database type " + datasourceClassName);
        }

        url = urlBuilder.toString();

        String user = dbProps.getProperty("dataSource.user");
        String password = dbProps.getProperty("dataSource.password");

        if (passwordResolver != null && (password != null && !password.isEmpty())) {
            password = new String(passwordResolver.resolvePassword(password));
        }

        return new LiquibaseDatabaseConf(driverClassName, user, password, url, schema);
    } // method getInstance

    public static boolean isNotBlank(final String str) {
        return str != null && !str.isEmpty();
    }

}
