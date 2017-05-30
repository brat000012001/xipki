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

package org.xipki.pki.ca.server.impl.scep;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.audit.AuditEvent;
import org.xipki.commons.audit.AuditLevel;
import org.xipki.commons.audit.AuditService;
import org.xipki.commons.audit.AuditServiceRegister;
import org.xipki.commons.audit.AuditStatus;
import org.xipki.commons.common.util.IoUtil;
import org.xipki.commons.common.util.LogUtil;
import org.xipki.commons.common.util.RandomUtil;
import org.xipki.pki.ca.api.OperationException;
import org.xipki.pki.ca.api.OperationException.ErrorCode;
import org.xipki.pki.ca.api.RequestType;
import org.xipki.pki.ca.server.impl.CaAuditConstants;
import org.xipki.pki.ca.server.impl.CaManagerImpl;
import org.xipki.pki.ca.server.mgmt.api.CaStatus;
import org.xipki.pki.scep.exception.MessageDecodingException;
import org.xipki.pki.scep.transaction.Operation;
import org.xipki.pki.scep.util.ScepConstants;

/**
 * URL http://host:port/scep/&lt;name&gt;/&lt;profile-alias&gt;/pkiclient.ext
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class ScepServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ScepServlet.class);

    private static final long serialVersionUID = 1L;

    private static final String CGI_PROGRAM = "/pkiclient.exe";

    private static final int CGI_PROGRAM_LEN = CGI_PROGRAM.length();

    private static final String CT_RESPONSE = ScepConstants.CT_PKI_MESSAGE;

    private AuditServiceRegister auditServiceRegister;

    private CaManagerImpl responderManager;

    public ScepServlet() {
    }

    @Override
    public void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        service(request, response, false);
    }

    @Override
    public void doPost(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        service(request, response, true);
    }

    private void service(final HttpServletRequest request, final HttpServletResponse response,
            final boolean post) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String servletPath = request.getServletPath();

        int len = servletPath.length();

        String scepName = null;
        String certProfileName = null;
        if (requestUri.length() > len + 1) {
            String scepPath = URLDecoder.decode(requestUri.substring(len + 1), "UTF-8");
            if (scepPath.endsWith(CGI_PROGRAM)) {
                String path = scepPath.substring(0, scepPath.length() - CGI_PROGRAM_LEN);
                String[] tokens = path.split("/");
                if (tokens.length == 2) {
                    scepName = tokens[0];
                    certProfileName = tokens[1].toUpperCase();
                }
            } // end if
        } // end if

        if (scepName == null || certProfileName == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        AuditService auditService = auditServiceRegister.getAuditService();
        AuditEvent event = new AuditEvent(new Date());
        event.setApplicationName("SCEP");
        event.setName(CaAuditConstants.NAME_PERF);
        event.addEventData(CaAuditConstants.NAME_SCEP_name, scepName + "/" + certProfileName);
        event.addEventData(CaAuditConstants.NAME_reqType, RequestType.SCEP.name());

        String msgId = RandomUtil.nextHexLong();
        event.addEventData(CaAuditConstants.NAME_mid, msgId);

        AuditLevel auditLevel = AuditLevel.INFO;
        AuditStatus auditStatus = AuditStatus.SUCCESSFUL;
        String auditMessage = null;

        try {
            if (responderManager == null) {
                auditMessage = "responderManager in servlet not configured";
                LOG.error(auditMessage);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentLength(0);

                auditLevel = AuditLevel.ERROR;
                auditStatus = AuditStatus.FAILED;
                return;
            }

            Scep responder = responderManager.getScep(scepName);
            if (responder == null || responder.getStatus() != CaStatus.ACTIVE
                    || !responder.supportsCertProfile(certProfileName)) {
                auditMessage = "unknown SCEP '" + scepName + "/" + certProfileName + "'";
                LOG.warn(auditMessage);

                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.setContentLength(0);

                auditStatus = AuditStatus.FAILED;
                return;
            }

            String operation = request.getParameter("operation");
            event.addEventData(CaAuditConstants.NAME_SCEP_operation, operation);

            if ("PKIOperation".equalsIgnoreCase(operation)) {
                CMSSignedData reqMessage;
                // parse the request
                try {
                    byte[] content;
                    if (post) {
                        content = IoUtil.read(request.getInputStream());
                    } else {
                        String b64 = request.getParameter("message");
                        content = Base64.decode(b64);
                    }

                    reqMessage = new CMSSignedData(content);
                } catch (Exception ex) {
                    final String msg = "invalid request";
                    LogUtil.error(LOG, ex, msg);

                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.setContentLength(0);

                    auditMessage = msg;
                    auditStatus = AuditStatus.FAILED;
                    return;
                }

                ContentInfo ci;
                try {
                    ci = responder.servicePkiOperation(reqMessage, certProfileName, msgId, event);
                } catch (MessageDecodingException ex) {
                    final String msg = "could not decrypt and/or verify the request";
                    LogUtil.error(LOG, ex, msg);

                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.setContentLength(0);

                    auditMessage = msg;
                    auditStatus = AuditStatus.FAILED;
                    return;
                } catch (OperationException ex) {
                    ErrorCode code = ex.getErrorCode();

                    int httpCode;
                    switch (code) {
                    case ALREADY_ISSUED:
                    case CERT_REVOKED:
                    case CERT_UNREVOKED:
                        httpCode = HttpServletResponse.SC_FORBIDDEN;
                        break;
                    case BAD_CERT_TEMPLATE:
                    case BAD_REQUEST:
                    case BAD_POP:
                    case INVALID_EXTENSION:
                    case UNKNOWN_CERT:
                    case UNKNOWN_CERT_PROFILE:
                        httpCode = HttpServletResponse.SC_BAD_REQUEST;
                        break;
                    case NOT_PERMITTED:
                        httpCode = HttpServletResponse.SC_UNAUTHORIZED;
                        break;
                    case SYSTEM_UNAVAILABLE:
                        httpCode = HttpServletResponse.SC_SERVICE_UNAVAILABLE;
                        break;
                    case CRL_FAILURE:
                    case DATABASE_FAILURE:
                    case SYSTEM_FAILURE:
                        httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                        break;
                    default:
                        httpCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                        break;
                    }

                    final String msg = ex.getMessage();
                    LogUtil.error(LOG, ex, msg);

                    response.setStatus(httpCode);
                    response.setContentLength(0);

                    auditMessage = msg;
                    auditStatus = AuditStatus.FAILED;
                    return;
                }
                response.setContentType(CT_RESPONSE);

                ASN1OutputStream asn1Out = new ASN1OutputStream(response.getOutputStream());
                asn1Out.writeObject(ci);
                asn1Out.flush();
            } else if (Operation.GetCACaps.getCode().equalsIgnoreCase(operation)) {
                // CA-Ident is ignored
                response.setContentType(ScepConstants.CT_TEXT_PLAIN);
                byte[] caCapsBytes = responder.getCaCaps().getBytes();

                response.getOutputStream().write(caCapsBytes);
            } else if (Operation.GetCACert.getCode().equalsIgnoreCase(operation)) {
                // CA-Ident is ignored
                byte[] respBytes = responder.getCaCertResp().getBytes();
                response.setContentType(ScepConstants.CT_X509_CA_RA_CERT);
                response.setContentLength(respBytes.length);
                response.getOutputStream().write(respBytes);
            } else if (Operation.GetNextCACert.getCode().equalsIgnoreCase(operation)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentLength(0);

                auditMessage = "SCEP operation '" + operation + "' is not permitted";
                auditStatus = AuditStatus.FAILED;
                return;
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentLength(0);

                auditMessage = "unknown SCEP operation '" + operation + "'";
                auditStatus = AuditStatus.FAILED;
                return;
            }
        } catch (EOFException ex) {
            final String msg = "connection reset by peer";
            if (LOG.isWarnEnabled()) {
                LogUtil.warn(LOG, ex, msg);
            }
            LOG.debug(msg, ex);

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentLength(0);
        } catch (Throwable th) {
            LOG.error("Throwable thrown, this should not happen!", th);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentLength(0);
            auditLevel = AuditLevel.ERROR;
            auditStatus = AuditStatus.FAILED;
            auditMessage = "internal error";
        } finally {
            try {
                response.flushBuffer();
            } finally {
                audit(auditService, event, auditLevel, auditStatus, auditMessage);
            }
        }
    } // method service

    protected PKIMessage generatePkiMessage(final InputStream is) throws IOException {
        ASN1InputStream asn1Stream = new ASN1InputStream(is);

        try {
            return PKIMessage.getInstance(asn1Stream.readObject());
        } finally {
            try {
                asn1Stream.close();
            } catch (Exception ex) {
                LOG.error("could not close ASN1 stream: {}", asn1Stream);
            }
        }
    } // method generatePKIMessage

    public void setResponderManager(final CaManagerImpl responderManager) {
        this.responderManager = responderManager;
    }

    public void setAuditServiceRegister(final AuditServiceRegister auditServiceRegister) {
        this.auditServiceRegister = auditServiceRegister;
    }

    private static void audit(final AuditService auditService, final AuditEvent event,
            final AuditLevel auditLevel, final AuditStatus auditStatus, final String auditMessage) {
        event.setLevel(auditLevel);

        if (auditStatus != null) {
            event.setStatus(auditStatus);
        }

        if (auditMessage != null) {
            event.addEventData(CaAuditConstants.NAME_message, auditMessage);
        }

        event.finish();
        auditService.logEvent(event);
    } // method audit

}
