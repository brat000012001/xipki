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

package org.xipki.pki.ca.dbtool.xmlio;

import java.io.InputStream;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CaCertsReader extends DbiXmlReader {

    public CaCertsReader(final InputStream xmlStream)
    throws XMLStreamException, InvalidDataObjectException {
        super("certs", xmlStream);
    }

    @Override
    protected DbDataObject retrieveNext() throws InvalidDataObjectException, XMLStreamException {
        CaCertType ret = null;

        StringBuilder buffer = new StringBuilder();
        int lastEvent = -1;

        while (reader.hasNext()) {
            int event = reader.next();
            String tagContent = null;

            if (event != XMLStreamConstants.CHARACTERS) {
                tagContent = buffer.toString();

                if (lastEvent == XMLStreamConstants.CHARACTERS) {
                    buffer.delete(0, buffer.length());
                }
            }

            lastEvent = event;

            switch (event) {
            case XMLStreamConstants.START_ELEMENT:
                if (CaCertType.TAG_ROOT.equals(reader.getLocalName())) {
                    ret = new CaCertType();
                }
                break;
            case XMLStreamConstants.CHARACTERS:
                buffer.append(reader.getText());
                break;
            case XMLStreamConstants.END_ELEMENT:
                if (ret == null) {
                    break;
                }

                switch (reader.getLocalName()) {
                case CaCertType.TAG_ROOT:
                    ret.validate();
                    return ret;
                case CaCertType.TAG_ART:
                    ret.setArt(Integer.parseInt(tagContent));
                    break;
                case CaCertType.TAG_CAID:
                    ret.setCaId(Integer.parseInt(tagContent));
                    break;
                case CaCertType.TAG_FILE:
                    ret.setFile(tagContent);
                    break;
                case CaCertType.TAG_FP_RS:
                    ret.setFpRs(Long.parseLong(tagContent));
                    break;
                case CaCertType.TAG_ID:
                    ret.setId(Long.parseLong(tagContent));
                    break;
                case CaCertType.TAG_PID:
                    ret.setPid(Integer.parseInt(tagContent));
                    break;
                case CaCertType.TAG_REQ_TYPE:
                    ret.setReqType(Integer.parseInt(tagContent));
                    break;
                case CaCertType.TAG_REV:
                    ret.setRev(Boolean.parseBoolean(tagContent));
                    break;
                case CaCertType.TAG_RID:
                    ret.setRid(Integer.parseInt(tagContent));
                    break;
                case CaCertType.TAG_RIT:
                    ret.setRit(Long.parseLong(tagContent));
                    break;
                case CaCertType.TAG_RR:
                    ret.setRr(Integer.parseInt(tagContent));
                    break;
                case CaCertType.TAG_RS:
                    ret.setRs(tagContent);
                    break;
                case CaCertType.TAG_RT:
                    ret.setRt(Long.parseLong(tagContent));
                    break;
                case CaCertType.TAG_SN:
                    ret.setSn(tagContent);
                    break;
                case CaCertType.TAG_TID:
                    ret.setTid(tagContent);
                    break;
                case CaCertType.TAG_UPDATE:
                    ret.setUpdate(Long.parseLong(tagContent));
                    break;
                case CaCertType.TAG_USER:
                    ret.setUser(tagContent);
                    break;
                default:
                    break;
                } // end switch (reader.getLocalName())
                break;
            default:
                break;
            } // end switch (event)
        } // end while

        return null;
    } // method retrieveNext

}
