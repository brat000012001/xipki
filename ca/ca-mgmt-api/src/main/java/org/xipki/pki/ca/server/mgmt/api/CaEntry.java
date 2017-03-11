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

package org.xipki.pki.ca.server.mgmt.api;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.xipki.commons.common.ConfPairs;
import org.xipki.commons.common.util.CollectionUtil;
import org.xipki.commons.common.util.CompareUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.common.util.StringUtil;
import org.xipki.commons.security.SignerConf;
import org.xipki.commons.security.exception.XiSecurityException;
import org.xipki.commons.security.util.AlgorithmUtil;
import org.xipki.pki.ca.api.profile.CertValidity;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CaEntry {

    private Integer id;

    private String name;

    private CaStatus status;

    private CertValidity maxValidity;

    private String signerType;

    private String signerConf;

    private String cmpControlName;

    private String responderName;

    private boolean duplicateKeyPermitted;

    private boolean duplicateSubjectPermitted;

    private boolean saveRequest;

    private ValidityMode validityMode = ValidityMode.STRICT;

    private Set<Permission> permissions;

    private int expirationPeriod;

    private int keepExpiredCertInDays;

    private String extraControl;

    public CaEntry(final String name, final String signerType, final String signerConf,
            final int expirationPeriod) throws CaMgmtException {
        this.name = ParamUtil.requireNonBlank("name", name).toUpperCase();
        this.signerType = ParamUtil.requireNonBlank("signerType", signerType);
        this.expirationPeriod = ParamUtil.requireMin("expirationPeriod", expirationPeriod, 0);
        this.signerConf = signerConf;
    }

    public static List<String[]> splitCaSignerConfs(final String conf) throws XiSecurityException {
        ConfPairs pairs = new ConfPairs(conf);
        String str = pairs.getValue("algo");
        List<String> list = StringUtil.split(str, ":");
        if (list == null) {
            throw new XiSecurityException("no algo is defined in CA signerConf");
        }

        List<String[]> signerConfs = new ArrayList<>(list.size());
        for (String n : list) {
            String c14nAlgo;
            try {
                c14nAlgo = AlgorithmUtil.canonicalizeSignatureAlgo(n);
            } catch (NoSuchAlgorithmException ex) {
                throw new XiSecurityException(ex.getMessage(), ex);
            }
            pairs.putPair("algo", c14nAlgo);
            signerConfs.add(new String[]{c14nAlgo, pairs.getEncoded()});
        }

        return signerConfs;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public CertValidity getMaxValidity() {
        return maxValidity;
    }

    public void setMaxValidity(final CertValidity maxValidity) {
        this.maxValidity = maxValidity;
    }

    public int getKeepExpiredCertInDays() {
        return keepExpiredCertInDays;
    }

    public void setKeepExpiredCertInDays(final int days) {
        this.keepExpiredCertInDays = days;
    }

    public String getSignerConf() {
        return signerConf;
    }

    public CaStatus getStatus() {
        return status;
    }

    public void setStatus(final CaStatus status) {
        this.status = status;
    }

    public String getSignerType() {
        return signerType;
    }

    public void setCmpControlName(final String cmpControlName) {
        this.cmpControlName = cmpControlName;
    }

    public String getCmpControlName() {
        return cmpControlName;
    }

    public String getResponderName() {
        return responderName;
    }

    public void setResponderName(final String responderName) {
        this.responderName = responderName;
    }

    public boolean isDuplicateKeyPermitted() {
        return duplicateKeyPermitted;
    }

    public void setDuplicateKeyPermitted(final boolean duplicateKeyPermitted) {
        this.duplicateKeyPermitted = duplicateKeyPermitted;
    }

    public boolean isDuplicateSubjectPermitted() {
        return duplicateSubjectPermitted;
    }

    public void setDuplicateSubjectPermitted(final boolean duplicateSubjectPermitted) {
        this.duplicateSubjectPermitted = duplicateSubjectPermitted;
    }

    public boolean isSaveRequest() {
        return saveRequest;
    }

    public void setSaveRequest(boolean saveRequest) {
        this.saveRequest = saveRequest;
    }

    public ValidityMode getValidityMode() {
        return validityMode;
    }

    public void setValidityMode(final ValidityMode mode) {
        this.validityMode = ParamUtil.requireNonNull("mode", mode);
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public String getPermissionsAsText() {
        return toString(permissions);
    }

    public void setPermissions(final Set<Permission> permissions) {
        this.permissions = CollectionUtil.unmodifiableSet(permissions);
    }

    public int getExpirationPeriod() {
        return expirationPeriod;
    }

    public String getExtraControl() {
        return extraControl;
    }

    public void setExtraControl(final String extraControl) {
        this.extraControl = extraControl;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(final boolean verbose) {
        return toString(verbose, true);
    }

    public String toString(final boolean verbose, final boolean ignoreSensitiveInfo) {
        StringBuilder sb = new StringBuilder(500);
        sb.append("id: ").append(id).append('\n');
        sb.append("name: ").append(name).append('\n');
        sb.append("status: ").append((status == null) ? "null" : status.getStatus()).append('\n');
        sb.append("maxValidity: ").append(maxValidity).append("\n");
        sb.append("expirationPeriod: ").append(expirationPeriod).append(" days\n");
        sb.append("signerType: ").append(signerType).append('\n');
        sb.append("signerConf: ");
        if (signerConf == null) {
            sb.append("null");
        } else {
            sb.append(SignerConf.toString(signerConf, verbose, ignoreSensitiveInfo));
        }
        sb.append('\n');
        sb.append("cmpcontrolName: ").append(cmpControlName).append('\n');
        sb.append("responderName: ").append(responderName).append('\n');
        sb.append("duplicateKey: ").append(duplicateKeyPermitted).append('\n');
        sb.append("duplicateSubject: ").append(duplicateSubjectPermitted).append('\n');
        sb.append("saveRequest: ").append(saveRequest).append('\n');
        sb.append("validityMode: ").append(validityMode).append('\n');
        sb.append("permissions: ").append(Permission.toString(permissions)).append('\n');
        sb.append("keepExpiredCerts: ");
        if (keepExpiredCertInDays < 0) {
            sb.append("forever");
        } else {
            sb.append(keepExpiredCertInDays).append(" days");
        }
        sb.append("\n");
        sb.append("extraControl: ").append(extraControl).append('\n');

        return sb.toString();
    } // method toString

    protected static String toString(final Collection<? extends Object> tokens) {
        if (CollectionUtil.isEmpty(tokens)) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        int size = tokens.size();
        int idx = 0;
        for (Object token : tokens) {
            sb.append(token);
            if (idx++ < size - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CaEntry)) {
            return false;
        }

        CaEntry objB = (CaEntry) obj;
        if (!name.equals(objB.name)) {
            return false;
        }

        if (!signerType.equals(objB.signerType)) {
            return false;
        }

        if (!CompareUtil.equalsObject(status, objB.status)) {
            return false;
        }

        if (!CompareUtil.equalsObject(maxValidity, objB.maxValidity)) {
            return false;
        }

        if (!CompareUtil.equalsObject(cmpControlName, objB.cmpControlName)) {
            return false;
        }

        if (!CompareUtil.equalsObject(responderName, objB.responderName)) {
            return false;
        }

        if (duplicateKeyPermitted != objB.duplicateKeyPermitted) {
            return false;
        }

        if (duplicateSubjectPermitted != objB.duplicateSubjectPermitted) {
            return false;
        }

        if (saveRequest != objB.saveRequest) {
            return false;
        }

        if (!CompareUtil.equalsObject(validityMode, objB.validityMode)) {
            return false;
        }

        if (!CompareUtil.equalsObject(permissions, objB.permissions)) {
            return false;
        }

        if (expirationPeriod != objB.expirationPeriod) {
            return false;
        }

        if (keepExpiredCertInDays != objB.keepExpiredCertInDays) {
            return false;
        }

        if (!CompareUtil.equalsObject(extraControl, objB.extraControl)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

}
