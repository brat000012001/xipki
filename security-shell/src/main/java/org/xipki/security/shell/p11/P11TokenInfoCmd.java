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

package org.xipki.security.shell.p11;

import java.util.List;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.xipki.security.pkcs11.P11Module;
import org.xipki.security.pkcs11.P11Slot;
import org.xipki.security.pkcs11.P11SlotIdentifier;
import org.xipki.security.shell.SecurityCommandSupport;
import org.xipki.security.shell.completer.P11ModuleNameCompleter;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

@Command(scope = "xipki-tk", name = "token-info",
        description = "list objects in PKCS#11 device")
@Service
public class P11TokenInfoCmd extends SecurityCommandSupport {

    @Option(name = "--verbose", aliases = "-v",
            description = "show object information verbosely")
    private Boolean verbose = Boolean.FALSE;

    @Option(name = "--module",
            description = "name of the PKCS#11 module.")
    @Completion(P11ModuleNameCompleter.class)
    private String moduleName = DEFAULT_P11MODULE_NAME;

    @Option(name = "--slot",
            description = "slot index")
    private Integer slotIndex;

    @Override
    protected Object doExecute() throws Exception {
        P11Module module = getP11Module(moduleName);
        println("module: " + moduleName);
        List<P11SlotIdentifier> slots = module.getSlotIdentifiers();
        if (slotIndex == null) {
            output(slots);
            return null;
        }

        P11Slot slot = getSlot(moduleName, slotIndex);
        slot.showDetails(System.out, verbose);
        System.out.println();
        System.out.flush();
        return null;
    }

    private void output(final List<P11SlotIdentifier> slots) {
        // list all slots
        final int n = slots.size();

        if (n == 0 || n == 1) {
            String numText = (n == 0) ? "no" : "1";
            println(numText + " slot is configured");
        } else {
            println(n + " slots are configured");
        }

        for (P11SlotIdentifier slotId : slots) {
            println("\tslot[" + slotId.getIndex() + "]: " + slotId.getId());
        }
    }

}
