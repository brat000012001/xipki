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

package org.xipki.pki.ca.client.shell.loadtest;

import java.io.FileInputStream;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Properties;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.bouncycastle.asn1.x509.Certificate;
import org.xipki.commons.common.util.FileBigIntegerIterator;
import org.xipki.commons.common.util.IoUtil;
import org.xipki.commons.console.karaf.IllegalCmdParamException;
import org.xipki.commons.console.karaf.completer.FilePathCompleter;
import org.xipki.commons.datasource.DataSourceFactory;
import org.xipki.commons.datasource.DataSourceWrapper;
import org.xipki.commons.security.SecurityFactory;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

@Command(scope = "xipki-cli", name = "loadtest-revoke",
        description = "CA client revoke load test")
@Service
public class CaLoadTestRevokeCmd extends CaLoadTestCommandSupport {

    @Option(name = "--issuer",
            required = true,
            description = "issuer certificate file\n"
                    + "(required)")
    @Completion(FilePathCompleter.class)
    private String issuerCertFile;

    @Option(name = "--duration",
            description = "maximal duration")
    private String duration = "30s";

    @Option(name = "--thread",
            description = "number of threads")
    private Integer numThreads = 5;

    @Option(name = "--ca-db",
            description = "CA database configuration file\n"
                    + "(exactly one of ca-db and serial-file must be specified)")
    @Completion(FilePathCompleter.class)
    private String caDbConfFile;

    @Option(name = "--hex",
            description = "serial number without prefix in the serial-file is hex number")
    private Boolean hex = Boolean.FALSE;

    @Option(name = "--serial-file",
            description = "file that contains serial numbers")
    @Completion(FilePathCompleter.class)
    private String serialNumberFile;

    @Option(name = "--max-num",
            description = "maximal number of certificates to be revoked\n"
                    + "0 for unlimited")
    private Integer maxCerts = 0;

    @Option(name = "-n",
            description = "number of certificates to be revoked in one request")
    private Integer num = 1;

    @Reference
    private SecurityFactory securityFactory;

    @Override
    protected Object doExecute() throws Exception {
        if (numThreads < 1) {
            throw new IllegalCmdParamException("invalid number of threads " + numThreads);
        }

        if (!(serialNumberFile == null ^ caDbConfFile == null)) {
            throw new IllegalCmdParamException(
                    "exactly one of ca-db and serial-file must be specified");
        }

        StringBuilder description = new StringBuilder(200);
        description.append("issuer: ").append(issuerCertFile).append("\n");
        description.append("cadb: ").append(caDbConfFile).append("\n");
        description.append("serialNumberFile: ").append(serialNumberFile).append("\n");
        description.append("maxCerts: ").append(maxCerts).append("\n");
        description.append("#certs/req: ").append(num).append("\n");
        description.append("unit: ").append(num).append(" certificate");
        if (num > 1) {
            description.append("s");
        }
        description.append("\n");

        Certificate caCert = Certificate.getInstance(IoUtil.read(issuerCertFile));
        Properties props = new Properties();
        props.load(new FileInputStream(IoUtil.expandFilepath(caDbConfFile)));
        props.setProperty("autoCommit", "false");
        props.setProperty("readOnly", "true");
        props.setProperty("maximumPoolSize", "1");
        props.setProperty("minimumIdle", "1");

        DataSourceWrapper caDataSource = null;
        Iterator<BigInteger> serialNumberIterator;
        if (caDbConfFile != null) {
            caDataSource = new DataSourceFactory().createDataSource(
                    "ds-" + caDbConfFile, props, securityFactory.getPasswordResolver());
            serialNumberIterator = new DbGoodCertSerialIterator(caCert, caDataSource);
        } else {
            serialNumberIterator = new FileBigIntegerIterator(serialNumberFile, hex, false);
        }

        try {
            CaLoadTestRevoke loadTest = new CaLoadTestRevoke(
                    caClient, caCert, serialNumberIterator, maxCerts, num, description.toString());

            loadTest.setDuration(duration);
            loadTest.setThreads(numThreads);
            loadTest.test();
        } finally {
            if (caDataSource != null) {
                caDataSource.shutdown();
            }

            if (serialNumberIterator instanceof FileBigIntegerIterator) {
                ((FileBigIntegerIterator) serialNumberIterator).close();
            }
        }

        return null;
    } // method doExecute

}
