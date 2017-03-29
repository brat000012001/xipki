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

package org.xipki.pki.ca.server.impl.store;

import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.common.LruCache;
import org.xipki.commons.common.util.CollectionUtil;
import org.xipki.commons.common.util.LogUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.common.util.StringUtil;
import org.xipki.commons.datasource.DataSourceWrapper;
import org.xipki.commons.datasource.springframework.dao.DataAccessException;
import org.xipki.commons.security.CertRevocationInfo;
import org.xipki.commons.security.CrlReason;
import org.xipki.commons.security.FpIdCalculator;
import org.xipki.commons.security.HashAlgoType;
import org.xipki.commons.security.ObjectIdentifiers;
import org.xipki.commons.security.X509Cert;
import org.xipki.commons.security.util.X509Util;
import org.xipki.pki.ca.api.NameId;
import org.xipki.pki.ca.api.OperationException;
import org.xipki.pki.ca.api.OperationException.ErrorCode;
import org.xipki.pki.ca.api.RequestType;
import org.xipki.pki.ca.api.X509CertWithDbId;
import org.xipki.pki.ca.api.publisher.x509.X509CertificateInfo;
import org.xipki.pki.ca.server.impl.CaIdNameMap;
import org.xipki.pki.ca.server.impl.CertRevInfoWithSerial;
import org.xipki.pki.ca.server.impl.CertStatus;
import org.xipki.pki.ca.server.impl.DbSchemaInfo;
import org.xipki.pki.ca.server.impl.KnowCertResult;
import org.xipki.pki.ca.server.impl.SerialWithId;
import org.xipki.pki.ca.server.impl.UniqueIdGenerator;
import org.xipki.pki.ca.server.impl.util.CaUtil;
import org.xipki.pki.ca.server.impl.util.PasswordHash;
import org.xipki.pki.ca.server.mgmt.api.CaHasUserEntry;
import org.xipki.pki.ca.server.mgmt.api.CertArt;
import org.xipki.pki.ca.server.mgmt.api.CertListInfo;
import org.xipki.pki.ca.server.mgmt.api.CertListOrderBy;
import org.xipki.pki.ca.server.mgmt.api.Permission;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

class CertStoreQueryExecutor {

    // CHECKSTYLE:SKIP
    private static class SQLs {
        private static final String SQL_ADD_CERT =
                "INSERT INTO CERT (ID,ART,LUPDATE,SN,SUBJECT,FP_S,FP_RS,NBEFORE,NAFTER,REV,PID,"
                + "CA_ID,RID,UID,FP_K,EE,RTYPE,TID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        private static final String SQL_ADD_CRAW =
                "INSERT INTO CRAW (CID,SHA1,REQ_SUBJECT,CERT) VALUES (?,?,?,?)";

        private static final String SQL_REVOKE_CERT =
                "UPDATE CERT SET LUPDATE=?,REV=?,RT=?,RIT=?,RR=? WHERE ID=?";

        private static final String SQL_REVOKE_SUSPENDED_CERT =
                "UPDATE CERT SET LUPDATE=?,RR=? WHERE ID=?";

        private static final String SQL_INSERT_PUBLISHQUEUE =
                "INSERT INTO PUBLISHQUEUE (PID,CA_ID,CID) VALUES (?,?,?)";

        private static final String SQL_REMOVE_PUBLISHQUEUE =
                "DELETE FROM PUBLISHQUEUE WHERE PID=? AND CID=?";

        private static final String SQL_MAXID_DELTACRL_CACHE =
                "SELECT MAX(ID) FROM DELTACRL_CACHE WHERE CA_ID=?";

        private static final String CLEAR_DELTACRL_CACHE =
                "DELETE FROM DELTACRL_CACHE WHERE ID<? AND CA_ID=?";

        private static final String SQL_MAX_CRLNO =
                "SELECT MAX(CRL_NO) FROM CRL WHERE CA_ID=?";

        private static final String SQL_MAX_THISUPDAATE_CRL =
                "SELECT MAX(THISUPDATE) FROM CRL WHERE CA_ID=?";

        private static final String SQL_ADD_CRL =
                "INSERT INTO CRL (ID,CA_ID,CRL_NO,THISUPDATE,NEXTUPDATE,DELTACRL,BASECRL_NO,CRL)"
                + " VALUES (?,?,?,?,?,?,?,?)";

        private static final String SQL_ADD_DELTACRL_CACHE =
                "INSERT INTO DELTACRL_CACHE (ID,CA_ID,SN) VALUES (?,?,?)";

        private static final String SQL_REMOVE_CERT =
                "DELETE FROM CERT WHERE CA_ID=? AND SN=?";

        private static final String CORESQL_CERT_FOR_ID =
            "PID,RID,REV,RR,RT,RIT,CERT FROM CERT INNER JOIN CRAW ON CERT.ID=?"
            + " AND CRAW.CID=CERT.ID";

        private static final String CORESQL_RAWCERT_FOR_ID =
                "CERT FROM CRAW WHERE CID=?";

        private static final String CORESQL_CERT_WITH_REVINFO =
                "ID,REV,RR,RT,RIT,PID,CERT FROM CERT INNER JOIN CRAW ON CERT.CA_ID=?"
                + " AND CERT.SN=? AND CRAW.CID=CERT.ID";

        private static final String CORESQL_CERTINFO =
                "PID,RID,REV,RR,RT,RIT,CERT FROM CERT INNER JOIN CRAW ON CERT.CA_ID=? AND CERT.SN=?"
                + " AND CRAW.CID=CERT.ID";

        private static final String CORESQL_CERT_FOR_SUBJECT_ISSUED =
                "ID FROM CERT WHERE CA_ID=? AND FP_S=?";

        private static final String CORESQL_CERT_FOR_KEY_ISSUED =
                "ID FROM CERT WHERE CA_ID=? AND FP_K=?";

        private static final String SQL_DELETE_UNREFERENCED_REQUEST =
                "DELETE FROM REQUEST WHERE ID NOT IN (SELECT req.RID FROM REQCERT req)";

        private static final String SQL_ADD_REQUEST =
                "INSERT INTO REQUEST (ID,LUPDATE,DATA) VALUES(?,?,?)";

        private static final String SQL_ADD_REQCERT =
                "INSERT INTO REQCERT (ID,RID,CID) VALUES(?,?,?)";

        private final String sqlCaHasCrl;

        private final String sqlContainsCertificates;

        private final String sqlCertForId;

        private final String sqlRawCertForId;

        private final String sqlCertWithRevInfo;

        private final String sqlCertInfo;

        private final String sqlCertprofileForCertId;

        private final String sqlCertprofileForSerial;

        private final String sqlActiveUserInfoForName;

        private final String sqlActiveUserNameForId;

        private final String sqlCaHasUser;

        private final String sqlKnowsCertForSerial;

        private final String sqlRevForId;

        private final String sqlCertStatusForSubjectFp;

        private final String sqlCertforSubjectIssued;

        private final String sqlCertForKeyIssued;

        private final String sqlLatestSerialForSubjectLike;

        private final String sqlLatestSerialForCertprofileAndSubjectLike;

        private final String sqlCrl;

        private final String sqlCrlWithNo;

        private final String sqlReqIdForSerial;

        private final String sqlReqForId;

        private final DataSourceWrapper datasource;

        private final LruCache<Integer, String> cacheSqlCidFromPublishQueue = new LruCache<>(5);

        private final LruCache<Integer, String> cacheSqlExpiredSerials = new LruCache<>(5);

        private final LruCache<Integer, String> cacheSqlSuspendedSerials = new LruCache<>(5);

        private final LruCache<Integer, String> cacheSqlDeltaCrlCacheIds = new LruCache<>(5);

        private final LruCache<Integer, String> cacheSqlRevokedCerts = new LruCache<>(5);

        private final LruCache<Integer, String> cacheSqlRevokedCertsWithEe = new LruCache<>(5);

        private final LruCache<Integer, String> cacheSqlSerials = new LruCache<>(5);

        private final LruCache<Integer, String> cacheSqlSerialsRevoked = new LruCache<>(5);

