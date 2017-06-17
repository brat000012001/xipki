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

package org.xipki.audit;

import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public abstract class AuditService {

    public abstract void logEvent0(@NonNull AuditEvent event);

    public abstract void logEvent0(@NonNull PciAuditEvent event);

    public final void logEvent(@NonNull AuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        /*
        switch (event.getLevel()) {
        case DEBUG:
            if (LOG.isDebugEnabled()) {
                LOG.debug("AuditEvent {}", createMessage(event));
            }
            break;
        default:
            if (LOG.isInfoEnabled()) {
                LOG.info("AuditEvent {}", createMessage(event));
            }
            break;
        } // end switch
        */

        logEvent0(event);
    }

    public final void logEvent(@NonNull PciAuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        /*
        CharArrayWriter msg = event.toCharArrayWriter("");
        AuditLevel al = event.getLevel();
        switch (al) {
        case DEBUG:
            if (LOG.isDebugEnabled()) {
                LOG.debug("PciAuditEvent {} | {}", al.getAlignedText(), msg);
            }
            break;
        default:
            if (LOG.isInfoEnabled()) {
                LOG.info("PciAuditEvent {} | {}", al.getAlignedText(), msg);
            }
            break;
        } // end switch
        */

        logEvent0(event);
    }

    protected static String createMessage(final AuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        String applicationName = event.applicationName();
        if (applicationName == null) {
            applicationName = "undefined";
        }

        String name = event.name();
        if (name == null) {
            name = "undefined";
        }

        StringBuilder sb = new StringBuilder(150);

        sb.append(event.level().alignedText()).append(" | ");
        sb.append(applicationName).append(" - ").append(name);

        AuditStatus status = event.status();
        if (status == null) {
            status = AuditStatus.UNDEFINED;
        }
        sb.append(":\tstatus: ").append(status.name());
        List<AuditEventData> eventDataArray = event.eventDatas();

        long duration = event.duration();
        if (duration >= 0) {
            sb.append("\tduration: ").append(duration);
        }

        if ((eventDataArray != null) && (eventDataArray.size() > 0)) {
            for (AuditEventData m : eventDataArray) {
                if (duration >= 0 && "duration".equalsIgnoreCase(m.name())) {
                    continue;
                }

                sb.append("\t").append(m.name()).append(": ").append(m.value());
            }
        }

        return sb.toString();
    }

}
