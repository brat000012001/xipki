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

package org.xipki.commons.audit;

import java.io.CharArrayWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class PciAuditEvent {

    private static final String UNDEFINED = "undefined";

    private static final char DEFAULT_DELIMITER = ' ';

    private static final String DEFAULT_REPLACE_DELIMITER = "_";

    private static final ZoneId ZONE_UTC = ZoneId.of("UTC");

    private static final DateTimeFormatter DATE_FORMATTER
            = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 10.3.1 "User Identification"
     */
    private String userId = UNDEFINED;

    /**
     * 10.3.2 "Type of event"
     */
    private String eventType = UNDEFINED;

    /**
     * 10.3.3 "Date and time" (date)
     */
    private final String date;

    /**
     * 10.3.3 "Date and time" (time)
     */
    private final String time;

    /**
     * 10.3.4 "Success or failure indication"
     */
    private String status = UNDEFINED;

    /**
     * 10.3.5 "Origination of Event"
     */
    private String origination;

    /**
     * 10.3.6 "Identity or name of affected data, system component, or resource"
     */
    private String affectedResource = UNDEFINED;

    /**
     * The AuditLevel this Event belongs to.
     */
    private AuditLevel level;

    public PciAuditEvent(final Date date) {
        Objects.requireNonNull(date, "date must not be null");
        LocalDateTime localDate = LocalDateTime.ofInstant(date.toInstant(), ZONE_UTC);
        this.date = DATE_FORMATTER.format(localDate);
        this.time = TIME_FORMATTER.format(localDate);
        this.level = AuditLevel.INFO;
    }

    public AuditLevel getLevel() {
        return level;
    }

    public void setLevel(final AuditLevel level) {
        this.level = Objects.requireNonNull(level, "level must not be null");
    }

    public String getUserId() {
        if (isBlank(this.userId)) {
            return UNDEFINED;
        }

        return this.userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public String getEventType() {
        if (isBlank(this.eventType)) {
            return UNDEFINED;
        }

        return this.eventType;
    }

    public void setEventType(final String eventType) {
        this.eventType = eventType;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public String getStatus() {
        if (isBlank(this.status)) {
            return UNDEFINED;
        }

        return this.status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getOrigination() {
        if (isBlank(origination)) {
            origination = getHostAddress();
        }

        return origination;
    }

    public void setOrigination(final String origination) {
        this.origination = origination;
    }

    public String getAffectedResource() {
        if (isBlank(this.affectedResource)) {
            return UNDEFINED;
        }

        return this.affectedResource;
    }

    public void setAffectedResource(final String affectedResource) {
        this.affectedResource = affectedResource;
    }

    public CharArrayWriter toCharArrayWriter(final String prefix) {
        CharArrayWriter buffer = new CharArrayWriter(100);

        final char delimiter = DEFAULT_DELIMITER;
        final String replaceDelimiter = DEFAULT_REPLACE_DELIMITER;

        if (prefix != null && !prefix.isEmpty()) {
            buffer.append(prefix);
        }

        buffer.append(replaceDelimiter(getUserId(), delimiter, replaceDelimiter)).append(delimiter);
        buffer.append(replaceDelimiter(getEventType(), delimiter, replaceDelimiter))
                .append(delimiter);
        buffer.append(replaceDelimiter(getDate(), delimiter, replaceDelimiter)).append(delimiter);
        buffer.append(replaceDelimiter(getTime(), delimiter, replaceDelimiter)).append(delimiter);
        buffer.append(replaceDelimiter(getStatus(), delimiter, replaceDelimiter)).append(delimiter);
        buffer.append(replaceDelimiter(getOrigination(), delimiter, replaceDelimiter))
                .append(delimiter);
        buffer.append(replaceDelimiter(getAffectedResource(), delimiter, replaceDelimiter));

        return buffer;
    }

    private static boolean isBlank(final CharSequence cs) {
        if (cs == null) {
            return true;
        }

        int strLen = cs.length();
        if (strLen == 0) {
            return true;
        }

        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String replaceDelimiter(final String fieldValue, final char delimiter,
            final String replaceDelimiter) {
        if (replaceDelimiter == null || replaceDelimiter.length() < 1
                || fieldValue == null || fieldValue.length() < 1) {
            return fieldValue;
        }

        return fieldValue.replaceAll("\\" + delimiter, replaceDelimiter);
    }

    private static String getHostAddress() {
        List<String> addresses = new LinkedList<>();

        Enumeration<NetworkInterface> interfaces;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException ex) {
            return "UNKNOWN";
        }
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            Enumeration<InetAddress> ee = ni.getInetAddresses();
            while (ee.hasMoreElements()) {
                InetAddress ia = ee.nextElement();
                if (ia instanceof Inet4Address) {
                    addresses.add(ia.getHostAddress());
                }
            }
        }

        for (String addr : addresses) {
            if (!addr.startsWith("192.") && !addr.startsWith("127.")) {
                return addr;
            }
        }

        for (String addr : addresses) {
            if (!addr.startsWith("127.")) {
                return addr;
            }
        }

        if (addresses.size() > 0) {
            return addresses.get(0);
        } else {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException ex) {
                return "UNKNOWN";
            }
        }
    }

}
