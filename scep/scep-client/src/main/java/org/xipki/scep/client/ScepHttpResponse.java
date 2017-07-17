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

package org.xipki.scep.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.common.util.ParamUtil;
import org.xipki.scep.client.exception.ScepClientException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class ScepHttpResponse {

    private static final Logger LOG = LoggerFactory.getLogger(ScepHttpResponse.class);

    private final String contentType;

    private final int contentLength;

    private final InputStream content;

    private String contentEncoding;

    public ScepHttpResponse(final String contentType, final int contentLength,
            final InputStream content) {
        this.contentType = ParamUtil.requireNonNull("contentType", contentType);
        this.content = ParamUtil.requireNonNull("content", content);
        this.contentLength = contentLength;
    }

    public ScepHttpResponse(final String contentType, final int contentLength,
            final byte[] contentBytes) {
        this(contentType, contentLength,
                new ByteArrayInputStream(ParamUtil.requireNonNull("contentBytes", contentBytes)));
    }

    public String contentType() {
        return contentType;
    }

    public int contentLength() {
        return contentLength;
    }

    public String encoding() {
        return contentEncoding;
    }

    public void setContentEncoding(final String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    public InputStream content() {
        return content;
    }

    public byte[] getContentBytes() throws ScepClientException {
        if (content == null) {
            return null;
        }

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            int readed = 0;
            byte[] buffer = new byte[2048];
            while ((readed = content.read(buffer)) != -1) {
                bout.write(buffer, 0, readed);
            }

            return bout.toByteArray();
        } catch (IOException ex) {
            throw new ScepClientException(ex);
        } finally {
            if (content != null) {
                try {
                    content.close();
                } catch (IOException ex) {
                    LOG.error("could not close stream: {}", ex.getMessage());
                }
            }
        }
    }

}
