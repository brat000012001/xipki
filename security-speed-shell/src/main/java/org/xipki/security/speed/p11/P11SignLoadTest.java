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

package org.xipki.security.speed.p11;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.common.LoadExecutor;
import org.xipki.common.ObjectCreationException;
import org.xipki.common.util.LogUtil;
import org.xipki.common.util.ParamUtil;
import org.xipki.security.ConcurrentContentSigner;
import org.xipki.security.SecurityFactory;
import org.xipki.security.SignerConf;
import org.xipki.security.pkcs11.P11NewKeyControl;
import org.xipki.security.pkcs11.P11ObjectIdentifier;
import org.xipki.security.pkcs11.P11Slot;
import org.xipki.security.pkcs11.P11SlotIdentifier;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public abstract class P11SignLoadTest extends LoadExecutor {

    class Testor implements Runnable {

        final byte[] data = new byte[1024];

        public Testor() {
            new SecureRandom().nextBytes(data);
        }

        @Override
        public void run() {
            while (!stop() && getErrorAccout() < 1) {
                try {
                    signer.sign(data);
                    account(1, 0);
                } catch (Exception ex) {
                    account(1, 1);
                }
            }
        }

    } // class Testor

    private static final Logger LOG = LoggerFactory.getLogger(P11SignLoadTest.class);

    private final P11Slot slot;

    private final ConcurrentContentSigner signer;

    private final P11ObjectIdentifier objectId;

    public P11SignLoadTest(final SecurityFactory securityFactory, final P11Slot slot,
            final String signatureAlgorithm, final P11ObjectIdentifier objectId,
            final String description) throws ObjectCreationException {
        super(description + "\nsignature algorithm: " + signatureAlgorithm);

        ParamUtil.requireNonNull("securityFactory", securityFactory);
        ParamUtil.requireNonNull("slot", slot);
        ParamUtil.requireNonBlank("signatureAlgorithm", signatureAlgorithm);
        ParamUtil.requireNonNull("objectId", objectId);

        this.slot = slot;
        this.objectId = objectId;

        P11SlotIdentifier slotId = slot.slotId();
        SignerConf signerConf = SignerConf.getPkcs11SignerConf(slot.moduleName(),
                null, slotId.id(), null, objectId.id(), signatureAlgorithm, 20);
        try {
            this.signer = securityFactory.createSigner("PKCS11", signerConf,
                    (X509Certificate) null);
        } catch (ObjectCreationException ex) {
            shutdown();
            throw ex;
        }
    }

    @Override
    protected void shutdown() {
        try {
            slot.removeIdentity(objectId);
        } catch (Exception ex) {
            LogUtil.error(LOG, ex, "could not delete PKCS#11 key " + objectId);
        }
    }

    protected static P11NewKeyControl getNewKeyControl() {
        P11NewKeyControl control = new P11NewKeyControl();
        control.setExtractable(true);
        return control;
    }

    @Override
    protected Runnable getTestor() throws Exception {
        return new Testor();
    }

}
