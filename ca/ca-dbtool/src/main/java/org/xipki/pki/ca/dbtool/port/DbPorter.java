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

package org.xipki.pki.ca.dbtool.port;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.bind.JAXBException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.bouncycastle.util.encoders.Base64;
import org.xipki.commons.common.util.IoUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.datasource.DataSourceWrapper;
import org.xipki.commons.datasource.springframework.dao.DataAccessException;
import org.xipki.pki.ca.dbtool.DbSchemaInfo;
import org.xipki.pki.ca.dbtool.DbToolBase;
import org.xipki.pki.ca.dbtool.jaxb.ca.FileOrBinaryType;
import org.xipki.pki.ca.dbtool.jaxb.ca.FileOrValueType;
import org.xml.sax.SAXException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class DbPorter extends DbToolBase {

    enum OcspDbEntryType {
        CERT("certs", "CERT", 1);

        private final String dirName;

        private final String tableName;

        private final float sqlBatchFactor;

        private OcspDbEntryType(final String dirName, final String tableName,
                final float sqlBatchFactor) {
            this.dirName = dirName;
            this.tableName = tableName;
            this.sqlBatchFactor = sqlBatchFactor;
        }

        public String getDirName() {
            return dirName;
        }

        public String getTableName() {
            return tableName;
        }

        public float getSqlBatchFactor() {
            return sqlBatchFactor;
        }

    }

    enum CaDbEntryType {
        CERT("certs", "CERT", 1),
        CRL("crls", "CRL", 0.1f),
        USER("users", "USERNAME", 10),
        REQUEST("requests", "REQUEST", 0.1f),
        REQCERT("reqcerts", "REQCERT", 50);

        private final String dirName;

        private final String tableName;

        private final float sqlBatchFactor;

        private CaDbEntryType(final String dirName, final String tableName,
                final float sqlBatchFactor) {
            this.dirName = dirName;
            this.tableName = tableName;
            this.sqlBatchFactor = sqlBatchFactor;
        }

        public String getDirName() {
            return dirName;
        }

        public String getTableName() {
            return tableName;
        }

        public float getSqlBatchFactor() {
            return sqlBatchFactor;
        }

    }

    public static final String FILENAME_CA_CONFIGURATION = "ca-configuration.xml";

    public static final String FILENAME_CA_CERTSTORE = "ca-certstore.xml";

    public static final String FILENAME_OCSP_CERTSTORE = "ocsp-certstore.xml";

    public static final String DIRNAME_CRL = "crl";

    public static final String DIRNAME_CERT = "cert";

    public static final String PREFIX_FILENAME_CERTS = "certs-";

    public static final String EXPORT_PROCESS_LOG_FILENAME = "export.process";

    public static final String IMPORT_PROCESS_LOG_FILENAME = "import.process";

    public static final String IMPORT_TO_OCSP_PROCESS_LOG_FILENAME = "import-to-ocsp.process";

    public static final int VERSION = 1;

    protected final boolean evaulateOnly;

    protected final int dbSchemaVersion;

    protected final int maxX500nameLen;

    public DbPorter(final DataSourceWrapper datasource, final String baseDir,
            final AtomicBoolean stopMe, final boolean evaluateOnly) throws DataAccessException {
        super(datasource, baseDir, stopMe);

        this.evaulateOnly = evaluateOnly;

        DbSchemaInfo dbSchemaInfo = new DbSchemaInfo(datasource);
        String str = dbSchemaInfo.getVariableValue("VERSION");
        this.dbSchemaVersion = Integer.parseInt(str);
        str = dbSchemaInfo.getVariableValue("X500NAME_MAXLEN");
        this.maxX500nameLen = Integer.parseInt(str);
    }

    protected FileOrValueType buildFileOrValue(final String content, final String fileName)
    throws IOException {
        if (content == null) {
            return null;
        }

        ParamUtil.requireNonNull("fileName", fileName);

        FileOrValueType ret = new FileOrValueType();
        if (content.length() < 256) {
            ret.setValue(content);
            return ret;
        }

        File file = new File(baseDir, fileName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        IoUtil.save(file, content.getBytes("UTF-8"));

        ret.setFile(fileName);
        return ret;
    }

    protected String getValue(final FileOrValueType fileOrValue) throws IOException {
        if (fileOrValue == null) {
            return null;
        }

        if (fileOrValue.getValue() != null) {
            return fileOrValue.getValue();
        }

        File file = new File(baseDir, fileOrValue.getFile());
        return new String(IoUtil.read(file), "UTF-8");
    }

    protected FileOrBinaryType buildFileOrBase64Binary(final String base64Content,
            final String fileName) throws IOException {
        if (base64Content == null) {
            return null;
        }
        return buildFileOrBinary(Base64.decode(base64Content), fileName);
    }

    protected FileOrBinaryType buildFileOrBinary(final byte[] content, final String fileName)
    throws IOException {
        if (content == null) {
            return null;
        }

        ParamUtil.requireNonNull("fileName", fileName);

        FileOrBinaryType ret = new FileOrBinaryType();
        if (content.length < 256) {
            ret.setBinary(content);
            return ret;
        }

        File file = new File(baseDir, fileName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        IoUtil.save(file, content);

        ret.setFile(fileName);
        return ret;
    }

    protected byte[] getBinary(final FileOrBinaryType fileOrValue) throws IOException {
        if (fileOrValue == null) {
            return null;
        }

        if (fileOrValue.getBinary() != null) {
            return fileOrValue.getBinary();
        }

        File file = new File(baseDir, fileOrValue.getFile());
        return IoUtil.read(file);
    }

    protected String getImportingText() {
        return evaulateOnly ? "evaluating import " : "importing ";
    }

    protected String getImportedText() {
        return evaulateOnly ? " evaluated import " : " imported ";
    }

    protected String getExportingText() {
        return evaulateOnly ? "evaluating export " : "exporting ";
    }

    protected String getExportedText() {
        return evaulateOnly ? " evaluated export " : " exported ";
    }

    public static final Schema retrieveSchema(final String schemaPath) throws JAXBException {
        ParamUtil.requireNonNull("schemaPath", schemaPath);

        URL schemaUrl = DbPorter.class.getResource(schemaPath);
        final SchemaFactory schemaFact = SchemaFactory.newInstance(
                javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            return schemaFact.newSchema(schemaUrl);
        } catch (SAXException ex) {
            throw new JAXBException("could not load schemas for the specified classes\nDetails:\n"
                    + ex.getMessage());
        }
    }

    public static void echoToFile(final String content, final File file) throws IOException {
        ParamUtil.requireNonNull("content", content);
        ParamUtil.requireNonNull("file", file);

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(content.getBytes());
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }

}
