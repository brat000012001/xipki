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

import java.math.BigInteger;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.ca.api.OperationException;
import org.xipki.ca.api.OperationException.ErrorCode;
import org.xipki.ca.api.publisher.CertificateInfo;
import org.xipki.ca.common.X509CertificateWithMetaInfo;
import org.xipki.ca.server.CertRevocationInfoWithSerial;
import org.xipki.ca.server.CertStatus;
import org.xipki.ca.server.SubjectKeyProfileTriple;
import org.xipki.database.api.DataSourceWrapper;
import org.xipki.security.common.CertRevocationInfo;
import org.xipki.security.common.LogUtil;
import org.xipki.security.common.ParamChecker;

/**
 * @author Lijun Liao
 */

public class CertificateStore
{
    private static final Logger LOG = LoggerFactory.getLogger(CertificateStore.class);
    private final CertStoreQueryExecutor queryExecutor;

    public CertificateStore(DataSourceWrapper dataSource)
    throws SQLException
    {
        ParamChecker.assertNotNull("dataSource", dataSource);

        this.queryExecutor = new CertStoreQueryExecutor(dataSource);
    }

    public boolean addCertificate(CertificateInfo certInfo)
    {
        try
        {
            queryExecutor.addCert(certInfo.getIssuerCert(),
                    certInfo.getCert(),
                    certInfo.getSubjectPublicKey(),
                    certInfo.getProfileName(),
                    certInfo.getRequestor(),
                    certInfo.getUser());
        } catch (Exception e)
        {
            LOG.error("Could not save certificate {}: {}. Message: {}",
                    new Object[]{certInfo.getCert().getSubject(),
                        Base64.toBase64String(certInfo.getCert().getEncodedCert()),
                        e.getMessage()});
            LOG.debug("error", e);
            return false;
        }

        return true;
    }

