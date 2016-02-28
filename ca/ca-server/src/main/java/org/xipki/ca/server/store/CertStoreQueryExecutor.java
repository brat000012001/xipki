/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2013-2016 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * (version 3 or later at your option)
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
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

package org.xipki.ca.server.store;

import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.ca.api.OperationException;
import org.xipki.ca.api.OperationException.ErrorCode;
import org.xipki.ca.api.publisher.CertificateInfo;
import org.xipki.ca.common.RequestorInfo;
import org.xipki.ca.common.X509CertificateWithMetaInfo;
import org.xipki.ca.server.CertRevocationInfoWithSerial;
import org.xipki.ca.server.CertStatus;
import org.xipki.ca.server.SubjectKeyProfileTriple;
import org.xipki.database.api.DataSourceWrapper;
import org.xipki.security.common.CRLReason;
import org.xipki.security.common.CertRevocationInfo;
import org.xipki.security.common.IoCertUtil;
import org.xipki.security.common.LruCache;
import org.xipki.security.common.ObjectIdentifiers;
import org.xipki.security.common.ParamChecker;

/**
 * @author Lijun Liao
 */

class CertStoreQueryExecutor
{
    private static final Logger LOG = LoggerFactory.getLogger(CertStoreQueryExecutor.class);

    private final DataSourceWrapper dataSource;

    private final CertBasedIdentityStore caInfoStore;
    private final NameIdStore requestorInfoStore;
    private final NameIdStore certprofileStore;
    private final NameIdStore publisherStore;

    private final LruCache<String, Integer> usernameIdCache = new LruCache<>(100);

    CertStoreQueryExecutor(DataSourceWrapper dataSource)
    throws SQLException
    {
        this.dataSource = dataSource;

        this.caInfoStore = initCertBasedIdentyStore("CAINFO");
        this.requestorInfoStore = initNameIdStore("REQUESTORINFO");
        this.certprofileStore = initNameIdStore("CERTPROFILEINFO");
        this.publisherStore = initNameIdStore("PUBLISHERINFO");
    }

