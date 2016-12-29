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

package org.xipki.commons.security;

import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.xipki.commons.common.ObjectCreationException;
import org.xipki.commons.password.PasswordResolver;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public interface SecurityFactory {

    PasswordResolver getPasswordResolver();

    KeyCertPair createPrivateKeyAndCert(@NonNull String type, @Nullable SignerConf conf,
            @Nullable X509Certificate cert) throws ObjectCreationException;

    ConcurrentContentSigner createSigner(@NonNull String type, @Nullable SignerConf conf,
            @Nullable X509Certificate cert) throws ObjectCreationException;

    ConcurrentContentSigner createSigner(@NonNull String type, @Nullable SignerConf conf,
            @Nullable X509Certificate[] certs) throws ObjectCreationException;

    ContentVerifierProvider getContentVerifierProvider(@NonNull PublicKey publicKey)
    throws InvalidKeyException;

    ContentVerifierProvider getContentVerifierProvider(@NonNull X509Certificate cert)
    throws InvalidKeyException;

    ContentVerifierProvider getContentVerifierProvider(@NonNull X509CertificateHolder cert)
    throws InvalidKeyException;

    /**
     *
     * @param csr CSR to be verified
     * @param algoValidator signature algorithms validator. <code>null</null> to accept all
     *            algorithms
     * @return <code>true</code> if the signature is valid and the signature algorithm is accepted,
     *         <code>false</code> otherwise.
     */
    boolean verifyPopo(@NonNull PKCS10CertificationRequest csr, AlgorithmValidator algoValidator);

    /**
     *
     * @param csr CSR to be verified
     * @param algoValidator signature algorithms validator. <code>null</null> to accept all
     *            algorithms
     * @return <code>true</code> if the signature is valid and the signature algorithm is accepted,
     *         <code>false</code> otherwise.
     */
    boolean verifyPopo(@NonNull CertificationRequest csr, AlgorithmValidator algoValidator);

    PublicKey generatePublicKey(@NonNull SubjectPublicKeyInfo subjectPublicKeyInfo)
    throws InvalidKeyException;

    byte[] extractMinimalKeyStore(@NonNull String keystoreType, @NonNull byte[] keystoreBytes,
            @Nullable String keyname, @NonNull char[] password,
            @Nullable X509Certificate[] newCertChain) throws KeyStoreException;

    SecureRandom getRandom4Sign();

    SecureRandom getRandom4Key();

    int getDefaultSignerParallelism();

}
