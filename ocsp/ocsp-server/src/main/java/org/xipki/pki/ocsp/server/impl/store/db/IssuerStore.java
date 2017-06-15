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

package org.xipki.pki.ocsp.server.impl.store.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.xipki.pki.ocsp.api.IssuerHashNameAndKey;
import org.xipki.security.HashAlgoType;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class IssuerStore {

    private final List<IssuerEntry> entries;

    private Set<Integer> ids;

    public IssuerStore(final List<IssuerEntry> entries) {
        this.entries = new ArrayList<>(entries.size());
        Set<Integer> idSet = new HashSet<>(entries.size());

        for (IssuerEntry entry : entries) {
            for (IssuerEntry existingEntry : this.entries) {
                if (existingEntry.getId() == entry.getId()) {
                    throw new IllegalArgumentException(
                            "issuer with the same id " + entry.getId() + " already available");
                }
            }
            this.entries.add(entry);
            idSet.add(entry.getId());
        }

        this.ids = Collections.unmodifiableSet(idSet);
    }

    public int getSize() {
        return ids.size();
    }

    public Set<Integer> getIds() {
        return ids;
    }

    public Integer getIssuerIdForFp(final HashAlgoType hashAlgo, final byte[] issuerNameHash,
            final byte[] issuerKeyHash) {
        IssuerEntry issuerEntry = getIssuerForFp(hashAlgo, issuerNameHash, issuerKeyHash);
        return (issuerEntry == null) ? null : issuerEntry.getId();
    }

    public IssuerEntry getIssuerForId(final int id) {
        for (IssuerEntry entry : entries) {
            if (entry.getId() == id) {
                return entry;
            }
        }

        return null;
    }

    public IssuerEntry getIssuerForFp(final HashAlgoType hashAlgo, final byte[] issuerNameHash,
            final byte[] issuerKeyHash) {
        for (IssuerEntry entry : entries) {
            if (entry.matchHash(hashAlgo, issuerNameHash, issuerKeyHash)) {
                return entry;
            }
        }

        return null;
    }

    public Set<IssuerHashNameAndKey> getIssuerHashNameAndKeys() {
        Set<IssuerHashNameAndKey> ret = new HashSet<>();
        for (IssuerEntry issuerEntry : entries) {
            ret.addAll(issuerEntry.getIssuerHashNameAndKeys());
        }
        return ret;
    }

    public void addIssuer(IssuerEntry issuer) {
        this.entries.add(issuer);
        Set<Integer> newIds = new HashSet<>(this.ids);
        newIds.add(issuer.getId());
        this.ids = Collections.unmodifiableSet(newIds);
    }

}
