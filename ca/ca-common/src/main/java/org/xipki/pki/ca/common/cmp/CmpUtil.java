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

package org.xipki.pki.ca.common.cmp;

import java.util.Date;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.cmp.CMPObjectIdentifiers;
import org.bouncycastle.asn1.cmp.InfoTypeAndValue;
import org.bouncycastle.asn1.cmp.PKIFreeText;
import org.bouncycastle.asn1.cmp.PKIHeader;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.crmf.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.cmp.CMPException;
import org.bouncycastle.cert.cmp.ProtectedPKIMessage;
import org.bouncycastle.cert.cmp.ProtectedPKIMessageBuilder;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.ConcurrentContentSigner;
import org.xipki.commons.security.exception.NoIdleSignerException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class CmpUtil {

    private CmpUtil() {
    }

    public static PKIMessage addProtection(final PKIMessage pkiMessage,
            final ConcurrentContentSigner signer, final GeneralName signerName)
    throws CMPException, NoIdleSignerException {
        return addProtection(pkiMessage, signer, signerName, true);
    }

    public static PKIMessage addProtection(final PKIMessage pkiMessage,
            final ConcurrentContentSigner signer, final GeneralName signerName,
            final boolean addSignerCert) throws CMPException, NoIdleSignerException {
        ParamUtil.requireNonNull("pkiMessage", pkiMessage);
        ParamUtil.requireNonNull("signer", signer);

        final GeneralName tmpSignerName;
        if (signerName != null) {
            tmpSignerName = signerName;
        } else {
            if (signer.getCertificate() == null) {
                throw new IllegalArgumentException("signer without certificate is not allowed");
            }
            X500Name x500Name = X500Name.getInstance(
                    signer.getCertificate().getSubjectX500Principal().getEncoded());
            tmpSignerName = new GeneralName(x500Name);
        }
        PKIHeader header = pkiMessage.getHeader();
        ProtectedPKIMessageBuilder builder = new ProtectedPKIMessageBuilder(
                tmpSignerName, header.getRecipient());
        PKIFreeText freeText = header.getFreeText();
        if (freeText != null) {
            builder.setFreeText(freeText);
        }

        InfoTypeAndValue[] generalInfo = header.getGeneralInfo();
        if (generalInfo != null) {
            for (InfoTypeAndValue gi : generalInfo) {
                builder.addGeneralInfo(gi);
            }
        }

        ASN1OctetString octet = header.getRecipKID();
        if (octet != null) {
            builder.setRecipKID(octet.getOctets());
        }

        octet = header.getRecipNonce();
        if (octet != null) {
            builder.setRecipNonce(octet.getOctets());
        }

        octet = header.getSenderKID();
        if (octet != null) {
            builder.setSenderKID(octet.getOctets());
        }

        octet = header.getSenderNonce();
        if (octet != null) {
            builder.setSenderNonce(octet.getOctets());
        }

        octet = header.getTransactionID();
        if (octet != null) {
            builder.setTransactionID(octet.getOctets());
        }

        if (header.getMessageTime() != null) {
            builder.setMessageTime(new Date());
        }
        builder.setBody(pkiMessage.getBody());

        if (addSignerCert) {
            X509CertificateHolder signerCert = signer.getCertificateAsBcObject();
            builder.addCMPCertificate(signerCert);
        }

        ProtectedPKIMessage signedMessage = signer.build(builder);
        return signedMessage.toASN1Structure();
    } // method addProtection

    public static boolean isImplictConfirm(final PKIHeader header) {
        ParamUtil.requireNonNull("header", header);

        InfoTypeAndValue[] regInfos = header.getGeneralInfo();
        if (regInfos == null) {
            return false;
        }

        for (InfoTypeAndValue regInfo : regInfos) {
            if (CMPObjectIdentifiers.it_implicitConfirm.equals(regInfo.getInfoType())) {
                return true;
            }
        }
        return false;
    }

    public static InfoTypeAndValue getImplictConfirmGeneralInfo() {
        return new InfoTypeAndValue(CMPObjectIdentifiers.it_implicitConfirm, DERNull.INSTANCE);
    }

    public static CmpUtf8Pairs extract(final InfoTypeAndValue[] regInfos) {
        if (regInfos == null) {
            return null;
        }

        for (InfoTypeAndValue regInfo : regInfos) {
            if (CMPObjectIdentifiers.regInfo_utf8Pairs.equals(regInfo.getInfoType())) {
                String regInfoValue = ((ASN1String) regInfo.getInfoValue()).getString();
                return new CmpUtf8Pairs(regInfoValue);
            }
        }

        return null;
    }

    public static CmpUtf8Pairs extract(final AttributeTypeAndValue[] atvs) {
        if (atvs == null) {
            return null;
        }

        for (AttributeTypeAndValue atv : atvs) {
            if (CMPObjectIdentifiers.regInfo_utf8Pairs.equals(atv.getType())) {
                String regInfoValue = ((ASN1String) atv.getValue()).getString();
                return new CmpUtf8Pairs(regInfoValue);
            }
        }

        return null;
    }

    public static InfoTypeAndValue buildInfoTypeAndValue(final CmpUtf8Pairs utf8Pairs) {
        ParamUtil.requireNonNull("utf8Pairs", utf8Pairs);
        return new InfoTypeAndValue(CMPObjectIdentifiers.regInfo_utf8Pairs,
                new DERUTF8String(utf8Pairs.getEncoded()));
    }

    public static AttributeTypeAndValue buildAttributeTypeAndValue(final CmpUtf8Pairs utf8Pairs) {
        ParamUtil.requireNonNull("utf8Pairs", utf8Pairs);
        return new AttributeTypeAndValue(CMPObjectIdentifiers.regInfo_utf8Pairs,
                new DERUTF8String(utf8Pairs.getEncoded()));
    }

}
