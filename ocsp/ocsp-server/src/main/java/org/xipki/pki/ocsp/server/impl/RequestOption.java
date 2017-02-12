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

package org.xipki.pki.ocsp.server.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.xipki.commons.common.InvalidConfException;
import org.xipki.commons.common.util.IoUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.CertpathValidationModel;
import org.xipki.commons.security.HashAlgoType;
import org.xipki.commons.security.util.KeyUtil;
import org.xipki.commons.security.util.X509Util;
import org.xipki.pki.ocsp.server.impl.jaxb.CertCollectionType;
import org.xipki.pki.ocsp.server.impl.jaxb.CertCollectionType.Keystore;
import org.xipki.pki.ocsp.server.impl.jaxb.NonceType;
import org.xipki.pki.ocsp.server.impl.jaxb.RequestOptionType;
import org.xipki.pki.ocsp.server.impl.jaxb.RequestOptionType.CertpathValidation;
import org.xipki.pki.ocsp.server.impl.jaxb.RequestOptionType.HashAlgorithms;
import org.xipki.pki.ocsp.server.impl.jaxb.VersionsType;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

class RequestOption {

    static final Set<HashAlgoType> SUPPORTED_HASH_ALGORITHMS = new HashSet<>();

    static {
        SUPPORTED_HASH_ALGORITHMS.add(HashAlgoType.SHA1);
        SUPPORTED_HASH_ALGORITHMS.add(HashAlgoType.SHA224);
        SUPPORTED_HASH_ALGORITHMS.add(HashAlgoType.SHA256);
        SUPPORTED_HASH_ALGORITHMS.add(HashAlgoType.SHA384);
        SUPPORTED_HASH_ALGORITHMS.add(HashAlgoType.SHA512);
    }

    private final boolean supportsHttpGet;

    private final boolean signatureRequired;

    private final boolean validateSignature;

    private final int maxRequestSize;

    private final Collection<Integer> versions;

    private final boolean nonceRequired;

    private final int nonceMinLen;

    private final int nonceMaxLen;

    private final Set<HashAlgoType> hashAlgos;

    private final Set<CertWithEncoded> trustAnchors;

    private final Set<X509Certificate> certs;

    private final CertpathValidationModel certpathValidationModel;

    RequestOption(final RequestOptionType conf) throws InvalidConfException {
        ParamUtil.requireNonNull("conf", conf);

        supportsHttpGet = conf.isSupportsHttpGet();
        signatureRequired = conf.isSignatureRequired();
        validateSignature = conf.isValidateSignature();

        NonceType nonceConf = conf.getNonce();
        int minLen = 4;
        int maxLen = 32;
        // Request nonce
        if (nonceConf != null) {
            nonceRequired = nonceConf.isRequired();
            if (nonceConf.getMinLen() != null) {
                minLen = nonceConf.getMinLen();
            }

            if (nonceConf.getMaxLen() != null) {
                maxLen = nonceConf.getMaxLen();
            }
        } else {
            nonceRequired = false;
        }

        int tmpMaxSize = 0;
        if (conf.getMaxRequestSize() != null) {
            tmpMaxSize = conf.getMaxRequestSize().intValue();
        }

        if (tmpMaxSize < 255) {
            tmpMaxSize = 4096;
        }
        this.maxRequestSize = tmpMaxSize;

        this.nonceMinLen = minLen;
        this.nonceMaxLen = maxLen;

        // Request versions

        VersionsType versionsConf = conf.getVersions();
        if (versionsConf == null) {
            this.versions = null;
        } else {
            this.versions = new HashSet<>();
            this.versions.addAll(versionsConf.getVersion());
        }

        // Request hash algorithms
        hashAlgos = new HashSet<>();

        HashAlgorithms reqHashAlgosConf = conf.getHashAlgorithms();
        if (reqHashAlgosConf != null) {
            for (String token : reqHashAlgosConf.getAlgorithm()) {
                HashAlgoType algo = HashAlgoType.getHashAlgoType(token);
                if (algo != null && SUPPORTED_HASH_ALGORITHMS.contains(algo)) {
                    hashAlgos.add(algo);
                } else {
                    throw new InvalidConfException("hash algorithm " + token + " is unsupported");
                }
            }
        } else {
            hashAlgos.addAll(SUPPORTED_HASH_ALGORITHMS);
        }

        // certpath validation
        CertpathValidation certpathConf = conf.getCertpathValidation();
        if (certpathConf == null) {
            if (validateSignature) {
                throw new InvalidConfException("certpathValidation is not specified");
            }
            trustAnchors = null;
            certs = null;
            certpathValidationModel = CertpathValidationModel.PKIX;
            return;
        }

        switch (certpathConf.getValidationModel()) {
        case CHAIN:
            certpathValidationModel = CertpathValidationModel.CHAIN;
            break;
        case PKIX:
            certpathValidationModel = CertpathValidationModel.PKIX;
            break;
        default:
            throw new RuntimeException("should not reach here, unknown ValidationModel "
                    + certpathConf.getValidationModel());
        } // end switch

        try {
            Set<X509Certificate> tmpCerts = doGetCerts(certpathConf.getTrustAnchors());
            trustAnchors = new HashSet<>(tmpCerts.size());
            for (X509Certificate m : tmpCerts) {
                trustAnchors.add(new CertWithEncoded(m));
            }
        } catch (Exception ex) {
            throw new InvalidConfException(
                    "could not initialize the trustAnchors: " + ex.getMessage(), ex);
        }

        CertCollectionType certsType = certpathConf.getCerts();
        try {
            this.certs = (certsType == null) ? null : doGetCerts(certsType);
        } catch (Exception ex) {
            throw new InvalidConfException(
                    "could not initialize the certs: " + ex.getMessage(), ex);
        }
    } // constructor

