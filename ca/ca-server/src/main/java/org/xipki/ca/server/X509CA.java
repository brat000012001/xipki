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

package org.xipki.ca.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.CertificateList;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.IssuingDistributionPoint;
import org.bouncycastle.asn1.x509.ReasonFlags;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory;
import org.bouncycastle.jce.provider.X509CRLObject;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.x509.extension.X509ExtensionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.audit.api.AuditEvent;
import org.xipki.audit.api.AuditEventData;
import org.xipki.audit.api.AuditLevel;
import org.xipki.audit.api.AuditLoggingService;
import org.xipki.audit.api.AuditLoggingServiceRegister;
import org.xipki.audit.api.AuditStatus;
import org.xipki.ca.api.OperationException;
import org.xipki.ca.api.OperationException.ErrorCode;
import org.xipki.ca.api.profile.ExtensionOccurrence;
import org.xipki.ca.api.profile.ExtensionTuple;
import org.xipki.ca.api.profile.ExtensionTuples;
import org.xipki.ca.api.profile.SpecialCertProfileBehavior;
import org.xipki.ca.api.profile.SubjectInfo;
import org.xipki.ca.api.profile.X509Util;
import org.xipki.ca.api.publisher.CertificateInfo;
import org.xipki.ca.common.BadCertTemplateException;
import org.xipki.ca.common.BadFormatException;
import org.xipki.ca.common.CAMgmtException;
import org.xipki.ca.common.CAStatus;
import org.xipki.ca.common.CertProfileException;
import org.xipki.ca.common.X509CertificateWithMetaInfo;
import org.xipki.ca.server.mgmt.CAInfo;
import org.xipki.ca.server.mgmt.CAManagerImpl;
import org.xipki.ca.server.mgmt.CRLControl;
import org.xipki.ca.server.mgmt.CRLControl.UpdateMode;
import org.xipki.ca.server.mgmt.IdentifiedCertProfile;
import org.xipki.ca.server.mgmt.IdentifiedCertPublisher;
import org.xipki.ca.server.mgmt.api.DuplicationMode;
import org.xipki.ca.server.mgmt.api.ValidityMode;
import org.xipki.ca.server.store.CertWithRevocationInfo;
import org.xipki.ca.server.store.CertificateStore;
import org.xipki.security.api.ConcurrentContentSigner;
import org.xipki.security.api.NoIdleSignerException;
import org.xipki.security.common.CRLReason;
import org.xipki.security.common.CertRevocationInfo;
import org.xipki.security.common.CustomObjectIdentifiers;
import org.xipki.security.common.HashAlgoType;
import org.xipki.security.common.HashCalculator;
import org.xipki.security.common.HealthCheckResult;
import org.xipki.security.common.IoCertUtil;
import org.xipki.security.common.LogUtil;
import org.xipki.security.common.ObjectIdentifiers;
import org.xipki.security.common.ParamChecker;

/**
 * @author Lijun Liao
 */

public class X509CA
{

    private class ScheduledNextSerialCommitService implements Runnable
    {
        private boolean inProcess = false;
        @Override
        public void run()
        {
            if(inProcess)
            {
                return;
            }

            inProcess = true;
            try
            {
                caInfo.commitNextSerial();
            } catch (Throwable t)
            {
                final String message = "Could not commit the next_serial";
                if(LOG.isErrorEnabled())
                {
                    LOG.error(LogUtil.buildExceptionLogFormat(message), t.getClass().getName(), t.getMessage());
                }
                LOG.debug(message, t);

            } finally
            {
                inProcess = false;
            }

        }
    }

    private class ScheduledExpiredCertsRemover implements Runnable
    {
        private boolean inProcess = false;
        @Override
        public void run()
        {
            if(inProcess)
            {
                return;
            }

            inProcess = true;
            boolean allCertsRemoved = true;
            long startTime = System.currentTimeMillis();
            RemoveExpiredCertsInfo task = null;
            try
            {
                task = removeExpiredCertsQueue.poll();
                if(task == null)
                {
                    return;
                }

                String caName = caInfo.getName();

                final int numEntries = 100;

                X509CertificateWithMetaInfo caCert = caInfo.getCertificate();
                long expiredAt = task.getExpiredAt();

                List<BigInteger> serials;

                do
                {
                    serials = certstore.getExpiredCertSerials(caCert, expiredAt, numEntries,
                            task.getCertProfile(), task.getUserLike());

                    for(BigInteger serial : serials)
                    {
                        if((caInfo.isSelfSigned() && caInfo.getSerialNumber().equals(serial)) == false)
                        {
                            boolean removed = false;
                            try
                            {
                                removed = do_removeCertificate(serial) != null;
                            }catch(Throwable t)
                            {
                                final String message = "Could not remove expired certificate";
                                if(LOG.isErrorEnabled())
                                {
                                    LOG.error(LogUtil.buildExceptionLogFormat(message), t.getClass().getName(), t.getMessage());
                                }
                                removed = false;
                            } finally
                            {
                                AuditLoggingService audit = getAuditLoggingService();
                                if(audit != null);
                                {
                                    AuditEvent auditEvent = newAuditEvent();
                                    if(removed)
                                    {
                                        auditEvent.setLevel(AuditLevel.INFO);
                                        auditEvent.setStatus(AuditStatus.OK);
                                    }
                                    else
                                    {
                                        auditEvent.setLevel(AuditLevel.ERROR);
                                        auditEvent.setStatus(AuditStatus.FAILED);
                                    }
                                    auditEvent.addEventData(new AuditEventData("CA", caName));
                                    auditEvent.addEventData(new AuditEventData("serialNumber", serial.toString()));
                                    auditEvent.addEventData(new AuditEventData("eventType", "REMOVE_EXPIRED_CERT"));
                                    audit.logEvent(auditEvent);
                                }

                                if(removed == false)
                                {
                                    allCertsRemoved = false;
                                }
                            }
                        }
                    }
                }while(serials.size() >= numEntries && allCertsRemoved);

            } catch (Throwable t)
            {
                if(allCertsRemoved == false && task != null)
                {
                    removeExpiredCertsQueue.add(task);
                }

                final String message = "Could not remove expired certificates";
                if(LOG.isErrorEnabled())
                {
                    LOG.error(LogUtil.buildExceptionLogFormat(message), t.getClass().getName(), t.getMessage());
                }
                LOG.debug(message, t);
            } finally
            {
                AuditLoggingService audit = getAuditLoggingService();
                if(audit != null && task != null);
                {
                    long durationMillis = System.currentTimeMillis() - startTime;
                    AuditEvent auditEvent = newAuditEvent();
                    auditEvent.addEventData(new AuditEventData("duration", durationMillis));
                    auditEvent.addEventData(new AuditEventData("CA", caInfo.getName()));
                    auditEvent.addEventData(new AuditEventData("cerProfile", task.getCertProfile()));
                    auditEvent.addEventData(new AuditEventData("user", task.getUserLike()));
                    auditEvent.addEventData(new AuditEventData("expiredAt",
                            new Date(task.getExpiredAt() * SECOND).toString()));
                    auditEvent.addEventData(new AuditEventData("eventType", "REMOVE_EXPIRED_CERTS"));

                    if(allCertsRemoved)
                    {
                        auditEvent.setLevel(AuditLevel.INFO);
                        auditEvent.setStatus(AuditStatus.OK);
                    }
                    else
                    {
                        auditEvent.setLevel(AuditLevel.ERROR);
                        auditEvent.setStatus(AuditStatus.FAILED);
                    }
                    audit.logEvent(auditEvent);
                }

                inProcess = false;
            }

        }
    }

    private class ScheduledCRLGenerationService implements Runnable
    {
        @Override
        public void run()
        {
            if(crlGenInProcess.get())
            {
                return;
            }

            crlGenInProcess.set(true);

            int thisInterval = -1;

            Date thisUpdate = new Date();
            long nowInSecond = thisUpdate.getTime() / SECOND;

            try
            {
                int lastInterval = caInfo.getLastCRLInterval();

                long lastCRLIntervalDateInSecond = caInfo.getLastCRLIntervalDate();
                CRLControl control = crlSigner.getCRLcontrol();

                boolean deltaCrl;
                if(lastCRLIntervalDateInSecond == 0) // first time
                {
                    deltaCrl = false;
                    thisInterval = 0;
                }
                else
                {
                    final int minutesSinceLastInterval = (int) (nowInSecond - lastCRLIntervalDateInSecond) / 60;
                    int skippedIntervals = 0;

                    // check whether it is the time to increase the number of intervals
                    if(control.getIntervalMinutes() != null)
                    {
                        int intervalMinute = control.getIntervalMinutes().intValue();
                        if(minutesSinceLastInterval < intervalMinute)
                        {
                            return;
                        }
                        else if(minutesSinceLastInterval % intervalMinute > 1)
                        {
                            skippedIntervals = minutesSinceLastInterval % intervalMinute - 1;
                        }
                    }
                    else
                    {
                        final int minutesPerDay = 24 * 60;
                        // in the last day the CA was not running
                        if(minutesSinceLastInterval > minutesPerDay)
                        {
                            if(minutesSinceLastInterval / minutesPerDay > 1)
                            {
                                skippedIntervals = minutesSinceLastInterval % minutesPerDay - 1;
                            }
                        }
                        // last interval date just in the last 10 minutes, ignore it
                        else if(minutesSinceLastInterval > 10)
                        {
                        }
                        else
                        {
                            int min = (int) ((nowInSecond / 60) % minutesPerDay);
                            int expectedMin = 60 * control.getIntervalDayTime().getHour() + control.getIntervalMinutes();
                            if(min - expectedMin < 0)
                            {
                                return;
                            }
                        }
                    }

                    thisInterval = lastInterval + 1;

                    boolean generateFullCRL = false;
                    boolean generateDeltaCRL = false;
                    for(int i = 0; i < 1 + skippedIntervals; i++)
                    {
                        int interval = thisInterval + i;
                        if(interval % control.getFullCRLIntervals() == 0)
                        {
                            generateFullCRL = true;
                            break;
                        }
                        else if(control.getDeltaCRLIntervals() > 0 && interval % control.getDeltaCRLIntervals() == 0)
                        {
                            generateDeltaCRL = true;
                        }
                    }

                    if(generateFullCRL == false && generateDeltaCRL == false)
                    {
                        return;
                    }

                    deltaCrl = generateFullCRL == false;
                }

                // find out the next interval for fullCRL and deltaCRL
                int nextFullCRLInterval = 0;
                int nextDeltaCRLInterval = 0;

                for(int i = thisInterval + 1; ; i++)
                {
                    if(i % control.getFullCRLIntervals() == 0)
                    {
                        nextFullCRLInterval = i;
                        break;
                    }

                    if(nextDeltaCRLInterval != 0 &&
                            control.getDeltaCRLIntervals() != 0 &&
                            i % control.getDeltaCRLIntervals() == 0)
                    {
                        nextDeltaCRLInterval = i;
                    }
                }

                int intervalOfNextUpdate;
                if(deltaCrl)
                {
                    intervalOfNextUpdate = nextDeltaCRLInterval == 0 ?
                            nextFullCRLInterval : Math.min(nextFullCRLInterval, nextDeltaCRLInterval);
                }
                else
                {
                    if(nextDeltaCRLInterval == 0)
                    {
                        intervalOfNextUpdate = nextFullCRLInterval;
                    }
                    else
                    {
                        intervalOfNextUpdate = control.isExtendedNextUpdate() ?
                                nextFullCRLInterval : Math.min(nextFullCRLInterval, nextDeltaCRLInterval);
                    }
                }

                Date nextUpdate;
                if(control.getIntervalMinutes() != null)
                {
                    int minutesTillNextUpdate = (intervalOfNextUpdate - thisInterval) * control.getIntervalMinutes()
                            + control.getOverlapMinutes();
                    nextUpdate = new Date(SECOND * (nowInSecond + minutesTillNextUpdate * 60));
                }
                else
                {
                    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    c.setTime(new Date(nowInSecond * SECOND));
                    c.add(Calendar.DAY_OF_YEAR, (intervalOfNextUpdate - thisInterval));
                    c.set(Calendar.HOUR_OF_DAY, control.getIntervalDayTime().getHour());
                    c.set(Calendar.MINUTE, control.getIntervalDayTime().getMinute());
                    c.add(Calendar.MINUTE, control.getOverlapMinutes());
                    c.set(Calendar.SECOND, 0);
                    c.set(Calendar.MILLISECOND, 0);
                    nextUpdate = c.getTime();
                }

                try
                {
                    long maxIdOfDeltaCRLCache = certstore.getMaxIdOfDeltaCRLCache(caInfo.getCertificate());
                    generateCRL(deltaCrl, thisUpdate, nextUpdate);

                    try
                    {
                        certstore.clearDeltaCRLCache(caInfo.getCertificate(), maxIdOfDeltaCRLCache);
                    } catch (Throwable t)
                    {
                        final String message = "Could not clear DeltaCRLCache of CA " + caInfo.getName();
                        if(LOG.isErrorEnabled())
                        {
                            LOG.error(LogUtil.buildExceptionLogFormat(message), t.getClass().getName(), t.getMessage());
                        }
                        LOG.debug(message, t);
                    }

                    try
                    {
                        caInfo.setLastCRLInterval(deltaCrl ? thisInterval : 0);
                        caInfo.setLastCRLIntervalDate(nowInSecond);
                        caManager.setCrlLastInterval(caInfo.getName(), caInfo.getLastCRLInterval(),
                                caInfo.getLastCRLIntervalDate());
                    } catch (Throwable t)
                    {
                        final String message = "Could not set the CRL lastInterval of CA " + caInfo.getName();
                        if(LOG.isErrorEnabled())
                        {
                            LOG.error(LogUtil.buildExceptionLogFormat(message), t.getClass().getName(), t.getMessage());
                        }
                        LOG.debug(message, t);
                    }
                }catch(OperationException e)
                {
                    final String message = "Error in generateCRL()";
                    if(LOG.isErrorEnabled())
                    {
                        LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                    }
                    LOG.debug(message, e);
                }
            } finally
            {
                crlGenInProcess.set(false);
            }
        }
    }

