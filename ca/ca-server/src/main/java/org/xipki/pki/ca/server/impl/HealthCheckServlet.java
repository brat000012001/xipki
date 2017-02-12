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

package org.xipki.pki.ca.server.impl;

import java.io.EOFException;
import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.common.HealthCheckResult;
import org.xipki.commons.common.util.LogUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.pki.ca.server.impl.cmp.CmpResponderManager;
import org.xipki.pki.ca.server.impl.cmp.X509CaCmpResponder;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class HealthCheckServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(HealthCheckServlet.class);

    private static final long serialVersionUID = 1L;

    private static final String CT_RESPONSE = "application/json";

    private CmpResponderManager responderManager;

    public HealthCheckServlet() {
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
    throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        try {
            if (responderManager == null) {
                LOG.error("responderManager in servlet is not configured");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentLength(0);
                return;
            }

            String requestUri = request.getRequestURI();
            String servletPath = request.getServletPath();

            String caName = null;
            X509CaCmpResponder responder = null;
            int len = servletPath.length();
            if (requestUri.length() > len + 1) {
                String caAlias = URLDecoder.decode(requestUri.substring(len + 1), "UTF-8");
                caName = responderManager.getCaNameForAlias(caAlias);
                if (caName == null) {
                    caName = caAlias;
                }
                caName = caName.toUpperCase();
                responder = responderManager.getX509CaCmpResponder(caName);
            }

            if (caName == null || responder == null || !responder.isInService()) {
                String auditMessage;
                if (caName == null) {
                    auditMessage = "no CA is specified";
                } else if (responder == null) {
                    auditMessage = "unknown CA '" + caName + "'";
                } else {
                    auditMessage = "CA '" + caName + "' is out of service";
                }
                LOG.warn(auditMessage);

                response.setContentLength(0);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.flushBuffer();
                return;
            }

            HealthCheckResult healthResult = responder.healthCheck();
            response.setStatus(healthResult.isHealthy() ? HttpServletResponse.SC_OK
                    : HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType(HealthCheckServlet.CT_RESPONSE);
            byte[] respBytes = healthResult.toJsonMessage(true).getBytes();
            response.setContentLength(respBytes.length);
            response.getOutputStream().write(respBytes);
        } catch (EOFException ex) {
            LogUtil.warn(LOG, ex, "connection reset by peer");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentLength(0);
        } catch (Throwable th) {
            final String message = "Throwable thrown, this should not happen!";
            LOG.warn(message, th);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentLength(0);
        }

        response.flushBuffer();
    } // method doGet

    public void setResponderManager(final CmpResponderManager responderManager) {
        this.responderManager = ParamUtil.requireNonNull("responderManager", responderManager);
    }

}
