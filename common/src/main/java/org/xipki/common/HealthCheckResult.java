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

package org.xipki.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.xipki.common.util.CollectionUtil;
import org.xipki.common.util.ParamUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class HealthCheckResult {

    private String name;

    private boolean healthy;

    private Map<String, Object> statuses = new ConcurrentHashMap<>();

    private List<HealthCheckResult> childChecks = new LinkedList<>();

    /**
     *
     * @param name Name of the check result.
     */
    public HealthCheckResult(final String name) {
        this.name = ParamUtil.requireNonBlank("name", name);
    }

    public void setHealthy(final boolean healthy) {
        this.healthy = healthy;
    }

    public void clearStatuses() {
        this.statuses.clear();
    }

    public Object status(final String statusName) {
        return (statusName == null) ? null : statuses.get(statusName);
    }

    public void clearChildChecks() {
        this.childChecks.clear();
    }

    public void addChildCheck(final HealthCheckResult childCheck) {
        ParamUtil.requireNonNull("childCheck", childCheck);
        this.childChecks.add(childCheck);
    }

    public Set<String> statusNames() {
        return statuses.keySet();
    }

    public boolean isHealthy() {
        return healthy;
    }

    public Map<String, Object> statuses() {
        return Collections.unmodifiableMap(statuses);
    }

    public String toJsonMessage(final boolean pretty) {
        return toJsonMessage(0, pretty);
    }

    private String toJsonMessage(final int level, final boolean pretty) {
        // Non root check requires always a name
        StringBuilder sb = new StringBuilder(1000);
        if (pretty) {
            addIndent(sb, level);
        }
        if (level > 0) {
            sb.append("\"").append(name).append("\":");
        }
        sb.append("{");

        boolean lastElement = true;
        if (lastElement && CollectionUtil.isNonEmpty(statuses)) {
            lastElement = false;
        }
        if (lastElement && CollectionUtil.isNonEmpty(childChecks)) {
            lastElement = false;
        }
        append(sb, "healthy", healthy, level + 1, pretty, lastElement);

        Set<String> names = statuses.keySet();
        int size = names.size();
        int count = 0;
        for (String entry : names) {
            count++;
            append(sb, entry, statuses.get(entry), level + 1, pretty,
                    CollectionUtil.isEmpty(childChecks) && count == size);
        }

        if (CollectionUtil.isNonEmpty(childChecks)) {
            if (pretty) {
                sb.append("\n");
                addIndent(sb, level + 1);
            }

            sb.append("\"checks\":{");
            if (pretty) {
                sb.append("\n");
            }

            int childChecksSize = childChecks.size();
            for (int i = 0; i < childChecksSize; i++) {
                HealthCheckResult childCheck = childChecks.get(i);
                if (i > 0 && pretty) {
                    sb.append("\n");
                }
                sb.append(childCheck.toJsonMessage(level + 2, pretty));
                if (i < childChecksSize - 1) {
                    sb.append(",");
                }
            }

            if (pretty) {
                sb.append("\n");
                addIndent(sb, level + 1);
            }
            sb.append("}");
        }

        if (pretty) {
            sb.append("\n");
            addIndent(sb, level);
        }
        sb.append("}");
        return sb.toString();
    } // method toJsonMessage

    private static void append(final StringBuilder sb, final String name, final Object value,
            final int level, final boolean pretty, final boolean lastElement) {
        if (pretty) {
            sb.append("\n");
            addIndent(sb, level);
        }
        sb.append("\"").append(name).append("\":");

        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else {
            sb.append("\"").append(value).append("\"");
        }

        if (!lastElement) {
            sb.append(",");
        }
    } // method append

    private static void addIndent(final StringBuilder buffer, final int level) {
        if (level == 0) {
            return;
        }

        for (int i = 0; i < level; i++) {
            buffer.append("    ");
        }
    }

    public static HealthCheckResult getInstanceFromJsonMessage(final String name,
            final String jsonMessage) {
        // remove white spaces and line breaks
        String jsonMsg = jsonMessage.replaceAll(" |\t|\r|\n", "");
        if (!jsonMsg.startsWith("{\"healthy\":")) {
            throw new IllegalArgumentException("invalid healthcheck message");
        }

        int startIdx = "{\"healthy\":".length();
        int endIdx = jsonMsg.indexOf(',', startIdx);
        boolean containsChildChecks = true;
        if (endIdx == -1) {
            endIdx = jsonMsg.indexOf('}', startIdx);
            containsChildChecks = false;
        }

        if (endIdx == -1) {
            throw new IllegalArgumentException("invalid healthcheck message");
        }

        String str = jsonMsg.substring(startIdx, endIdx);

        boolean healthy;
        if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) {
            healthy = Boolean.parseBoolean(str);
        } else {
            throw new IllegalArgumentException("invalid healthcheck message");
        }

        HealthCheckResult result = new HealthCheckResult(name);
        result.setHealthy(healthy);

        if (!containsChildChecks) {
            return result;
        }

        if (!jsonMsg.startsWith("\"checks\":", endIdx + 1)) {
            return result;
        }

        String checksBlock = getBlock(jsonMsg, endIdx + 1 + "\"checks\":".length());
        String block = checksBlock.substring(1, checksBlock.length() - 1);
        Map<String, String> childBlocks = getChildBlocks(block);
        for (String childBlockName : childBlocks.keySet()) {
            HealthCheckResult childResult = getInstanceFromJsonMessage(childBlockName,
                    childBlocks.get(childBlockName));
            result.addChildCheck(childResult);
        }

        return result;
    }

    private static Map<String, String> getChildBlocks(final String block) {
        Map<String, String> childBlocks = new HashMap<>();

        int offset = 0;
        while (true) {
            int idx = block.indexOf('"', offset + 1);
            String blockName = block.substring(offset + 1, idx);
            String blockValue = getBlock(block, offset + blockName.length() + 3);
            childBlocks.put(blockName, blockValue);

            offset += blockName.length() + 4 + blockValue.length();
            if (offset >= block.length() - 1) {
                break;
            }
        }

        return childBlocks;
    } // method getInstanceFromJsonMessage

    private static String getBlock(final String text, final int offset) {
        if (!text.startsWith("{", offset)) {
            throw new IllegalArgumentException("invalid text: '" + text + "'");
        }

        StringBuilder sb = new StringBuilder("{");
        final int len = text.length();
        if (len < 2) {
            throw new IllegalArgumentException("invalid text: '" + text + "'");
        }

        char ch;
        int im = 0;
        for (int i = offset + 1; i < len; i++) {
            ch = text.charAt(i);
            sb.append(ch);

            if (ch == '{') {
                im++;
            } else if (ch == '}') {
                if (im == 0) {
                    return sb.toString();
                } else {
                    im--;
                }
            } // end if
        } // end for

        throw new IllegalArgumentException("invalid text: '" + text + "'");
    } // method getBlock

}
