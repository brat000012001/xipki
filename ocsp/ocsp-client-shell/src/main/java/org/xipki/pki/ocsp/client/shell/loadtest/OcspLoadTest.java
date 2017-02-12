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

package org.xipki.pki.ocsp.client.shell.loadtest;

import java.math.BigInteger;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.common.LoadExecutor;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.pki.ocsp.client.api.OcspRequestor;
import org.xipki.pki.ocsp.client.api.OcspRequestorException;
import org.xipki.pki.ocsp.client.api.OcspResponseException;
import org.xipki.pki.ocsp.client.api.RequestOptions;
import org.xipki.pki.ocsp.client.shell.OcspUtils;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class OcspLoadTest extends LoadExecutor {

    final class Testor implements Runnable {

        @Override
        public void run() {
            while (!stop() && getErrorAccout() < 10) {
                BigInteger sn = nextSerialNumber();
                if (sn == null) {
                    break;
                }
                int numFailed = testNext(sn) ? 0 : 1;
                account(1, numFailed);
            }
        }

        private boolean testNext(final BigInteger sn) {
            BasicOCSPResp basicResp;
            try {
                OCSPResp response = requestor.ask(caCert, sn, serverUrl, options, null);
                basicResp = OcspUtils.extractBasicOcspResp(response);
            } catch (OcspRequestorException ex) {
                LOG.warn("OCSPRequestorException: {}", ex.getMessage());
                return false;
            } catch (OcspResponseException ex) {
                LOG.warn("OCSPResponseException: {}", ex.getMessage());
                return false;
            } catch (Throwable th) {
                LOG.warn("{}: {}", th.getClass().getName(), th.getMessage());
                return false;
            }

            SingleResp[] singleResponses = basicResp.getResponses();

            if (singleResponses == null || singleResponses.length == 0) {
                LOG.warn("received no status from server");
                return false;
            }

            final int n = singleResponses.length;
            if (n != 1) {
                LOG.warn("received status with {} single responses from server, {}", n,
                        "but 1 was requested");
                return false;
            } else {
                SingleResp singleResp = singleResponses[0];
                CertificateStatus singleCertStatus = singleResp.getCertStatus();

                String status;
                if (singleCertStatus == null) {
                    status = "good";
                } else if (singleCertStatus instanceof RevokedStatus) {
                    RevokedStatus revStatus = (RevokedStatus) singleCertStatus;
                    Date revTime = revStatus.getRevocationTime();

                    if (revStatus.hasRevocationReason()) {
                        int reason = revStatus.getRevocationReason();
                        status = "revoked, reason = " + reason + ", revocationTime = " + revTime;
                    } else {
                        status = "revoked, no reason, revocationTime = " + revTime;
                    }
                } else if (singleCertStatus instanceof UnknownStatus) {
                    status = "unknown";
                } else {
                    LOG.warn("status: ERROR");
                    return false;
                }

                LOG.info("SN: {}, status: {}", sn, status);
                return true;
            } // end if
        } // method testNext

    } // class Testor

    private static final Logger LOG = LoggerFactory.getLogger(OcspLoadTest.class);

    private final OcspRequestor requestor;

    private final Iterator<BigInteger> serials;

    private X509Certificate caCert;

    private URL serverUrl;

    private RequestOptions options;

    private final int maxRequests;

    private AtomicInteger processedRequests = new AtomicInteger(0);

    public OcspLoadTest(final OcspRequestor requestor, final Iterator<BigInteger> serials,
            final X509Certificate caCert, final URL serverUrl, final RequestOptions options,
            final int maxRequests, final String description) {
        super(description);
        this.maxRequests = maxRequests;
        this.requestor = ParamUtil.requireNonNull("requestor", requestor);
        this.serials = ParamUtil.requireNonNull("serials", serials);
        this.caCert = ParamUtil.requireNonNull("caCert", caCert);
        this.serverUrl = ParamUtil.requireNonNull("serverUrl", serverUrl);
        this.options = ParamUtil.requireNonNull("options", options);
    }

    @Override
    protected Runnable getTestor() throws Exception {
        return new Testor();
    }

    private BigInteger nextSerialNumber() {
        if (maxRequests > 0) {
            int num = processedRequests.getAndAdd(1);
            if (num >= maxRequests) {
                return null;
            }
        }

        try {
            return this.serials.next();
        } catch (NoSuchElementException ex) {
            return null;
        }
    }

}
