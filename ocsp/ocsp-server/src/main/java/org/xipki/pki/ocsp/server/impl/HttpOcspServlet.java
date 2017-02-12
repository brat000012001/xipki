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

package org.xipki.pki.ocsp.server.impl;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.ASN1StreamParser;
import org.bouncycastle.asn1.ocsp.OCSPRequest;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.audit.AuditEvent;
import org.xipki.commons.audit.AuditLevel;
import org.xipki.commons.audit.AuditService;
import org.xipki.commons.audit.AuditServiceRegister;
import org.xipki.commons.audit.AuditStatus;
import org.xipki.commons.common.util.LogUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.common.util.StringUtil;
import org.xipki.commons.security.HashAlgoType;
import org.xipki.pki.ocsp.server.impl.OcspRespWithCacheInfo.ResponseCacheInfo;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class HttpOcspServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(HttpOcspServlet.class);

    private static final long serialVersionUID = 1L;

    private static final String CT_REQUEST = "application/ocsp-request";

    private static final String CT_RESPONSE = "application/ocsp-response";

    private AuditServiceRegister auditServiceRegister;

    private OcspServer server;

    public HttpOcspServlet() {
    }

    public void setServer(final OcspServer server) {
        this.server = server;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
    throws ServletException, IOException {
        ResponderAndRelativeUri respAndUri = server.getResponderAndRelativeUri(request);
        if (respAndUri == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Responder responder = respAndUri.getResponder();
        if (responder.getRequestOption().supportsHttpGet()) {
            processRequest(request, response, respAndUri, true);
        } else {
            super.doGet(request, response);
        }
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
    throws ServletException, IOException {
        ResponderAndRelativeUri respAndUri = server.getResponderAndRelativeUri(request);
        if (respAndUri == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (StringUtil.isNotBlank(respAndUri.getRelativeUri())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        processRequest(request, response, respAndUri, false);
    }

    private void processRequest(final HttpServletRequest request,
            final HttpServletResponse response, final ResponderAndRelativeUri respAndUri,
            final boolean getMethod) throws ServletException, IOException {
        Responder responder = respAndUri.getResponder();
        AuditEvent event = null;
        AuditLevel auditLevel = AuditLevel.INFO;
        AuditStatus auditStatus = AuditStatus.SUCCESSFUL;
        String auditMessage = null;

        AuditService auditService = (auditServiceRegister == null) ? null
                : auditServiceRegister.getAuditService();

        if (responder.getAuditOption() != null) {
            event = new AuditEvent(new Date());
            event.setApplicationName(OcspAuditConstants.APPNAME);
            event.setName(OcspAuditConstants.NAME_PERF);
        }

        try {
            if (server == null) {
                String message = "responder in servlet not configured";
                LOG.error(message);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentLength(0);

                auditLevel = AuditLevel.ERROR;
                auditStatus = AuditStatus.FAILED;
                auditMessage = message;
                return;
            }

            InputStream requestStream;
            if (getMethod) {
                String relativeUri = respAndUri.getRelativeUri();

                // RFC2560 A.1.1 specifies that request longer than 255 bytes SHOULD be sent by
                // POST, we support GET for longer requests anyway.
                if (relativeUri.length() > responder.getRequestOption().getMaxRequestSize()) {
                    response.setContentLength(0);
                    response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);

                    auditStatus = AuditStatus.FAILED;
                    auditMessage = "request too large";
                    return;
                }

                requestStream = new ByteArrayInputStream(Base64.decode(relativeUri));
            } else {
                // accept only "application/ocsp-request" as content type
                if (!CT_REQUEST.equalsIgnoreCase(request.getContentType())) {
                    response.setContentLength(0);
                    response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);

                    auditStatus = AuditStatus.FAILED;
                    auditMessage = "unsupported media type " + request.getContentType();
                    return;
                }

                // request too long
                if (request.getContentLength() > responder.getRequestOption().getMaxRequestSize()) {
                    response.setContentLength(0);
                    response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);

                    auditStatus = AuditStatus.FAILED;
                    auditMessage = "request too large";
                    return;
                } // if (CT_REQUEST)

                requestStream = request.getInputStream();
            } // end if (getMethod)

            OCSPRequest ocspRequest;
            try {
                ASN1StreamParser parser = new ASN1StreamParser(requestStream);
                ocspRequest = OCSPRequest.getInstance(parser.readObject());
            } catch (Exception ex) {
                response.setContentLength(0);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

                auditStatus = AuditStatus.FAILED;
                auditMessage = "bad request";

                LogUtil.error(LOG, ex, "could not parse the request (OCSPRequest)");
                return;
            }

            OCSPReq ocspReq = new OCSPReq(ocspRequest);

            response.setContentType(HttpOcspServlet.CT_RESPONSE);

            OcspRespWithCacheInfo ocspRespWithCacheInfo =
                    server.answer(responder, ocspReq, getMethod, event);
            if (ocspRespWithCacheInfo == null) {
                auditMessage = "processRequest returned null, this should not happen";
                LOG.error(auditMessage);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentLength(0);

                auditLevel = AuditLevel.ERROR;
                auditStatus = AuditStatus.FAILED;
            } else {
                OCSPResp resp = ocspRespWithCacheInfo.getResponse();
                byte[] encodedOcspResp = null;
                response.setStatus(HttpServletResponse.SC_OK);

                ResponseCacheInfo cacheInfo = ocspRespWithCacheInfo.getCacheInfo();
                if (getMethod && cacheInfo != null) {
                    encodedOcspResp = resp.getEncoded();
                    long now = System.currentTimeMillis();
                    // RFC 5019 6.2: Date: The date and time at which the OCSP server generated
                    // the HTTP response.
                    response.setDateHeader("Date", now);
                    // RFC 5019 6.2: Last-Modified: date and time at which the OCSP responder
                    // last modified the response.
                    response.setDateHeader("Last-Modified", cacheInfo.getThisUpdate());
                    // RFC 5019 6.2: Expires: This date and time will be the same as the
                    // nextUpdate time-stamp in the OCSP
                    // response itself.
                    // This is overridden by max-age on HTTP/1.1 compatible components
                    if (cacheInfo.getNextUpdate() != null) {
                        response.setDateHeader("Expires", cacheInfo.getNextUpdate());
                    }
                    // RFC 5019 6.2: This profile RECOMMENDS that the ETag value be the ASCII
                    // HEX representation of the SHA1 hash of the OCSPResponse structure.
                    response.setHeader("ETag",
                            new StringBuilder(42).append('\\')
                                .append(HashAlgoType.SHA1.hexHash(encodedOcspResp))
                                .append('\\')
                            .toString());

                    // Max age must be in seconds in the cache-control header
                    long maxAge;
                    if (responder.getResponseOption().getCacheMaxAge() != null) {
                        maxAge = responder.getResponseOption().getCacheMaxAge().longValue();
                    } else {
                        maxAge = OcspServer.DFLT_CACHE_MAX_AGE;
                    }

                    if (cacheInfo.getNextUpdate() != null) {
                        maxAge = Math.min(maxAge,
                                (cacheInfo.getNextUpdate() - cacheInfo.getThisUpdate()) / 1000);
                    }

                    response.setHeader("Cache-Control",
                            new StringBuilder(55).append("max-age=").append(maxAge)
                                .append(",public,no-transform,must-revalidate").toString());
                } // end if (getMethod && cacheInfo != null)

                if (encodedOcspResp != null) {
                    response.getOutputStream().write(encodedOcspResp);
                } else {
                    ASN1OutputStream asn1Out = new ASN1OutputStream(response.getOutputStream());
                    asn1Out.writeObject(resp.toASN1Structure());
                    asn1Out.flush();
                }
            } // end if (ocspRespWithCacheInfo)
        } catch (EOFException ex) {
            LogUtil.warn(LOG, ex, "Connection reset by peer");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentLength(0);
        } catch (Throwable th) {
            final String message = "Throwable thrown, this should not happen!";
            LogUtil.error(LOG, th, message);

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentLength(0);

            auditLevel = AuditLevel.ERROR;
            auditStatus = AuditStatus.FAILED;
            auditMessage = "internal error";
        } finally {
            try {
                response.flushBuffer();
            } catch (IOException ex) {
                final String message = "error while calling responsse.flushBuffer";
                LogUtil.error(LOG, ex, message);
                auditLevel = AuditLevel.ERROR;
                auditStatus = AuditStatus.FAILED;
                auditMessage = "internal error";
            } finally {
                if (event != null) {
                    if (auditLevel != null) {
                        event.setLevel(auditLevel);
                    }

                    if (auditStatus != null) {
                        event.setStatus(auditStatus);
                    }

                    if (auditMessage != null) {
                        event.addEventData(OcspAuditConstants.NAME_message, auditMessage);
                    }

                    event.finish();
                    auditService.logEvent(event);
                }
            } // end internal try
        } // end external try
    } // method processRequest

    public void setAuditServiceRegister(final AuditServiceRegister auditServiceRegister) {
        this.auditServiceRegister = ParamUtil.requireNonNull("auditServiceRegister",
                auditServiceRegister);
    }

}
