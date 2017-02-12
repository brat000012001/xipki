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

package org.xipki.pki.ocsp.server.impl.store.crl;

import java.util.Date;
import java.util.Map;

import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.CertRevocationInfo;
import org.xipki.commons.security.HashAlgoType;
import org.xipki.pki.ocsp.api.CertStatus;
import org.xipki.pki.ocsp.api.CertStatusInfo;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

class CrlCertStatusInfo {

    private final CertStatus certStatus;

    private final CertRevocationInfo revocationInfo;

    private final String certprofile;

    private final Date notBefore;

    private final Date notAfter;

    private final Map<HashAlgoType, byte[]> certHashes;

    private CrlCertStatusInfo(final CertStatus certStatus, final CertRevocationInfo revocationInfo,
            final String certprofile, final Map<HashAlgoType, byte[]> certHashes,
            final Date notBefore, final Date notAfter) {
        this.certStatus = ParamUtil.requireNonNull("certStatus", certStatus);
        this.revocationInfo = revocationInfo;
        this.certprofile = certprofile;
        this.certHashes = certHashes;
        this.notBefore = notBefore;
        this.notAfter = notAfter;
    }

    CertStatus getCertStatus() {
        return certStatus;
    }

    CertRevocationInfo getRevocationInfo() {
        return revocationInfo;
    }

    String getCertprofile() {
        return certprofile;
    }

    boolean isExpired(Date time) {
        return (notAfter == null) ? true : time.after(notAfter);
    }

    boolean isNotYetValid(Date time) {
        return (notBefore == null) ? true : time.before(notBefore);
    }

    CertStatusInfo getCertStatusInfo(final HashAlgoType hashAlgo, final Date thisUpdate,
            final Date nextUpdate) {
        switch (certStatus) {
        case ISSUER_UNKNOWN:
        case UNKNOWN:
            throw new RuntimeException("should not reach here");
        case IGNORE:
            return CertStatusInfo.getIgnoreCertStatusInfo(thisUpdate, nextUpdate);
        case GOOD:
        case REVOKED:
            byte[] certHash = null;
            if (hashAlgo != null) {
                certHash = (certHashes == null) ? null : certHashes.get(hashAlgo);
            }

            if (certStatus == CertStatus.GOOD) {
                return CertStatusInfo.getGoodCertStatusInfo(hashAlgo, certHash, thisUpdate,
                        nextUpdate, certprofile);
            } else {
                return CertStatusInfo.getRevokedCertStatusInfo(revocationInfo, hashAlgo, certHash,
                        thisUpdate, nextUpdate, certprofile);
            }
        default:
            throw new RuntimeException("unknown certStatus: " + certStatus);
        } // end switch
    } // method getCertStatusInfo

    static CrlCertStatusInfo getIgnoreCertStatusInfo() {
        return new CrlCertStatusInfo(CertStatus.IGNORE, null, null, null, null, null);
    }

    static CrlCertStatusInfo getGoodCertStatusInfo(final String certprofile, final Map<HashAlgoType,
            byte[]> certHashes, final Date notBefore, final Date notAfter) {
        ParamUtil.requireNonBlank("certprofile", certprofile);
        return new CrlCertStatusInfo(CertStatus.GOOD, null, certprofile, certHashes, notBefore,
                notAfter);
    }

    static CrlCertStatusInfo getRevokedCertStatusInfo(final CertRevocationInfo revocationInfo,
            final String certprofile, final Map<HashAlgoType, byte[]> certHashes,
            final Date notBefore, final Date notAfter) {
        ParamUtil.requireNonNull("revocationInfo", revocationInfo);
        return new CrlCertStatusInfo(CertStatus.REVOKED, revocationInfo, certprofile, certHashes,
                notBefore, notAfter);
    }

}