    private static long SECOND = 1000;
    private static long DAY = 24L * 60 * 60 * SECOND;

    private static Logger LOG = LoggerFactory.getLogger(X509CA.class);

    private final CertificateFactory cf;

    private final CAInfo caInfo;
    private final ConcurrentContentSigner caSigner;
    private final X500Name caSubjectX500Name;
    private final byte[] caSKI;
    private final GeneralNames caSubjectAltName;
    private final CertificateStore certstore;
    private final CrlSigner crlSigner;
    private final boolean masterMode;

    private final CAManagerImpl caManager;
    private Boolean tryXipkiNSStoVerify;
    private AtomicBoolean crlGenInProcess = new AtomicBoolean(false);

    private final ConcurrentSkipListMap<String, List<String>> pendingSubjectMap = new ConcurrentSkipListMap<>();
    private final ConcurrentSkipListMap<String, List<String>> pendingKeyMap = new ConcurrentSkipListMap<>();
    private final ConcurrentLinkedDeque<RemoveExpiredCertsInfo> removeExpiredCertsQueue = new ConcurrentLinkedDeque<>();

    private final AtomicInteger numActiveRevocations = new AtomicInteger(0);

    private AuditLoggingServiceRegister serviceRegister;

    public X509CA(
            CAManagerImpl caManager,
            CAInfo caInfo,
            ConcurrentContentSigner caSigner,
            CertificateStore certstore,
            CrlSigner crlSigner,
            boolean masterMode)
    throws OperationException
    {
        ParamChecker.assertNotNull("caManager", caManager);
        ParamChecker.assertNotNull("caInfo", caInfo);
        ParamChecker.assertNotNull("certstore", certstore);

        this.caManager = caManager;
        this.caInfo = caInfo;
        this.caSigner = caSigner;
        this.certstore = certstore;
        this.crlSigner = crlSigner;
        this.masterMode = masterMode;

        X509CertificateWithMetaInfo caCert = caInfo.getCertificate();

        // corrected the lastCRLIntervalDate if required
        if(crlSigner != null && caInfo.getLastCRLIntervalDate() == 0)
        {
            caInfo.setLastCRLIntervalDate(certstore.getThisUpdateOfCurrentCRL(caCert));
        }

        this.caSubjectX500Name = X500Name.getInstance(
                caCert.getCert().getSubjectX500Principal().getEncoded());

        try
        {
            this.caSKI = IoCertUtil.extractSKI(caCert.getCert());
        } catch (CertificateEncodingException e)
        {
            throw new OperationException(ErrorCode.INVALID_EXTENSION, e.getMessage());
        }

        byte[] encodedSubjectAltName = caCert.getCert().getExtensionValue(Extension.subjectAlternativeName.getId());
        if(encodedSubjectAltName == null)
        {
            this.caSubjectAltName = null;
        }
        else
        {
            try
            {
                this.caSubjectAltName = GeneralNames.getInstance(X509ExtensionUtil.fromExtensionValue(encodedSubjectAltName));
            } catch (IOException e)
            {
                throw new OperationException(ErrorCode.INVALID_EXTENSION, "invalid SubjectAltName extension in CA certificate");
            }
        }

        this.cf = new CertificateFactory();

        if(caInfo.useRandomSerialNumber() == false)
        {
            ScheduledNextSerialCommitService nextSerialCommitService = new ScheduledNextSerialCommitService();
            caManager.getScheduledThreadPoolExecutor().scheduleAtFixedRate(
                    nextSerialCommitService, 1, 1, TimeUnit.MINUTES); // commit the next_serial every 1 minute
        }

        if(masterMode == false)
        {
            return;
        }

        for(IdentifiedCertPublisher publisher : getPublishers())
        {
            publisher.issuerAdded(caCert);
        }

        // CRL generation services
        if(crlSigner != null && crlSigner.getCRLcontrol().getUpdateMode() == UpdateMode.interval)
        {
            ScheduledCRLGenerationService crlGenerationService = new ScheduledCRLGenerationService();
            caManager.getScheduledThreadPoolExecutor().scheduleAtFixedRate(crlGenerationService,
                    1, 1, TimeUnit.MINUTES);
        }

        ScheduledExpiredCertsRemover expiredCertsRemover = new ScheduledExpiredCertsRemover();
        caManager.getScheduledThreadPoolExecutor().scheduleAtFixedRate(expiredCertsRemover,
                10, 10, TimeUnit.MINUTES);
    }

    public CAInfo getCAInfo()
    {
        return caInfo;
    }

    public X500Name getCASubjectX500Name()
    {
        return caSubjectX500Name;
    }

    public CertificateList getCurrentCRL()
    throws OperationException
    {
        return getCRL(null);
    }

    /**
     *
     * @param crlNumber
     * @return
     * @throws OperationException
     */
    public CertificateList getCRL(BigInteger crlNumber)
    throws OperationException
    {
        LOG.info("START getCurrentCRL: ca={}, crlNumber={}", caInfo.getName(), crlNumber);
        boolean successfull = false;

        try
        {
            byte[] encodedCrl = certstore.getEncodedCRL(caInfo.getCertificate(), crlNumber);
            if(encodedCrl == null)
            {
                return null;
            }

            try
            {
                CertificateList crl = CertificateList.getInstance(encodedCrl);
                successfull = true;

                LOG.info("SUCCESSFULL getCurrentCRL: ca={}, thisUpdate={}", caInfo.getName(),
                        crl.getThisUpdate().getTime());

                return crl;
            } catch (RuntimeException e)
            {
                throw new OperationException(ErrorCode.System_Failure,
                        e.getClass().getName() + ": " + e.getMessage());
            }
        }finally
        {
            if(successfull == false)
            {
                LOG.info("FAILED getCurrentCRL: ca={}", caInfo.getName());
            }
        }
    }

    private void cleanupCRLs()
    throws OperationException
    {
        int numCrls = caInfo.getNumCrls();
        LOG.info("START cleanupCRLs: ca={}, numCrls={}", caInfo.getName(), numCrls);

        boolean successfull = false;

        try
        {
            int numOfRemovedCRLs;
            if(numCrls > 0)
            {
                numOfRemovedCRLs = certstore.cleanupCRLs(caInfo.getCertificate(), caInfo.getNumCrls());
            }
            else
            {
                numOfRemovedCRLs = 0;
            }
            successfull = true;
            LOG.info("SUCCESSFULL cleanupCRLs: ca={}, numOfRemovedCRLs={}", caInfo.getName(),
                    numOfRemovedCRLs);
        } catch (RuntimeException e)
        {
            throw new OperationException(ErrorCode.System_Failure,
                    e.getClass().getName() + ": " + e.getMessage());
        }
        finally
        {
            if(successfull == false)
            {
                LOG.info("FAILED cleanupCRLs: ca={}", caInfo.getName());
            }
        }
    }

    public X509CRL generateCRLonDemand()
    throws OperationException
    {
        if(masterMode == false)
        {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "CA cannot generate CRL at slave mode");
        }

        if(crlGenInProcess.get())
        {
            throw new OperationException(ErrorCode.System_Unavailable,
                    "TRY_LATER");
        }