        SQLs(final DataSourceWrapper datasource) {
            this.datasource = ParamUtil.requireNonNull("datasource", datasource);

            this.sqlCaHasCrl = datasource.buildSelectFirstSql("ID FROM CRL WHERE CA_ID=?", 1);
            this.sqlContainsCertificates = datasource.buildSelectFirstSql(
                    "ID FROM CERT WHERE CA_ID=? AND EE=?", 1);
            this.sqlCertForId = datasource.buildSelectFirstSql(CORESQL_CERT_FOR_ID, 1);
            this.sqlRawCertForId = datasource.buildSelectFirstSql(CORESQL_RAWCERT_FOR_ID, 1);
            this.sqlCertWithRevInfo = datasource.buildSelectFirstSql(CORESQL_CERT_WITH_REVINFO, 1);
            this.sqlCertInfo = datasource.buildSelectFirstSql(CORESQL_CERTINFO, 1);
            this.sqlCertprofileForCertId = datasource.buildSelectFirstSql(
                    "PID FROM CERT WHERE ID=? AND CA_ID=?", 1);
            this.sqlCertprofileForSerial = datasource.buildSelectFirstSql(
                    "PID FROM CERT WHERE SN=? AND CA_ID=?", 1);
            this.sqlActiveUserInfoForName = datasource.buildSelectFirstSql(
                    "ID,PASSWORD FROM USERNAME WHERE NAME=? AND ACTIVE=1", 1);
            this.sqlActiveUserNameForId = datasource.buildSelectFirstSql(
                    "NAME FROM USERNAME WHERE ID=? AND ACTIVE=1", 1);
            this.sqlCaHasUser = datasource.buildSelectFirstSql(
                    "PERMISSIONS,PROFILES FROM CA_HAS_USER WHERE CA_ID=? AND USER_ID=?", 1);
            this.sqlKnowsCertForSerial = datasource.buildSelectFirstSql(
                    "UID FROM CERT WHERE SN=? AND CA_ID=?", 1);
            this.sqlRevForId = datasource.buildSelectFirstSql(
                    "SN,EE,REV,RR,RT,RIT FROM CERT WHERE ID=?", 1);
            this.sqlCertStatusForSubjectFp = datasource.buildSelectFirstSql(
                    "REV FROM CERT WHERE FP_S=? AND CA_ID=?", 1);
            this.sqlCertforSubjectIssued = datasource.buildSelectFirstSql(
                    CORESQL_CERT_FOR_SUBJECT_ISSUED, 1);
            this.sqlCertForKeyIssued = datasource.buildSelectFirstSql(CORESQL_CERT_FOR_KEY_ISSUED,
                    1);
            this.sqlLatestSerialForSubjectLike = datasource.buildSelectFirstSql(
                    "SUBJECT FROM CERT WHERE SUBJECT LIKE ?", 1, "NBEFORE DESC");
            this.sqlLatestSerialForCertprofileAndSubjectLike = datasource.buildSelectFirstSql(
                    "NBEFORE FROM CERT WHERE PID=? AND SUBJECT LIKE ?", 1, "NBEFORE ASC");
            this.sqlCrl = datasource.buildSelectFirstSql(
                    "THISUPDATE,CRL FROM CRL WHERE CA_ID=?", 1, "THISUPDATE DESC");
            this.sqlCrlWithNo = datasource.buildSelectFirstSql(
                    "THISUPDATE,CRL FROM CRL WHERE CA_ID=? AND CRL_NO=?", 1, "THISUPDATE DESC");
            this.sqlReqIdForSerial = datasource.buildSelectFirstSql(
                    "REQCERT.RID as REQ_ID FROM REQCERT INNER JOIN CERT ON CERT.CA_ID=? "
                    + "AND CERT.SN=? AND REQCERT.CID=CERT.ID", 1);
            this.sqlReqForId = datasource.buildSelectFirstSql(
                    "DATA FROM REQUEST WHERE ID=?", 1);
        } // constructor

        String getSqlCidFromPublishQueue(final int numEntries) {
            String sql = cacheSqlCidFromPublishQueue.get(numEntries);
            if (sql == null) {
                sql = datasource.buildSelectFirstSql(
                        "CID FROM PUBLISHQUEUE WHERE PID=? AND CA_ID=?", numEntries, "CID ASC");
                cacheSqlCidFromPublishQueue.put(numEntries, sql);
            }
            return sql;
        }

        String getSqlExpiredSerials(final int numEntries) {
            String sql = cacheSqlExpiredSerials.get(numEntries);
            if (sql == null) {
                sql = datasource.buildSelectFirstSql(
                        "SN FROM CERT WHERE CA_ID=? AND NAFTER<?", numEntries);
                cacheSqlExpiredSerials.put(numEntries, sql);
            }
            return sql;
        }

        String getSqlSuspendedSerials(final int numEntries) {
            String sql = cacheSqlSuspendedSerials.get(numEntries);
            if (sql == null) {
                sql = datasource.buildSelectFirstSql(
                        "SN FROM CERT WHERE CA_ID=? AND LUPDATE<? AND RR=?", numEntries);
                cacheSqlSuspendedSerials.put(numEntries, sql);
            }
            return sql;
        }

        String getSqlDeltaCrlCacheIds(final int numEntries) {
            String sql = cacheSqlDeltaCrlCacheIds.get(numEntries);
            if (sql == null) {
                sql = datasource.buildSelectFirstSql(
                        "ID FROM DELTACRL_CACHE WHERE ID>? AND CA_ID=?", numEntries, "ID ASC");
                cacheSqlDeltaCrlCacheIds.put(numEntries, sql);
            }
            return sql;
        }

        String getSqlRevokedCerts(final int numEntries, final boolean withEe) {
            LruCache<Integer, String> cache = withEe ? cacheSqlRevokedCertsWithEe
                    : cacheSqlRevokedCerts;
            String sql = cache.get(numEntries);
            if (sql == null) {
                String coreSql =
                        "ID,SN,RR,RT,RIT FROM CERT WHERE ID>? AND CA_ID=? AND REV=1 AND NAFTER>?";
                if (withEe) {
                    coreSql += " AND EE=?";
                }
                sql = datasource.buildSelectFirstSql(coreSql, numEntries, "ID ASC");
                cache.put(numEntries, sql);
            }
            return sql;
        }

        String getSqlSerials(final int numEntries, final boolean onlyRevoked) {
            LruCache<Integer, String> cache = onlyRevoked ? cacheSqlSerialsRevoked :
                cacheSqlSerials;
            String sql = cache.get(numEntries);
            if (sql == null) {
                String coreSql = "ID,SN FROM CERT WHERE ID>? AND CA_ID=?";
                if (onlyRevoked) {
                    coreSql += "AND REV=1";
                }
                sql = datasource.buildSelectFirstSql(coreSql, numEntries, "ID ASC");
                cache.put(numEntries, sql);
            }
            return sql;
        }

        String getSqlSerials(final int numEntries, final Date notExpiredAt,
                final boolean onlyRevoked, final boolean withEe) {
            StringBuilder sb = new StringBuilder("ID,SN FROM CERT WHERE ID>? AND CS=?");
            if (notExpiredAt != null) {
                sb.append(" AND NAFTER>?");
            }
            if (onlyRevoked) {
                sb.append(" AND REV=1");
            }
            if (withEe) {
                sb.append(" AND EE=?");
            }
            return datasource.buildSelectFirstSql(sb.toString(), numEntries, "ID ASC");
        }

    }

    private static final Logger LOG = LoggerFactory.getLogger(CertStoreQueryExecutor.class);

    private final DataSourceWrapper datasource;

    @SuppressWarnings("unused")
    private final float dbSchemaVersion;

    private final int maxX500nameLen;

    private final UniqueIdGenerator idGenerator;

    private final SQLs sqls;

    CertStoreQueryExecutor(final DataSourceWrapper datasource, final UniqueIdGenerator idGenerator)
            throws DataAccessException {
        this.datasource = ParamUtil.requireNonNull("datasource", datasource);
        this.idGenerator = ParamUtil.requireNonNull("idGenerator", idGenerator);

        DbSchemaInfo dbSchemaInfo = new DbSchemaInfo(datasource);
        String str = dbSchemaInfo.getVariableValue("VERSION");
        this.dbSchemaVersion = Float.parseFloat(str);
        str = dbSchemaInfo.getVariableValue("X500NAME_MAXLEN");
        this.maxX500nameLen = Integer.parseInt(str);

        this.sqls = new SQLs(datasource);
    } // constructor

    void addCert(final NameId ca, final X509CertWithDbId certificate,
            final byte[] encodedSubjectPublicKey, final NameId certProfile,
            final NameId requestor, final Integer userId, final RequestType reqType,
            final byte[] transactionId, final X500Name reqSubject)
            throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireNonNull("certificate", certificate);
        ParamUtil.requireNonNull("certProfile", certProfile);
        ParamUtil.requireNonNull("requestor", requestor);

        long certId = idGenerator.nextId();
        X509Certificate cert = certificate.getCert();

        long fpPk = FpIdCalculator.hash(encodedSubjectPublicKey);
        String subjectText = X509Util.cutText(certificate.getSubject(), maxX500nameLen);
        long fpSubject = X509Util.fpCanonicalizedName(cert.getSubjectX500Principal());

