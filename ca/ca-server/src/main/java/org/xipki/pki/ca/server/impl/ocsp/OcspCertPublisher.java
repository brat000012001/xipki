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

package org.xipki.pki.ca.server.impl.ocsp;

import java.security.NoSuchAlgorithmException;
import java.security.cert.X509CRL;
import java.util.Date;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.audit.AuditEvent;
import org.xipki.commons.audit.AuditLevel;
import org.xipki.commons.audit.AuditServiceRegister;
import org.xipki.commons.audit.AuditStatus;
import org.xipki.commons.common.ConfPairs;
import org.xipki.commons.common.util.LogUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.datasource.DataSourceWrapper;
import org.xipki.commons.datasource.springframework.dao.DataAccessException;
import org.xipki.commons.password.PasswordResolver;
import org.xipki.commons.security.CertRevocationInfo;
import org.xipki.commons.security.X509Cert;
import org.xipki.commons.security.util.X509Util;
import org.xipki.pki.ca.api.EnvParameterResolver;
import org.xipki.pki.ca.api.X509CertWithDbId;
import org.xipki.pki.ca.api.publisher.CertPublisherException;
import org.xipki.pki.ca.api.publisher.x509.X509CertPublisher;
import org.xipki.pki.ca.api.publisher.x509.X509CertificateInfo;
import org.xipki.pki.ca.server.impl.CaAuditConstants;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class OcspCertPublisher extends X509CertPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(OcspCertPublisher.class);

    @SuppressWarnings("unused")
    private EnvParameterResolver envParameterResolver;

    private OcspStoreQueryExecutor queryExecutor;

    private boolean asyn;

    private boolean publishsGoodCert = true;

    private AuditServiceRegister auditServiceRegister;

    public OcspCertPublisher() {
    }

    @Override
    public void initialize(final String conf, final PasswordResolver passwordResolver,
            final Map<String, DataSourceWrapper> datasources) throws CertPublisherException {
        ParamUtil.requireNonNull("conf", conf);
        ParamUtil.requireNonEmpty("datasources", datasources);

        ConfPairs utf8pairs = new ConfPairs(conf);
        String str = utf8pairs.getValue("publish.goodcerts");
        this.publishsGoodCert = (str == null) ? true : Boolean.parseBoolean(str);

        str = utf8pairs.getValue("asyn");
        this.asyn = (str == null) ? false : Boolean.parseBoolean(str);

        ConfPairs confPairs = new ConfPairs(conf);
        String datasourceName = confPairs.getValue("datasource");

        DataSourceWrapper datasource = null;
        if (datasourceName != null) {
            datasource = datasources.get(datasourceName);
        }

        if (datasource == null) {
            throw new CertPublisherException(
                    "no datasource named '" + datasourceName + "' is specified");
        }

        try {
            queryExecutor = new OcspStoreQueryExecutor(datasource, this.publishsGoodCert);
        } catch (NoSuchAlgorithmException | DataAccessException ex) {
            throw new CertPublisherException(ex.getMessage(), ex);
        }
    } // method initialize

    @Override
    public void setEnvParameterResolver(final EnvParameterResolver parameterResolver) {
        this.envParameterResolver = parameterResolver;
    }

    @Override
    public boolean caAdded(final X509Cert issuer) {
        try {
            queryExecutor.addIssuer(issuer);
            return true;
        } catch (Exception ex) {
            logAndAudit(issuer.getSubject(), issuer, ex, "could not publish issuer");
            return false;
        }
    }

    @Override
    public boolean certificateAdded(final X509CertificateInfo certInfo) {
        X509Cert caCert = certInfo.getIssuerCert();
        X509CertWithDbId cert = certInfo.getCert();

        try {
            queryExecutor.addCert(caCert, cert, certInfo.getProfileName(),
                    certInfo.getRevocationInfo());
            return true;
        } catch (Exception ex) {
            logAndAudit(caCert.getSubject(), cert, ex, "could not save certificate");
            return false;
        }
    }

    @Override
    public boolean certificateRevoked(final X509Cert caCert, final X509CertWithDbId cert,
            final String certprofile, final CertRevocationInfo revInfo) {
        try {
            queryExecutor.revokeCert(caCert, cert, certprofile, revInfo);
            return true;
        } catch (Exception ex) {
            logAndAudit(caCert.getSubject(), cert, ex, "could not publish revoked certificate");
            return false;
        }
    }

    @Override
    public boolean certificateUnrevoked(final X509Cert caCert, final X509CertWithDbId cert) {
        try {
            queryExecutor.unrevokeCert(caCert, cert);
            return true;
        } catch (Exception ex) {
            logAndAudit(caCert.getSubject(), cert, ex,
                    "could not publish unrevocation of certificate");
            return false;
        }
    }

    private void logAndAudit(final String issuer, final X509Cert cert, final Exception ex,
            final String messagePrefix) {
        String subjectText = cert.getSubject();
        String serialText = LogUtil.formatCsn(cert.getCert().getSerialNumber());

        LOG.error("{} (issuser='{}': subject='{}', serialNumber={}). Message: {}",
                messagePrefix, issuer, subjectText, serialText, ex.getMessage());
        LOG.debug("error", ex);

        AuditEvent event = new AuditEvent(new Date());
        event.setApplicationName("CAPublisher");
        event.setName("SYSTEM");
        event.setLevel(AuditLevel.ERROR);
        event.setStatus(AuditStatus.FAILED);
        if (cert instanceof X509CertWithDbId) {
            Long certId = ((X509CertWithDbId) cert).getCertId();
            if (certId != null) {
                event.addEventData(CaAuditConstants.NAME_id, certId);
            }
        }
        event.addEventData(CaAuditConstants.NAME_issuer, issuer);
        event.addEventData(CaAuditConstants.NAME_subject, subjectText);
        event.addEventData(CaAuditConstants.NAME_serial, serialText);
        event.addEventData(CaAuditConstants.NAME_message, messagePrefix);
        auditServiceRegister.getAuditService().logEvent(event);
    } // method logAndAudit

    @Override
    public boolean crlAdded(final X509Cert caCert, final X509CRL crl) {
        return true;
    }

    @Override
    public boolean isHealthy() {
        return queryExecutor.isHealthy();
    }

    @Override
    public void setAuditServiceRegister(final AuditServiceRegister auditServiceRegister) {
        this.auditServiceRegister = ParamUtil.requireNonNull("auditServiceRegister",
                auditServiceRegister);
    }

    @Override
    public boolean caRevoked(final X509Cert caCert, final CertRevocationInfo revocationInfo) {
        try {
            queryExecutor.revokeCa(caCert, revocationInfo);
            return true;
        } catch (Exception ex) {
            String issuerText = X509Util.getRfc4519Name(caCert.getCert().getIssuerX500Principal());
            logAndAudit(issuerText, caCert, ex, "could not publish revocation of CA");
            return false;
        }
    }

    @Override
    public boolean caUnrevoked(final X509Cert caCert) {
        try {
            queryExecutor.unrevokeCa(caCert);
            return true;
        } catch (Exception ex) {
            String issuerText = X509Util.getRfc4519Name(caCert.getCert().getIssuerX500Principal());
            logAndAudit(issuerText, caCert, ex, "could not publish unrevocation of CA");
            return false;
        }
    }

    @Override
    public boolean certificateRemoved(final X509Cert issuerCert, final X509CertWithDbId cert) {
        try {
            queryExecutor.removeCert(issuerCert, cert);
            return true;
        } catch (Exception ex) {
            String issuerText = X509Util.getRfc4519Name(
                    issuerCert.getCert().getIssuerX500Principal());
            logAndAudit(issuerText, issuerCert, ex, "could not publish removal of certificate");
            return false;
        }
    }

    @Override
    public boolean isAsyn() {
        return asyn;
    }

    @Override
    public boolean publishsGoodCert() {
        return publishsGoodCert;
    }

}