        crlGenInProcess.set(true);
        try
        {
            Date thisUpdate = new Date();
            Date nextUpdate = getCRLNextUpdate(thisUpdate);
            if(nextUpdate != null && nextUpdate.after(thisUpdate) == false)
            {
                nextUpdate = null;
            }

            long maxIdOfDeltaCRLCache = certstore.getMaxIdOfDeltaCRLCache(caInfo.getCertificate());
            X509CRL crl = generateCRL(false, thisUpdate, nextUpdate);

            if(crl != null)
            {
                try
                {
                    certstore.clearDeltaCRLCache(caInfo.getCertificate(), maxIdOfDeltaCRLCache);
                } catch (Throwable t)
                {
                    final String message = "Could not clear DeltaCRLCache of CA " + caInfo.getName();
                    if(LOG.isErrorEnabled())
                    {
                        LOG.error(LogUtil.buildExceptionLogFormat(message), t.getClass().getName(), t.getMessage());
                    }
                    LOG.debug(message, t);
                }

                caInfo.setLastCRLInterval(0);
                caInfo.setLastCRLIntervalDate(thisUpdate.getTime() / SECOND);
                try
                {
                    caManager.setCrlLastInterval(caInfo.getName(), caInfo.getLastCRLInterval(),
                            caInfo.getLastCRLIntervalDate());
                } catch (Throwable t)
                {
                    final String message = "Could not set the CRL lastInterval of CA " + caInfo.getName();
                    if(LOG.isErrorEnabled())
                    {
                        LOG.error(LogUtil.buildExceptionLogFormat(message), t.getClass().getName(), t.getMessage());
                    }
                    LOG.debug(message, t);
                }
            }

            return crl;
        }finally
        {
            crlGenInProcess.set(false);
        }
    }

    private X509CRL generateCRL(boolean deltaCRL, Date thisUpdate, Date nextUpdate)
    throws OperationException
    {
        if(crlSigner == null)
        {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "CRL generation is not allowed");
        }

        LOG.info("START generateCRL: ca={}, deltaCRL={}, nextUpdate={}",
                new Object[]{caInfo.getName(), deltaCRL, nextUpdate});

        if(nextUpdate != null)
        {
            if(nextUpdate.getTime() - thisUpdate.getTime() < 10 * 60 * SECOND) // less than 10 minutes
            throw new OperationException(ErrorCode.CRL_FAILURE, "nextUpdate and thisUpdate are too close");
        }

        CRLControl crlControl = crlSigner.getCRLcontrol();
        boolean successfull = false;

        try
        {
            ConcurrentContentSigner signer = crlSigner.getSigner();

            CRLControl control = crlSigner.getCRLcontrol();

            boolean directCRL = signer == null;
            X500Name crlIssuer = directCRL ? caSubjectX500Name :
                X500Name.getInstance(signer.getCertificate().getSubjectX500Principal().getEncoded());

            X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(crlIssuer, thisUpdate);
            if(nextUpdate != null)
            {
                crlBuilder.setNextUpdate(nextUpdate);
            }

            BigInteger startSerial = BigInteger.ONE;
            final int numEntries = 100;

            X509CertificateWithMetaInfo caCert = caInfo.getCertificate();
            List<CertRevocationInfoWithSerial> revInfos;
            boolean isFirstCRLEntry = true;

            Date notExpireAt;
            if(control.isIncludeExpiredCerts())
            {
                notExpireAt = new Date(0);
            }
            else
            {
                // 10 minutes buffer
                notExpireAt = new Date(thisUpdate.getTime() - 600L * SECOND);
            }

            do
            {
                if(deltaCRL)
                {
                    revInfos = certstore.getCertificatesForDeltaCRL(caCert, startSerial, numEntries,
                            control.isOnlyContainsCACerts(), control.isOnlyContainsUserCerts());
                }
                else
                {
                    revInfos = certstore.getRevokedCertificates(caCert, notExpireAt, startSerial, numEntries,
                            control.isOnlyContainsCACerts(), control.isOnlyContainsUserCerts());
                }

                BigInteger maxSerial = BigInteger.ONE;
                for(CertRevocationInfoWithSerial revInfo : revInfos)
                {
                    BigInteger serial = revInfo.getSerial();
                    if(serial.compareTo(maxSerial) > 0)
                    {
                        maxSerial = serial;
                    }

                    CRLReason reason = revInfo.getReason();
                    Date revocationTime = revInfo.getRevocationTime();
                    Date invalidityTime = revInfo.getInvalidityTime();
                    if(invalidityTime != null && invalidityTime.equals(revocationTime))
                    {
                        invalidityTime = null;
                    }

                    if(directCRL || isFirstCRLEntry == false)
                    {
                        if(invalidityTime != null)
                        {
                            crlBuilder.addCRLEntry(revInfo.getSerial(), revocationTime,
                                    reason.getCode(), invalidityTime);
                        }
                        else
                        {
                            crlBuilder.addCRLEntry(revInfo.getSerial(), revocationTime,
                                    reason.getCode());
                        }
                    }
                    else
                    {
                        List<Extension> extensions = new ArrayList<>(3);
                        if(reason != CRLReason.UNSPECIFIED)
                        {
                            Extension ext = createReasonExtension(reason.getCode());
                            extensions.add(ext);
                        }
                        if(invalidityTime != null)
                        {
                            Extension ext = createInvalidityDateExtension(invalidityTime);
                            extensions.add(ext);
                        }

                        Extension ext = createCertificateIssuerExtension(caSubjectX500Name);
                        extensions.add(ext);

                        Extensions asn1Extensions = new Extensions(extensions.toArray(new Extension[0]));
                        crlBuilder.addCRLEntry(revInfo.getSerial(), revocationTime, asn1Extensions);
                        isFirstCRLEntry = false;
                    }
                }

                startSerial = maxSerial.add(BigInteger.ONE);

            }while(revInfos.size() >= numEntries);

            int crlNumber = certstore.getNextFreeCRLNumber(caCert);

            try
            {
                // AuthorityKeyIdentifier
                byte[] akiValues = directCRL ? this.caSKI : crlSigner.getSubjectKeyIdentifier();
                AuthorityKeyIdentifier aki = new AuthorityKeyIdentifier(akiValues);
                crlBuilder.addExtension(Extension.authorityKeyIdentifier, false, aki);

                // add extension CRL Number
                crlBuilder.addExtension(Extension.cRLNumber, false, new ASN1Integer(crlNumber));

                // IssuingDistributionPoint
                boolean onlyUserCerts = crlControl.isOnlyContainsUserCerts();
                if(onlyUserCerts == false)
                {
                    if(certstore.containsCACertificates(caCert) == false)
                    {
                        onlyUserCerts = true;
                    }
                }

                boolean onlyCACerts = crlControl.isOnlyContainsCACerts();
                if(onlyCACerts == false)
                {
                    if(certstore.containsUserCertificates(caCert) == false)
                    {
                        onlyCACerts = true;
                    }
                }

                if(onlyUserCerts && onlyCACerts)
                {
                    throw new RuntimeException("should not reach here");
                }

                IssuingDistributionPoint idp = new IssuingDistributionPoint(
                        (DistributionPointName) null, // distributionPoint,
                        onlyUserCerts, // onlyContainsUserCerts,
                        onlyCACerts, // onlyContainsCACerts,
                        (ReasonFlags) null, // onlySomeReasons,
                        directCRL == false, // indirectCRL,
                        false // onlyContainsAttributeCerts
                        );

                crlBuilder.addExtension(Extension.issuingDistributionPoint, true, idp);
            } catch (CertIOException e)
            {
                final String message = "crlBuilder.addExtension";
                if(LOG.isErrorEnabled())
                {
                    LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                }
                LOG.debug(message, e);
                throw new OperationException(ErrorCode.INVALID_EXTENSION, e.getMessage());
            }

            startSerial = BigInteger.ONE;
            if(deltaCRL == false && control.isEmbedsCerts()) // XiPKI extension
            {
                ASN1EncodableVector vector = new ASN1EncodableVector();

                List<BigInteger> serials;

                do
                {
                    serials = certstore.getCertSerials(caCert, notExpireAt, startSerial, numEntries, false,
                            control.isOnlyContainsCACerts(), control.isOnlyContainsUserCerts());

                    BigInteger maxSerial = BigInteger.ONE;
                    for(BigInteger serial : serials)
                    {
                        if(serial.compareTo(maxSerial) > 0)
                        {
                            maxSerial = serial;
                        }

                        CertificateInfo certInfo;
                        try
                        {
                            certInfo = certstore.getCertificateInfoForSerial(caCert, serial);
                        } catch (CertificateException e)
                        {
                            throw new OperationException(ErrorCode.System_Failure,
                                    "CertificateException: " + e.getMessage());
                        }

                        Certificate cert = Certificate.getInstance(certInfo.getCert().getEncodedCert());

                        ASN1EncodableVector v = new ASN1EncodableVector();
                        v.add(cert);
                        String profileName = certInfo.getProfileName();
                        if(profileName != null && profileName.isEmpty() == false)
                        {
                            v.add(new DERUTF8String(certInfo.getProfileName()));
                        }
                        ASN1Sequence certWithInfo = new DERSequence(v);

                        vector.add(certWithInfo);
                    }

                    startSerial = maxSerial.add(BigInteger.ONE);
                }while(serials.size() >= numEntries);

                try
                {
                    crlBuilder.addExtension(
                            new ASN1ObjectIdentifier(CustomObjectIdentifiers.id_crl_certset),
                                false, new DERSet(vector));
                } catch (CertIOException e)
                {
                    throw new OperationException(ErrorCode.INVALID_EXTENSION,
                            "CertIOException: " + e.getMessage());
                }
            }

            ConcurrentContentSigner concurrentSigner = (signer == null) ? caSigner : signer;
            ContentSigner contentSigner;
            try
            {
                contentSigner = concurrentSigner.borrowContentSigner();
            } catch (NoIdleSignerException e)
            {
                throw new OperationException(ErrorCode.System_Failure, "NoIdleSignerException: " + e.getMessage());
            }

            X509CRLHolder crlHolder;
            try
            {
                crlHolder = crlBuilder.build(contentSigner);
            }finally
            {
                concurrentSigner.returnContentSigner(contentSigner);
            }

            try
            {
                X509CRL crl = new X509CRLObject(crlHolder.toASN1Structure());
                publishCRL(crl);

                successfull = true;
                LOG.info("SUCCESSFULL generateCRL: ca={}, crlNumber={}, thisUpdate={}",
                        new Object[]{caInfo.getName(), crlNumber, crl.getThisUpdate()});

                // clean up the CRL
                if(deltaCRL == false)
                {
                    try
                    {
                        cleanupCRLs();
                    }catch(Throwable t)
                    {
                        LOG.warn("Could not cleanup CRLs.{}: {}", t.getClass().getName(), t.getMessage());
                    }
                }

                return crl;
            } catch (CRLException e)
            {
                throw new OperationException(ErrorCode.CRL_FAILURE, "CRLException: " + e.getMessage());
            }
        }finally
        {
            if(successfull == false)
            {
                LOG.info("FAILED generateCRL: ca={}", caInfo.getName());
            }
        }
    }

    private static Extension createReasonExtension(int reasonCode)
    {
        org.bouncycastle.asn1.x509.CRLReason crlReason =
                org.bouncycastle.asn1.x509.CRLReason.lookup(reasonCode);

        try
        {
            return new Extension(Extension.reasonCode, false, crlReason.getEncoded());
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("error encoding reason: " + e.getMessage(), e);
        }
    }

    private static Extension createInvalidityDateExtension(Date invalidityDate)
    {
        try
        {
            ASN1GeneralizedTime asnTime = new ASN1GeneralizedTime(invalidityDate);
            return new Extension(Extension.invalidityDate, false, asnTime.getEncoded());
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("error encoding reason: " + e.getMessage(), e);
        }
    }

    /**
     * added by lijun liao add the support of
     * @param certificateIssuer
     * @return
     */
    private static Extension createCertificateIssuerExtension(X500Name certificateIssuer)
    {
        try
        {
            GeneralName generalName = new GeneralName(certificateIssuer);
            return new Extension(Extension.certificateIssuer, true,
                    new GeneralNames(generalName).getEncoded());
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("error encoding reason: " + e.getMessage(), e);
        }
    }

    public CertificateInfo generateCertificate(boolean requestedByRA,
            String certProfileName,
            String user,
            X500Name subject,
            SubjectPublicKeyInfo publicKeyInfo,
            Date notBefore,
            Date notAfter,
            Extensions extensions)
    throws OperationException
    {
        final String subjectText = IoCertUtil.canonicalizeName(subject);
        LOG.info("START generateCertificate: CA={}, profile={}, subject={}",
                new Object[]{caInfo.getName(), certProfileName, subjectText});

        boolean successfull = false;

        try
        {
            try
            {
                CertificateInfo ret = intern_generateCertificate(requestedByRA,
                        certProfileName, user,
                        subject, publicKeyInfo,
                        notBefore, notAfter, extensions, false);
                successfull = true;

                String prefix = ret.isAlreadyIssued() ? "RETURN_OLD_CERT" : "SUCCESSFULL";
                LOG.info("{} generateCertificate: CA={}, profile={},"
                        + " subject={}, serialNumber={}",
                        new Object[]{prefix, caInfo.getName(), certProfileName,
                            ret.getCert().getSubject(), ret.getCert().getCert().getSerialNumber()});
                return ret;
            }catch(RuntimeException e)
            {
                final String message = "RuntimeException in generateCertificate()";
                if(LOG.isWarnEnabled())
                {
                    LOG.warn(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                }
                LOG.debug(message, e);
                throw new OperationException(ErrorCode.System_Failure, "RuntimeException:  " + e.getMessage());
            }
        }finally
        {
            if(successfull == false)
            {
                LOG.warn("FAILED generateCertificate: CA={}, profile={}, subject={}",
                        new Object[]{caInfo.getName(), certProfileName, subjectText});
            }
        }
    }

    public CertificateInfo regenerateCertificate(
            boolean requestedByRA,
            String certProfileName,
            String user,
            X500Name subject,
            SubjectPublicKeyInfo publicKeyInfo,
            Date notBefore,
            Date notAfter,
            Extensions extensions)
    throws OperationException
    {
        final String subjectText = IoCertUtil.canonicalizeName(subject);
        LOG.info("START regenerateCertificate: CA={}, profile={}, subject={}",
                new Object[]{caInfo.getName(), certProfileName, subjectText});

        boolean successfull = false;

        try
        {
            CertificateInfo ret = intern_generateCertificate(requestedByRA, certProfileName, user,
                    subject, publicKeyInfo,
                    notBefore, notAfter, extensions, false);
            successfull = true;
            LOG.info("SUCCESSFULL generateCertificate: CA={}, profile={},"
                    + " subject={}, serialNumber={}",
                    new Object[]{caInfo.getName(), certProfileName,
                        ret.getCert().getSubject(), ret.getCert().getCert().getSerialNumber()});

            return ret;
        }catch(RuntimeException e)
        {
            final String message = "RuntimeException in regenerateCertificate()";
            if(LOG.isWarnEnabled())
            {
                LOG.warn(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
            }
            LOG.debug(message, e);
            throw new OperationException(ErrorCode.System_Failure, "RuntimeException:  " + e.getMessage());
        } finally
        {
            if(successfull == false)
            {
                LOG.warn("FAILED regenerateCertificate: CA={}, profile={}, subject={}",
                        new Object[]{caInfo.getName(), certProfileName, subjectText});
            }
        }
    }

    public boolean publishCertificate(CertificateInfo certInfo)
    {
        if(certInfo.isAlreadyIssued())
        {
            return true;
        }

        if(certstore.addCertificate(certInfo) == false)
        {
            return false;
        }

        for(IdentifiedCertPublisher publisher : getPublishers())
        {
            if(publisher.isAsyn() == false)
            {
                boolean successfull;
                try
                {
                    successfull = publisher.certificateAdded(certInfo);
                }
                catch (RuntimeException e)
                {
                    successfull = false;
                    final String message = "Error while publish certificate to the publisher " + publisher.getName();
                    if(LOG.isWarnEnabled())
                    {
                        LOG.warn(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                    }
                    LOG.debug(message, e);
                }

                if(successfull)
                {
                    continue;
                }
            }

            Integer certId = certInfo.getCert().getCertId();
            try
            {
                certstore.addToPublishQueue(publisher.getName(), certId.intValue(), caInfo.getCertificate());
            } catch(Throwable t)
            {
                final String message = "Error while add entry to PublishQueue";
                if(LOG.isErrorEnabled())
                {
                    LOG.error(LogUtil.buildExceptionLogFormat(message), t.getClass().getName(), t.getMessage());
                }
                LOG.debug(message, t);
                return false;
            }
        }

        return true;
    }

    public boolean republishCertificates(List<String> publisherNames)
    {
        List<IdentifiedCertPublisher> publishers;
        if(publisherNames == null)
        {
            publishers = getPublishers();
        }
        else
        {
            publishers = new ArrayList<>(publisherNames.size());

            for(String publisherName : publisherNames)
            {
                IdentifiedCertPublisher publisher = null;
                for(IdentifiedCertPublisher p : getPublishers())
                {
                    if(p.getName().equals(publisherName))
                    {
                        publisher = p;
                        break;
                    }
                }

                if(publisher == null)
                {
                    throw new IllegalArgumentException(
                            "Could not find publisher " + publisherName + " for CA " + caInfo.getName());
                }
                publishers.add(publisher);
            }
        }

        if(publishers.isEmpty())
        {
            return true;
        }

        CAStatus status = caInfo.getStatus();

        caInfo.setStatus(CAStatus.INACTIVE);

        // wait till no certificate request in process
        while(pendingSubjectMap.isEmpty() == false || numActiveRevocations.get() > 0)
        {
            LOG.info("Certificate requests are still in process, wait 1 second");
            try
            {
                Thread.sleep(SECOND);
            }catch(InterruptedException e)
            {
            }
        }

        boolean allPublishersOnlyForRevokedCerts = true;
        for(IdentifiedCertPublisher publisher : publishers)
        {
            if(publisher.publishsGoodCert())
            {
                allPublishersOnlyForRevokedCerts = false;
            }

            String name = publisher.getName();
            try
            {
                LOG.info("Clearing PublishQueue for publisher {}", name);
                certstore.clearPublishQueue(this.caInfo.getCertificate(), name);
                LOG.info(" Cleared PublishQueue for publisher {}", name);
            } catch (SQLException | OperationException e)
            {
                final String message = "Exception while clearing PublishQueue for publisher";
                if(LOG.isErrorEnabled())
                {
                    LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                }
                LOG.debug(message, e);
            }
        }

        try
        {
            List<BigInteger> serials;
            X509CertificateWithMetaInfo caCert = caInfo.getCertificate();

            Date notExpiredAt = null;

            BigInteger startSerial = BigInteger.ONE;
            int numEntries = 100;

            boolean onlyRevokedCerts = false;

            int sum = 0;
            do
            {
                try
                {
                    serials = certstore.getCertSerials(caCert, notExpiredAt, startSerial, numEntries, onlyRevokedCerts,
                            false, false);
                } catch (OperationException e)
                {
                    final String message = "Exception";
                    if(LOG.isErrorEnabled())
                    {
                        LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                    }
                    LOG.debug(message, e);
                    return false;
                }

                // Even if only revoked certificates will be published, good certificates will be republished
                // at the first round. This is required to publish CA information if there is no revoked certs
                if(allPublishersOnlyForRevokedCerts)
                {
                    onlyRevokedCerts = true;
                }

                BigInteger maxSerial = BigInteger.ONE;
                for(BigInteger serial : serials)
                {
                    if(serial.compareTo(maxSerial) > 0)
                    {
                        maxSerial = serial;
                    }

                    CertificateInfo certInfo;

                    try
                    {
                        certInfo = certstore.getCertificateInfoForSerial(caCert, serial);
                    } catch (OperationException | CertificateException e)
                    {
                        final String message = "Exception";
                        if(LOG.isErrorEnabled())
                        {
                            LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                        }
                        LOG.debug(message, e);
                        return false;
                    }

                    for(IdentifiedCertPublisher publisher : publishers)
                    {
                        boolean successfull = publisher.certificateAdded(certInfo);
                        if(successfull == false)
                        {
                            LOG.error("Republish certificate serial={} to publisher {} failed", serial, publisher.getName());
                            return false;
                        }
                    }
                }

                startSerial = maxSerial.add(BigInteger.ONE);

                sum += serials.size();
                System.out.println("CA " + caInfo.getName() + " republished " + sum + " certificates");
            } while(serials.size() >= numEntries);

            if(caInfo.getRevocationInfo() != null)
            {
                for(IdentifiedCertPublisher publisher : publishers)
                {
                    boolean successfull = publisher.caRevoked(caInfo.getCertificate(), caInfo.getRevocationInfo());
                    if(successfull == false)
                    {
                        LOG.error("Republish CA revocation to publisher {} failed", publisher.getName());
                        return false;
                    }
                }
            }

            return true;
        } finally
        {
            caInfo.setStatus(status);
        }
    }

    public boolean clearPublishQueue(List<String> publisherNames)
    throws CAMgmtException
    {
        if(publisherNames == null)
        {
            try
            {
                certstore.clearPublishQueue(caInfo.getCertificate(), null);
                return true;
            } catch (SQLException | OperationException e)
            {
                throw new CAMgmtException(e);
            }
        }

        for(String publisherName : publisherNames)
        {
            try
            {
                certstore.clearPublishQueue(caInfo.getCertificate(), publisherName);
            } catch (SQLException | OperationException e)
            {
                throw new CAMgmtException(e);
            }
        }

        return true;
    }

    public boolean publishCertsInQueue()
    {
        boolean allSuccessfull = true;
        for(IdentifiedCertPublisher publisher : getPublishers())
        {
            if(publishCertsInQueue(publisher) == false)
            {
                allSuccessfull = false;
            }
        }

        return allSuccessfull;
    }

    private boolean publishCertsInQueue(IdentifiedCertPublisher publisher)
    {
        X509CertificateWithMetaInfo caCert = caInfo.getCertificate();

        final int numEntries = 500;

        while(true)
        {
            List<Integer> certIds;
            try
            {
                certIds = certstore.getPublishQueueEntries(caCert, publisher.getName(), numEntries);
            } catch (OperationException e)
            {
                final String message = "Exception";
                if(LOG.isErrorEnabled())
                {
                    LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                }
                LOG.debug(message, e);
                return false;
            }

            if(certIds == null || certIds.isEmpty())
            {
                break;
            }

            for(Integer certId : certIds)
            {
                CertificateInfo certInfo;

                try
                {
                    certInfo = certstore.getCertificateInfoForId(caCert, certId);
                } catch (OperationException | CertificateException e)
                {
                    final String message = "Exception";
                    if(LOG.isErrorEnabled())
                    {
                        LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                    }
                    LOG.debug(message, e);
                    return false;
                }

                boolean successfull = publisher.certificateAdded(certInfo);
                if(successfull)
                {
                    try
                    {
                        certstore.removeFromPublishQueue(publisher.getName(), certId);
                    } catch (OperationException e)
                    {
                        final String message = "SQLException while removing republished cert id=" + certId +
                                " and publisher=" + publisher.getName();
                        if(LOG.isWarnEnabled())
                        {
                            LOG.warn(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                        }
                        LOG.debug(message, e);
                        continue;
                    }
                }
                else
                {
                    LOG.error("Republish certificate id={} failed", certId);
                    return false;
                }
            }
        }

        return true;
    }

    private boolean publishCRL(X509CRL crl)
    {
        X509CertificateWithMetaInfo caCert = caInfo.getCertificate();
        if(certstore.addCRL(caCert, crl) == false)
        {
            return false;
        }

        for(IdentifiedCertPublisher publisher : getPublishers())
        {
            try
            {
                publisher.crlAdded(caCert, crl);
            }
            catch (RuntimeException e)
            {
                final String message = "Error while publish CRL to the publisher " + publisher.getName();
                if(LOG.isErrorEnabled())
                {
                    LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                }
                LOG.debug(message, e);
            }
        }

        return true;
    }

    public CertWithRevocationInfo revokeCertificate(BigInteger serialNumber,
            CRLReason reason, Date invalidityTime)
    throws OperationException
    {
        if(caInfo.isSelfSigned() && caInfo.getSerialNumber().equals(serialNumber))
        {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "Not allow to revoke CA certificate");
        }

        if(reason == null)
        {
            reason = CRLReason.UNSPECIFIED;
        }

        switch(reason)
        {
            case CA_COMPROMISE:
            case AA_COMPROMISE:
            case REMOVE_FROM_CRL:
                throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                        "Not allow to revoke certificate with reason " + reason.getDescription());
            case UNSPECIFIED:
            case KEY_COMPROMISE:
            case AFFILIATION_CHANGED:
            case SUPERSEDED:
            case CESSATION_OF_OPERATION:
            case CERTIFICATE_HOLD:
            case PRIVILEGE_WITHDRAWN:
                break;
        }
        return do_revokeCertificate(serialNumber, reason, invalidityTime, false);
    }

    public X509CertificateWithMetaInfo unrevokeCertificate(BigInteger serialNumber)
    throws OperationException
    {
        if(caInfo.isSelfSigned() && caInfo.getSerialNumber().equals(serialNumber))
        {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "Not allow to unrevoke CA certificate");
        }

        return do_unrevokeCertificate(serialNumber, false);
    }

    public X509CertificateWithMetaInfo removeCertificate(BigInteger serialNumber)
    throws OperationException
    {
        if(caInfo.isSelfSigned() && caInfo.getSerialNumber().equals(serialNumber))
        {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "Not allow to remove CA certificate");
        }

        return do_removeCertificate(serialNumber);
    }

    private X509CertificateWithMetaInfo do_removeCertificate(BigInteger serialNumber)
    throws OperationException
    {
        CertWithRevocationInfo certWithRevInfo =
                certstore.getCertWithRevocationInfo(caInfo.getCertificate(), serialNumber);
        if(certWithRevInfo == null)
        {
            return null;
        }

        boolean successful = true;
        X509CertificateWithMetaInfo certToRemove = certWithRevInfo.getCert();
        for(IdentifiedCertPublisher publisher : getPublishers())
        {
            boolean singleSuccessful;
            try
            {
                singleSuccessful = publisher.certificateRemoved(caInfo.getCertificate(), certToRemove);
            }
            catch (RuntimeException e)
            {
                singleSuccessful = false;
                final String message = "Error while remove certificate to the publisher " + publisher.getName();
                if(LOG.isWarnEnabled())
                {
                    LOG.warn(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                }
                LOG.debug(message, e);
            }

            if(singleSuccessful == false)
            {
                successful = false;
                X509Certificate c = certToRemove.getCert();
                LOG.error("Removing certificate issuer={}, serial={}, subject={} from publisher {} failed.",
                        new Object[]
                        {
                                IoCertUtil.canonicalizeName(c.getIssuerX500Principal()),
                                c.getSerialNumber(),
                                IoCertUtil.canonicalizeName(c.getSubjectX500Principal()),
                                publisher.getName()});
            }
        }

        if(successful == false)
        {
            return null;
        }

        certstore.removeCertificate(caInfo.getCertificate(), serialNumber);
        return certToRemove;
    }

    private CertWithRevocationInfo do_revokeCertificate(BigInteger serialNumber,
            CRLReason reason, Date invalidityTime, boolean force)
    throws OperationException
    {
        LOG.info("START revokeCertificate: ca={}, serialNumber={}, reason={}, invalidityTime={}",
                new Object[]{caInfo.getName(), serialNumber, reason.getDescription(), invalidityTime});

        numActiveRevocations.addAndGet(1);
        CertWithRevocationInfo revokedCert = null;

        try
        {
            CertRevocationInfo revInfo = new CertRevocationInfo(reason, new Date(), invalidityTime);
            revokedCert = certstore.revokeCertificate(
                    caInfo.getCertificate(),
                    serialNumber, revInfo, force, shouldPublishToDeltaCRLCache());
            if(revokedCert == null)
            {
                return null;
            }

            for(IdentifiedCertPublisher publisher : getPublishers())
            {
                if(publisher.isAsyn() == false)
                {
                    boolean successfull;
                    try
                    {
                        successfull = publisher.certificateRevoked(caInfo.getCertificate(),
                                revokedCert.getCert(), revokedCert.getRevInfo());
                    }
                    catch (RuntimeException e)
                    {
                        successfull = false;
                        final String message = "Error while publish revocation of certificate to the publisher " +
                                publisher.getName();
                        if(LOG.isErrorEnabled())
                        {
                            LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                        }
                        LOG.debug(message, e);
                    }

                    if(successfull)
                    {
                        continue;
                    }
                }

                Integer certId = revokedCert.getCert().getCertId();
                try
                {
                    certstore.addToPublishQueue(publisher.getName(), certId.intValue(), caInfo.getCertificate());
                }catch(Throwable t)
                {
                    final String message = "Error while add entry to PublishQueue";
                    if(LOG.isErrorEnabled())
                    {
                        LOG.error(LogUtil.buildExceptionLogFormat(message), t.getClass().getName(), t.getMessage());
                    }
                    LOG.debug(message, t);
                }
            }
        } finally
        {
            numActiveRevocations.addAndGet(-1);
        }

        String resultText = revokedCert == null ? "CERT_NOT_EXIST" : "REVOKED";
        LOG.info("SUCCESSFULL revokeCertificate: ca={}, serialNumber={}, reason={},"
                + " invalidityTime={}, revocationResult={}",
                new Object[]{caInfo.getName(), serialNumber, reason.getDescription(),
                        invalidityTime, resultText});

        return revokedCert;
    }

    private X509CertificateWithMetaInfo do_unrevokeCertificate(BigInteger serialNumber, boolean force)
    throws OperationException
    {
        LOG.info("START unrevokeCertificate: ca={}, serialNumber={}", caInfo.getName(), serialNumber);

        numActiveRevocations.addAndGet(1);
        X509CertificateWithMetaInfo unrevokedCert = null;

        try
        {
            unrevokedCert = certstore.unrevokeCertificate(
                    caInfo.getCertificate(), serialNumber, force, shouldPublishToDeltaCRLCache());
            if(unrevokedCert == null)
            {
                return null;
            }

            for(IdentifiedCertPublisher publisher : getPublishers())
            {
                if(publisher.isAsyn())
                {
                    boolean successfull;
                    try
                    {
                        successfull = publisher.certificateUnrevoked(caInfo.getCertificate(), unrevokedCert);
                    }
                    catch (RuntimeException e)
                    {
                        successfull = false;
                        final String message = "Error while publish unrevocation of certificate to the publisher " +
                                publisher.getName();
                        if(LOG.isErrorEnabled())
                        {
                            LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                        }
                        LOG.debug(message, e);
                    }

                    if(successfull)
                    {
                        continue;
                    }
                }

                Integer certId = unrevokedCert.getCertId();
                try
                {
                    certstore.addToPublishQueue(publisher.getName(), certId.intValue(), caInfo.getCertificate());
                }catch(Throwable t)
                {
                    final String message = "Error while add entry to PublishQueue";
                    if(LOG.isErrorEnabled())
                    {
                        LOG.error(LogUtil.buildExceptionLogFormat(message), t.getClass().getName(), t.getMessage());
                    }
                    LOG.debug(message, t);
                }
            }
        } finally
        {
            numActiveRevocations.addAndGet(-1);
        }

        String resultText = unrevokedCert == null ? "CERT_NOT_EXIST" : "UNREVOKED";
        LOG.info("SUCCESSFULL unrevokeCertificate: ca={}, serialNumber={}, revocationResult={}",
                new Object[]{caInfo.getName(), serialNumber, resultText});

        return unrevokedCert;
    }

    private boolean shouldPublishToDeltaCRLCache()
    {
        if(crlSigner == null)
        {
            return false;
        }

        CRLControl control = crlSigner.getCRLcontrol();
        if(control.getUpdateMode() == UpdateMode.onDemand)
        {
            return false;
        }

        int deltaCRLInterval = control.getDeltaCRLIntervals();
        if(deltaCRLInterval == 0 || deltaCRLInterval >= control.getFullCRLIntervals())
        {
            return false;
        }

        return true;
    }

    public void revoke(CertRevocationInfo revocationInfo)
    throws OperationException
    {
        ParamChecker.assertNotNull("revocationInfo", revocationInfo);

        caInfo.setRevocationInfo(revocationInfo);
        if(caInfo.isSelfSigned())
        {
            do_revokeCertificate(caInfo.getSerialNumber(), revocationInfo.getReason(),
                revocationInfo.getInvalidityTime(), true);
        }

        for(IdentifiedCertPublisher publisher : getPublishers())
        {
            try
            {
                boolean successfull = publisher.caRevoked(caInfo.getCertificate(), revocationInfo);
                if(successfull == false)
                {
                    throw new OperationException(ErrorCode.System_Failure, "Publishing CA revocation failed");
                }
            }
            catch (RuntimeException e)
            {
                String message = "Error while publish revocation of CA to the publisher " + publisher.getName();
                if(LOG.isErrorEnabled())
                {
                    LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                }
                LOG.debug(message, e);
                throw new OperationException(ErrorCode.System_Failure, message);
            }
        }
    }

    public void unrevoke()
    throws OperationException
    {
        caInfo.setRevocationInfo(null);
        if(caInfo.isSelfSigned())
        {
            do_unrevokeCertificate(caInfo.getSerialNumber(), true);
        }

        for(IdentifiedCertPublisher publisher : getPublishers())
        {
            try
            {
                boolean successfull = publisher.caUnrevoked(caInfo.getCertificate());
                if(successfull == false)
                {
                    throw new OperationException(ErrorCode.System_Failure, "Publishing CA revocation failed");
                }
            }
            catch (RuntimeException e)
            {
                String message = "Error while publish revocation of CA to the publisher " + publisher.getName();
                if(LOG.isErrorEnabled())
                {
                    LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                }
                LOG.debug(message, e);
                throw new OperationException(ErrorCode.System_Failure, message);
            }
        }
    }

    private List<IdentifiedCertPublisher> getPublishers()
    {
        return caManager.getIdentifiedPublishersForCa(caInfo.getName());
    }

    private CertificateInfo intern_generateCertificate(boolean requestedByRA,
            String certProfileName,
            String user,
            X500Name requestedSubject,
            SubjectPublicKeyInfo publicKeyInfo,
            Date notBefore,
            Date notAfter,
            org.bouncycastle.asn1.x509.Extensions extensions,
            boolean keyUpdate)
    throws OperationException
    {
        IdentifiedCertProfile certProfile;
        try
        {
            certProfile = getX509CertProfile(certProfileName);
        } catch (CertProfileException e)
        {
            throw new OperationException(ErrorCode.System_Failure, "invalid configuration of cert profile " + certProfileName);
        }

        if(certProfile == null)
        {
            throw new OperationException(ErrorCode.UNKNOWN_CERT_PROFILE, "unknown cert profile " + certProfileName);
        }

        if(certProfile.isOnlyForRA() && requestedByRA == false)
        {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "Profile " + certProfileName + " not applied to non-RA");
        }

        requestedSubject = removeEmptyRDNs(requestedSubject);

        if(certProfile.isSerialNumberInReqPermitted() == false)
        {
            RDN[] rdns = requestedSubject.getRDNs(ObjectIdentifiers.DN_SN);
            if(rdns != null && rdns.length > 0)
            {
                throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE,
                        "SubjectDN SerialNumber in request is not permitted");
            }
        }

        notBefore = certProfile.getNotBefore(notBefore);
        if(notBefore == null)
        {
            notBefore = new Date();
        }

        if(certProfile.hasMidnightNotBefore())
        {
            notBefore = setToMidnight(notBefore, certProfile.getTimezone());
        }

        if(notBefore.before(caInfo.getNotBefore()))
        {
            notBefore = caInfo.getNotBefore();
            if(certProfile.hasMidnightNotBefore())
            {
                notBefore = setToMidnight(new Date(notBefore.getTime() + DAY), certProfile.getTimezone());
            }
        }

        long t = caInfo.getNoNewCertificateAfter();
        if(notBefore.getTime() > t)
        {
            throw new OperationException(ErrorCode.NOT_PERMITTED,
                    "CA is not permitted to issue certifate after " + new Date(t));
        }

        publicKeyInfo = IoCertUtil.toRfc3279Style(publicKeyInfo);

        // public key
        try
        {
            certProfile.checkPublicKey(publicKeyInfo);
        } catch (BadCertTemplateException e)
        {
            throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE, e.getMessage());
        }

        Date gSMC_KFirstNotBefore = null;
        if(certProfile.getSpecialCertProfileBehavior() == SpecialCertProfileBehavior.gematik_gSMC_K)
        {
            gSMC_KFirstNotBefore = notBefore;

            RDN[] cnRDNs = requestedSubject.getRDNs(ObjectIdentifiers.DN_CN);
            if(cnRDNs != null && cnRDNs.length > 0)
            {
                String requestedCN = IETFUtils.valueToString(cnRDNs[0].getFirst().getValue());
                Long gsmckFirstNotBeforeInSecond = certstore.getNotBeforeOfFirstCertStartsWithCN(
                        requestedCN, certProfileName);
                if(gsmckFirstNotBeforeInSecond != null)
                {
                    gSMC_KFirstNotBefore = new Date(gsmckFirstNotBeforeInSecond * SECOND);
                }

                // append the commonName with '-' + yyyyMMdd
                SimpleDateFormat dateF = new SimpleDateFormat("yyyyMMdd");
                dateF.setTimeZone(new SimpleTimeZone(0,"Z"));
                String yyyyMMdd = dateF.format(gSMC_KFirstNotBefore);
                String suffix = "-" + yyyyMMdd;

                // append the -yyyyMMdd to the commonName
                RDN[] rdns = requestedSubject.getRDNs();
                for(int i = 0; i < rdns.length; i++)
                {
                    if(ObjectIdentifiers.DN_CN.equals(rdns[i].getFirst().getType()))
                    {
                        rdns[i] = new RDN(ObjectIdentifiers.DN_CN, new DERUTF8String(requestedCN + suffix));
                    }
                }
                requestedSubject = new X500Name(rdns);
            }
        }

        // subject
        SubjectInfo subjectInfo;
        try
        {
            subjectInfo = certProfile.getSubject(requestedSubject);
        }catch(CertProfileException e)
        {
            throw new OperationException(ErrorCode.System_Failure, "exception in cert profile " + certProfileName);
        } catch (BadCertTemplateException e)
        {
            throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE, e.getMessage());
        }

        X500Name grantedSubject = subjectInfo.getGrantedSubject();

        // make sure that the grantedSubject does not equal the CA's subject
        if(grantedSubject.equals(caSubjectX500Name))
        {
            throw new OperationException(ErrorCode.ALREADY_ISSUED,
                    "Certificate with the same subject as CA is not allowed");
        }

        DuplicationMode keyMode = caInfo.getDuplicateKeyMode();
        if(keyMode == DuplicationMode.PERMITTED && certProfile.isDuplicateKeyPermitted() == false)
        {
            keyMode = DuplicationMode.FORBIDDEN_WITHIN_PROFILE;
        }

        DuplicationMode subjectMode = caInfo.getDuplicateSubjectMode();
        if(subjectMode == DuplicationMode.PERMITTED && certProfile.isDuplicateSubjectPermitted() == false)
        {
            subjectMode = DuplicationMode.FORBIDDEN_WITHIN_PROFILE;
        }

        String sha1FpSubject = IoCertUtil.sha1sum_canonicalized_name(grantedSubject);
        String grandtedSubjectText = IoCertUtil.canonicalizeName(grantedSubject);

        byte[] subjectPublicKeyData =  publicKeyInfo.getPublicKeyData().getBytes();
        String sha1FpPublicKey = IoCertUtil.sha1sum(subjectPublicKeyData);

        if(keyUpdate)
        {
            CertStatus certStatus = certstore.getCertStatusForSubject(caInfo.getCertificate(), grantedSubject);
            if(certStatus == CertStatus.Revoked)
            {
                throw new OperationException(ErrorCode.CERT_REVOKED);
            }
            else if(certStatus == CertStatus.Unknown)
            {
                throw new OperationException(ErrorCode.UNKNOWN_CERT);
            }
        }
        else
        {
            // try to get certificate with the same subject, key and certificate profile
            SubjectKeyProfileTriple triple = certstore.getLatestCert(caInfo.getCertificate(),
                    sha1FpSubject, sha1FpPublicKey, certProfileName);

            if(triple != null)
            {
                /*
                 * If there exists a certificate whose public key, subject and profile match the request,
                 * returns the certificate if it is not revoked, otherwise OperationException with
                 * ErrorCode CERT_REVOKED will be thrown
                 */
                if(triple.isRevoked())
                {
                    throw new OperationException(ErrorCode.CERT_REVOKED);
                }
                else
                {
                    X509CertificateWithMetaInfo issuedCert = certstore.getCertForId(triple.getCertId());
                    if(issuedCert == null)
                    {
                        throw new OperationException(ErrorCode.System_Failure,
                            "Find no certificate in table RAWCERT for CERT_ID " + triple.getCertId());
                    }
                    else
                    {
                        CertificateInfo certInfo;
                        try
                        {
                            certInfo = new CertificateInfo(issuedCert,
                                    caInfo.getCertificate(), subjectPublicKeyData, certProfileName);
                        } catch (CertificateEncodingException e)
                        {
                            throw new OperationException(ErrorCode.System_Failure,
                                    "could not construct CertificateInfo: " + e.getMessage());
                        }
                        certInfo.setAlreadyIssued(true);
                        return certInfo;
                    }
                }
            }

            if(keyMode != DuplicationMode.PERMITTED)
            {
                if(keyMode == DuplicationMode.FORBIDDEN)
                {
                    if(certstore.isCertForKeyIssued(caInfo.getCertificate(), sha1FpPublicKey))
                    {
                        throw new OperationException(ErrorCode.ALREADY_ISSUED,
                                "Certificate for the given public key already issued");
                    }
                }
                else if(keyMode == DuplicationMode.FORBIDDEN_WITHIN_PROFILE)
                {
                    if(certstore.isCertForKeyIssued(caInfo.getCertificate(), sha1FpPublicKey, certProfileName))
                    {
                        throw new OperationException(ErrorCode.ALREADY_ISSUED,
                                "Certificate for the given public key and profile " + certProfileName + " already issued");
                    }
                }
                else
                {
                    throw new RuntimeException("should not reach here");
                }
            }

            if(subjectMode != DuplicationMode.PERMITTED)
            {
                final boolean incSerial = certProfile.incSerialNumberIfSubjectExists();
                final boolean certIssued;
                if(subjectMode == DuplicationMode.FORBIDDEN)
                {
                    certIssued = certstore.isCertForSubjectIssued(caInfo.getCertificate(), sha1FpSubject);
                    if(certIssued && incSerial == false)
                    {
                        throw new OperationException(ErrorCode.ALREADY_ISSUED,
                                "Certificate for the given subject " + grandtedSubjectText + " already issued");
                    }
                }
                else if(subjectMode == DuplicationMode.FORBIDDEN_WITHIN_PROFILE)
                {
                    certIssued = certstore.isCertForSubjectIssued(caInfo.getCertificate(), sha1FpSubject, certProfileName);
                    if(certIssued && incSerial == false)
                    {
                        throw new OperationException(ErrorCode.ALREADY_ISSUED,
                                "Certificate for the given subject " + grandtedSubjectText +
                                " and profile " + certProfileName + " already issued");
                    }
                }
                else
                {
                    throw new RuntimeException("should not reach here");
                }

                if(certIssued)
                {
                    String latestSN;
                    try
                    {
                        Object[] objs = incSerialNumber(certProfile, grantedSubject, null);
                        latestSN = certstore.getLatestSN((X500Name) objs[0]);
                    }catch(BadFormatException e)
                    {
                        throw new OperationException(ErrorCode.System_Failure, "BadFormatException: " + e.getMessage());
                    }

                    boolean foundUniqueSubject = false;
                    // maximal 100 tries
                    for(int i = 0; i < 100; i++)
                    {
                        try
                        {
                            Object[] objs = incSerialNumber(certProfile, grantedSubject, latestSN);
                            grantedSubject = (X500Name) objs[0];
                            latestSN = (String) objs[1];
                        }catch (BadFormatException e)
                        {
                            throw new OperationException(ErrorCode.System_Failure, "BadFormatException: " + e.getMessage());
                        }

                        foundUniqueSubject = (certstore.isCertForSubjectIssued(
                                        caInfo.getCertificate(),
                                        IoCertUtil.sha1sum_canonicalized_name(grantedSubject)) == false);
                        if(foundUniqueSubject)
                        {
                            break;
                        }
                    }

                    if(foundUniqueSubject == false)
                    {
                        throw new OperationException(ErrorCode.ALREADY_ISSUED,
                                "Certificate for the given subject " + grandtedSubjectText +
                                " and profile " + certProfileName +
                                " already issued, and could not create new unique serial number");
                    }
                }
            }
        }

        try
        {
            if(subjectMode == DuplicationMode.FORBIDDEN || subjectMode == DuplicationMode.FORBIDDEN_WITHIN_PROFILE)
            {
                synchronized (pendingSubjectMap)
                {
                    // check request with the same subject is still in process
                    if(subjectMode == DuplicationMode.FORBIDDEN)
                    {
                        if(pendingSubjectMap.containsKey(sha1FpSubject))
                        {
                            throw new OperationException(ErrorCode.ALREADY_ISSUED,
                                    "Certificate for the given subject " + grandtedSubjectText + " already in process");
                        }
                    }
                    else if(subjectMode == DuplicationMode.FORBIDDEN_WITHIN_PROFILE)
                    {
                        if(pendingSubjectMap.containsKey(sha1FpSubject) &&
                                pendingSubjectMap.get(sha1FpSubject).contains(certProfileName))
                        {
                            throw new OperationException(ErrorCode.ALREADY_ISSUED,
                                    "Certificate for the given subject " + grandtedSubjectText +
                                    " and profile " + certProfileName + " already in process");
                        }
                    }

                    List<String> profiles = pendingSubjectMap.get(sha1FpSubject);
                    if(profiles == null)
                    {
                        profiles = new LinkedList<>();
                        pendingSubjectMap.put(sha1FpSubject, profiles);
                    }
                    profiles.add(certProfileName);
                }
            }

            if(keyMode == DuplicationMode.FORBIDDEN || keyMode == DuplicationMode.FORBIDDEN_WITHIN_PROFILE)
            {
                synchronized (pendingSubjectMap)
                {
                    // check request with the same subject is still in process
                    if(keyMode == DuplicationMode.FORBIDDEN)
                    {
                        if(pendingKeyMap.containsKey(sha1FpPublicKey))
                        {
                            throw new OperationException(ErrorCode.ALREADY_ISSUED,
                                    "Certificate for the given public key already in process");
                        }
                    }
                    else if(keyMode == DuplicationMode.FORBIDDEN_WITHIN_PROFILE)
                    {
                        if(pendingKeyMap.containsKey(sha1FpPublicKey) &&
                                pendingKeyMap.get(sha1FpPublicKey).contains(certProfileName))
                        {
                            throw new OperationException(ErrorCode.ALREADY_ISSUED,
                                    "Certificate for the given public key" +
                                    " and profile " + certProfileName + " already in process");
                        }
                    }

                    List<String> profiles = pendingKeyMap.get(sha1FpSubject);
                    if(profiles == null)
                    {
                        profiles = new LinkedList<>();
                        pendingKeyMap.put(sha1FpPublicKey, profiles);
                    }
                    profiles.add(certProfileName);
                }
            }

            StringBuilder msgBuilder = new StringBuilder();

            if(subjectInfo.getWarning() != null)
            {
                msgBuilder.append(", ").append(subjectInfo.getWarning());
            }

            Integer validity = certProfile.getValidity();
            if(validity == null)
            {
                validity = caInfo.getMaxValidity();
            }

            Date maxNotAfter = new Date(notBefore.getTime() + DAY * validity - SECOND);
            Date origMaxNotAfter = maxNotAfter;

            if(certProfile.getSpecialCertProfileBehavior() == SpecialCertProfileBehavior.gematik_gSMC_K)
            {
                String s = certProfile.getParameter(SpecialCertProfileBehavior.PARAMETER_MAXLIFTIME);
                long maxLifetimeInDays = Long.parseLong(s);
                Date maxLifetime = new Date(gSMC_KFirstNotBefore.getTime() + maxLifetimeInDays * DAY - SECOND);
                if(maxNotAfter.after(maxLifetime))
                {
                    maxNotAfter = maxLifetime;
                }
            }

            if(notAfter != null)
            {
                if(notAfter.after(maxNotAfter))
                {
                    notAfter = maxNotAfter;
                    msgBuilder.append(", NotAfter modified");
                }
            }
            else
            {
                notAfter = maxNotAfter;
            }

            if(notAfter.after(caInfo.getNotAfter()))
            {
                ValidityMode mode = caInfo.getValidityMode();
                if(mode == ValidityMode.CUTOFF)
                {
                    notAfter = caInfo.getNotAfter();
                }
                else if(mode == ValidityMode.STRICT)
                {
                    throw new OperationException(ErrorCode.NOT_PERMITTED,
                            "notAfter outside of CA's validity is not permitted");
                }
                else if(mode == ValidityMode.LAX)
                {
                    // permitted
                }
                else
                {
                    throw new RuntimeException("should not reach here");
                }
            }

            if(certProfile.hasMidnightNotBefore() && maxNotAfter.equals(origMaxNotAfter) == false)
            {
                Calendar c = Calendar.getInstance(certProfile.getTimezone());
                c.setTime(new Date(notAfter.getTime() - DAY));
                c.set(Calendar.HOUR_OF_DAY, 23);
                c.set(Calendar.MINUTE, 59);
                c.set(Calendar.SECOND, 59);
                c.set(Calendar.MILLISECOND, 0);
                notAfter = c.getTime();
            }

            try
            {
                RdnUpperBounds.checkUpperBounds(grantedSubject);
            } catch (BadCertTemplateException e)
            {
                throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE, e.getMessage());
            }

            X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                    caSubjectX500Name,
                    caInfo.nextSerial(),
                    notBefore,
                    notAfter,
                    grantedSubject,
                    publicKeyInfo);

            CertificateInfo ret;

            try
            {
                String warningMsg = addExtensions(
                        certBuilder,
                        certProfile,
                        requestedSubject,
                        publicKeyInfo,
                        extensions,
                        caInfo.getPublicCAInfo());
                if(warningMsg != null && warningMsg.isEmpty() == false)
                {
                    msgBuilder.append(", ").append(warningMsg);
                }

                ContentSigner contentSigner;
                try
                {
                    contentSigner = caSigner.borrowContentSigner();
                } catch (NoIdleSignerException e)
                {
                    throw new OperationException(ErrorCode.System_Failure, "NoIdleSignerException: " + e.getMessage());
                }

                Certificate bcCert;
                try
                {
                    bcCert = certBuilder.build(contentSigner).toASN1Structure();
                }finally
                {
                    caSigner.returnContentSigner(contentSigner);
                }

                byte[] encodedCert = bcCert.getEncoded();

                X509Certificate cert = (X509Certificate) cf.engineGenerateCertificate(
                        new ByteArrayInputStream(encodedCert));
                if(verifySignature(cert) == false)
                {
                    throw new OperationException(ErrorCode.System_Failure,
                            "Could not verify the signature of generated certificate");
                }

                X509CertificateWithMetaInfo certWithMeta = new X509CertificateWithMetaInfo(cert, encodedCert);

                ret = new CertificateInfo(certWithMeta, caInfo.getCertificate(),
                        subjectPublicKeyData, certProfileName);
                ret.setUser(user);

                publishCertificate(ret);
            } catch (CertificateException | IOException | CertProfileException e)
            {
                throw new OperationException(ErrorCode.System_Failure, e.getClass().getName() + ": " + e.getMessage());
            } catch (BadCertTemplateException e)
            {
                throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE, e.getMessage());
            }

            if(msgBuilder.length() > 2)
            {
                ret.setWarningMessage(msgBuilder.substring(2));
            }

            return ret;
        }finally
        {
            synchronized (pendingSubjectMap)
            {
                List<String> profiles = pendingSubjectMap.remove(sha1FpSubject);
                if(profiles != null)
                {
                    profiles.remove(certProfileName);
                    if(profiles.isEmpty() == false)
                    {
                        pendingSubjectMap.put(sha1FpSubject, profiles);
                    }
                }

                profiles = pendingKeyMap.remove(sha1FpSubject);
                if(profiles != null)
                {
                    profiles.remove(certProfileName);
                    if(profiles.isEmpty() == false)
                    {
                        pendingKeyMap.put(sha1FpSubject, profiles);
                    }
                }
            }
        }
    }

    // remove the RDNs with empty content
    private static X500Name removeEmptyRDNs(X500Name name)
    {
        RDN[] rdns = name.getRDNs();
        List<RDN> l = new ArrayList<RDN>(rdns.length);
        boolean changed = false;
        for(RDN rdn : rdns)
        {
            String textValue = IETFUtils.valueToString(rdn.getFirst().getValue());
            if(textValue == null || textValue.isEmpty())
            {
                changed = true;
            }
            else
            {
                l.add(rdn);
            }
        }

        if(changed)
        {
            return new X500Name(l.toArray(new RDN[0]));
        }
        else
        {
            return name;
        }
    }

    private String addExtensions(X509v3CertificateBuilder certBuilder,
            IdentifiedCertProfile certProfile,
            X500Name requestedSubject,
            SubjectPublicKeyInfo requestedPublicKeyInfo,
            org.bouncycastle.asn1.x509.Extensions requestedExtensions,
            PublicCAInfo publicCaInfo)
    throws CertProfileException, BadCertTemplateException, IOException
    {
        addSubjectKeyIdentifier(certBuilder, requestedPublicKeyInfo, certProfile);
        addAuthorityKeyIdentifier(certBuilder, certProfile);
        addAuthorityInformationAccess(certBuilder, certProfile);
        addCRLDistributionPoints(certBuilder, certProfile);
        addDeltaCRLDistributionPoints(certBuilder, certProfile);
        addIssuerAltName(certBuilder, certProfile);

        ExtensionTuples extensionTuples = certProfile.getExtensions(requestedSubject, requestedExtensions);
        for(ExtensionTuple extension : extensionTuples.getExtensions())
        {
            certBuilder.addExtension(extension.getType(), extension.isCritical(), extension.getValue());
        }

        return extensionTuples.getWarning();
    }

    public IdentifiedCertProfile getX509CertProfile(String certProfileName)
    throws CertProfileException
    {
        if(certProfileName != null)
        {
            Set<String> profileNames = caManager.getCertProfilesForCA(caInfo.getName());
            if(profileNames == null || profileNames.contains(certProfileName) == false)
            {
                return null;
            }

            return caManager.getIdentifiedCertProfile(certProfileName);
        }
        return null;
    }

    private void addSubjectKeyIdentifier(
            X509v3CertificateBuilder certBuilder, SubjectPublicKeyInfo publicKeyInfo,
            IdentifiedCertProfile profile)
    throws IOException
    {
        ExtensionOccurrence extOccurrence = profile.getOccurenceOfSubjectKeyIdentifier();
        if(extOccurrence == null)
        {
            return;
        }

        byte[] data = publicKeyInfo.getPublicKeyData().getBytes();
        byte[] skiValue = HashCalculator.hash(HashAlgoType.SHA1, data);
        SubjectKeyIdentifier value = new SubjectKeyIdentifier(skiValue);

        certBuilder.addExtension(Extension.subjectKeyIdentifier, extOccurrence.isCritical(), value);
    }

    private void addAuthorityKeyIdentifier(X509v3CertificateBuilder certBuilder, IdentifiedCertProfile profile)
    throws IOException
    {
        ExtensionOccurrence extOccurrence = profile.getOccurenceOfAuthorityKeyIdentifier();
        if(extOccurrence == null)
        {
            return;
        }

        AuthorityKeyIdentifier value;
        if(profile.includeIssuerAndSerialInAKI())
        {
            GeneralNames caSubject = new GeneralNames(new GeneralName(caSubjectX500Name));
            BigInteger caSN = caInfo.getCertificate().getCert().getSerialNumber();
            value = new AuthorityKeyIdentifier(this.caSKI, caSubject, caSN);
        }
        else
        {
            value = new AuthorityKeyIdentifier(this.caSKI);
        }

        certBuilder.addExtension(Extension.authorityKeyIdentifier, extOccurrence.isCritical(), value);
    }

    private void addIssuerAltName(X509v3CertificateBuilder certBuilder, IdentifiedCertProfile profile)
    throws IOException, CertProfileException
    {
        ExtensionOccurrence extOccurrence = profile.getOccurenceOfIssuerAltName();
        if(extOccurrence == null)
        {
            return;
        }

        if(caSubjectAltName == null)
        {
            if(extOccurrence.isRequired())
            {
                throw new CertProfileException("Could not add required extension issuerAltName");
            }
        }
        else
        {
            certBuilder.addExtension(Extension.issuerAlternativeName, extOccurrence.isCritical(), caSubjectAltName);
        }
    }

    private void addAuthorityInformationAccess(X509v3CertificateBuilder certBuilder, IdentifiedCertProfile profile)
    throws IOException, CertProfileException
    {
        ExtensionOccurrence extOccurrence = profile.getOccurenceOfAuthorityInfoAccess();
        if(extOccurrence == null)
        {
            return;
        }

        AuthorityInformationAccess value = X509Util.createAuthorityInformationAccess(caInfo.getOcspUris());
        if(value == null)
        {
            if(extOccurrence.isRequired())
            {
                throw new CertProfileException("Could not add required extension authorityInfoAccess");
            }
            return;
        }
        else
        {
            certBuilder.addExtension(Extension.authorityInfoAccess, extOccurrence.isCritical(), value);
        }
    }

    private void addCRLDistributionPoints(X509v3CertificateBuilder certBuilder, IdentifiedCertProfile profile)
    throws IOException, CertProfileException
    {
        ExtensionOccurrence extOccurrence = profile.getOccurenceOfCRLDistributinPoints();
        if(extOccurrence == null)
        {
            return;
        }

        List<String> crlUris = caInfo.getCrlUris();
        X500Principal crlSignerSubject = null;
        if(crlSigner != null && crlSigner.getSigner() != null)
        {
            X509Certificate crlSignerCert =  crlSigner.getSigner().getCertificate();
            if(crlSignerCert != null)
            {
                crlSignerSubject = crlSignerCert.getSubjectX500Principal();
            }
        }

        CRLDistPoint value = X509Util.createCRLDistributionPoints(
                crlUris, caInfo.getCertificate().getCert().getSubjectX500Principal(),
                crlSignerSubject);
        if(value == null)
        {
            if(extOccurrence.isRequired())
            {
                throw new CertProfileException("Could not add required extension CRLDistributionPoints");
            }
            return;
        }
        else
        {
            certBuilder.addExtension(Extension.cRLDistributionPoints, extOccurrence.isCritical(), value);
        }
    }

    private void addDeltaCRLDistributionPoints(X509v3CertificateBuilder certBuilder, IdentifiedCertProfile profile)
    throws IOException, CertProfileException
    {
        ExtensionOccurrence extOccurrence = profile.getOccurenceOfFreshestCRL();
        if(extOccurrence == null)
        {
            return;
        }

        List<String> uris = caInfo.getDeltaCrlUris();
        X500Principal crlSignerSubject = null;
        if(crlSigner != null && crlSigner.getSigner() != null)
        {
            X509Certificate crlSignerCert =  crlSigner.getSigner().getCertificate();
            if(crlSignerCert != null)
            {
                crlSignerSubject = crlSignerCert.getSubjectX500Principal();
            }
        }

        CRLDistPoint value = X509Util.createCRLDistributionPoints(
                uris, caInfo.getCertificate().getCert().getSubjectX500Principal(),
                crlSignerSubject);
        if(value == null)
        {
            if(extOccurrence.isRequired())
            {
                throw new CertProfileException("Could not add required extension FreshestCRL");
            }
            return;
        }
        else
        {
            certBuilder.addExtension(Extension.freshestCRL, extOccurrence.isCritical(), value);
        }
    }

    public CAManagerImpl getCAManager()
    {
        return caManager;
    }

    public RemoveExpiredCertsInfo removeExpiredCerts(String certProfile, String userLike, Long overlapSeconds)
    throws OperationException
    {
        if(masterMode == false)
        {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "CA cannot remove expired certificates at slave mode");
        }

        if(userLike != null)
        {
            if(userLike.indexOf(' ') != -1 || userLike.indexOf('\t') != -1 ||
                    userLike.indexOf('\r') != -1|| userLike.indexOf('\n') != -1)
            {
                throw new OperationException(ErrorCode.BAD_REQUEST, "invalid userLike '" + userLike + "'");
            }

            if(userLike.indexOf('*') != -1)
            {
                userLike = userLike.replace('*', '%');
            }
        }

        RemoveExpiredCertsInfo info = new RemoveExpiredCertsInfo();
        info.setUserLike(userLike);
        info.setCertProfile(certProfile);

        if(overlapSeconds == null || overlapSeconds < 0)
        {
            overlapSeconds = 24L * 60 * 60;
        }
        info.setOverlap(overlapSeconds);

        long now = System.currentTimeMillis();
        // remove the following DEBUG CODE
        // now += DAY * 10 * 365;

        long expiredAt = now / SECOND - overlapSeconds;
        info.setExpiredAt(expiredAt);

        int numOfCerts = certstore.getNumOfExpiredCerts(caInfo.getCertificate(), expiredAt,
                certProfile, userLike);
        info.setNumOfCerts(numOfCerts);

        if(numOfCerts > 0)
        {
            removeExpiredCertsQueue.add(info);
        }

        return info;
    }

    private Date getCRLNextUpdate(Date thisUpdate)
    {
        CRLControl control = crlSigner.getCRLcontrol();
        if(control.getUpdateMode() != UpdateMode.interval)
        {
            return null;
        }

        int intervalsTillNextCRL = 0;
        for(int i = 1; ; i++)
        {
            if(i % control.getFullCRLIntervals() == 0)
            {
                intervalsTillNextCRL = i;
                break;
            }
            else if(control.isExtendedNextUpdate() == false && control.getDeltaCRLIntervals() > 0)
            {
                if(i% control.getDeltaCRLIntervals() == 0)
                {
                    intervalsTillNextCRL = i;
                    break;
                }
            }
        }

        Date nextUpdate;
        if(control.getIntervalMinutes() != null)
        {
            int minutesTillNextUpdate = intervalsTillNextCRL * control.getIntervalMinutes()
                    + control.getOverlapMinutes();
            nextUpdate = new Date(SECOND * (thisUpdate.getTime() / SECOND / 60  + minutesTillNextUpdate) * 60);
        }
        else
        {
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            c.setTime(thisUpdate);
            c.add(Calendar.DAY_OF_YEAR, intervalsTillNextCRL);
            c.set(Calendar.HOUR_OF_DAY, control.getIntervalDayTime().getHour());
            c.set(Calendar.MINUTE, control.getIntervalDayTime().getMinute());
            c.add(Calendar.MINUTE, control.getOverlapMinutes());
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            nextUpdate = c.getTime();
        }

        return nextUpdate;
    }

    public HealthCheckResult healthCheck()
    {
        HealthCheckResult result = new HealthCheckResult("X509CA");

        boolean healthy = true;

        if(caSigner != null)
        {
            boolean caSignerHealthy = caSigner.isHealthy();
            healthy &= caSignerHealthy;

            HealthCheckResult signerHealth = new HealthCheckResult("Signer");
            signerHealth.setHealthy(caSignerHealthy);
            result.addChildCheck(signerHealth);
        }

        boolean databaseHealthy = certstore.isHealthy();
        healthy &= databaseHealthy;

        HealthCheckResult databaseHealth = new HealthCheckResult("Database");
        databaseHealth.setHealthy(databaseHealthy);
        result.addChildCheck(databaseHealth);

        if(crlSigner != null && crlSigner.getSigner() != null)
        {
            boolean crlSignerHealthy = crlSigner.getSigner().isHealthy();
            healthy &= crlSignerHealthy;

            HealthCheckResult crlSignerHealth = new HealthCheckResult("CRLSigner");
            crlSignerHealth.setHealthy(crlSignerHealthy);
            result.addChildCheck(crlSignerHealth);
        }

        for(IdentifiedCertPublisher publisher : getPublishers())
        {
            boolean ph = publisher.isHealthy();
            healthy &= ph;

            HealthCheckResult publisherHealth = new HealthCheckResult("Publisher");
            publisherHealth.setHealthy(publisher.isHealthy());
            result.addChildCheck(publisherHealth);
        }

        result.setHealthy(healthy);

        return result;
    }

    public void setAuditServiceRegister(AuditLoggingServiceRegister serviceRegister)
    {
        this.serviceRegister = serviceRegister;
    }

    private AuditLoggingService getAuditLoggingService()
    {
        return serviceRegister == null ? null : serviceRegister.getAuditLoggingService();
    }

    private AuditEvent newAuditEvent()
    {
        AuditEvent ae = new AuditEvent(new Date());
        ae.setApplicationName("CA");
        ae.setName("SYSTEM");
        return ae;
    }

    private static Object[] incSerialNumber(IdentifiedCertProfile profile, X500Name origName, String latestSN)
    throws BadFormatException
    {
        RDN[] rdns = origName.getRDNs();

        int commonNameIndex = -1;
        int serialNumberIndex = -1;
        for(int i = 0; i < rdns.length; i++)
        {
            RDN rdn = rdns[i];
            ASN1ObjectIdentifier type = rdn.getFirst().getType();
            if(ObjectIdentifiers.DN_CN.equals(type))
            {
                commonNameIndex = i;
            }
            else if(ObjectIdentifiers.DN_SERIALNUMBER.equals(type))
            {
                serialNumberIndex = i;
            }
        }

        String newSerialNumber = profile.incSerialNumber(latestSN);
        RDN serialNumberRdn = new RDN(ObjectIdentifiers.DN_SERIALNUMBER, new DERPrintableString(newSerialNumber));

        X500Name newName;
        if(serialNumberIndex != -1)
        {
            rdns[serialNumberIndex] = serialNumberRdn;
            newName = new X500Name(rdns);
        }
        else
        {
            List<RDN> newRdns = new ArrayList<>(rdns.length + 1);

            if(commonNameIndex == -1)
            {
                newRdns.add(serialNumberRdn);
            }

            for(int i = 0; i < rdns.length; i++)
            {
                newRdns.add(rdns[i]);
                if(i == commonNameIndex)
                {
                    newRdns.add(serialNumberRdn);
                }
            }

            newName = new X500Name(newRdns.toArray(new RDN[0]));
        }

        return new Object[]{newName, newSerialNumber};
    }

    private boolean verifySignature(X509Certificate cert)
    {
        PublicKey caPublicKey = caInfo.getCertificate().getCert().getPublicKey();
        try
        {
            final String provider = "XipkiNSS";

            if(tryXipkiNSStoVerify == null)
            {
                // Not for ECDSA
                if(caPublicKey instanceof ECPublicKey)
                {
                    tryXipkiNSStoVerify = Boolean.FALSE;
                }
                else
                {
                    if(Security.getProvider(provider) == null)
                    {
                        LOG.info("Security provider {} is not registered", provider);
                        tryXipkiNSStoVerify = Boolean.FALSE;
                    }
                    else
                    {
                        byte[] tbs = cert.getTBSCertificate();
                        byte[] signatureValue = cert.getSignature();
                        String sigAlgName = cert.getSigAlgName();
                        try
                        {
                            Signature verifier = Signature.getInstance(sigAlgName, provider);
                            verifier.initVerify(caPublicKey);
                            verifier.update(tbs);
                            boolean sigValid = verifier.verify(signatureValue);

                            LOG.info("Use {} to verify {} signature", provider, sigAlgName);
                            tryXipkiNSStoVerify = Boolean.TRUE;
                            return sigValid;
                        }catch(Exception e)
                        {
                            LOG.info("Cannot use {} to verify {} signature", provider, sigAlgName);
                            tryXipkiNSStoVerify = Boolean.FALSE;
                        }
                    }
                }
            }

            if(tryXipkiNSStoVerify)
            {
                byte[] tbs = cert.getTBSCertificate();
                byte[] signatureValue = cert.getSignature();
                String sigAlgName = cert.getSigAlgName();
                Signature verifier = Signature.getInstance(sigAlgName, provider);
                verifier.initVerify(caPublicKey);
                verifier.update(tbs);
                return verifier.verify(signatureValue);
            }
            else
            {
                cert.verify(caPublicKey);
                return true;
            }
        } catch (SignatureException | InvalidKeyException | CertificateException |
                NoSuchAlgorithmException | NoSuchProviderException e)
        {
            LOG.debug("{} while verifying signature: {}", e.getClass().getName(), e.getMessage());
            return false;
        }
    }

    private static Date setToMidnight(Date date, TimeZone timezone)
    {
        Calendar c = Calendar.getInstance(timezone);
        c.setTime(date);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