        String reqSubjectText = null;
        Long fpReqSubject = null;
        if (reqSubject != null) {
            fpReqSubject = X509Util.fpCanonicalizedName(reqSubject);
            if (fpSubject == fpReqSubject) {
                fpReqSubject = null;
            } else {
                reqSubjectText = X509Util.cutX500Name(CaUtil.sortX509Name(reqSubject),
                        maxX500nameLen);
            }
        }

        String b64FpCert = base64Fp(certificate.getEncodedCert());
        String b64Cert = Base64.toBase64String(certificate.getEncodedCert());
        String tid = (transactionId == null) ? null : Base64.toBase64String(transactionId);

        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        BigInteger serialNumber = cert.getSerialNumber();
        long notBeforeSeconds = cert.getNotBefore().getTime() / 1000;
        long notAfterSeconds = cert.getNotAfter().getTime() / 1000;

        Connection conn = null;
        PreparedStatement[] pss = borrowPreparedStatements(SQLs.SQL_ADD_CERT, SQLs.SQL_ADD_CRAW);

        try {
            PreparedStatement psAddcert = pss[0];
            // all statements have the same connection
            conn = psAddcert.getConnection();

            // cert
            int idx = 2;
            psAddcert.setInt(idx++, CertArt.X509PKC.getCode());
            psAddcert.setLong(idx++, currentTimeSeconds);
            psAddcert.setString(idx++, serialNumber.toString(16));
            psAddcert.setString(idx++, subjectText);
            psAddcert.setLong(idx++, fpSubject);
            setLong(psAddcert, idx++, fpReqSubject);
            psAddcert.setLong(idx++, notBeforeSeconds);
            psAddcert.setLong(idx++, notAfterSeconds);
            setBoolean(psAddcert, idx++, false);
            psAddcert.setInt(idx++, certProfile.getId());
            psAddcert.setInt(idx++, ca.getId());
            setInt(psAddcert, idx++, requestor.getId());
            setInt(psAddcert, idx++, userId);
            psAddcert.setLong(idx++, fpPk);
            boolean isEeCert = cert.getBasicConstraints() == -1;
            psAddcert.setInt(idx++, isEeCert ? 1 : 0);
            psAddcert.setInt(idx++, reqType.getCode());
            psAddcert.setString(idx++, tid);

            // rawcert
            PreparedStatement psAddRawcert = pss[1];

            idx = 2;
            psAddRawcert.setString(idx++, b64FpCert);
            psAddRawcert.setString(idx++, reqSubjectText);
            psAddRawcert.setString(idx++, b64Cert);

            certificate.setCertId(certId);

            psAddcert.setLong(1, certId);
            psAddRawcert.setLong(1, certId);

            final boolean origAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            String sql = null;
            try {
                sql = SQLs.SQL_ADD_CERT;
                psAddcert.executeUpdate();

                sql = SQLs.SQL_ADD_CRAW;
                psAddRawcert.executeUpdate();

                sql = "(commit add cert to CA certstore)";
                conn.commit();
            } catch (Throwable th) {
                conn.rollback();
                // more secure
                datasource.deleteFromTable(null, "CRAW", "CID", certId);
                datasource.deleteFromTable(null, "CERT", "ID", certId);

                if (th instanceof SQLException) {
                    LOG.error("datasource {} could not add certificate with id {}: {}",
                        datasource.getDatasourceName(), certId, th.getMessage());
                    throw datasource.translate(sql, (SQLException) th);
                } else {
                    throw new OperationException(ErrorCode.SYSTEM_FAILURE, th);
                }
            } finally {
                conn.setAutoCommit(origAutoCommit);
            }
        } catch (SQLException ex) {
            throw datasource.translate(null, ex);
        } finally {
            try {
                for (PreparedStatement ps : pss) {
                    releaseStatement(ps);
                }
            } finally {
                if (conn != null) {
                    datasource.returnConnection(conn);
                }
            }
        }
    } // method addCert

    void addToPublishQueue(final NameId publisher, final long certId,
            final NameId ca)
            throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);

        final String sql = SQLs.SQL_INSERT_PUBLISHQUEUE;
        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            int idx = 1;
            ps.setInt(idx++, publisher.getId());
            ps.setInt(idx++, ca.getId());
            ps.setLong(idx++, certId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, null);
        }
    }

    void removeFromPublishQueue(final NameId publisher, final long certId)
            throws DataAccessException {
        final String sql = SQLs.SQL_REMOVE_PUBLISHQUEUE;
        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            int idx = 1;
            ps.setInt(idx++, publisher.getId());
            ps.setLong(idx++, certId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, null);
        }
    }

    long getMaxIdOfDeltaCrlCache(final NameId ca)
            throws OperationException, DataAccessException {
        ParamUtil.requireNonNull("ca", ca);

        final String sql = SQLs.SQL_MAXID_DELTACRL_CACHE;
        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            ps.setInt(1, ca.getId());
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return 0;
            }
            return rs.getLong(1);
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, null);
        }
    }

    public void clearDeltaCrlCache(final NameId ca, final long maxId)
            throws OperationException, DataAccessException {
        final String sql = SQLs.CLEAR_DELTACRL_CACHE;
        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            ps.setLong(1, maxId + 1);
            ps.setInt(2, ca.getId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, null);
        }
    }

    void clearPublishQueue(final NameId ca, final NameId publisher)
            throws OperationException, DataAccessException {
        StringBuilder sqlBuilder = new StringBuilder(80);
        sqlBuilder.append("DELETE FROM PUBLISHQUEUE");
        if (ca != null || publisher != null) {
            sqlBuilder.append(" WHERE");
            if (ca != null) {
                sqlBuilder.append(" CA_ID=?");
                if (publisher != null) {
                    sqlBuilder.append(" AND");
                }
            }
            if (publisher != null) {
                sqlBuilder.append(" PID=?");
            }
        }

        String sql = sqlBuilder.toString();
        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            int idx = 1;
            if (ca != null) {
                ps.setInt(idx++, ca.getId());
            }

            if (publisher != null) {
                ps.setInt(idx++, publisher.getId());
            }
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, null);
        }
    }

    long getMaxCrlNumber(final NameId ca) throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);

        final String sql = SQLs.SQL_MAX_CRLNO;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            ps.setInt(1, ca.getId());
            rs = ps.executeQuery();
            if (!rs.next()) {
                return 0;
            }
            long maxCrlNumber = rs.getLong(1);
            return (maxCrlNumber < 0) ? 0 : maxCrlNumber;
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    }

    Long getThisUpdateOfCurrentCrl(final NameId ca)
            throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);

        final String sql = SQLs.SQL_MAX_THISUPDAATE_CRL;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            ps.setInt(1, ca.getId());
            rs = ps.executeQuery();
            if (!rs.next()) {
                return 0L;
            }
            return rs.getLong(1);
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    }

    boolean hasCrl(final NameId ca) throws DataAccessException {
        ParamUtil.requireNonNull("ca", ca);

        final String sql = sqls.sqlCaHasCrl;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = borrowPreparedStatement(sql);
            ps.setInt(1, ca.getId());
            rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    }

    void addCrl(final NameId ca, final X509CRL crl)
            throws DataAccessException, CRLException, OperationException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireNonNull("crl", crl);

        byte[] encodedExtnValue = crl.getExtensionValue(Extension.cRLNumber.getId());
        Long crlNumber = null;
        if (encodedExtnValue != null) {
            byte[] extnValue = DEROctetString.getInstance(encodedExtnValue).getOctets();
            crlNumber = ASN1Integer.getInstance(extnValue).getPositiveValue().longValue();
        }

        encodedExtnValue = crl.getExtensionValue(Extension.deltaCRLIndicator.getId());
        Long baseCrlNumber = null;
        if (encodedExtnValue != null) {
            byte[] extnValue = DEROctetString.getInstance(encodedExtnValue).getOctets();
            baseCrlNumber = ASN1Integer.getInstance(extnValue).getPositiveValue().longValue();
        }

        final String sql = SQLs.SQL_ADD_CRL;
        long currentMaxCrlId = datasource.getMax(null, "CRL", "ID");
        long crlId = currentMaxCrlId + 1;

        String b64Crl = Base64.toBase64String(crl.getEncoded());

        PreparedStatement ps = null;

        try {
            ps = borrowPreparedStatement(sql);

            int idx = 1;
            ps.setLong(idx++, crlId);
            ps.setInt(idx++, ca.getId());
            setLong(ps, idx++, crlNumber);
            Date date = crl.getThisUpdate();
            ps.setLong(idx++, date.getTime() / 1000);
            setDateSeconds(ps, idx++, crl.getNextUpdate());
            setBoolean(ps, idx++, (baseCrlNumber != null));
            setLong(ps, idx++, baseCrlNumber);
            ps.setString(idx++, b64Crl);

            ps.executeUpdate();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, null);
        }
    } // method addCrl

    X509CertWithRevocationInfo revokeCert(final NameId ca, final BigInteger serialNumber,
            final CertRevocationInfo revInfo, final boolean force,
            final boolean publishToDeltaCrlCache, final CaIdNameMap idNameMap)
            throws OperationException, DataAccessException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireNonNull("serialNumber", serialNumber);
        ParamUtil.requireNonNull("revInfo", revInfo);

        X509CertWithRevocationInfo certWithRevInfo
                = getCertWithRevocationInfo(ca, serialNumber, idNameMap);
        if (certWithRevInfo == null) {
            LOG.warn("certificate with CA={} and serialNumber={} does not exist",
                    ca.getName(), LogUtil.formatCsn(serialNumber));
            return null;
        }

        CertRevocationInfo currentRevInfo = certWithRevInfo.getRevInfo();
        if (currentRevInfo != null) {
            CrlReason currentReason = currentRevInfo.getReason();
            if (currentReason == CrlReason.CERTIFICATE_HOLD) {
                if (revInfo.getReason() == CrlReason.CERTIFICATE_HOLD) {
                    throw new OperationException(ErrorCode.CERT_REVOKED,
                            "certificate already revoked with the requested reason "
                            + currentReason.getDescription());
                } else {
                    revInfo.setRevocationTime(currentRevInfo.getRevocationTime());
                    revInfo.setInvalidityTime(currentRevInfo.getInvalidityTime());
                }
            } else if (!force) {
                throw new OperationException(ErrorCode.CERT_REVOKED,
                    "certificate already revoked with reason " + currentReason.getDescription());
            }
        }

        long certId = certWithRevInfo.getCert().getCertId().longValue();
        long revTimeSeconds = revInfo.getRevocationTime().getTime() / 1000;
        Long invTimeSeconds = null;
        if (revInfo.getInvalidityTime() != null) {
            invTimeSeconds = revInfo.getInvalidityTime().getTime() / 1000;
        }

        PreparedStatement ps = borrowPreparedStatement(SQLs.SQL_REVOKE_CERT);
        try {
            int idx = 1;
            ps.setLong(idx++, System.currentTimeMillis() / 1000);
            setBoolean(ps, idx++, true);
            ps.setLong(idx++, revTimeSeconds);
            setLong(ps, idx++, invTimeSeconds);
            ps.setInt(idx++, revInfo.getReason().getCode());
            ps.setLong(idx++, certId);

            int count = ps.executeUpdate();
            if (count != 1) {
                String message = (count > 1)
                        ? count + " rows modified, but exactly one is expected"
                        : "no row is modified, but exactly one is expected";
                throw new OperationException(ErrorCode.SYSTEM_FAILURE, message);
            }
        } catch (SQLException ex) {
            throw datasource.translate(SQLs.SQL_REVOKE_CERT, ex);
        } finally {
            releaseDbResources(ps, null);
        }

        if (publishToDeltaCrlCache) {
            publishToDeltaCrlCache(ca, certWithRevInfo.getCert().getCert().getSerialNumber());
        }

        certWithRevInfo.setRevInfo(revInfo);
        return certWithRevInfo;
    } // method revokeCert

    X509CertWithRevocationInfo revokeSuspendedCert(final NameId ca,
            final BigInteger serialNumber, final CrlReason reason,
            final boolean publishToDeltaCrlCache, final CaIdNameMap idNameMap)
            throws OperationException, DataAccessException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireNonNull("serialNumber", serialNumber);
        ParamUtil.requireNonNull("reason", reason);

        X509CertWithRevocationInfo certWithRevInfo =
                getCertWithRevocationInfo(ca, serialNumber, idNameMap);
        if (certWithRevInfo == null) {
            LOG.warn("certificate with CA={} and serialNumber={} does not exist",
                    ca.getName(), LogUtil.formatCsn(serialNumber));
            return null;
        }

        CertRevocationInfo currentRevInfo = certWithRevInfo.getRevInfo();
        if (currentRevInfo == null) {
            throw new OperationException(ErrorCode.CERT_UNREVOKED, "certificate is not revoked");
        }

        CrlReason currentReason = currentRevInfo.getReason();
        if (currentReason != CrlReason.CERTIFICATE_HOLD) {
            throw new OperationException(ErrorCode.CERT_REVOKED,
                    "certificate is revoked but not with reason "
                    + CrlReason.CERTIFICATE_HOLD.getDescription());
        }

        long certId = certWithRevInfo.getCert().getCertId().longValue();

        PreparedStatement ps = borrowPreparedStatement(SQLs.SQL_REVOKE_SUSPENDED_CERT);
        try {
            int idx = 1;
            ps.setLong(idx++, System.currentTimeMillis() / 1000);
            ps.setInt(idx++, reason.getCode());
            ps.setLong(idx++, certId);

            int count = ps.executeUpdate();
            if (count != 1) {
                String message = (count > 1)
                        ? count + " rows modified, but exactly one is expected"
                        : "no row is modified, but exactly one is expected";
                throw new OperationException(ErrorCode.SYSTEM_FAILURE, message);
            }
        } catch (SQLException ex) {
            throw datasource.translate(SQLs.SQL_REVOKE_CERT, ex);
        } finally {
            releaseDbResources(ps, null);
        }

        if (publishToDeltaCrlCache) {
            publishToDeltaCrlCache(ca, certWithRevInfo.getCert().getCert().getSerialNumber());
        }

        currentRevInfo.setReason(reason);
        return certWithRevInfo;
    } // method revokeSuspendedCert

    X509CertWithDbId unrevokeCert(final NameId ca, final BigInteger serialNumber,
            final boolean force, final boolean publishToDeltaCrlCache, final CaIdNameMap idNamMap)
            throws OperationException, DataAccessException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireNonNull("serialNumber", serialNumber);

        X509CertWithRevocationInfo certWithRevInfo =
                getCertWithRevocationInfo(ca, serialNumber, idNamMap);
        if (certWithRevInfo == null) {
            LOG.warn("certificate with CA={} and serialNumber={} does not exist",
                    ca.getName(), LogUtil.formatCsn(serialNumber));
            return null;
        }

        CertRevocationInfo currentRevInfo = certWithRevInfo.getRevInfo();
        if (currentRevInfo == null) {
            throw new OperationException(ErrorCode.CERT_UNREVOKED, "certificate is not revoked");
        }

        CrlReason currentReason = currentRevInfo.getReason();
        if (!force) {
            if (currentReason != CrlReason.CERTIFICATE_HOLD) {
                throw new OperationException(ErrorCode.NOT_PERMITTED,
                        "could not unrevoke certificate revoked with reason "
                        + currentReason.getDescription());
            }
        }

        final String sql = "UPDATE CERT SET LUPDATE=?,REV=?,RT=?,RIT=?,RR=? WHERE ID=?";
        long certId = certWithRevInfo.getCert().getCertId().longValue();
        long currentTimeSeconds = System.currentTimeMillis() / 1000;

        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            int idx = 1;
            ps.setLong(idx++, currentTimeSeconds);
            setBoolean(ps, idx++, false);
            ps.setNull(idx++, Types.INTEGER);
            ps.setNull(idx++, Types.INTEGER);
            ps.setNull(idx++, Types.INTEGER);
            ps.setLong(idx++, certId);

            int count = ps.executeUpdate();
            if (count != 1) {
                String message = (count > 1)
                        ? count + " rows modified, but exactly one is expected"
                        : "no row is modified, but exactly one is expected";
                throw new OperationException(ErrorCode.SYSTEM_FAILURE, message);
            }
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, null);
        }

        if (publishToDeltaCrlCache) {
            publishToDeltaCrlCache(ca, certWithRevInfo.getCert().getCert().getSerialNumber());
        }

        return certWithRevInfo.getCert();
    } // method unrevokeCert

    private void publishToDeltaCrlCache(final NameId ca, final BigInteger serialNumber)
            throws DataAccessException {
        ParamUtil.requireNonNull("serialNumber", serialNumber);

        final String sql = SQLs.SQL_ADD_DELTACRL_CACHE;
        PreparedStatement ps = null;
        try {
            long id = idGenerator.nextId();
            ps = borrowPreparedStatement(sql);
            int idx = 1;
            ps.setLong(idx++, id);
            ps.setInt(idx++, ca.getId());
            ps.setString(idx++, serialNumber.toString(16));
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, null);
        }
    }

    X509CertWithDbId getCert(final NameId ca, final BigInteger serialNumber,
            final CaIdNameMap idNameMap)
            throws OperationException, DataAccessException {
        X509CertWithRevocationInfo crtWithRevInfo
                = getCertWithRevocationInfo(ca, serialNumber, idNameMap);
        return (crtWithRevInfo == null) ? null : crtWithRevInfo.getCert();
    }

    void removeCertificate(final NameId ca, final BigInteger serialNumber)
            throws OperationException, DataAccessException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireNonNull("serialNumber", serialNumber);

        final String sql = SQLs.SQL_REMOVE_CERT;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setInt(idx++, ca.getId());
            ps.setString(idx++, serialNumber.toString(16));

            int count = ps.executeUpdate();
            if (count != 1) {
                String message = (count > 1)
                        ? count + " rows modified, but exactly one is expected"
                        : "no row is modified, but exactly one is expected";
                throw new OperationException(ErrorCode.SYSTEM_FAILURE, message);
            }
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, null);
        }
    } // method removeCertificate

    List<Long> getPublishQueueEntries(final NameId ca, final NameId publisher,
            final int numEntries)
            throws DataAccessException, OperationException {
        final String sql = sqls.getSqlCidFromPublishQueue(numEntries);
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setInt(idx++, publisher.getId());
            ps.setInt(idx++, ca.getId());
            rs = ps.executeQuery();
            List<Long> ret = new ArrayList<>();
            while (rs.next() && ret.size() < numEntries) {
                long certId = rs.getLong("CID");
                if (!ret.contains(certId)) {
                    ret.add(certId);
                }
            }
            return ret;
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    } // method getPublishQueueEntries

    boolean containsCertificates(final NameId ca, final boolean ee)
            throws DataAccessException, OperationException {
        final String sql = sqls.sqlContainsCertificates;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            int idx = 1;
            ps.setInt(idx++, ca.getId());
            ps.setInt(idx++, ee ? 1 : 0);
            rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    } // method containsCertificates

    long getCountOfCerts(final NameId ca, final boolean onlyRevoked)
            throws DataAccessException, OperationException {
        final String sql;
        if (onlyRevoked) {
            sql = "SELECT COUNT(*) FROM CERT WHERE CA_ID=? AND REV=1";
        } else {
            sql = "SELECT COUNT(*) FROM CERT WHERE CA_ID=?";
        }

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            ps.setInt(1, ca.getId());
            rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    }

    List<SerialWithId> getSerialNumbers(final NameId ca,  final long startId,
            final int numEntries, final boolean onlyRevoked)
            throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireMin("numEntries", numEntries, 1);

        final String sql = sqls.getSqlSerials(numEntries, onlyRevoked);

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setLong(idx++, startId - 1);
            ps.setInt(idx++, ca.getId());
            rs = ps.executeQuery();
            List<SerialWithId> ret = new ArrayList<>();
            while (rs.next() && ret.size() < numEntries) {
                long id = rs.getLong("ID");
                String serial = rs.getString("SN");
                ret.add(new SerialWithId(id, new BigInteger(serial, 16)));
            }
            return ret;
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    } // method getSerialNumbers

    List<SerialWithId> getSerialNumbers(final NameId ca, final Date notExpiredAt,
            final long startId, final int numEntries, final boolean onlyRevoked,
            final boolean onlyCaCerts, final boolean onlyUserCerts)
            throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireMin("numEntries", numEntries, 1);

        if (onlyCaCerts && onlyUserCerts) {
            throw new IllegalArgumentException(
                    "onlyCaCerts and onlyUserCerts cannot be both of true");
        }
        boolean withEe = onlyCaCerts || onlyUserCerts;
        final String sql = sqls.getSqlSerials(numEntries, notExpiredAt, onlyRevoked, withEe);

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setLong(idx++, startId - 1);
            ps.setInt(idx++, ca.getId());
            if (notExpiredAt != null) {
                ps.setLong(idx++, notExpiredAt.getTime() / 1000 + 1);
            }
            if (withEe) {
                setBoolean(ps, idx++, onlyUserCerts);
            }
            rs = ps.executeQuery();
            List<SerialWithId> ret = new ArrayList<>();
            while (rs.next() && ret.size() < numEntries) {
                long id = rs.getLong("ID");
                String serial = rs.getString("SN");
                ret.add(new SerialWithId(id, new BigInteger(serial, 16)));
            }
            return ret;
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    } // method getSerialNumbers

    List<BigInteger> getExpiredSerialNumbers(final NameId ca, final long expiredAt,
            final int numEntries) throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireMin("numEntries", numEntries, 1);

        final String sql = sqls.getSqlExpiredSerials(numEntries);

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setInt(idx++, ca.getId());
            ps.setLong(idx++, expiredAt);
            rs = ps.executeQuery();
            List<BigInteger> ret = new ArrayList<>();
            while (rs.next() && ret.size() < numEntries) {
                String serial = rs.getString("SN");
                ret.add(new BigInteger(serial, 16));
            }
            return ret;
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    } // method getExpiredSerialNumbers

    List<BigInteger> getSuspendedCertSerials(final NameId ca, final long latestLastUpdate,
            final int numEntries) throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireMin("numEntries", numEntries, 1);

        final String sql = sqls.getSqlSuspendedSerials(numEntries);
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setInt(idx++, ca.getId());
            ps.setLong(idx++, latestLastUpdate + 1);
            ps.setInt(idx++, CrlReason.CERTIFICATE_HOLD.getCode());
            rs = ps.executeQuery();
            List<BigInteger> ret = new ArrayList<>();
            while (rs.next() && ret.size() < numEntries) {
                String str = rs.getString("SN");
                ret.add(new BigInteger(str, 16));
            }
            return ret;
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    } // method getSuspendedCertIds

    byte[] getEncodedCrl(final NameId ca, final BigInteger crlNumber)
            throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);

        String sql = (crlNumber == null) ? sqls.sqlCrl : sqls.sqlCrlWithNo;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        String b64Crl = null;
        try {
            int idx = 1;
            ps.setInt(idx++, ca.getId());
            if (crlNumber != null) {
                ps.setLong(idx++, crlNumber.longValue());
            }
            rs = ps.executeQuery();
            long currentThisUpdate = 0;
            // iterate all entries to make sure that the latest CRL will be returned
            while (rs.next()) {
                long thisUpdate = rs.getLong("THISUPDATE");
                if (thisUpdate >= currentThisUpdate) {
                    b64Crl = rs.getString("CRL");
                    currentThisUpdate = thisUpdate;
                }
            }
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }

        if (b64Crl == null) {
            return null;
        }

        return Base64.decode(b64Crl);
    } // method getEncodedCrl

    int cleanupCrls(final NameId ca, final int numCrls)
            throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireMin("numCrls", numCrls, 1);

        String sql = "SELECT CRL_NO FROM CRL WHERE CA_ID=? AND DELTACRL=?";
        PreparedStatement ps = borrowPreparedStatement(sql);
        List<Integer> crlNumbers = new LinkedList<>();
        ResultSet rs = null;
        try {
            int idx = 1;
            ps.setInt(idx++, ca.getId());
            setBoolean(ps, idx++, false);
            rs = ps.executeQuery();

            while (rs.next()) {
                int crlNumber = rs.getInt("CRL_NO");
                crlNumbers.add(crlNumber);
            }
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }

        int size = crlNumbers.size();
        Collections.sort(crlNumbers);

        int numCrlsToDelete = size - numCrls;
        if (numCrlsToDelete < 1) {
            return 0;
        }

        int crlNumber = crlNumbers.get(numCrlsToDelete - 1);
        sql = "DELETE FROM CRL WHERE CA_ID=? AND CRL_NO<?";
        ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setInt(idx++, ca.getId());
            ps.setInt(idx++, crlNumber + 1);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, null);
        }

        return numCrlsToDelete;
    } // method cleanupCrls

    X509CertificateInfo getCertForId(final NameId ca, final X509Cert caCert,
            final long certId, final CaIdNameMap idNameMap)
            throws DataAccessException, OperationException, CertificateException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireNonNull("caCert", caCert);
        ParamUtil.requireNonNull("idNameMap", idNameMap);

        final String sql = sqls.sqlCertForId;

        String b64Cert;
        int certprofileId;
        int requestorId;
        boolean revoked;
        int revReason = 0;
        long revTime = 0;
        long revInvTime = 0;

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            ps.setLong(1, certId);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            b64Cert = rs.getString("CERT");
            certprofileId = rs.getInt("PID");
            requestorId = rs.getInt("RID");
            revoked = rs.getBoolean("REV");
            if (revoked) {
                revReason = rs.getInt("RR");
                revTime = rs.getLong("RT");
                revInvTime = rs.getLong("RIT");
            }
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }

        byte[] encodedCert = Base64.decode(b64Cert);
        X509Certificate cert = X509Util.parseCert(encodedCert);
        X509CertWithDbId certWithMeta = new X509CertWithDbId(cert, encodedCert);
        certWithMeta.setCertId(certId);
        X509CertificateInfo certInfo = new X509CertificateInfo(certWithMeta,
                ca, caCert, cert.getPublicKey().getEncoded(),
                idNameMap.getCertprofile(certprofileId),
                idNameMap.getRequestor(requestorId));
        if (!revoked) {
            return certInfo;
        }
        Date invalidityTime = (revInvTime == 0 || revInvTime == revTime) ? null
                : new Date(revInvTime * 1000);
        CertRevocationInfo revInfo = new CertRevocationInfo(revReason,
                new Date(revTime * 1000), invalidityTime);
        certInfo.setRevocationInfo(revInfo);
        return certInfo;
    } // method getCertForId

    X509CertWithDbId getCertForId(final long certId)
            throws DataAccessException, OperationException {
        final String sql = sqls.sqlRawCertForId;

        String b64Cert;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            ps.setLong(1, certId);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            b64Cert = rs.getString("CERT");
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }

        if (b64Cert == null) {
            return null;
        }
        byte[] encodedCert = Base64.decode(b64Cert);
        X509Certificate cert;
        try {
            cert = X509Util.parseCert(encodedCert);
        } catch (CertificateException ex) {
            throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
        }
        return new X509CertWithDbId(cert, encodedCert);
    } // method getCertForId

    X509CertWithRevocationInfo getCertWithRevocationInfo(final NameId ca,
            final BigInteger serial, final CaIdNameMap idNameMap)
            throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireNonNull("serial", serial);
        ParamUtil.requireNonNull("idNameMap", idNameMap);

        final String sql = sqls.sqlCertWithRevInfo;

        long certId;
        String b64Cert;
        boolean revoked;
        int revReason = 0;
        long revTime = 0;
        long revInvTime = 0;
        int certprofileId = 0;

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setInt(idx++, ca.getId());
            ps.setString(idx++, serial.toString(16));
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            certId = rs.getLong("ID");
            b64Cert = rs.getString("CERT");
            certprofileId = rs.getInt("PID");

            revoked = rs.getBoolean("REV");
            if (revoked) {
                revReason = rs.getInt("RR");
                revTime = rs.getLong("RT");
                revInvTime = rs.getLong("RIT");
            }
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, null);
        }

        byte[] certBytes = Base64.decode(b64Cert);
        X509Certificate cert;
        try {
            cert = X509Util.parseCert(certBytes);
        } catch (CertificateException ex) {
            throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
        }

        CertRevocationInfo revInfo = null;
        if (revoked) {
            Date invalidityTime = (revInvTime == 0) ? null : new Date(1000 * revInvTime);
            revInfo = new CertRevocationInfo(revReason, new Date(1000 * revTime), invalidityTime);
        }

        X509CertWithDbId certWithMeta = new X509CertWithDbId(cert, certBytes);
        certWithMeta.setCertId(certId);

        String profileName = idNameMap.getCertprofileName(certprofileId);
        X509CertWithRevocationInfo ret = new X509CertWithRevocationInfo();
        ret.setCertprofile(profileName);
        ret.setCert(certWithMeta);
        ret.setRevInfo(revInfo);
        return ret;
    } // method getCertWithRevocationInfo

    X509CertificateInfo getCertificateInfo(final NameId ca, final X509Cert caCert,
            final BigInteger serial, final CaIdNameMap idNameMap)
            throws DataAccessException, OperationException, CertificateException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireNonNull("caCert", caCert);
        ParamUtil.requireNonNull("idNameMap", idNameMap);
        ParamUtil.requireNonNull("serial", serial);

        final String sql = sqls.sqlCertInfo;

        String b64Cert;
        boolean revoked;
        int revReason = 0;
        long revTime = 0;
        long revInvTime = 0;
        int certprofileId;
        int requestorId;

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setInt(idx++, ca.getId());
            ps.setString(idx++, serial.toString(16));
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            b64Cert = rs.getString("CERT");
            certprofileId = rs.getInt("PID");
            requestorId = rs.getInt("RID");
            revoked = rs.getBoolean("REV");
            if (revoked) {
                revReason = rs.getInt("RR");
                revTime = rs.getLong("RT");
                revInvTime = rs.getLong("RIT");
            }
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }

        try {
            byte[] encodedCert = Base64.decode(b64Cert);
            X509Certificate cert = X509Util.parseCert(encodedCert);

            X509CertWithDbId certWithMeta = new X509CertWithDbId(cert, encodedCert);

            byte[] subjectPublicKeyInfo = Certificate.getInstance(encodedCert)
                    .getTBSCertificate().getSubjectPublicKeyInfo().getEncoded();
            X509CertificateInfo certInfo = new X509CertificateInfo(certWithMeta, ca,
                    caCert, subjectPublicKeyInfo,
                    idNameMap.getCertprofile(certprofileId),
                    idNameMap.getRequestor(requestorId));

            if (!revoked) {
                return certInfo;
            }

            Date invalidityTime = (revInvTime == 0) ? null : new Date(revInvTime * 1000);
            CertRevocationInfo revInfo = new CertRevocationInfo(revReason,
                    new Date(revTime * 1000), invalidityTime);
            certInfo.setRevocationInfo(revInfo);
            return certInfo;
        } catch (IOException ex) {
            LOG.warn("getCertificateInfo()", ex);
            throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
        }
    } // method getCertificateInfo

    Integer getCertProfileForCertId(final NameId ca, final long cid)
            throws OperationException, DataAccessException {
        ParamUtil.requireNonNull("ca", ca);

        final String sql = sqls.sqlCertprofileForCertId;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            ps.setLong(1, cid);
            ps.setInt(2, ca.getId());
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }

            return rs.getInt("PID");
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    } // method getCertProfileForId

    Integer getCertProfileForSerial(final NameId ca, final BigInteger serial)
            throws OperationException, DataAccessException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireNonNull("serial", serial);

        final String sql = sqls.sqlCertprofileForSerial;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setString(idx++, serial.toString(16));
            ps.setInt(idx++, ca.getId());

            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }

            return rs.getInt("PID");
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    } // method getCertProfileForSerial

    /**
     *
     * @param subjectName Subject of Certificate or requested Subject.
     * @param transactionId will only be considered if there are more than one certificate
     *     matches the subject.
     */
    List<X509Certificate> getCertificate(final X500Name subjectName, final byte[] transactionId)
            throws DataAccessException, OperationException {
        final String sql = (transactionId != null)
                ? "SELECT ID FROM CERT WHERE TID=? AND (FP_S=? OR FP_RS=?)"
                : "SELECT ID FROM CERT WHERE FP_S=? OR FP_RS=?";

        long fpSubject = X509Util.fpCanonicalizedName(subjectName);
        List<Long> certIds = new LinkedList<Long>();

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            if (transactionId != null) {
                ps.setString(idx++, Base64.toBase64String(transactionId));
            }
            ps.setLong(idx++, fpSubject);
            ps.setLong(idx++, fpSubject);
            rs = ps.executeQuery();

            while (rs.next()) {
                long id = rs.getLong("ID");
                certIds.add(id);
            }
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }

        if (CollectionUtil.isEmpty(certIds)) {
            return Collections.emptyList();
        }

        List<X509Certificate> certs = new ArrayList<X509Certificate>(certIds.size());
        for (Long certId : certIds) {
            X509CertWithDbId cert = getCertForId(certId);
            if (cert != null) {
                certs.add(cert.getCert());
            }
        }

        return certs;
    } // method getCertificate

    byte[] getCertRequest(final NameId ca, final BigInteger serialNumber)
            throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireNonNull("serialNumber", serialNumber);

        String sql = sqls.sqlReqIdForSerial;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        Long reqId = null;
        try {
            ps.setInt(1, ca.getId());
            ps.setString(2, serialNumber.toString(16));
            rs = ps.executeQuery();

            if (rs.next()) {
                reqId = rs.getLong("REQ_ID");
            }
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }

        if (reqId == null) {
            return null;
        }

        String b64Req = null;
        sql = sqls.sqlReqForId;
        ps = borrowPreparedStatement(sql);
        try {
            ps.setLong(1, reqId);
            rs = ps.executeQuery();
            if (rs.next()) {
                b64Req = rs.getString("DATA");
            }
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }

        return (b64Req == null) ? null : Base64.decode(b64Req);
    }

    List<CertListInfo> listCertificates(final NameId ca, final X500Name subjectPattern,
            final Date validFrom, final Date validTo, final CertListOrderBy orderBy,
            final int numEntries) throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireMin("numEntries", numEntries, 1);

        StringBuilder sb = new StringBuilder(200);
        sb.append("SN,NBEFORE,NAFTER,SUBJECT FROM CERT WHERE CA_ID=?");
        //.append(caId)

        Integer idxNotBefore = null;
        Integer idxNotAfter = null;
        Integer idxSubject = null;

        int idx = 2;
        if (validFrom != null) {
            idxNotBefore = idx++;
            sb.append(" AND NBEFORE<?");
        }
        if (validTo != null) {
            idxNotAfter = idx++;
            sb.append(" AND NAFTER>?");
        }

        String subjectLike = null;
        if (subjectPattern != null) {
            idxSubject = idx++;
            sb.append(" AND SUBJECT LIKE ?");

            StringBuilder buffer = new StringBuilder(100);
            buffer.append("%");
            RDN[] rdns = subjectPattern.getRDNs();
            for (int i = 0; i < rdns.length; i++) {
                X500Name rdnName = new X500Name(new RDN[]{rdns[i]});
                String rdnStr = X509Util.getRfc4519Name(rdnName);
                if (rdnStr.indexOf('%') != -1) {
                    throw new OperationException(ErrorCode.BAD_REQUEST,
                            "the character '%' is not allowed in subjectPattern");
                }
                if (rdnStr.indexOf('*') != -1) {
                    rdnStr = rdnStr.replace('*', '%');
                }
                buffer.append(rdnStr);
                buffer.append("%");
            }
            subjectLike = buffer.toString();
        }

        String sortByStr = null;
        if (orderBy != null) {
            switch (orderBy) {
            case NOT_BEFORE:
                sortByStr = "NBEFORE";
                break;
            case NOT_BEFORE_DESC:
                sortByStr = "NBEFORE DESC";
                break;
            case NOT_AFTER:
                sortByStr = "NAFTER";
                break;
            case NOT_AFTER_DESC:
                sortByStr = "NAFTER DESC";
                break;
            case SUBJECT:
                sortByStr = "SUBJECT";
                break;
            case SUBJECT_DESC:
                sortByStr = "SUBJECT DESC";
                break;
            default:
                throw new RuntimeException("unknown CertListOrderBy " + orderBy);
            }
        }

        final String sql = datasource.buildSelectFirstSql(sb.toString(), numEntries, sortByStr);
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        List<CertListInfo> ret = new LinkedList<>();

        try {
            ps.setInt(1, ca.getId());

            if (idxNotBefore != null) {
                long time = validFrom.getTime() / 1000;
                ps.setLong(idxNotBefore, time - 1);
            }

            if (idxNotAfter != null) {
                long time = validTo.getTime() / 1000;
                ps.setLong(idxNotAfter, time);
            }

            if (idxSubject != null) {
                ps.setString(idxSubject, subjectLike);
            }

            rs = ps.executeQuery();
            while (rs.next()) {
                String snStr = rs.getString("SN");
                BigInteger sn = new BigInteger(snStr, 16);
                Date notBefore = new Date(rs.getLong("NBEFORE") * 1000);
                Date notAfter = new Date(rs.getLong("NAFTER") * 1000);
                String subject = rs.getString("SUBJECT");
                CertListInfo info = new CertListInfo(sn, subject, notBefore, notAfter);
                ret.add(info);
            }
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }

        return ret;
    }

    NameId authenticateUser(final String user, final byte[] password)
            throws DataAccessException, OperationException {
        final String sql = sqls.sqlActiveUserInfoForName;

        int id;
        String expPasswordText;

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            ps.setString(1, user);
            rs = ps.executeQuery();

            if (!rs.next()) {
                return null;
            }

            id = rs.getInt("ID");
            expPasswordText = rs.getString("PASSWORD");
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }

        if (StringUtil.isBlank(expPasswordText)) {
            return null;
        }

        boolean valid;
        try {
            valid = PasswordHash.validatePassword(password, expPasswordText);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
        }

        return valid ? new NameId(id, user) : null;
    } // method authenticateUser

    String getUsername(final int id)
            throws DataAccessException {
        final String sql = sqls.sqlActiveUserNameForId;

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            ps.setInt(1, id);
            rs = ps.executeQuery();

            if (!rs.next()) {
                return null;
            }

            return rs.getString("NAME");
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    } // method authenticateUser

    CaHasUserEntry getCaHasUser(final NameId ca, final NameId user)
            throws DataAccessException, OperationException {
        final String sql = sqls.sqlCaHasUser;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            ps.setInt(1, ca.getId());
            ps.setInt(2, user.getId());
            rs = ps.executeQuery();

            if (!rs.next()) {
                return null;
            }

            String str = rs.getString("PERMISSIONS");
            Set<Permission> permissions = CaUtil.getPermissions(str);
            str = rs.getString("PROFILES");
            List<String> list = StringUtil.split(str, ",");
            Set<String> profiles = (list == null) ? null : new HashSet<>(list);

            CaHasUserEntry entry = new CaHasUserEntry(user);
            entry.setPermissions(permissions);
            entry.setProfiles(profiles);
            return entry;
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    }

    KnowCertResult knowsCertForSerial(final NameId ca, final BigInteger serial)
            throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("serial", serial);
        final String sql = sqls.sqlKnowsCertForSerial;

        Integer userId = null;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            ps.setString(1, serial.toString(16));
            ps.setInt(2, ca.getId());
            rs = ps.executeQuery();

            if (!rs.next()) {
                return KnowCertResult.UNKNOWN;
            }

            userId = rs.getInt("UID");
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }

        return new KnowCertResult(true, userId);
    } // method knowsCertForSerial

    List<CertRevInfoWithSerial> getRevokedCertificates(final NameId ca,
            final Date notExpiredAt, final long startId, final int numEntries,
            final boolean onlyCaCerts, final boolean onlyUserCerts)
            throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireNonNull("notExpiredAt", notExpiredAt);
        ParamUtil.requireMin("numEntries", numEntries, 1);
        if (onlyCaCerts && onlyUserCerts) {
            throw new IllegalArgumentException(
                    "onlyCaCerts and onlyUserCerts cannot be both of true");
        }
        boolean withEe = onlyCaCerts || onlyUserCerts;

        String sql = sqls.getSqlRevokedCerts(numEntries, withEe);

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setLong(idx++, startId - 1);
            ps.setInt(idx++, ca.getId());
            ps.setLong(idx++, notExpiredAt.getTime() / 1000 + 1);
            if (withEe) {
                setBoolean(ps, idx++, onlyUserCerts);
            }
            rs = ps.executeQuery();

            List<CertRevInfoWithSerial> ret = new LinkedList<>();
            while (rs.next()) {
                long id = rs.getLong("ID");
                String serial = rs.getString("SN");
                int revReason = rs.getInt("RR");
                long revTime = rs.getLong("RT");
                long revInvalidityTime = rs.getLong("RIT");

                Date invalidityTime = (revInvalidityTime == 0) ? null
                        : new Date(1000 * revInvalidityTime);
                CertRevInfoWithSerial revInfo = new CertRevInfoWithSerial(id,
                        new BigInteger(serial, 16), revReason, new Date(1000 * revTime),
                        invalidityTime);
                ret.add(revInfo);
            }

            return ret;
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    } // method getRevokedCertificates

    List<CertRevInfoWithSerial> getCertificatesForDeltaCrl(final NameId ca,
            final long startId, final int numEntries, final boolean onlyCaCerts,
            final boolean onlyUserCerts)
            throws DataAccessException, OperationException {
        ParamUtil.requireNonNull("ca", ca);
        ParamUtil.requireMin("numEntries", numEntries, 1);

        String sql = sqls.getSqlDeltaCrlCacheIds(numEntries);
        List<Long> ids = new LinkedList<>();
        ResultSet rs = null;

        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            int idx = 1;
            ps.setLong(idx++, startId - 1);
            ps.setInt(idx++, ca.getId());
            rs = ps.executeQuery();
            while (rs.next()) {
                long id = rs.getLong("ID");
                ids.add(id);
            }
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }

        sql = sqls.sqlRevForId;
        ps = borrowPreparedStatement(sql);

        List<CertRevInfoWithSerial> ret = new ArrayList<>();
        for (Long id : ids) {
            try {
                ps.setLong(1, id);
                rs = ps.executeQuery();

                if (!rs.next()) {
                    continue;
                }

                int ee = rs.getInt("EE");
                if (onlyCaCerts) {
                    if (ee != 0) {
                        continue;
                    }
                } else if (onlyUserCerts) {
                    if (ee != 1) {
                        continue;
                    }
                }

                CertRevInfoWithSerial revInfo;

                String serial = rs.getString("SN");
                boolean revoked = rs.getBoolean("REVOEKD");
                if (revoked) {
                    int revReason = rs.getInt("RR");
                    long tmpRevTime = rs.getLong("RT");
                    long revInvalidityTime = rs.getLong("RIT");

                    Date invalidityTime = (revInvalidityTime == 0) ? null
                            : new Date(1000 * revInvalidityTime);
                    revInfo = new CertRevInfoWithSerial(id, new BigInteger(serial, 16), revReason,
                            new Date(1000 * tmpRevTime), invalidityTime);
                } else {
                    long lastUpdate = rs.getLong("LUPDATE");
                    revInfo = new CertRevInfoWithSerial(id, new BigInteger(serial, 16),
                            CrlReason.REMOVE_FROM_CRL.getCode(), new Date(1000 * lastUpdate), null);
                }
                ret.add(revInfo);
            } catch (SQLException ex) {
                throw datasource.translate(sql, ex);
            } finally {
                releaseDbResources(null, rs);
            }
        } // end for

        return ret;
    } // method getCertificatesForDeltaCrl

    CertStatus getCertStatusForSubject(final NameId ca, final X500Principal subject)
            throws DataAccessException {
        long subjectFp = X509Util.fpCanonicalizedName(subject);
        return getCertStatusForSubjectFp(ca, subjectFp);
    }

    CertStatus getCertStatusForSubject(final NameId ca, final X500Name subject)
            throws DataAccessException {
        long subjectFp = X509Util.fpCanonicalizedName(subject);
        return getCertStatusForSubjectFp(ca, subjectFp);
    }

    private CertStatus getCertStatusForSubjectFp(final NameId ca, final long subjectFp)
            throws DataAccessException {
        ParamUtil.requireNonNull("ca", ca);

        final String sql = sqls.sqlCertStatusForSubjectFp;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setLong(idx++, subjectFp);
            ps.setInt(idx++, ca.getId());
            rs = ps.executeQuery();
            if (!rs.next()) {
                return CertStatus.UNKNOWN;
            }
            return rs.getBoolean("REV") ? CertStatus.REVOKED : CertStatus.GOOD;
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    } // method getCertStatusForSubjectFp

    boolean isCertForSubjectIssued(final NameId ca, final long subjectFp)
            throws DataAccessException {
        ParamUtil.requireNonNull("ca", ca);
        String sql = sqls.sqlCertforSubjectIssued;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setInt(idx++, ca.getId());
            ps.setLong(idx++, subjectFp);
            rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    }

    boolean isCertForKeyIssued(final NameId ca, final long keyFp)
            throws DataAccessException {
        ParamUtil.requireNonNull("ca", ca);
        String sql = sqls.sqlCertForKeyIssued;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setInt(idx++, ca.getId());
            ps.setLong(idx++, keyFp);
            rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    }

    private String base64Fp(final byte[] data) {
        return HashAlgoType.SHA1.base64Hash(data);
    }

    private PreparedStatement[] borrowPreparedStatements(final String... sqlQueries)
            throws DataAccessException {
        Connection conn = datasource.getConnection();
        if (conn == null) {
            throw new DataAccessException("could not get connection");
        }

        final int n = sqlQueries.length;
        PreparedStatement[] pss = new PreparedStatement[n];
        for (int i = 0; i < n; i++) {
            pss[i] = datasource.prepareStatement(conn, sqlQueries[i]);
            if (pss[i] != null) {
                continue;
            }

            // destroy all already initialized statements
            for (int j = 0; j < i; j++) {
                try {
                    pss[j].close();
                } catch (Throwable th) {
                    LOG.warn("could not close preparedStatement", th);
                }
            }

            try {
                conn.close();
            } catch (Throwable th) {
                LOG.warn("could not close connection", th);
            }

            throw new DataAccessException(
                    "could not create prepared statement for " + sqlQueries[i]);
        }

        return pss;
    } // method borrowPreparedStatements

    private PreparedStatement borrowPreparedStatement(final String sqlQuery)
            throws DataAccessException {
        PreparedStatement ps = null;
        Connection conn = datasource.getConnection();
        if (conn != null) {
            ps = datasource.prepareStatement(conn, sqlQuery);
        }

        if (ps != null) {
            return ps;
        }

        throw new DataAccessException("could not create prepared statement for " + sqlQuery);
    } // method borrowPreparedStatement

    private void releaseDbResources(final Statement ps, final ResultSet rs) {
        datasource.releaseResources(ps, rs);
    }

    boolean isHealthy() {
        final String sql = "SELECT ID FROM CA";

        try {
            PreparedStatement ps = borrowPreparedStatement(sql);

            ResultSet rs = null;
            try {
                rs = ps.executeQuery();
            } finally {
                releaseDbResources(ps, rs);
            }
            return true;
        } catch (Exception ex) {
            LOG.error("isHealthy(). {}: {}", ex.getClass().getName(), ex.getMessage());
            LOG.debug("isHealthy()", ex);
            return false;
        }
    } // method isHealthy

    String getLatestSerialNumber(final X500Name nameWithSn) throws OperationException {
        RDN[] rdns1 = nameWithSn.getRDNs();
        RDN[] rdns2 = new RDN[rdns1.length];
        for (int i = 0; i < rdns1.length; i++) {
            RDN rdn = rdns1[i];
            rdns2[i] =  rdn.getFirst().getType().equals(ObjectIdentifiers.DN_SERIALNUMBER)
                    ? new RDN(ObjectIdentifiers.DN_SERIALNUMBER, new DERPrintableString("%")) : rdn;
        }

        String namePattern = X509Util.getRfc4519Name(new X500Name(rdns2));

        final String sql = sqls.sqlLatestSerialForSubjectLike;;
        ResultSet rs = null;
        PreparedStatement ps;
        try {
            ps = borrowPreparedStatement(sql);
        } catch (DataAccessException ex) {
            throw new OperationException(ErrorCode.DATABASE_FAILURE, ex.getMessage());
        }

        String subjectStr;

        try {
            ps.setString(1, namePattern);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }

            subjectStr = rs.getString("SUBJECT");
        } catch (SQLException ex) {
            throw new OperationException(ErrorCode.DATABASE_FAILURE, ex.getMessage());
        } finally {
            releaseDbResources(ps, rs);
        }

        X500Name lastName = new X500Name(subjectStr);
        RDN[] rdns = lastName.getRDNs(ObjectIdentifiers.DN_SERIALNUMBER);
        if (rdns == null || rdns.length == 0) {
            return null;
        }

        return X509Util.rdnValueToString(rdns[0].getFirst().getValue());
    } // method getLatestSerialNumber

    Long getNotBeforeOfFirstCertStartsWithCommonName(final String commonName,
            final NameId profile) throws DataAccessException {
        final String sql = sqls.sqlLatestSerialForCertprofileAndSubjectLike;

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try {
            int idx = 1;
            ps.setInt(idx++, profile.getId());
            ps.setString(idx++, "%cn=" + commonName + "%");

            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }

            long notBefore = rs.getLong("NBEFORE");
            return (notBefore == 0) ? null : notBefore;
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, rs);
        }
    } // method getNotBeforeOfFirstCertStartsWithCommonName

    void deleteUnreferencedRequests() throws DataAccessException {
        final String sql = SQLs.SQL_DELETE_UNREFERENCED_REQUEST;
        PreparedStatement ps = borrowPreparedStatement(sql);
        ResultSet rs = null;
        try {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            datasource.releaseResources(ps, rs);
        }
    }

    long addRequest(byte[] request) throws DataAccessException {
        ParamUtil.requireNonNull("request", request);

        long id = idGenerator.nextId();
        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        String b64Request = Base64.toBase64String(request);
        final String sql = SQLs.SQL_ADD_REQUEST;
        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            int index = 1;
            ps.setLong(index++, id);
            ps.setLong(index++, currentTimeSeconds);
            ps.setString(index++, b64Request);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, null);
        }

        return id;
    }

    void addRequestCert(long requestId, long certId) throws DataAccessException {
        final String sql = SQLs.SQL_ADD_REQCERT;
        long id = idGenerator.nextId();
        PreparedStatement ps = borrowPreparedStatement(sql);
        try {
            int index = 1;
            ps.setLong(index++, id);
            ps.setLong(index++, requestId);
            ps.setLong(index++, certId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw datasource.translate(sql, ex);
        } finally {
            releaseDbResources(ps, null);
        }
    }

    private static void releaseStatement(final Statement statment) {
        if (statment == null) {
            return;
        }
        try {
            statment.close();
        } catch (SQLException ex) {
            LOG.warn("could not close Statement", ex);
        }
    }

    private static void setBoolean(final PreparedStatement ps, final int index, final boolean value)
            throws SQLException {
        ps.setInt(index, value ? 1 : 0);
    }

    private static void setLong(final PreparedStatement ps, final int index, final Long value)
            throws SQLException {
        if (value != null) {
            ps.setLong(index, value.longValue());
        } else {
            ps.setNull(index, Types.BIGINT);
        }
    }

    private static void setInt(final PreparedStatement ps, final int index, final Integer value)
            throws SQLException {
        if (value != null) {
            ps.setInt(index, value.intValue());
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    private static void setDateSeconds(final PreparedStatement ps, final int index, final Date date)
            throws SQLException {
        if (date != null) {
            ps.setLong(index, date.getTime() / 1000);
        } else {
            ps.setNull(index, Types.BIGINT);
        }
    }

}
