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

package org.xipki.commons.security.pkcs11.emulator;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfoBuilder;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEOutputEncryptorBuilder;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.exception.P11TokenException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

class PrivateKeyCryptor {
    private static final ASN1ObjectIdentifier ALGO =
            PKCSObjectIdentifiers.pbeWithSHAAnd2_KeyTripleDES_CBC;
    private static final int ITERATION_COUNT = 2048;

    private OutputEncryptor encryptor;
    private InputDecryptorProvider decryptorProvider;

    PrivateKeyCryptor(final char[] password) throws P11TokenException {
        ParamUtil.requireNonNull("password", password);
        JcePKCSPBEOutputEncryptorBuilder eb = new JcePKCSPBEOutputEncryptorBuilder(ALGO);
        eb.setProvider("BC");
        eb.setIterationCount(ITERATION_COUNT);
        try {
            encryptor = eb.build(password);
        } catch (OperatorCreationException ex) {
            throw new P11TokenException(ex.getMessage(), ex);
        }

        JcePKCSPBEInputDecryptorProviderBuilder db = new JcePKCSPBEInputDecryptorProviderBuilder();
        decryptorProvider = db.build(password);
    }

    PrivateKey decrypt(final PKCS8EncryptedPrivateKeyInfo encryptedPrivateKeyInfo)
            throws P11TokenException {
        ParamUtil.requireNonNull("encryptedPrivateKeyInfo", encryptedPrivateKeyInfo);
        PrivateKeyInfo privateKeyInfo;
        synchronized (decryptorProvider) {
            try {
                privateKeyInfo = encryptedPrivateKeyInfo.decryptPrivateKeyInfo(decryptorProvider);
            } catch (PKCSException ex) {
                throw new P11TokenException(ex.getMessage(), ex);
            }
        }

        AlgorithmIdentifier keyAlg = privateKeyInfo.getPrivateKeyAlgorithm();
        ASN1ObjectIdentifier keyAlgOid = keyAlg.getAlgorithm();

        String algoName;
        if (PKCSObjectIdentifiers.rsaEncryption.equals(keyAlgOid)) {
            algoName = "RSA";
        } else if (X9ObjectIdentifiers.id_dsa.equals(keyAlgOid)) {
            algoName = "DSA";
        } else if (X9ObjectIdentifiers.id_ecPublicKey.equals(keyAlgOid)) {
            algoName = "EC";
        } else {
            throw new P11TokenException("unknown private key algorithm " + keyAlgOid.getId());
        }

        try {
            KeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyInfo.getEncoded());
            KeyFactory keyFactory = KeyFactory.getInstance(algoName, "BC");
            return keyFactory.generatePrivate(keySpec);
        } catch (IOException | NoSuchAlgorithmException | NoSuchProviderException
                | InvalidKeySpecException ex) {
            throw new P11TokenException(ex.getClass().getName() + ": " + ex.getMessage(), ex);
        }
    }

    PKCS8EncryptedPrivateKeyInfo encrypt(final PrivateKey privateKey) {
        ParamUtil.requireNonNull("privateKey", privateKey);
        PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(privateKey.getEncoded());
        PKCS8EncryptedPrivateKeyInfoBuilder builder = new PKCS8EncryptedPrivateKeyInfoBuilder(
                privateKeyInfo);
        synchronized (encryptor) {
            return builder.build(encryptor);
        }
    }

}
