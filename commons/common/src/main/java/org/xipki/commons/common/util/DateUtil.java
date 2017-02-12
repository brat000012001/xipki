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

package org.xipki.commons.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class DateUtil {

    private static final ZoneId ZONE_UTC = ZoneId.of("UTC");

    private static final DateTimeFormatter SDF1 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final DateTimeFormatter SDF2 = DateTimeFormatter.ofPattern("yyyyMMdd");

    private DateUtil() {
    }

    public static Date parseUtcTimeyyyyMMddhhmmss(final String utcTime) {
        String coreUtcTime = utcTime;
        if (StringUtil.isNotBlank(utcTime)) {
            char ch = utcTime.charAt(utcTime.length() - 1);
            if (ch == 'z' || ch == 'Z') {
                coreUtcTime = utcTime.substring(0, utcTime.length() - 1);
            }
        }

        if (coreUtcTime == null || coreUtcTime.length() != 14) {
            throw new IllegalArgumentException("invalid utcTime '" + utcTime + "'");
        }
        try {
            LocalDateTime localDate = LocalDateTime.parse(coreUtcTime, SDF1);
            Instant instant = localDate.atZone(ZONE_UTC).toInstant();
            return Date.from(instant);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("invalid utcTime '" + utcTime + "': "
                    + ex.getMessage());
        }
    }

    public static Date parseUtcTimeyyyyMMdd(final String utcTime) {
        String coreUtcTime = utcTime;
        if (StringUtil.isNotBlank(utcTime)) {
            char ch = utcTime.charAt(utcTime.length() - 1);
            if (ch == 'z' || ch == 'Z') {
                coreUtcTime = utcTime.substring(0, utcTime.length() - 1);
            }
        }

        if (coreUtcTime == null || coreUtcTime.length() != 8) {
            throw new IllegalArgumentException("invalid utcTime '" + utcTime + "'");
        }
        try {
            LocalDateTime localDate = LocalDateTime.parse(coreUtcTime + "000000", SDF1);
            Instant instant = localDate.atZone(ZONE_UTC).toInstant();
            return Date.from(instant);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("invalid utcTime '" + utcTime + "': "
                    + ex.getMessage());
        }
    }

    public static String toUtcTimeyyyyMMddhhmmss(final Date utcTime) {
        ZonedDateTime zd = utcTime.toInstant().atZone(ZONE_UTC);
        return SDF1.format(zd);
    }

    public static String toUtcTimeyyyyMMdd(final Date utcTime) {
        ZonedDateTime zd = utcTime.toInstant().atZone(ZONE_UTC);
        return SDF2.format(zd);
    }

}