    public void addToPublishQueue(String publisherName, int certId, X509CertificateWithMetaInfo caCert)
    throws OperationException
    {
        try
        {
            queryExecutor.addToPublishQueue(publisherName, certId, caCert);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public void removeFromPublishQueue(String publisherName, int certId)
    throws OperationException
    {
        try
        {
            queryExecutor.removeFromPublishQueue(publisherName, certId);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public void clearPublishQueue(X509CertificateWithMetaInfo caCert, String publisherName)
    throws OperationException, SQLException
    {
        queryExecutor.clearPublishQueue(caCert, publisherName);
    }

    public long getMaxIdOfDeltaCRLCache(X509CertificateWithMetaInfo caCert)
    throws OperationException
    {
        try
        {
            return queryExecutor.getMaxIdOfDeltaCRLCache(caCert);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public void clearDeltaCRLCache(X509CertificateWithMetaInfo caCert, long maxId)
    throws OperationException, SQLException
    {
        queryExecutor.clearDeltaCRLCache(caCert, maxId);
    }

    public CertWithRevocationInfo revokeCertificate(X509CertificateWithMetaInfo caCert,
            BigInteger serialNumber, CertRevocationInfo revInfo, boolean force, boolean publishToDeltaCRLCache)
    throws OperationException
    {
        try
        {
            CertWithRevocationInfo revokedCert = queryExecutor.revokeCert(
                    caCert, serialNumber, revInfo, force, publishToDeltaCRLCache);
            if(revokedCert == null)
            {
                LOG.info("Could not revoke non-existing certificate issuer={}, serialNumber={}",
                    caCert.getSubject(), serialNumber);
            }
            else
            {
                LOG.info("revoked certificate issuer={}, serialNumber={}", caCert.getSubject(), serialNumber);
            }

            return revokedCert;
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public X509CertificateWithMetaInfo unrevokeCertificate(X509CertificateWithMetaInfo caCert,
            BigInteger serialNumber, boolean force, boolean publishToDeltaCRLCache)
    throws OperationException
    {
        try
        {
            X509CertificateWithMetaInfo unrevokedCert = queryExecutor.unrevokeCert(
                    caCert, serialNumber, force, publishToDeltaCRLCache);
            if(unrevokedCert == null)
            {
                LOG.info("Could not unrevoke non-existing certificate issuer={}, serialNumber={}",
                    caCert.getSubject(), serialNumber);
            }
            else
            {
                LOG.info("revoked certificate issuer={}, serialNumber={}", caCert.getSubject(), serialNumber);
            }

            return unrevokedCert;
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    X509CertificateWithMetaInfo getCert(X509CertificateWithMetaInfo caCert, BigInteger serialNumber)
    throws OperationException, SQLException
    {
        try
        {
            return queryExecutor.getCert(caCert, serialNumber);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public void removeCertificate(X509CertificateWithMetaInfo caCert,
            BigInteger serialNumber)
    throws OperationException
    {
        try
        {
            queryExecutor.removeCertificate(caCert, serialNumber);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public boolean addCRL(X509CertificateWithMetaInfo caCert, X509CRL crl)
    {
        try
        {
            queryExecutor.addCRL(caCert, crl);
            return true;
        } catch (Exception e)
        {
            LOG.error("Could not add CRL ca={}, thisUpdate={}: {}, ",
                new Object[]{caCert.getSubject(),
                    crl.getThisUpdate(), e.getMessage()});
            LOG.debug("Exception", e);
            return false;
        }
    }

    public int getNextFreeCRLNumber(X509CertificateWithMetaInfo caCert)
    throws OperationException
    {
        try
        {
            return queryExecutor.getNextFreeCrlNumber(caCert);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public long getThisUpdateOfCurrentCRL(X509CertificateWithMetaInfo caCert)
    throws OperationException
    {
        try
        {
            return queryExecutor.getThisUpdateOfCurrentCRL(caCert);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public byte[] getEncodedCRL(X509CertificateWithMetaInfo caCert, BigInteger crlNumber)
    {
        try
        {
            return queryExecutor.getEncodedCRL(caCert, crlNumber);
        } catch (Exception e)
        {
            LOG.error("Could not get CRL ca={}: error message: {}",
                    caCert.getSubject(),
                    e.getMessage());
            LOG.debug("Exception", e);
            return null;
        }
    }

    public int cleanupCRLs(X509CertificateWithMetaInfo caCert, int numCRLs)
    {
        try
        {
            return queryExecutor.cleanupCRLs(caCert, numCRLs);
        } catch (Exception e)
        {
            LOG.error("Could not cleanup CRLs ca={}: error message: {}",
                    caCert.getSubject(),
                    e.getMessage());
            LOG.debug("Exception", e);
            return 0;
        }
    }

    public CertStatus getCertStatusForSubject(X509CertificateWithMetaInfo caCert, X500Principal subject)
    {
        try
        {
            return queryExecutor.getCertStatusForSubject(caCert, subject);
        } catch (SQLException e)
        {
            LOG.error("queryExecutor.getCertStatusForSubject. SQLException: {}", e.getMessage());
            LOG.debug("queryExecutor.getCertStatusForSubject", e);
            return CertStatus.Unknown;
        }
    }

    public CertStatus getCertStatusForSubject(X509CertificateWithMetaInfo caCert, X500Name subject)
    {
        try
        {
            return queryExecutor.getCertStatusForSubject(caCert, subject);
        } catch (SQLException e)
        {
            final String message = "queryExecutor.getCertStatusForSubject";
            if(LOG.isErrorEnabled())
            {
                LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
            }
            LOG.debug(message, e);
            return CertStatus.Unknown;
        }
    }

    /**
     * Returns the first serial number ascend sorted {@code numEntries} revoked certificates
     * which are not expired at {@code notExpiredAt} and their serial numbers are not less than
     * {@code startSerial}.
     * @param caCert
     * @param notExpiredAt
     * @param startSerial
     * @param numEntries
     * @return
     * @throws SQLException
     */
    public List<CertRevocationInfoWithSerial> getRevokedCertificates(X509CertificateWithMetaInfo caCert,
            Date notExpiredAt, BigInteger startSerial, int numEntries,
            boolean onlyCACerts, boolean onlyUserCerts)
    throws OperationException
    {
        try
        {
            return queryExecutor.getRevokedCertificates(caCert, notExpiredAt, startSerial, numEntries,
                    onlyCACerts, onlyUserCerts);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public List<CertRevocationInfoWithSerial> getCertificatesForDeltaCRL(
            X509CertificateWithMetaInfo caCert, BigInteger startSerial, int numEntries,
            boolean onlyCACerts, boolean onlyUserCerts)
    throws OperationException
    {
        try
        {
            return queryExecutor.getCertificatesForDeltaCRL(caCert, startSerial, numEntries,
                    onlyCACerts, onlyUserCerts);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public List<BigInteger> getCertSerials(X509CertificateWithMetaInfo caCert,
            Date notExpiredAt, BigInteger startSerial, int numEntries, boolean onlyRevoked,
            boolean onlyCACerts, boolean onlyUserCerts)
    throws OperationException
    {
        try
        {
            return queryExecutor.getSerialNumbers(caCert, notExpiredAt, startSerial, numEntries, onlyRevoked,
                    onlyCACerts, onlyUserCerts);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public List<BigInteger> getExpiredCertSerials(X509CertificateWithMetaInfo caCert,
            long expiredAt, int numEntries, String certProfile, String userLike)
    throws OperationException
    {
        try
        {
            return queryExecutor.getExpiredSerialNumbers(caCert, expiredAt, numEntries, certProfile, userLike);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public int getNumOfExpiredCerts(X509CertificateWithMetaInfo caCert, long expiredAt,
            String certProfile, String userLike)
    throws OperationException
    {
        try
        {
            return queryExecutor.getNumOfExpiredCerts(caCert, expiredAt, certProfile, userLike);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public List<Integer> getPublishQueueEntries(X509CertificateWithMetaInfo caCert,
            String publisherName, int numEntries)
    throws OperationException
    {
        try
        {
            return queryExecutor.getPublishQueueEntries(caCert, publisherName, numEntries);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public CertWithRevocationInfo getCertWithRevocationInfo(X509CertificateWithMetaInfo caCert,
            BigInteger serial)
    throws OperationException
    {
        try
        {
            return queryExecutor.getCertWithRevocationInfo(caCert, serial);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public CertificateInfo getCertificateInfoForSerial(X509CertificateWithMetaInfo caCert, BigInteger serial)
    throws OperationException, CertificateException
    {
        try
        {
            return queryExecutor.getCertificateInfo(caCert, serial);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public Long getGreatestSerialNumber(X509CertificateWithMetaInfo caCert)
    throws OperationException
    {
        try
        {
            return queryExecutor.getGreatestSerialNumber(caCert);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public boolean isHealthy()
    {
        return queryExecutor.isHealthy();
    }

    public SubjectKeyProfileTriple getLatestCert(X509CertificateWithMetaInfo caCert, String subjectFp,
            String keyFp, String profile)
    throws OperationException
    {
        try
        {
            return queryExecutor.getLatestCert(caCert, subjectFp, keyFp, profile);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public boolean isCertForSubjectIssued(X509CertificateWithMetaInfo caCert, String subjectFp)
    throws OperationException
    {
        return isCertForSubjectIssued(caCert, subjectFp, null);
    }

    public boolean isCertForSubjectIssued(X509CertificateWithMetaInfo caCert, String subjectFp, String profile)
    throws OperationException
    {
        try
        {
            return queryExecutor.isCertForSubjectIssued(caCert, subjectFp, profile);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public boolean isCertForKeyIssued(X509CertificateWithMetaInfo caCert, String keyFp)
    throws OperationException
    {
        return isCertForKeyIssued(caCert, keyFp, null);
    }

    public boolean isCertForKeyIssued(X509CertificateWithMetaInfo caCert, String keyFp, String profile)
    throws OperationException
    {
        try
        {
            return queryExecutor.isCertForKeyIssued(caCert, keyFp, profile);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public CertificateInfo getCertificateInfoForId(X509CertificateWithMetaInfo caCert, int certId)
    throws OperationException, CertificateException
    {
        try
        {
            return queryExecutor.getCertForId(caCert, certId);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public X509CertificateWithMetaInfo getCertForId(int certId)
    throws OperationException
    {
        try
        {
            return queryExecutor.getCertForId(certId);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public String getLatestSN(X500Name nameWithSN)
    throws OperationException
    {
        return queryExecutor.getLatestSN(nameWithSN);
    }

    public Long getNotBeforeOfFirstCertStartsWithCN(String commonName, String profileName)
    throws OperationException
    {
        try
        {
            return queryExecutor.getNotBeforeOfFirstCertStartsWithCN(commonName, profileName);
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public boolean containsCACertificates(X509CertificateWithMetaInfo caCert)
    throws OperationException
    {
        try
        {
            return queryExecutor.containsCertificates(caCert, true) == false;
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public boolean containsUserCertificates(X509CertificateWithMetaInfo caCert)
    throws OperationException
    {
        try
        {
            return queryExecutor.containsCertificates(caCert, false) == false;
        } catch (SQLException e)
        {
            LOG.debug("SQLException", e);
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        }
    }

    public long nextSerial(String seqName)
    throws OperationException
    {
        try
        {
            return queryExecutor.nextSerial(seqName);
        } catch (SQLException e)
        {
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        } catch (RuntimeException e)
        {
            throw new OperationException(ErrorCode.System_Failure, e.getMessage());
        }
    }

    public void commitNextSerialIfLess(String caName, long nextSerial)
    throws OperationException
    {
        try
        {
            queryExecutor.commitNextSerialIfLess(caName, nextSerial);
        } catch (SQLException e)
        {
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        } catch (RuntimeException e)
        {
            throw new OperationException(ErrorCode.System_Failure, e.getMessage());
        }
    }

    public void addCa(X509CertificateWithMetaInfo caCert)
    throws OperationException
    {
        try
        {
            queryExecutor.addCa(caCert);
        } catch (SQLException e)
        {
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        } catch (RuntimeException e)
        {
            throw new OperationException(ErrorCode.System_Failure, e.getMessage());
        }
    }

    public void addRequestorName(String name)
    throws OperationException
    {
        try
        {
            queryExecutor.addRequestorName(name);
        } catch (SQLException e)
        {
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        } catch (RuntimeException e)
        {
            throw new OperationException(ErrorCode.System_Failure, e.getMessage());
        }
    }

    public void addPublisherName(String name)
    throws OperationException
    {
        try
        {
            queryExecutor.addPublisherName(name);
        } catch (SQLException e)
        {
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        } catch (RuntimeException e)
        {
            throw new OperationException(ErrorCode.System_Failure, e.getMessage());
        }
    }

    public void addCertprofileName(String name)
    throws OperationException
    {
        try
        {
            queryExecutor.addCertprofileName(name);
        } catch (SQLException e)
        {
            throw new OperationException(ErrorCode.DATABASE_FAILURE, e.getMessage());
        } catch (RuntimeException e)
        {
            throw new OperationException(ErrorCode.System_Failure, e.getMessage());
        }
    }
}