    public Set<HashAlgoType> getHashAlgos() {
        return hashAlgos;
    }

    public boolean isSignatureRequired() {
        return signatureRequired;
    }

    public boolean isValidateSignature() {
        return validateSignature;
    }

    public boolean supportsHttpGet() {
        return supportsHttpGet;
    }

    public boolean isNonceRequired() {
        return nonceRequired;
    }

    public int getMaxRequestSize() {
        return maxRequestSize;
    }

    public int getNonceMinLen() {
        return nonceMinLen;
    }

    public int getNonceMaxLen() {
        return nonceMaxLen;
    }

    public boolean allows(final HashAlgoType hashAlgo) {
        return hashAlgos.contains(hashAlgo);
    }

    public CertpathValidationModel getCertpathValidationModel() {
        return certpathValidationModel;
    }

    public Set<CertWithEncoded> getTrustAnchors() {
        return trustAnchors;
    }

    public Set<X509Certificate> getCerts() {
        return certs;
    }

    public boolean isVersionAllowed(final Integer version) {
        return versions == null || versions.contains(version);
    }

    private static Set<X509Certificate> doGetCerts(final CertCollectionType conf)
    throws KeyStoreException, NoSuchAlgorithmException, NoSuchProviderException,
    CertificateException, IOException {
        ParamUtil.requireNonNull("conf", conf);
        Set<X509Certificate> tmpCerts = new HashSet<>();

        if (conf.getKeystore() != null) {
            Keystore ksConf = conf.getKeystore();
            KeyStore trustStore = KeyUtil.getKeyStore(ksConf.getType());

            String fileName = ksConf.getKeystore().getFile();
            InputStream is = (fileName != null)
                    ? new FileInputStream(IoUtil.expandFilepath(fileName))
                    : new ByteArrayInputStream(ksConf.getKeystore().getValue());

            char[] password = (ksConf.getPassword() == null)  ? null
                    : ksConf.getPassword().toCharArray();
            trustStore.load(is, password);

            Enumeration<String> aliases = trustStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (trustStore.isCertificateEntry(alias)) {
                    tmpCerts.add((X509Certificate) trustStore.getCertificate(alias));
                }
            }
        } else if (conf.getDir() != null) {
            File dir = new File(conf.getDir());
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.exists() && file.isFile()) {
                        tmpCerts.add(X509Util.parseCert(file));
                    }
                }
            }
        } else {
            throw new RuntimeException("should not happen, neither keystore nor dir is defined");
        }

        return tmpCerts;
    } // method getCerts

}
