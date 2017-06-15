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

package org.xipki.security.speed.p11.cmd;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.xipki.common.LoadExecutor;
import org.xipki.console.karaf.IllegalCmdParamException;
import org.xipki.security.pkcs11.P11Slot;
import org.xipki.security.speed.cmd.DSAControl;
import org.xipki.security.speed.cmd.completer.DSASigAlgCompleter;
import org.xipki.security.speed.p11.P11DSASignLoadTest;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

@Command(scope = "xipki-tk", name = "bspeed-dsa-sign",
        description = "performance test of PKCS#11 DSA signature creation (batch)")
@Service
// CHECKSTYLE:SKIP
public class BSpeedP11DSASignCmd extends BSpeedP11CommandSupport {

    @Option(name = "--sig-algo",
            required = true,
            description = "signature algorithm\n"
                    + "(required)")
    @Completion(DSASigAlgCompleter.class)
    private String sigAlgo;

    private final BlockingDeque<DSAControl> queue = new LinkedBlockingDeque<>();

    public BSpeedP11DSASignCmd() {
        queue.add(new DSAControl(1024, 160));
        queue.add(new DSAControl(2048, 224));
        queue.add(new DSAControl(2048, 256));
        queue.add(new DSAControl(3072, 256));
    }

    @Override
    protected LoadExecutor nextTester() throws Exception {
        DSAControl control = queue.takeFirst();
        if (control == null) {
            return null;
        }

        P11Slot slot = getSlot();

        if (control.getPlen() == 1024) {
            if (!"SHA1withDSA".equalsIgnoreCase(sigAlgo)) {
                throw new IllegalCmdParamException(
                        "only SHA1withDSA is permitted for DSA with 1024 bit");
            }
        }

        return new P11DSASignLoadTest(securityFactory, slot, sigAlgo, control.getPlen(),
                control.getQlen());
    }

}
