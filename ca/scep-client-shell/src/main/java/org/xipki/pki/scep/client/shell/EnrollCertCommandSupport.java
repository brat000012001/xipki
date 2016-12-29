/*
 *
 * Copyright (c) 2013 - 2016 Lijun Liao
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

package org.xipki.pki.scep.client.shell;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.support.completers.FileCompleter;
import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.eclipse.jdt.annotation.NonNull;
import org.xipki.commons.common.util.IoUtil;
import org.xipki.commons.console.karaf.CmdFailure;
import org.xipki.pki.scep.client.EnrolmentResponse;
import org.xipki.pki.scep.client.ScepClient;
import org.xipki.pki.scep.client.exception.ScepClientException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public abstract class EnrollCertCommandSupport extends ClientCommandSupport {

    @Option(name = "--csr",
            required = true,
            description = "CSR file\n"
                    + "(required)")
    @Completion(FileCompleter.class)
    private String csrFile;

    @Option(name = "--out", aliases = "-o",
            required = true,
            description = "where to save the certificate\n"
                    + "(required)")
    @Completion(FileCompleter.class)
    private String outputFile;

    protected abstract EnrolmentResponse requestCertificate(@NonNull ScepClient client,
            @NonNull CertificationRequest csr, @NonNull PrivateKey identityKey,
            @NonNull X509Certificate identityCert) throws ScepClientException;

    @Override
    protected Object doExecute() throws Exception {
        ScepClient client = getScepClient();

        CertificationRequest csr = CertificationRequest.getInstance(IoUtil.read(csrFile));
        EnrolmentResponse resp = requestCertificate(client, csr, getIdentityKey(),
                getIdentityCert());
        if (resp.isFailure()) {
            throw new CmdFailure("server returned 'failure'");
        }

        if (resp.isPending()) {
            throw new CmdFailure("server returned 'pending'");
        }

        X509Certificate cert = resp.getCertificates().get(0);
        saveVerbose("saved enrolled certificate to file", new File(outputFile), cert.getEncoded());
        return null;
    }

}
