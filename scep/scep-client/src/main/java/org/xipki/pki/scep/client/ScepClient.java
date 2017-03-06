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

package org.xipki.pki.scep.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.xipki.commons.common.util.IoUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.pki.scep.client.exception.ScepClientException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class ScepClient extends Client {

    public ScepClient(final CaIdentifier caId, final CaCertValidator caCertValidator)
            throws MalformedURLException {
        super(caId, caCertValidator);
    }

    @Override
    protected ScepHttpResponse httpGet(final String url) throws ScepClientException {
        ParamUtil.requireNonNull("url", url);
        try {
            URL tmpUrl = new URL(url);
            HttpURLConnection httpConn = IoUtil.openHttpConn(tmpUrl);;
            httpConn.setRequestMethod("GET");
            return parseResponse(httpConn);
        } catch (IOException ex) {
            throw new ScepClientException(ex);
        }
    }

    @Override
    protected ScepHttpResponse httpPost(final String url, final String requestContentType,
            final byte[] request) throws ScepClientException {
        ParamUtil.requireNonNull("url", url);
        try {
            URL tmpUrl = new URL(url);
            HttpURLConnection httpConn = IoUtil.openHttpConn(tmpUrl);
            httpConn.setDoOutput(true);
            httpConn.setUseCaches(false);

            httpConn.setRequestMethod("POST");
            if (request != null) {
                if (requestContentType != null) {
                    httpConn.setRequestProperty("Content-Type", requestContentType);
                }
                httpConn.setRequestProperty("Content-Length", Integer.toString(request.length));
                OutputStream outputstream = httpConn.getOutputStream();
                outputstream.write(request);
                outputstream.flush();
            }

            return parseResponse(httpConn);
        } catch (IOException ex) {
            throw new ScepClientException(ex.getMessage(), ex);
        }
    }

    protected ScepHttpResponse parseResponse(final HttpURLConnection conn)
            throws ScepClientException {
        ParamUtil.requireNonNull("conn", conn);
        try {
            InputStream inputstream = conn.getInputStream();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                inputstream.close();

                throw new ScepClientException("bad response: " + conn.getResponseCode() + "    "
                        + conn.getResponseMessage());
            }
            String contentType = conn.getContentType();
            int contentLength = conn.getContentLength();

            ScepHttpResponse resp = new ScepHttpResponse(contentType, contentLength, inputstream);
            String contentEncoding = conn.getContentEncoding();
            if (contentEncoding != null && !contentEncoding.isEmpty()) {
                resp.setContentEncoding(contentEncoding);
            }
            return resp;
        } catch (IOException ex) {
            throw new ScepClientException(ex);
        }
    }

}