    private CertBasedIdentityStore initCertBasedIdentyStore(String table)
    throws SQLException
    {
        final String SQL_GET_CAINFO = "SELECT ID, SUBJECT, SHA1_FP_CERT, CERT FROM " + table;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(SQL_GET_CAINFO);
        try
        {
            rs = ps.executeQuery();
            List<CertBasedIdentityEntry> caInfos = new LinkedList<>();
            while(rs.next())
            {
                int id = rs.getInt("ID");
                String subject = rs.getString("SUBJECT");
                String hexSha1Fp = rs.getString("SHA1_FP_CERT");
                String b64Cert = rs.getString("CERT");

                CertBasedIdentityEntry caInfoEntry = new CertBasedIdentityEntry(id, subject, hexSha1Fp, b64Cert);
                caInfos.add(caInfoEntry);
            }

            return new CertBasedIdentityStore(table, caInfos);
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    private NameIdStore initNameIdStore(String tableName)
    throws SQLException
    {
        final String sql = "SELECT ID, NAME FROM " + tableName;
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            rs = ps.executeQuery();
            Map<String, Integer> entries = new HashMap<>();

            while(rs.next())
            {
                int id = rs.getInt("ID");
                String name = rs.getString("NAME");
                entries.put(name, id);
            }

            return new NameIdStore(tableName, entries);
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    /**
     * @throws SQLException if there is problem while accessing database.
     * @throws NoSuchAlgorithmException
     * @throws CertificateEncodingException
     */
    void addCert(X509CertificateWithMetaInfo issuer,
            X509CertificateWithMetaInfo certificate,
            byte[] encodedSubjectPublicKey,
            String certprofileName,
            RequestorInfo requestor,
            String user)
    throws SQLException, OperationException
    {
        final String SQL_ADD_CERT =
                "INSERT INTO CERT" +
                " (ID, LAST_UPDATE, SERIAL, SUBJECT, NOTBEFORE, NOTAFTER, REVOKED, CERTPROFILEINFO_ID,"
                + " CAINFO_ID, REQUESTORINFO_ID, USER_ID, SHA1_FP_PK, SHA1_FP_SUBJECT, EE)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        final String SQL_ADD_RAWCERT = "INSERT INTO RAWCERT (CERT_ID, SHA1_FP, CERT) VALUES (?, ?, ?)";
        Connection conn = null;
        PreparedStatement[] pss = borrowPreparedStatements(SQL_ADD_CERT, SQL_ADD_RAWCERT);
        try
        {
            PreparedStatement ps_addcert = pss[0];
            PreparedStatement ps_addRawcert = pss[1];
            // all statements have the same connection
            conn = ps_addcert.getConnection();

            X509Certificate cert = certificate.getCert();

            int caId = getCaId(issuer);

            // the profile name of self signed CA certificate may not be contained in the
            // table CETPROFILEINFO
            if(cert.getIssuerDN().equals(cert.getSubjectDN()))
            {
                addCertprofileName(certprofileName);
            }
            int certprofileId = getCertprofileId(certprofileName);

            int certId = (int) dataSource.nextSeqValue("CERT_ID");
            certificate.setCertId(certId);

            // cert
            int idx = 1;
            ps_addcert.setInt(idx++, certId);
            ps_addcert.setLong(idx++, System.currentTimeMillis()/1000);
            ps_addcert.setLong(idx++, cert.getSerialNumber().longValue());
            ps_addcert.setString(idx++, certificate.getSubject());
            ps_addcert.setLong(idx++, cert.getNotBefore().getTime()/1000);
            ps_addcert.setLong(idx++, cert.getNotAfter().getTime()/1000);
            setBoolean(ps_addcert, idx++, false);
            ps_addcert.setInt(idx++, certprofileId);
            ps_addcert.setInt(idx++, caId);

            Integer requestorId = (requestor == null) ? null : getRequestorId(requestor.getName());
            if(requestorId != null)
            {
                ps_addcert.setInt(idx++, requestorId.intValue());
            }
            else
            {
                ps_addcert.setNull(idx++, Types.INTEGER);
            }

            Integer userId = (user == null) ? null : getUserId(user);
            if(userId != null)
            {
                ps_addcert.setInt(idx++, userId.intValue());
            }
            else
            {
                ps_addcert.setNull(idx++, Types.INTEGER);
            }

            ps_addcert.setString(idx++, fp(encodedSubjectPublicKey));
            String sha1_fp_subject = IoCertUtil.sha1sum_canonicalized_name(cert.getSubjectX500Principal());
            ps_addcert.setString(idx++, sha1_fp_subject);

            boolean isEECert = cert.getBasicConstraints() == -1;
            ps_addcert.setInt(idx++, isEECert ? 1 : 0);

            // rawcert
            String sha1_fp = fp(certificate.getEncodedCert());

            idx = 1;
            ps_addRawcert.setInt(idx++, certId);
            ps_addRawcert.setString(idx++, sha1_fp);
            ps_addRawcert.setString(idx++, Base64.toBase64String(certificate.getEncodedCert()));

            final boolean origAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try
            {
                try
                {
                    ps_addcert.executeUpdate();
                    ps_addRawcert.executeUpdate();
                    conn.commit();
                }catch(SQLException e)
                {
                    conn.rollback();
                    throw e;
                }
            }
            finally
            {
                conn.setAutoCommit(origAutoCommit);
            }
        } finally
        {
            try
            {
                for(PreparedStatement ps : pss)
                {
                    try
                    {
                        ps.close();
                    }catch(Throwable t)
                    {
                        LOG.warn("Could not close PreparedStatement", t);
                    }
                }
            }finally
            {
                dataSource.returnConnection(conn);
            }
        }
    }

    void addToPublishQueue(String publisherName, int certId, X509CertificateWithMetaInfo caCert)
    throws SQLException, OperationException
    {
        final String SQL = "INSERT INTO PUBLISHQUEUE (PUBLISHER_ID, CAINFO_ID, CERT_ID) VALUES (?, ?, ?)";
        PreparedStatement ps = borrowPreparedStatement(SQL);
        int caId = getCaId(caCert);
        try
        {
            int publisherId = getPublisherId(publisherName);
            int idx = 1;
            ps.setInt(idx++, publisherId);
            ps.setInt(idx++, caId);
            ps.setInt(idx++, certId);
            ps.executeUpdate();
        }finally
        {
            releaseDbResources(ps, null);
        }
    }

    void removeFromPublishQueue(String publisherName, int certId)
    throws SQLException
    {
        final String SQL = "DELETE FROM PUBLISHQUEUE WHERE PUBLISHER_ID=? AND CERT_ID=?";
        PreparedStatement ps = borrowPreparedStatement(SQL);
        try
        {
            int publisherId = getPublisherId(publisherName);
            int idx = 1;
            ps.setInt(idx++, publisherId);
            ps.setInt(idx++, certId);
            ps.executeUpdate();
        }finally
        {
            releaseDbResources(ps, null);
        }
    }

    long getMaxIdOfDeltaCRLCache(X509CertificateWithMetaInfo caCert)
    throws OperationException, SQLException
    {
        String sql = "SELECT MAX(ID) FROM DELTACRL_CACHE WHERE CAINFO_ID=?";
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int caId = getCaId(caCert);
            ps.setInt(1, caId);

            ResultSet rs = ps.executeQuery();
            if(rs.next())
            {
                return rs.getLong(1);
            }
            else
            {
                return 0;
            }
        }finally
        {
            releaseDbResources(ps, null);
        }
    }

    public void clearDeltaCRLCache(X509CertificateWithMetaInfo caCert, long maxId)
    throws OperationException, SQLException
    {
        String sql = "DELETE FROM DELTACRL_CACHE WHERE ID<? AND CAINFO_ID=?";

        PreparedStatement ps = borrowPreparedStatement(sql);
        try
        {
            ps.setLong(1, maxId + 1);
            int caId = getCaId(caCert);
            ps.setInt(2, caId);
            ps.executeUpdate();
        }finally
        {
            releaseDbResources(ps, null);
        }
    }

    void clearPublishQueue(X509CertificateWithMetaInfo caCert, String publisherName)
    throws OperationException, SQLException
    {
        StringBuilder sqlBuilder = new StringBuilder("DELETE FROM PUBLISHQUEUE");

        if(caCert != null || publisherName != null)
        {
            sqlBuilder.append(" WHERE");
            if(caCert != null)
            {
                sqlBuilder.append(" CAINFO_ID=?");
                if(publisherName != null)
                {
                    sqlBuilder.append(" AND");
                }
            }

            if(publisherName != null)
            {
                sqlBuilder.append(" PUBLISHER_ID=?");
            }
        }

        PreparedStatement ps = borrowPreparedStatement(sqlBuilder.toString());
        try
        {
            int idx = 1;
            if(caCert != null)
            {
                int caId = getCaId(caCert);
                ps.setInt(idx++, caId);
            }

            if(publisherName != null)
            {
                int publisherId = getPublisherId(publisherName);
                ps.setInt(idx++, publisherId);
            }
            ps.executeUpdate();
        }finally
        {
            releaseDbResources(ps, null);
        }
    }

    int getNextFreeCrlNumber(X509CertificateWithMetaInfo caCert)
    throws SQLException, OperationException
    {
        final String SQL = "SELECT MAX(CRL_NUMBER) FROM CRL WHERE CAINFO_ID=?";
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(SQL);

        try
        {
            int caId = getCaId(caCert);
            ps.setInt(1, caId);

            rs = ps.executeQuery();
            int maxCrlNumber = 0;
            if(rs.next())
            {
                maxCrlNumber = rs.getInt(1);
                if (maxCrlNumber < 0)
                {
                    maxCrlNumber = 0;
                }
            }

            return maxCrlNumber + 1;
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    Long getThisUpdateOfCurrentCRL(X509CertificateWithMetaInfo caCert)
    throws SQLException, OperationException
    {
        final String SQL = "SELECT MAX(THISUPDATE) FROM CRL WHERE CAINFO_ID=?";
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(SQL);

        try
        {
            int caId = getCaId(caCert);
            ps.setInt(1, caId);

            rs = ps.executeQuery();
            long thisUpdateOfCurrentCRL = 0;
            if(rs.next())
            {
                thisUpdateOfCurrentCRL = rs.getLong(1);
            }

            return thisUpdateOfCurrentCRL;
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    void addCRL(X509CertificateWithMetaInfo caCert, X509CRL crl)
    throws SQLException, CRLException, OperationException
    {
        byte[] encodedExtnValue = crl.getExtensionValue(Extension.cRLNumber.getId());
        Long crlNumber = null;
        if(encodedExtnValue != null)
        {
            byte[] extnValue = DEROctetString.getInstance(encodedExtnValue).getOctets();
            crlNumber = ASN1Integer.getInstance(extnValue).getPositiveValue().longValue();
        }

        encodedExtnValue = crl.getExtensionValue(Extension.deltaCRLIndicator.getId());
        Long baseCrlNumber = null;
        if(encodedExtnValue != null)
        {
            byte[] extnValue = DEROctetString.getInstance(encodedExtnValue).getOctets();
            baseCrlNumber = ASN1Integer.getInstance(extnValue).getPositiveValue().longValue();
        }

        final String SQL =
                "INSERT INTO CRL (ID, CAINFO_ID, CRL_NUMBER, THISUPDATE, NEXTUPDATE, DELTACRL, BASECRL_NUMBER, CRL)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement ps = borrowPreparedStatement(SQL);

        try
        {
            int caId = getCaId(caCert);
            int idx = 1;

            int crlId = (int) dataSource.nextSeqValue("CRL_ID");
            ps.setInt(idx++, crlId);

            ps.setInt(idx++, caId);
            if(crlNumber != null)
            {
                ps.setInt(idx++, crlNumber.intValue());
            }
            else
            {
                ps.setNull(idx++, Types.INTEGER);
            }
            Date d = crl.getThisUpdate();
            ps.setLong(idx++, d.getTime()/1000);
            d = crl.getNextUpdate();
            if(d != null)
            {
                ps.setLong(idx++, d.getTime()/1000);
            }
            else
            {
                ps.setNull(idx++, Types.BIGINT);
            }

            ps.setInt(idx++, baseCrlNumber != null ? 1 : 0);

            if(baseCrlNumber != null)
            {
                ps.setLong(idx++, baseCrlNumber);
            }
            else
            {
                ps.setNull(idx++, Types.BIGINT);
            }

            byte[] encodedCrl = crl.getEncoded();
            String b64Crl = Base64.toBase64String(encodedCrl);
            ps.setString(idx++, b64Crl);

            ps.executeUpdate();
        }finally
        {
            releaseDbResources(ps, null);
        }
    }

    CertWithRevocationInfo revokeCert(X509CertificateWithMetaInfo caCert, BigInteger serialNumber,
            CertRevocationInfo revInfo, boolean force, boolean publishToDeltaCRLCache)
    throws OperationException, SQLException
    {
        CertWithRevocationInfo certWithRevInfo = getCertWithRevocationInfo(caCert, serialNumber);
        if(certWithRevInfo == null)
        {
            LOG.warn("Certificate with issuer={} and serialNumber={} does not exist",
                    caCert.getSubject(), serialNumber);
            return null;
        }

        CertRevocationInfo currentRevInfo = certWithRevInfo.getRevInfo();
        if(currentRevInfo != null)
        {
            CRLReason currentReason = currentRevInfo.getReason();
            if(currentReason == CRLReason.CERTIFICATE_HOLD)
            {
                if(revInfo.getReason() == CRLReason.CERTIFICATE_HOLD)
                {
                    throw new OperationException(ErrorCode.CERT_REVOKED,
                            "certificate already issued with the requested reason " + currentReason.getDescription());
                }
                else
                {
                    revInfo.setRevocationTime(currentRevInfo.getRevocationTime());
                    revInfo.setInvalidityTime(currentRevInfo.getInvalidityTime());
                }
            }
            else if(force == false)
            {
                throw new OperationException(ErrorCode.CERT_REVOKED,
                        "certificate already issued with reason " + currentReason.getDescription());
            }
        }

        final String SQL_REVOKE_CERT = "UPDATE CERT" +
                " SET LAST_UPDATE=?, REVOKED=?, REV_TIME=?, REV_INVALIDITY_TIME=?, REV_REASON=?" +
                " WHERE ID=?";
        PreparedStatement ps = borrowPreparedStatement(SQL_REVOKE_CERT);

        int certId = certWithRevInfo.getCert().getCertId().intValue();
        try
        {
            int idx = 1;
            ps.setLong(idx++, new Date().getTime()/1000);
            setBoolean(ps, idx++, true);
            ps.setLong(idx++, revInfo.getRevocationTime().getTime()/1000);
            if(revInfo.getInvalidityTime() != null)
            {
                ps.setLong(idx++, revInfo.getInvalidityTime().getTime()/1000);
            }else
            {
                ps.setNull(idx++, Types.BIGINT);
            }

            ps.setInt(idx++, revInfo.getReason().getCode());
            ps.setLong(idx++, certId);

            int count = ps.executeUpdate();
            if(count != 1)
            {
                String message;
                if(count > 1)
                {
                    message = count + " rows modified, but exactly one is expected";
                }
                else
                {
                    message = "no row is modified, but exactly one is expected";
                }
                throw new OperationException(ErrorCode.System_Failure, message);
            }
        }finally
        {
            releaseDbResources(ps, null);
        }

        if(publishToDeltaCRLCache)
        {
            Integer caId = getCaId(caCert); // must not be null
            publishToDeltaCRLCache(caId, certWithRevInfo.getCert().getCert().getSerialNumber());
        }

        certWithRevInfo.setRevInfo(revInfo);
        return certWithRevInfo;
    }

    X509CertificateWithMetaInfo unrevokeCert(X509CertificateWithMetaInfo caCert, BigInteger serialNumber,
            boolean force, boolean publishToDeltaCRLCache)
    throws OperationException, SQLException
    {
        CertWithRevocationInfo certWithRevInfo = getCertWithRevocationInfo(caCert, serialNumber);
        if(certWithRevInfo == null)
        {
            LOG.warn("Certificate with issuer={} and serialNumber={} does not exist",
                    caCert.getSubject(), serialNumber);
            return null;
        }

        CertRevocationInfo currentRevInfo = certWithRevInfo.getRevInfo();
        if(currentRevInfo == null)
        {
            throw new OperationException(ErrorCode.CERT_UNREVOKED,
                    "certificate is not revoked");
        }

        CRLReason currentReason = currentRevInfo.getReason();
        if(force == false)
        {
            if(currentReason != CRLReason.CERTIFICATE_HOLD)
            {
                throw new OperationException(ErrorCode.NOT_PERMITTED,
                        "could not unrevoke certificate revoked with reason " + currentReason.getDescription());
            }
        }

        final String SQL_REVOKE_CERT =
                "UPDATE CERT" +
                " SET LAST_UPDATE=?, REVOKED=?, REV_TIME=?, REV_INVALIDITY_TIME=?, REV_REASON=?" +
                " WHERE ID=?";
        PreparedStatement ps = borrowPreparedStatement(SQL_REVOKE_CERT);

        int certId = certWithRevInfo.getCert().getCertId().intValue();
        try
        {
            int idx = 1;
            ps.setLong(idx++, new Date().getTime()/1000);
            setBoolean(ps, idx++, false);
            ps.setNull(idx++, Types.INTEGER);
            ps.setNull(idx++, Types.INTEGER);
            ps.setNull(idx++, Types.INTEGER);
            ps.setLong(idx++, certId);

            int count = ps.executeUpdate();
            if(count != 1)
            {
                String message;
                if(count > 1)
                {
                    message = count + " rows modified, but exactly one is expected";
                }
                else
                {
                    message = "no row is modified, but exactly one is expected";
                }
                throw new OperationException(ErrorCode.System_Failure, message);
            }
        }finally
        {
            releaseDbResources(ps, null);
        }

        if(publishToDeltaCRLCache)
        {
            Integer caId = getCaId(caCert); // must not be null
            publishToDeltaCRLCache(caId, certWithRevInfo.getCert().getCert().getSerialNumber());
        }

        return certWithRevInfo.getCert();
    }

    private void publishToDeltaCRLCache(int caId, BigInteger serialNumber)
    throws SQLException
    {
        final String SQL = "INSERT INTO DELTACRL_CACHE (ID, CAINFO_ID, SERIAL) VALUES (?, ?, ?)";

        PreparedStatement ps = null;

        try
        {
            long id = dataSource.nextSeqValue("DCC_ID");
            ps = borrowPreparedStatement(SQL);
            int idx = 1;
            ps.setLong(idx++, id);
            ps.setInt(idx++, caId);
            ps.setLong(idx++, serialNumber.longValue());
            ps.executeUpdate();
        }finally
        {
            releaseDbResources(ps, null);
        }
    }

    X509CertificateWithMetaInfo getCert(X509CertificateWithMetaInfo caCert, BigInteger serialNumber)
    throws OperationException, SQLException
    {
        CertWithRevocationInfo certWithRevInfo = getCertWithRevocationInfo(caCert, serialNumber);
        if(certWithRevInfo == null)
        {
            return null;
        }
        return certWithRevInfo.getCert();
    }

    void removeCertificate(X509CertificateWithMetaInfo caCert, BigInteger serialNumber)
    throws OperationException, SQLException
    {
        int caId = getCaId(caCert);

        final String SQL_REVOKE_CERT = "DELETE FROM CERT WHERE CAINFO_ID=? AND SERIAL=?";
        PreparedStatement ps = borrowPreparedStatement(SQL_REVOKE_CERT);

        try
        {
            int idx = 1;
            ps.setInt(idx++, caId);
            ps.setLong(idx++, serialNumber.longValue());

            int count = ps.executeUpdate();
            if(count != 1)
            {
                String message;
                if(count > 1)
                {
                    message = count + " rows modified, but exactly one is expected";
                }
                else
                {
                    message = "no row is modified, but exactly one is expected";
                }
                throw new OperationException(ErrorCode.System_Failure, message);
            }
        }finally
        {
            releaseDbResources(ps, null);
        }

    }

    Long getGreatestSerialNumber(X509CertificateWithMetaInfo caCert)
    throws SQLException, OperationException
    {
        ParamChecker.assertNotNull("caCert", caCert);

        int caId = getCaId(caCert);

        String sql = "SELECT MAX(SERIAL) FROM CERT WHERE CAINFO_ID=?";
        PreparedStatement ps = borrowPreparedStatement(sql);
        ResultSet rs = null;
        try
        {
            ps.setInt(1, caId);

            rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        } finally
        {
            releaseDbResources(ps, rs);
        }
    }

    List<Integer> getPublishQueueEntries(X509CertificateWithMetaInfo caCert,
            String publisherName, int numEntries)
    throws SQLException, OperationException
    {
        ParamChecker.assertNotNull("caCert", caCert);
        if(numEntries < 1)
        {
            throw new IllegalArgumentException("numEntries is not positive");
        }

        int caId = getCaId(caCert);
        int publisherId = getPublisherId(publisherName);

        String sql = "CERT_ID FROM PUBLISHQUEUE WHERE CAINFO_ID=? AND PUBLISHER_ID=?";
        sql = dataSource.createFetchFirstSelectSQL(sql, numEntries, "CERT_ID ASC");
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setInt(idx++, caId);
            ps.setLong(idx++, publisherId);
            rs = ps.executeQuery();

            List<Integer> ret = new ArrayList<>();
            while(rs.next() && ret.size() < numEntries)
            {
                int certId = rs.getInt("CERT_ID");
                if(ret.contains(certId) == false)
                {
                    ret.add(certId);
                }
            }

            return ret;
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    boolean containsCertificates(X509CertificateWithMetaInfo caCert, boolean ee)
    throws SQLException, OperationException
    {
        ParamChecker.assertNotNull("caCert", caCert);
        String sql = "COUNT(*) FROM CERT WHERE CAINFO_ID=? AND EE=?";
        sql = dataSource.createFetchFirstSelectSQL(sql, 1);
        int caId = getCaId(caCert);

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setInt(idx++, caId);
            ps.setInt(2, ee ? 1 : 0);
            rs = ps.executeQuery();

            if(rs.next())
            {
                return rs.getInt(1) > 0;
            }
            else
            {
                return false;
            }
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    List<BigInteger> getSerialNumbers(X509CertificateWithMetaInfo caCert,
            Date notExpiredAt, BigInteger startSerial, int numEntries, boolean onlyRevoked,
            boolean onlyCACerts, boolean onlyUserCerts)
    throws SQLException, OperationException
    {
        ParamChecker.assertNotNull("caCert", caCert);
        if(numEntries < 1)
        {
            throw new IllegalArgumentException("numEntries is not positive");
        }

        int caId = getCaId(caCert);

        StringBuilder sb = new StringBuilder("SERIAL FROM CERT WHERE CAINFO_ID=? AND SERIAL>?");
        if(notExpiredAt != null)
        {
            sb.append(" AND NOTAFTER>?");
        }
        if(onlyRevoked)
        {
            sb.append(" AND REVOKED=1");
        }

        if(onlyCACerts)
        {
            sb.append(" AND EE=0");
        }
        else if(onlyUserCerts)
        {
            sb.append(" AND EE=1");
        }

        final String sql = dataSource.createFetchFirstSelectSQL(sb.toString(), numEntries, "SERIAL ASC");
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setInt(idx++, caId);
            ps.setLong(idx++, (startSerial == null)? 0 : startSerial.longValue()-1);
            if(notExpiredAt != null)
            {
                ps.setLong(idx++, notExpiredAt.getTime()/1000 + 1);
            }
            rs = ps.executeQuery();

            List<BigInteger> ret = new ArrayList<>();
            while(rs.next() && ret.size() < numEntries)
            {
                long serial = rs.getLong("SERIAL");
                ret.add(BigInteger.valueOf(serial));
            }

            return ret;
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    List<BigInteger> getExpiredSerialNumbers(X509CertificateWithMetaInfo caCert,
            long expiredAt, int numEntries, String certProfile, String userLike)
    throws SQLException, OperationException
    {
        ParamChecker.assertNotNull("caCert", caCert);
        ParamChecker.assertNotNull("expiredAt", expiredAt);
        ParamChecker.assertNotEmpty("certProfile", certProfile);

        if(numEntries < 1)
        {
            throw new IllegalArgumentException("numEntries is not positive");
        }

        int caId = getCaId(caCert);

        StringBuilder sqlBuilder = new StringBuilder(
                "SERIAL FROM CERT WHERE CAINFO_ID=? AND NOTAFTER<? AND CERTPROFILEINFO_ID=?");

        if(userLike != null)
        {
            userLike = userLike.trim();
            if(userLike.isEmpty() || "null".equalsIgnoreCase(userLike))
            {
                userLike = null;
            }
        }

        Integer certProfileId = certprofileStore.getId(certProfile);
        if(certProfileId == null)
        {
            return Collections.emptyList();
        }

        if(userLike == null)
        {
            sqlBuilder.append(" AND USER_ID IS NULL");
        }
        else if("all".equalsIgnoreCase(userLike) == false)
        {
            sqlBuilder.append(" AND USER_ID IN (SELECT ID FROM USERNAME WHERE NAME LIKE ?)");
        }

        final String sql = dataSource.createFetchFirstSelectSQL(sqlBuilder.toString(), numEntries);
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setInt(idx++, caId);
            ps.setLong(idx++, expiredAt);
            ps.setInt(idx++, certProfileId);

            if(userLike != null && "all".equalsIgnoreCase(userLike) == false)
            {
                ps.setString(idx++, userLike);
            }

            rs = ps.executeQuery();

            List<BigInteger> ret = new ArrayList<>();
            while(rs.next() && ret.size() < numEntries)
            {
                long serial = rs.getLong("SERIAL");
                ret.add(BigInteger.valueOf(serial));
            }

            return ret;
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    int getNumOfExpiredCerts(X509CertificateWithMetaInfo caCert, long expiredAt,
            String certProfile, String userLike)
    throws SQLException, OperationException
    {
        ParamChecker.assertNotNull("caCert", caCert);
        ParamChecker.assertNotNull("expiredAt", expiredAt);
        ParamChecker.assertNotEmpty("certProfile", certProfile);

        int caId = getCaId(caCert);

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT COUNT(*) FROM CERT WHERE CAINFO_ID=? AND NOTAFTER<? AND CERTPROFILEINFO_ID=?");
        if(userLike != null)
        {
            userLike = userLike.trim();
            if(userLike.isEmpty() || "null".equalsIgnoreCase(userLike))
            {
                userLike = null;
            }
        }

        Integer certProfileId = certprofileStore.getId(certProfile);
        if(certProfileId == null)
        {
            return 0;
        }

        if(userLike == null)
        {
            sqlBuilder.append(" AND USER_ID IS NULL");
        }
        else if("all".equalsIgnoreCase(userLike) == false)
        {
            sqlBuilder.append(" AND USER_ID IN (SELECT ID FROM USERNAME WHERE NAME LIKE ?)");
        }

        PreparedStatement ps = borrowPreparedStatement(sqlBuilder.toString());

        ResultSet rs = null;
        try
        {
            int idx = 1;
            ps.setInt(idx++, caId);
            ps.setLong(idx++, expiredAt);
            ps.setInt(idx++, certProfileId);

            if(userLike != null && "all".equalsIgnoreCase(userLike) == false)
            {
                ps.setString(idx++, userLike);
            }

            rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    byte[] getEncodedCRL(X509CertificateWithMetaInfo caCert, BigInteger crlNumber)
    throws SQLException, OperationException
    {
        ParamChecker.assertNotNull("caCert", caCert);

        int caId = getCaId(caCert);

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("THISUPDATE, CRL FROM CRL WHERE CAINFO_ID=?");
        if(crlNumber != null)
        {
            sqlBuilder.append(" AND CRL_NUMBER=?");
        }

        String sql = dataSource.createFetchFirstSelectSQL(sqlBuilder.toString(), 1, "THISUPDATE DESC");
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setInt(idx++, caId);
            if(crlNumber != null)
            {
                ps.setLong(idx++, crlNumber.longValue());
            }

            rs = ps.executeQuery();

            byte[] encodedCrl = null;

            long current_thisUpdate = 0;
            // iterate all entries to make sure that the latest CRL will be returned
            while(rs.next())
            {
                long thisUpdate = rs.getLong("THISUPDATE");
                if(thisUpdate >= current_thisUpdate)
                {
                    String b64Crl = rs.getString("CRL");
                    encodedCrl = Base64.decode(b64Crl);
                    current_thisUpdate = thisUpdate;
                }
            }

            return encodedCrl;
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    int cleanupCRLs(X509CertificateWithMetaInfo caCert, int numCRLs)
    throws SQLException, OperationException
    {
        if(numCRLs < 1)
        {
            throw new IllegalArgumentException("numCRLs is not positive");
        }

        ParamChecker.assertNotNull("caCert", caCert);
        int caId = getCaId(caCert);

        String sql = "SELECT CRL_NUMBER FROM CRL WHERE CAINFO_ID=? AND DELTACRL=?";
        PreparedStatement ps = borrowPreparedStatement(sql);

        List<Integer> crlNumbers = new LinkedList<>();

        ResultSet rs = null;
        try
        {
            int idx = 1;
            ps.setInt(idx++, caId);
            ps.setBoolean(idx++, false);
            rs = ps.executeQuery();

            while(rs.next())
            {
                int crlNumber = rs.getInt("CRL_NUMBER");
                crlNumbers.add(crlNumber);
            }
        }finally
        {
            releaseDbResources(ps, rs);
        }

        int n = crlNumbers.size();
        Collections.sort(crlNumbers);

        int numCrlsToDelete = n - numCRLs;
        if(numCrlsToDelete < 1)
        {
            return 0;
        }

        int crlNumber = crlNumbers.get(numCrlsToDelete - 1);
        sql = "DELETE FROM CRL WHERE CAINFO_ID=? AND CRL_NUMBER<?";
        ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setInt(idx++, caId);
            ps.setInt(idx++, crlNumber + 1);
            ps.executeUpdate();
        }finally
        {
            releaseDbResources(ps, null);
        }

        return numCrlsToDelete;
    }

    CertificateInfo getCertForId(X509CertificateWithMetaInfo caCert, int certId)
    throws SQLException, OperationException, CertificateException
    {
        ParamChecker.assertNotNull("caCert", caCert);

        final String col_certprofileinfo_id = "CERTPROFILEINFO_ID";
        final String col_revoked = "REVOKED";
        final String col_rev_reason = "REV_REASON";
        final String col_rev_time = "REV_TIME";
        final String col_rev_invalidity_time = "REV_INVALIDITY_TIME";
        final String col_cert = "CERT";

        String sql = "T1." + col_certprofileinfo_id + " " + col_certprofileinfo_id +
                ", T1." + col_revoked + " " + col_revoked +
                ", T1." + col_rev_reason + " " + col_rev_reason +
                ", T1." + col_rev_time + " " + col_rev_time +
                ", T1." + col_rev_invalidity_time + " " + col_rev_invalidity_time +
                ", T2." + col_cert + " " + col_cert +
                " FROM CERT T1, RAWCERT T2" +
                " WHERE T1.ID=? AND T2.CERT_ID=T1.ID";

        sql = dataSource.createFetchFirstSelectSQL(sql, 1);
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            ps.setInt(1, certId);
            rs = ps.executeQuery();

            if(rs.next())
            {
                String b64Cert = rs.getString(col_cert);
                byte[] encodedCert = Base64.decode(b64Cert);
                X509Certificate cert = IoCertUtil.parseCert(encodedCert);

                int certProfileInfo_id = rs.getInt(col_certprofileinfo_id);
                String certProfileName = certprofileStore.getName(certProfileInfo_id);

                X509CertificateWithMetaInfo certWithMeta = new X509CertificateWithMetaInfo(cert, encodedCert);

                CertificateInfo certInfo = new CertificateInfo(certWithMeta,
                        caCert, cert.getPublicKey().getEncoded(), certProfileName);

                boolean revoked = rs.getBoolean(col_revoked);

                if(revoked)
                {
                    int rev_reasonCode = rs.getInt(col_rev_reason);
                    CRLReason rev_reason = CRLReason.forReasonCode(rev_reasonCode);
                    long rev_time = rs.getLong(col_rev_time);
                    long invalidity_time = rs.getLong(col_rev_invalidity_time);

                    Date invalidityTime = (invalidity_time == 0 || invalidity_time == rev_time) ?
                            null : new Date(invalidity_time * 1000);
                    CertRevocationInfo revInfo = new CertRevocationInfo(rev_reason,
                            new Date(rev_time * 1000), invalidityTime);
                    certInfo.setRevocationInfo(revInfo);
                }

                return certInfo;
            }
        } catch (IOException e)
        {
            throw new OperationException(ErrorCode.System_Failure, "IOException: " + e.getMessage());
        }finally
        {
            releaseDbResources(ps, rs);
        }

        return null;
    }

    X509CertificateWithMetaInfo getCertForId(int certId)
    throws SQLException, OperationException
    {
        String sql = "CERT FROM RAWCERT WHERE CERT_ID=?";
        sql = dataSource.createFetchFirstSelectSQL(sql, 1);
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            ps.setInt(1, certId);
            rs = ps.executeQuery();

            if(rs.next())
            {
                String b64Cert = rs.getString("CERT");
                if(b64Cert == null)
                {
                    return null;
                }

                byte[] encodedCert = Base64.decode(b64Cert);
                X509Certificate cert;
                try
                {
                    cert = IoCertUtil.parseCert(encodedCert);
                } catch (CertificateException e)
                {
                    throw new OperationException(ErrorCode.System_Failure, "CertificateException: " + e.getMessage());
                } catch (IOException e)
                {
                    throw new OperationException(ErrorCode.System_Failure, "IOException: " + e.getMessage());
                }
                return new X509CertificateWithMetaInfo(cert, encodedCert);
            }
        }finally
        {
            releaseDbResources(ps, rs);
        }

        return null;
    }

    CertWithRevocationInfo getCertWithRevocationInfo(X509CertificateWithMetaInfo caCert, BigInteger serial)
    throws SQLException, OperationException
    {
        ParamChecker.assertNotNull("caCert", caCert);
        ParamChecker.assertNotNull("serial", serial);

        int caId = getCaId(caCert);

        String sql = "T1.ID ID, T1.REVOKED REVOKED,"
                + " T1.REV_REASON REV_REASON,"
                + " T1.REV_TIME REV_TIME,"
                + " T1.REV_INVALIDITY_TIME REV_INVALIDITY_TIME,"
                + " T2.CERT CERT"
                + " FROM CERT T1, RAWCERT T2"
                + " WHERE T1.CAINFO_ID=? AND T1.SERIAL=? AND T2.CERT_ID=T1.ID";

        sql = dataSource.createFetchFirstSelectSQL(sql, 1);
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setInt(idx++, caId);
            ps.setLong(idx++, serial.longValue());
            rs = ps.executeQuery();

            if(rs.next())
            {
                int certId = rs.getInt("ID");
                String b64Cert = rs.getString("CERT");
                byte[] certBytes = (b64Cert == null) ? null : Base64.decode(b64Cert);
                X509Certificate cert;
                try
                {
                    cert = IoCertUtil.parseCert(certBytes);
                } catch (CertificateException | IOException e)
                {
                    throw new OperationException(ErrorCode.System_Failure, e.getClass().getName() + ": " + e.getMessage());
                }

                CertRevocationInfo revInfo = null;
                boolean revoked = rs.getBoolean("REVOKED");
                if(revoked)
                {
                    int rev_reason = rs.getInt("REV_REASON");
                    long rev_time = rs.getLong("REV_TIME");
                    long rev_invalidity_time = rs.getLong("REV_INVALIDITY_TIME");
                    Date invalidityTime = rev_invalidity_time == 0 ? null : new Date(1000 * rev_invalidity_time);
                    revInfo = new CertRevocationInfo(CRLReason.forReasonCode(rev_reason),
                            new Date(1000 * rev_time),
                            invalidityTime);
                }

                X509CertificateWithMetaInfo certWithMeta = new X509CertificateWithMetaInfo(cert, certBytes);
                certWithMeta.setCertId(certId);
                return new CertWithRevocationInfo(certWithMeta, revInfo);
            }
        }finally
        {
            releaseDbResources(ps, null);
        }

        return null;
    }

    CertificateInfo getCertificateInfo(X509CertificateWithMetaInfo caCert, BigInteger serial)
    throws SQLException, OperationException, CertificateException
    {
        ParamChecker.assertNotNull("caCert", caCert);
        ParamChecker.assertNotNull("serial", serial);

        int caId = getCaId(caCert);

        final String col_certprofileinfo_id = "CERTPROFILEINFO_ID";
        final String col_revoked = "REVOKED";
        final String col_rev_reason = "REV_REASON";
        final String col_rev_time = "REV_TIME";
        final String col_rev_invalidity_time = "REV_INVALIDITY_TIME";
        final String col_cert = "CERT";

        String sql = "T1." + col_certprofileinfo_id + " " + col_certprofileinfo_id +
                ", T1." + col_revoked + " " + col_revoked +
                ", T1." + col_rev_reason + " " + col_rev_reason +
                ", T1." + col_rev_time + " " + col_rev_time +
                ", T1." + col_rev_invalidity_time + " " + col_rev_invalidity_time +
                ", T2." + col_cert + " " + col_cert +
                " FROM CERT T1, RAWCERT T2" +
                " WHERE T1.CAINFO_ID=? AND T1.SERIAL=? AND T2.CERT_ID=T1.ID";

        sql = dataSource.createFetchFirstSelectSQL(sql, 1);
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setInt(idx++, caId);
            ps.setLong(idx++, serial.longValue());
            rs = ps.executeQuery();

            if(rs.next())
            {
                String b64Cert = rs.getString(col_cert);
                byte[] encodedCert = Base64.decode(b64Cert);
                X509Certificate cert = IoCertUtil.parseCert(encodedCert);

                int certProfileInfo_id = rs.getInt(col_certprofileinfo_id);
                String certProfileName = certprofileStore.getName(certProfileInfo_id);

                X509CertificateWithMetaInfo certWithMeta = new X509CertificateWithMetaInfo(cert, encodedCert);

                byte[] subjectPublicKeyInfo = Certificate.getInstance(encodedCert).getTBSCertificate()
                        .getSubjectPublicKeyInfo().getEncoded();
                CertificateInfo certInfo = new CertificateInfo(certWithMeta,
                        caCert, subjectPublicKeyInfo, certProfileName);

                boolean revoked = rs.getBoolean(col_revoked);

                if(revoked)
                {
                    int rev_reasonCode = rs.getInt(col_rev_reason);
                    CRLReason rev_reason = CRLReason.forReasonCode(rev_reasonCode);
                    long rev_time = rs.getLong(col_rev_time);
                    long invalidity_time = rs.getLong(col_rev_invalidity_time);

                    Date invalidityTime = invalidity_time == 0 ? null : new Date(invalidity_time * 1000);
                    CertRevocationInfo revInfo = new CertRevocationInfo(rev_reason,
                            new Date(rev_time * 1000), invalidityTime);
                    certInfo.setRevocationInfo(revInfo);
                }

                return certInfo;
            }
        } catch (IOException e)
        {
            LOG.warn("getCertificateInfo()", e);
            throw new OperationException(ErrorCode.System_Failure, "IOException: " + e.getMessage());
        }finally
        {
            releaseDbResources(ps, rs);
        }

        return null;
    }

    List<CertRevocationInfoWithSerial> getRevokedCertificates(X509CertificateWithMetaInfo caCert,
            Date notExpiredAt, BigInteger startSerial, int numEntries,
            boolean onlyCACerts, boolean onlyUserCerts)
    throws SQLException, OperationException
    {
        ParamChecker.assertNotNull("caCert", caCert);
        ParamChecker.assertNotNull("notExpiredAt", notExpiredAt);

        if(numEntries < 1)
        {
            throw new IllegalArgumentException("numEntries is not positive");
        }

        int caId = getCaId(caCert);

        StringBuilder sqlBuiler = new StringBuilder();
        sqlBuiler.append("SERIAL, REV_REASON, REV_TIME, REV_INVALIDITY_TIME FROM CERT");
        sqlBuiler.append(" WHERE CAINFO_ID=? AND REVOKED=? AND SERIAL>? AND NOTAFTER>?");
        if(onlyCACerts)
        {
            sqlBuiler.append(" AND EE=0");
        }
        else if(onlyUserCerts)
        {
            sqlBuiler.append(" AND EE=1");
        }

        String sql = dataSource.createFetchFirstSelectSQL(sqlBuiler.toString(), numEntries, "SERIAL ASC");
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setInt(idx++, caId);
            setBoolean(ps, idx++, true);
            ps.setLong(idx++, startSerial.longValue() - 1);
            ps.setLong(idx++, notExpiredAt.getTime() / 1000 + 1);
            rs = ps.executeQuery();

            List<CertRevocationInfoWithSerial> ret = new ArrayList<>();
            while(rs.next())
            {
                long serial = rs.getLong("SERIAL");
                int rev_reason = rs.getInt("REV_REASON");
                long rev_time = rs.getLong("REV_TIME");
                long rev_invalidity_time = rs.getLong("REV_INVALIDITY_TIME");

                Date invalidityTime = rev_invalidity_time == 0 ? null :  new Date(1000 * rev_invalidity_time);
                CertRevocationInfoWithSerial revInfo = new CertRevocationInfoWithSerial(
                        BigInteger.valueOf(serial),
                        rev_reason, new Date(1000 * rev_time), invalidityTime);
                ret.add(revInfo);
            }

            return ret;
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    List<CertRevocationInfoWithSerial> getCertificatesForDeltaCRL(
            X509CertificateWithMetaInfo caCert, BigInteger startSerial, int numEntries,
            boolean onlyCACerts, boolean onlyUserCerts)
    throws SQLException, OperationException
    {
        ParamChecker.assertNotNull("caCert", caCert);

        if(numEntries < 1)
        {
            throw new IllegalArgumentException("numEntries is not positive");
        }

        int caId = getCaId(caCert);

        String sql = "SERIAL FROM DELTACRL_CACHE WHERE CAINFO_ID=? AND SERIAL>?";
        sql = dataSource.createFetchFirstSelectSQL(sql, numEntries, "SERIAL ASC");
        List<Long> serials = new LinkedList<>();
        ResultSet rs = null;

        PreparedStatement ps = borrowPreparedStatement(sql);
        try
        {
            int idx = 1;
            ps.setInt(idx++, caId);
            ps.setLong(idx++, startSerial.longValue() - 1);
            rs = ps.executeQuery();

            while(rs.next())
            {
                long serial = rs.getLong("SERIAL");
                serials.add(serial);
            }
        }finally
        {
            releaseDbResources(ps, rs);
        }

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("REVOKED, REV_REASON, REV_TIME, REV_INVALIDITY_TIME");
        sqlBuilder.append(" FROM CERT WHERE CAINFO_ID=? AND SERIAL=?");
        if(onlyCACerts)
        {
            sqlBuilder.append(" AND EE=0");
        }
        else if(onlyUserCerts)
        {
            sqlBuilder.append(" AND EE=1");
        }

        sql = dataSource.createFetchFirstSelectSQL(sqlBuilder.toString(), 1);
        ps = borrowPreparedStatement(sql);

        List<CertRevocationInfoWithSerial> ret = new ArrayList<>();
        for(Long serial : serials)
        {
            ps.setInt(1, caId);
            ps.setLong(2, serial);
            rs = ps.executeQuery();

            try
            {
                if(rs.next() == false)
                {
                    continue;
                }
                CertRevocationInfoWithSerial revInfo;

                boolean revoked = rs.getBoolean("REVOEKD");
                if(revoked)
                {
                    int rev_reason = rs.getInt("REV_REASON");
                    long rev_time = rs.getLong("REV_TIME");
                    long rev_invalidity_time = rs.getLong("REV_INVALIDITY_TIME");

                    Date invalidityTime = rev_invalidity_time == 0 ? null :  new Date(1000 * rev_invalidity_time);
                    revInfo = new CertRevocationInfoWithSerial(
                            BigInteger.valueOf(serial),
                            rev_reason, new Date(1000 * rev_time), invalidityTime);
                }
                else
                {
                    long lastUpdate = rs.getLong("LAST_UPDATE");
                    revInfo = new CertRevocationInfoWithSerial(BigInteger.valueOf(serial),
                            CRLReason.REMOVE_FROM_CRL.getCode(), new Date(1000 * lastUpdate), null);
                }
                ret.add(revInfo);
            }finally
            {
                releaseDbResources(null, rs);
            }
        }

        return ret;
    }

    CertStatus getCertStatusForSubject(X509CertificateWithMetaInfo caCert, X500Principal subject)
    throws SQLException
    {
        String subjectFp = IoCertUtil.sha1sum_canonicalized_name(subject);
        return getCertStatusForSubjectFp(caCert, subjectFp);
    }

    CertStatus getCertStatusForSubject(X509CertificateWithMetaInfo caCert, X500Name subject)
    throws SQLException
    {
        String subjectFp = IoCertUtil.sha1sum_canonicalized_name(subject);
        return getCertStatusForSubjectFp(caCert, subjectFp);
    }

    private CertStatus getCertStatusForSubjectFp(
            X509CertificateWithMetaInfo caCert, String subjectFp)
    throws SQLException
    {
        byte[] encodedCert = caCert.getEncodedCert();
        Integer caId =  caInfoStore.getCaIdForCert(encodedCert);
        if(caId == null)
        {
            return CertStatus.Unknown;
        }

        String sql = "REVOKED FROM CERT WHERE SHA1_FP_SUBJECT=? AND CAINFO_ID=?";
        sql = dataSource.createFetchFirstSelectSQL(sql, 1);
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setString(idx++, subjectFp);
            ps.setInt(idx++, caId);

            rs = ps.executeQuery();
            if(rs.next())
            {
                return rs.getBoolean("REVOKED") ? CertStatus.Revoked : CertStatus.Good;
            }
            else
            {
                return CertStatus.Unknown;
            }
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    boolean certIssuedForSubject(X509CertificateWithMetaInfo caCert, String sha1FpSubject)
    throws OperationException, SQLException
    {
        byte[] encodedCert = caCert.getEncodedCert();
        Integer caId =  caInfoStore.getCaIdForCert(encodedCert);

        if(caId == null)
        {
            return false;
        }

        String sql = "COUNT(ID) FROM CERT WHERE SHA1_FP_SUBJECT=? AND CAINFO_ID=?";
        sql = dataSource.createFetchFirstSelectSQL(sql, 1);

        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setString(idx++, sha1FpSubject);
            ps.setInt(idx++, caId);

            rs = ps.executeQuery();
            if(rs.next())
            {
                return rs.getInt(1) > 0;
            }
        }finally
        {
            releaseDbResources(ps, rs);
        }

        return false;
    }

    SubjectKeyProfileTriple getLatestCert(X509CertificateWithMetaInfo caCert, String subjectFp,
            String keyFp, String profile)
    throws SQLException
    {
        byte[] encodedCert = caCert.getEncodedCert();
        Integer caId =  caInfoStore.getCaIdForCert(encodedCert);

        if(caId == null)
        {
            return null;
        }

        Integer profileId = certprofileStore.getId(profile);
        if(profileId == null)
        {
            return null;
        }

        String sql = "ID, REVOKED FROM CERT"
                + " WHERE SHA1_FP_PK=? AND SHA1_FP_SUBJECT=? AND CAINFO_ID=? AND CERTPROFILEINFO_ID=?";
        sql = dataSource.createFetchFirstSelectSQL(sql, 1, "ID DESC");
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setString(idx++, keyFp);
            ps.setString(idx++, subjectFp);
            ps.setInt(idx++, caId);
            ps.setInt(idx++, profileId);

            rs = ps.executeQuery();

            if(rs.next() == false)
            {
                return null;
            }

            int id = rs.getInt("ID");
            boolean revoked = rs.getBoolean("REVOKED");
            return new SubjectKeyProfileTriple(id, subjectFp, keyFp, profile, revoked);
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    boolean isCertForSubjectIssued(X509CertificateWithMetaInfo caCert, String subjectFp, String profile)
    throws SQLException
    {
        return isCertIssuedForFp("SHA1_FP_SUBJECT", caCert, subjectFp, profile);
    }

    boolean isCertForKeyIssued(X509CertificateWithMetaInfo caCert, String keyFp, String profile)
    throws SQLException
    {
        return isCertIssuedForFp("SHA1_FP_PK", caCert, keyFp, profile);
    }

    private boolean isCertIssuedForFp(String fpColumnName, X509CertificateWithMetaInfo caCert,
            String fp, String profile)
    throws SQLException
    {
        byte[] encodedCert = caCert.getEncodedCert();
        Integer caId =  caInfoStore.getCaIdForCert(encodedCert);

        if(caId == null)
        {
            return false;
        }

        Integer profileId = null;
        if(profile != null)
        {
            profileId = certprofileStore.getId(profile);
            if(profileId == null)
            {
                return false;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("COUNT(ID) FROM CERT WHERE ").append(fpColumnName).append("=?");
        sb.append(" AND CAINFO_ID=?");
        if(profile != null)
        {
            sb.append(" AND CERTPROFILEINFO_ID=?");
        }
        String sql = dataSource.createFetchFirstSelectSQL(sb.toString(), 1);
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setString(idx++, fp);
            ps.setInt(idx++, caId);
            if(profile != null)
            {
                ps.setInt(idx++, profileId);
            }

            rs = ps.executeQuery();

            rs.next();
            return rs.getInt(1) > 0;
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    private String fp(byte[] data)
    {
        return IoCertUtil.sha1sum(data);
    }

    private int getCaId(X509CertificateWithMetaInfo caCert)
    throws SQLException, OperationException
    {
        byte[] encodedCert = caCert.getEncodedCert();
        Integer id =  caInfoStore.getCaIdForCert(encodedCert);
        if(id == null)
        {
            throw new IllegalStateException("Could not find CA with subject  '" + caCert.getSubject() + "' in table " +
                    caInfoStore.getTable() + ", please start XiPKI in master mode first the restart this XiPKI system");
        }
        return id.intValue();
    }

    void addCa(X509CertificateWithMetaInfo caCert)
    throws SQLException, OperationException
    {
        byte[] encodedCert = caCert.getEncodedCert();
        if(caInfoStore.getCaIdForCert(encodedCert) != null)
        {
            return;
        }

        String hexSha1Fp = fp(encodedCert);

        String tblName = caInfoStore.getTable();
        long maxId = dataSource.getMax(null, tblName, "ID");
        int id = (int) maxId + 1;

        final String SQL_ADD_CAINFO =
                "INSERT INTO " + tblName +
                " (ID, SUBJECT, SHA1_FP_CERT, CERT)" +
                " VALUES (?, ?, ?, ?)";
        PreparedStatement ps = borrowPreparedStatement(SQL_ADD_CAINFO);

        try
        {
            String b64Cert = Base64.toBase64String(encodedCert);
            String subject = caCert.getSubject();
            int idx = 1;
            ps.setInt(idx++, id);
            ps.setString(idx++, subject);
            ps.setString(idx++, hexSha1Fp);
            ps.setString(idx++, b64Cert);

            ps.execute();

            CertBasedIdentityEntry newInfo = new CertBasedIdentityEntry(id, subject, hexSha1Fp, b64Cert);
            caInfoStore.addIdentityEntry(newInfo);
        } finally
        {
            releaseDbResources(ps, null);
        }
    }

    private int getUserId(String user)
    throws SQLException
    {
        Integer id = usernameIdCache.get(user);
        if(id != null)
        {
            return id.intValue();
        }

        String sql = "ID FROM USERNAME WHERE NAME=?";
        sql = dataSource.createFetchFirstSelectSQL(sql, 1);
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setString(idx++, user);
            rs = ps.executeQuery();
            if(rs.next())
            {
                id = rs.getInt("ID");
            }
        } finally
        {
            releaseDbResources(ps, rs);
        }

        if(id == null)
        {
            int userId = (int) dataSource.nextSeqValue("USER_ID");
            final String SQL_ADD_USER = "INSERT INTO USERNAME (ID, NAME) VALUES (?, ?)";
            try
            {
                ps = borrowPreparedStatement(SQL_ADD_USER);
                ps.setInt(1, userId);
                ps.setString(2, user);
                ps.executeUpdate();
                id = userId;
            } finally
            {
                releaseDbResources(ps, null);
            }
        }

        usernameIdCache.put(user, id);
        return id;
    }

    private int getRequestorId(String name)
    throws SQLException
    {
        return getIdForName(name, requestorInfoStore);
    }

    void addRequestorName(String name)
    throws SQLException
    {
        addName(name, requestorInfoStore);
    }

    private int getPublisherId(String name)
    throws SQLException
    {
        return getIdForName(name, publisherStore);
    }

    void addPublisherName(String name)
    throws SQLException
    {
        addName(name, publisherStore);
    }

    private int getCertprofileId(String name)
    throws SQLException
    {
        return getIdForName(name, certprofileStore);
    }

    void addCertprofileName(String name)
    throws SQLException
    {
        addName(name, certprofileStore);
    }

    private int getIdForName(String name, NameIdStore store)
    throws SQLException
    {
        if(name == null)
        {
            return -1;
        }

        Integer id = store.getId(name);
        if(id == null)
        {
            throw new IllegalStateException("Could not find entry named " + name + " in table " +
                    store.getTable() + ", please start XiPKI in master mode first and then restart this XiPKI system");
        }
        return id.intValue();
    }

    private void addName(String name, NameIdStore store)
    throws SQLException
    {
        if(store.getId(name) != null)
        {
            return;
        }

        String tblName = store.getTable();
        long maxId = dataSource.getMax(null, tblName, "ID");
        int id = (int) maxId + 1;

        final String sql = "INSERT INTO " + tblName + " (ID, NAME) VALUES (?, ?)";
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setInt(idx++, id);
            ps.setString(idx++, name);

            ps.execute();
            store.addEntry(name, id);
        }finally
        {
            releaseDbResources(ps, null);
        }
    }

    private PreparedStatement[] borrowPreparedStatements(String... sqlQueries)
    throws SQLException
    {
        PreparedStatement[] pss = new PreparedStatement[sqlQueries.length];

        Connection c = dataSource.getConnection();
        if(c != null)
        {
            final int n = sqlQueries.length;
            for(int i = 0; i < n; i++)
            {
                pss[i] = dataSource.prepareStatement(c, sqlQueries[i]);
                if(pss[i] == null)
                {
                    for(int j = 0; j < i; j++)
                    {
                        try
                        {
                            pss[j].close();
                        }catch(Throwable t)
                        {
                            LOG.warn("Could not close preparedStatement", t);
                        }
                    }

                    try
                    {
                        c.close();
                    }catch(Throwable t)
                    {
                        LOG.warn("Could not close connection", t);
                    }

                    throw new SQLException("Cannot create prepared statement for " + sqlQueries[i]);
                }
            }
        }

        return pss;
    }

    private PreparedStatement borrowPreparedStatement(String sqlQuery)
    throws SQLException
    {
        PreparedStatement ps = null;
        Connection c = dataSource.getConnection();
        if(c != null)
        {
            ps = dataSource.prepareStatement(c, sqlQuery);
        }

        if(ps == null)
        {
            throw new SQLException("Cannot create prepared statement for " + sqlQuery);
        }

        return ps;
    }

    private void releaseDbResources(Statement ps, ResultSet rs)
    {
        dataSource.releaseResources(ps, rs);
    }

    boolean isHealthy()
    {
        final String sql = "SELECT ID FROM CAINFO";

        try
        {
            PreparedStatement ps = borrowPreparedStatement(sql);

            ResultSet rs = null;
            try
            {
                rs = ps.executeQuery();
            }finally
            {
                releaseDbResources(ps, rs);
            }
            return true;
        }catch(Exception e)
        {
            LOG.error("isHealthy(). {}: {}", e.getClass().getName(), e.getMessage());
            LOG.debug("isHealthy()", e);
            return false;
        }
    }

    String getLatestSN(X500Name nameWithSN)
    throws OperationException
    {
        RDN[] rdns1 = nameWithSN.getRDNs();
        RDN[] rdns2 = new RDN[rdns1.length];
        for(int i = 0; i < rdns1.length; i++)
        {
            RDN rdn = rdns1[i];
            if(rdn.getFirst().getType().equals(ObjectIdentifiers.DN_SERIALNUMBER))
            {
                rdns2[i] = new RDN(ObjectIdentifiers.DN_SERIALNUMBER, new DERPrintableString("%"));
            }
            else
            {
                rdns2[i] = rdn;
            }
        }

        String namePattern = IoCertUtil.canonicalizeName(new X500Name(rdns2));

        String sql = "SUBJECT FROM CERT WHERE SUBJECT LIKE ?";
        sql = dataSource.createFetchFirstSelectSQL(sql, 1, "NOTBEFORE DESC");
        ResultSet rs = null;
        PreparedStatement ps;
        try
        {
            ps = borrowPreparedStatement(sql);
        } catch (SQLException e)
        {
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }

        try
        {
            ps.setString(1, namePattern);
            rs = ps.executeQuery();
            if(rs.next())
            {
                String str = rs.getString("SUBJECT");
                X500Name lastName = new X500Name(str);
                RDN[] rdns = lastName.getRDNs(ObjectIdentifiers.DN_SERIALNUMBER);
                if(rdns == null || rdns.length == 0)
                {
                    return null;
                }
                else
                {
                    return IETFUtils.valueToString(rdns[0].getFirst().getValue());
                }
            }
        }catch(SQLException e)
        {
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }finally
        {
            releaseDbResources(ps, rs);
        }

        return null;
    }

    Long getNotBeforeOfFirstCertStartsWithCN(String commonName, String profileName)
    throws SQLException
    {
        Integer profileId = certprofileStore.getId(profileName);
        if(profileId == null)
        {
            return null;
        }

        String sql = "NOTBEFORE FROM CERT WHERE CERTPROFILEINFO_ID=? AND SUBJECT LIKE ?";
        sql = dataSource.createFetchFirstSelectSQL(sql, 1, "NOTBEFORE ASC");
        ResultSet rs = null;
        PreparedStatement ps = borrowPreparedStatement(sql);

        try
        {
            int idx = 1;
            ps.setInt(idx++, profileId.intValue());
            ps.setString(idx++, "%cn=" + commonName + "%");

            rs = ps.executeQuery();

            if(rs.next() == false)
            {
                return null;
            }

            long notBefore = rs.getLong("NOTBEFORE");

            return notBefore == 0 ? null : notBefore;
        }finally
        {
            releaseDbResources(ps, rs);
        }
    }

    void commitNextSerialIfLess(String caName, long nextSerial)
    throws SQLException
    {
        Connection conn = dataSource.getConnection();
        PreparedStatement ps = null;
        try
        {
            final String SQL_SELECT = "SELECT NEXT_SERIAL FROM CA WHERE NAME = '" + caName + "'";
            ResultSet rs = null;
            long nextSerialInDB;

            try
            {
                ps = conn.prepareStatement(SQL_SELECT);
                rs = ps.executeQuery();
                rs.next();
                nextSerialInDB = rs.getLong("NEXT_SERIAL");
            }finally
            {
                try
                {
                    ps.close();
                }catch(SQLException e)
                {
                }

                if(rs != null)
                {
                    try
                    {
                        rs.close();
                    }catch(SQLException e)
                    {
                    }
                }
            }

            if(nextSerialInDB < nextSerial)
            {
                ps = conn.prepareStatement("UPDATE CA SET NEXT_SERIAL=? WHERE NAME=?");
                ps.setLong(1, nextSerial);
                ps.setString(2, caName);
                ps.executeUpdate();
            }
        }finally
        {
            dataSource.releaseResources(ps, null);
        }
    }

    long nextSerial(String seqName)
    throws SQLException
    {
        return dataSource.nextSeqValue(seqName);
    }

    private static void setBoolean(PreparedStatement ps, int index, boolean b)
    throws SQLException
    {
        ps.setInt(index, b ? 1 : 0);
    }

}
