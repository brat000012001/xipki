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

package org.xipki.security.pkcs11.emulator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.crypto.params.DSAParameters;
import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.util.encoders.Hex;
import org.eclipse.jdt.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.common.util.IoUtil;
import org.xipki.common.util.LogUtil;
import org.xipki.common.util.ParamUtil;
import org.xipki.common.util.StringUtil;
import org.xipki.security.HashAlgoType;
import org.xipki.security.X509Cert;
import org.xipki.security.exception.P11TokenException;
import org.xipki.security.exception.P11UnknownEntityException;
import org.xipki.security.pkcs11.AbstractP11Slot;
import org.xipki.security.pkcs11.P11EntityIdentifier;
import org.xipki.security.pkcs11.P11Identity;
import org.xipki.security.pkcs11.P11MechanismFilter;
import org.xipki.security.pkcs11.P11NewKeyControl;
import org.xipki.security.pkcs11.P11ObjectIdentifier;
import org.xipki.security.pkcs11.P11SlotIdentifier;
import org.xipki.security.pkcs11.P11SlotRefreshResult;
import org.xipki.security.util.KeyUtil;
import org.xipki.security.util.X509Util;

import iaik.pkcs.pkcs11.wrapper.PKCS11Constants;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

class EmulatorP11Slot extends AbstractP11Slot {

    private static class InfoFilenameFilter implements FilenameFilter {

        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(INFO_FILE_SUFFIX);
        }

    }

    // slotinfo
    private static final String FILE_SLOTINFO = "slot.info";
    private static final String PROP_NAMED_CURVE_SUPPORTED = "namedCurveSupported";

    private static final String DIR_PRIV_KEY = "privkey";
    private static final String DIR_PUB_KEY = "pubkey";
    private static final String DIR_SEC_KEY = "seckey";
    private static final String DIR_CERT = "cert";

    private static final String INFO_FILE_SUFFIX = ".info";
    private static final String VALUE_FILE_SUFFIX = ".value";

    private static final String PROP_ID = "id";
    private static final String PROP_LABEL = "label";
    private static final String PROP_SHA1SUM = "sha1";

    private static final String PROP_ALGORITHM = "algorithm";

    // RSA
    private static final String PROP_RSA_MODUS = "modus";
    private static final String PROP_RSA_PUBLIC_EXPONENT = "publicExponent";

    // DSA
    private static final String PROP_DSA_PRIME = "prime"; // p
    private static final String PROP_DSA_SUBPRIME = "subprime"; // q
    private static final String PROP_DSA_BASE = "base"; // g
    private static final String PROP_DSA_VALUE = "value"; // y

    // EC
    private static final String PROP_EC_ECDSA_PARAMS = "ecdsaParams";
    private static final String PROP_EC_EC_POINT = "ecPoint";

    private static final long[] supportedMechs = new long[]{
        PKCS11Constants.CKM_DSA_KEY_PAIR_GEN,
        PKCS11Constants.CKM_RSA_PKCS_KEY_PAIR_GEN,
        PKCS11Constants.CKM_EC_KEY_PAIR_GEN,
        PKCS11Constants.CKM_GENERIC_SECRET_KEY_GEN,

        // Digest
        PKCS11Constants.CKM_SHA_1,
        PKCS11Constants.CKM_SHA224,
        PKCS11Constants.CKM_SHA256,
        PKCS11Constants.CKM_SHA384,
        PKCS11Constants.CKM_SHA512,
        PKCS11Constants.CKM_SHA3_224,
        PKCS11Constants.CKM_SHA3_256,
        PKCS11Constants.CKM_SHA3_384,
        PKCS11Constants.CKM_SHA3_512,

        // HMAC
        PKCS11Constants.CKM_SHA_1_HMAC,
        PKCS11Constants.CKM_SHA224_HMAC,
        PKCS11Constants.CKM_SHA256_HMAC,
        PKCS11Constants.CKM_SHA384_HMAC,
        PKCS11Constants.CKM_SHA512_HMAC,
        PKCS11Constants.CKM_SHA3_224_HMAC,
        PKCS11Constants.CKM_SHA3_256_HMAC,
        PKCS11Constants.CKM_SHA3_384_HMAC,
        PKCS11Constants.CKM_SHA3_512_HMAC,

        PKCS11Constants.CKM_RSA_X_509,

        PKCS11Constants.CKM_RSA_PKCS,
        PKCS11Constants.CKM_SHA1_RSA_PKCS,
        PKCS11Constants.CKM_SHA224_RSA_PKCS,
        PKCS11Constants.CKM_SHA256_RSA_PKCS,
        PKCS11Constants.CKM_SHA384_RSA_PKCS,
        PKCS11Constants.CKM_SHA512_RSA_PKCS,
        PKCS11Constants.CKM_SHA3_224_RSA_PKCS,
        PKCS11Constants.CKM_SHA3_256_RSA_PKCS,
        PKCS11Constants.CKM_SHA3_384_RSA_PKCS,
        PKCS11Constants.CKM_SHA3_512_RSA_PKCS,

        PKCS11Constants.CKM_RSA_PKCS_PSS,
        PKCS11Constants.CKM_SHA1_RSA_PKCS_PSS,
        PKCS11Constants.CKM_SHA224_RSA_PKCS_PSS,
        PKCS11Constants.CKM_SHA256_RSA_PKCS_PSS,
        PKCS11Constants.CKM_SHA384_RSA_PKCS_PSS,
        PKCS11Constants.CKM_SHA512_RSA_PKCS_PSS,
        PKCS11Constants.CKM_SHA3_224_RSA_PKCS_PSS,
        PKCS11Constants.CKM_SHA3_256_RSA_PKCS_PSS,
        PKCS11Constants.CKM_SHA3_384_RSA_PKCS_PSS,
        PKCS11Constants.CKM_SHA3_512_RSA_PKCS_PSS,

        PKCS11Constants.CKM_DSA,
        PKCS11Constants.CKM_DSA_SHA1,
        PKCS11Constants.CKM_DSA_SHA224,
        PKCS11Constants.CKM_DSA_SHA256,
        PKCS11Constants.CKM_DSA_SHA384,
        PKCS11Constants.CKM_DSA_SHA512,
        PKCS11Constants.CKM_DSA_SHA3_224,
        PKCS11Constants.CKM_DSA_SHA3_256,
        PKCS11Constants.CKM_DSA_SHA3_384,
        PKCS11Constants.CKM_DSA_SHA3_512,

        PKCS11Constants.CKM_ECDSA,
        PKCS11Constants.CKM_ECDSA_SHA1,
        PKCS11Constants.CKM_ECDSA_SHA224,
        PKCS11Constants.CKM_ECDSA_SHA256,
        PKCS11Constants.CKM_ECDSA_SHA384,
        PKCS11Constants.CKM_ECDSA_SHA512,
        PKCS11Constants.CKM_ECDSA_SHA3_224,
        PKCS11Constants.CKM_ECDSA_SHA3_256,
        PKCS11Constants.CKM_ECDSA_SHA3_384,
        PKCS11Constants.CKM_ECDSA_SHA3_512};

    private static final FilenameFilter INFO_FILENAME_FILTER = new InfoFilenameFilter();

    private final boolean namedCurveSupported;

    private final File slotDir;

    private final File privKeyDir;

    private final File pubKeyDir;

    private final File secKeyDir;

    private final File certDir;

    private final char[] password;

    private final PrivateKeyCryptor privateKeyCryptor;

    private final SecureRandom random = new SecureRandom();
    //private final SecurityFactory securityFactory;

    private final int maxSessions;

    private static final Logger LOG = LoggerFactory.getLogger(EmulatorP11Slot.class);

    EmulatorP11Slot(final String moduleName, final File slotDir, final P11SlotIdentifier slotId,
            final boolean readOnly, final char[] password,
            final PrivateKeyCryptor privateKeyCryptor,
            final P11MechanismFilter mechanismFilter, final int maxSessions)
            throws P11TokenException {
        super(moduleName, slotId, readOnly, mechanismFilter);

        this.slotDir = ParamUtil.requireNonNull("slotDir", slotDir);
        this.password = ParamUtil.requireNonNull("password", password);
        this.privateKeyCryptor = ParamUtil.requireNonNull("privateKeyCryptor", privateKeyCryptor);
        this.maxSessions = ParamUtil.requireMin("maxSessions", maxSessions, 1);

        this.privKeyDir = new File(slotDir, DIR_PRIV_KEY);
        if (!this.privKeyDir.exists()) {
            this.privKeyDir.mkdirs();
        }

        this.pubKeyDir = new File(slotDir, DIR_PUB_KEY);
        if (!this.pubKeyDir.exists()) {
            this.pubKeyDir.mkdirs();
        }

        this.secKeyDir = new File(slotDir, DIR_SEC_KEY);
        if (!this.secKeyDir.exists()) {
            this.secKeyDir.mkdirs();
        }

        this.certDir = new File(slotDir, DIR_CERT);
        if (!this.certDir.exists()) {
            this.certDir.mkdirs();
        }

        File slotInfoFile = new File(slotDir, FILE_SLOTINFO);
        if (slotInfoFile.exists()) {
            Properties props = loadProperties(slotInfoFile);
            this.namedCurveSupported = Boolean.parseBoolean(
                    props.getProperty(PROP_NAMED_CURVE_SUPPORTED, "true"));
        } else {
            this.namedCurveSupported = true;
        }

        refresh();
    }

    @Override
    protected P11SlotRefreshResult refresh0()
            throws P11TokenException {
        P11SlotRefreshResult ret = new P11SlotRefreshResult();
        for (long mech : supportedMechs) {
            ret.addMechanism(mech);
        }

        // Secret Keys
        File[] secKeyInfoFiles = secKeyDir.listFiles(INFO_FILENAME_FILTER);

        if (secKeyInfoFiles != null && secKeyInfoFiles.length != 0) {
            for (File secKeyInfoFile : secKeyInfoFiles) {
                byte[] id = getKeyIdFromInfoFilename(secKeyInfoFile.getName());
                String hexId = Hex.toHexString(id);

                try {
                    Properties props = loadProperties(secKeyInfoFile);
                    String label = props.getProperty(PROP_LABEL);

                    P11ObjectIdentifier p11ObjId = new P11ObjectIdentifier(id, label);
                    byte[] encodedValue = IoUtil.read(
                            new File(secKeyDir, hexId + VALUE_FILE_SUFFIX));

                    KeyStore ks = KeyStore.getInstance("JCEKS");
                    ks.load(new ByteArrayInputStream(encodedValue), password);
                    SecretKey key = null;
                    Enumeration<String> aliases = ks.aliases();
                    while (aliases.hasMoreElements()) {
                        String alias = aliases.nextElement();
                        if (ks.isKeyEntry(alias)) {
                            key = (SecretKey) ks.getKey(alias, password);
                            break;
                        }
                    }

                    EmulatorP11Identity identity = new EmulatorP11Identity(this,
                            new P11EntityIdentifier(slotId, p11ObjId), key,
                            maxSessions, random);
                    LOG.info("added PKCS#11 secret key {}", p11ObjId);
                    ret.addIdentity(identity);
                } catch (InvalidKeyException | ClassCastException ex) {
                    LogUtil.warn(LOG, ex,
                            "InvalidKeyException while initializing key with key-id " + hexId);
                    continue;
                } catch (Throwable th) {
                    LOG.error("unexpected exception while initializing key with key-id " + hexId,
                            th);
                    continue;
                }
            }
        }

        // Certificates
        File[] certInfoFiles = certDir.listFiles(INFO_FILENAME_FILTER);
        if (certInfoFiles != null) {
            for (File infoFile : certInfoFiles) {
                byte[] id = getKeyIdFromInfoFilename(infoFile.getName());
                Properties props = loadProperties(infoFile);
                String label = props.getProperty(PROP_LABEL);
                P11ObjectIdentifier objId = new P11ObjectIdentifier(id, label);
                try {
                    X509Cert cert = readCertificate(id);
                    ret.addCertificate(objId, cert);
                } catch (CertificateException | IOException ex) {
                    LOG.warn("could not parse certificate " + objId);
                }
            }
        }

        // Private / Public keys
        File[] privKeyInfoFiles = privKeyDir.listFiles(INFO_FILENAME_FILTER);

        if (privKeyInfoFiles != null && privKeyInfoFiles.length != 0) {
            for (File privKeyInfoFile : privKeyInfoFiles) {
                byte[] id = getKeyIdFromInfoFilename(privKeyInfoFile.getName());
                String hexId = Hex.toHexString(id);

                try {
                    Properties props = loadProperties(privKeyInfoFile);
                    String label = props.getProperty(PROP_LABEL);

                    P11ObjectIdentifier p11ObjId = new P11ObjectIdentifier(id, label);
                    X509Cert cert = ret.getCertForId(id);
                    java.security.PublicKey publicKey = (cert == null) ? readPublicKey(id)
                            : cert.cert().getPublicKey();

                    if (publicKey == null) {
                        LOG.warn(
                            "Neither public key nor certificate is associated with private key {}",
                            p11ObjId);
                        continue;
                    }

                    byte[] encodedValue = IoUtil.read(
                            new File(privKeyDir, hexId + VALUE_FILE_SUFFIX));

                    PKCS8EncryptedPrivateKeyInfo epki =
                            new PKCS8EncryptedPrivateKeyInfo(encodedValue);
                    PrivateKey privateKey = privateKeyCryptor.decrypt(epki);

                    X509Certificate[] certs = (cert == null) ? null
                            : new X509Certificate[]{cert.cert()};

                    EmulatorP11Identity identity = new EmulatorP11Identity(this,
                            new P11EntityIdentifier(slotId, p11ObjId), privateKey, publicKey, certs,
                            maxSessions, random);
                    LOG.info("added PKCS#11 key {}", p11ObjId);
                    ret.addIdentity(identity);
                } catch (InvalidKeyException ex) {
                    LogUtil.warn(LOG, ex,
                            "InvalidKeyException while initializing key with key-id " + hexId);
                    continue;
                } catch (Throwable th) {
                    LOG.error("unexpected exception while initializing key with key-id " + hexId,
                            th);
                    continue;
                }
            }
        }

        return ret;
    } // method refresh

    File slotDir() {
        return slotDir;
    }

    private PublicKey readPublicKey(final byte[] keyId) throws P11TokenException {
        String hexKeyId = Hex.toHexString(keyId);
        File pubKeyFile = new File(pubKeyDir, hexKeyId + INFO_FILE_SUFFIX);
        Properties props = loadProperties(pubKeyFile);

        String algorithm = props.getProperty(PROP_ALGORITHM);
        if (PKCSObjectIdentifiers.rsaEncryption.getId().equals(algorithm)) {
            BigInteger exp = new BigInteger(1,
                    Hex.decode(props.getProperty(PROP_RSA_PUBLIC_EXPONENT)));
            BigInteger mod = new BigInteger(1, Hex.decode(props.getProperty(PROP_RSA_MODUS)));

            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(mod, exp);
            try {
                return KeyUtil.generateRSAPublicKey(keySpec);
            } catch (InvalidKeySpecException ex) {
                throw new P11TokenException(ex.getMessage(), ex);
            }
        } else if (X9ObjectIdentifiers.id_dsa.getId().equals(algorithm)) {
            BigInteger prime = new BigInteger(1,
                    Hex.decode(props.getProperty(PROP_DSA_PRIME))); // p
            BigInteger subPrime = new BigInteger(1,
                    Hex.decode(props.getProperty(PROP_DSA_SUBPRIME))); // q
            BigInteger base = new BigInteger(1,
                    Hex.decode(props.getProperty(PROP_DSA_BASE))); // g
            BigInteger value = new BigInteger(1,
                    Hex.decode(props.getProperty(PROP_DSA_VALUE))); // y

            DSAPublicKeySpec keySpec = new DSAPublicKeySpec(value, prime, subPrime, base);
            try {
                return KeyUtil.generateDSAPublicKey(keySpec);
            } catch (InvalidKeySpecException ex) {
                throw new P11TokenException(ex.getMessage(), ex);
            }
        } else if (X9ObjectIdentifiers.id_ecPublicKey.getId().equals(algorithm)) {
            byte[] ecdsaParams = Hex.decode(props.getProperty(PROP_EC_ECDSA_PARAMS));
            byte[] asn1EncodedPoint = Hex.decode(props.getProperty(PROP_EC_EC_POINT));
            byte[] ecPoint = DEROctetString.getInstance(asn1EncodedPoint).getOctets();
            try {
                return KeyUtil.createECPublicKey(ecdsaParams, ecPoint);
            } catch (InvalidKeySpecException ex) {
                throw new P11TokenException(ex.getMessage(), ex);
            }
        } else {
            throw new P11TokenException("unknown key algorithm " + algorithm);
        }
    }

    private X509Cert readCertificate(final byte[] keyId) throws CertificateException, IOException {
        byte[] encoded = IoUtil.read(new File(certDir, Hex.toHexString(keyId) + VALUE_FILE_SUFFIX));
        X509Certificate cert = X509Util.parseCert(encoded);
        return new X509Cert(cert, encoded);
    }

    private Properties loadProperties(final File file) throws P11TokenException {
        try {
            try (InputStream stream = new FileInputStream(file)) {
                Properties props = new Properties();
                props.load(stream);
                return props;
            }
        } catch (IOException ex) {
            throw new P11TokenException("could not load properties from the file " + file.getPath(),
                    ex);
        }
    }

    private static byte[] getKeyIdFromInfoFilename(final String fileName) {
        return Hex.decode(fileName.substring(0, fileName.length() - INFO_FILE_SUFFIX.length()));
    }

    @Override
    public void close() {
        LOG.info("close slot " + slotId);
    }

    private boolean removePkcs11Cert(final P11ObjectIdentifier objectId) throws P11TokenException {
        return removePkcs11Entry(certDir, objectId);
    }

    private boolean removePkcs11Entry(final File dir, final P11ObjectIdentifier objectId)
            throws P11TokenException {
        byte[] id = objectId.id();
        String label = objectId.label();
        if (id != null) {
            String hextId = Hex.toHexString(id);
            File infoFile = new File(dir, hextId + INFO_FILE_SUFFIX);
            if (!infoFile.exists()) {
                return false;
            }

            if (StringUtil.isBlank(label)) {
                return deletePkcs11Entry(dir, id);
            } else {
                Properties props = loadProperties(infoFile);

                return label.equals(props.getProperty("label")) ? deletePkcs11Entry(dir, id)
                        : false;
            }
        }

        // id is null, delete all entries with the specified label
        boolean deleted = false;
        File[] infoFiles = dir.listFiles(INFO_FILENAME_FILTER);
        if (infoFiles != null) {
            for (File infoFile : infoFiles) {
                if (!infoFile.isFile()) {
                    continue;
                }

                Properties props = loadProperties(infoFile);
                if (label.equals(props.getProperty("label"))) {
                    if (deletePkcs11Entry(dir, getKeyIdFromInfoFilename(infoFile.getName()))) {
                        deleted = true;
                    }
                }
            }
        }

        return deleted;
    }

    private static boolean deletePkcs11Entry(final File dir, final byte[] objectId) {
        String hextId = Hex.toHexString(objectId);
        File infoFile = new File(dir, hextId + INFO_FILE_SUFFIX);
        boolean b1 = true;
        if (infoFile.exists()) {
            b1 = infoFile.delete();
        }

        File valueFile = new File(dir, hextId + VALUE_FILE_SUFFIX);
        boolean b2 = true;
        if (valueFile.exists()) {
            b2 = valueFile.delete();
        }

        return b1 || b2;
    }

    private int deletePkcs11Entry(final File dir, final byte[] id, final String label)
            throws P11TokenException {
        if (StringUtil.isBlank(label)) {
            return deletePkcs11Entry(dir, id) ? 1 : 0;
        }

        if (id != null && id.length > 0) {
            String hextId = Hex.toHexString(id);
            File infoFile = new File(dir, hextId + INFO_FILE_SUFFIX);
            if (!infoFile.exists()) {
                return 0;
            }

            Properties props = loadProperties(infoFile);
            if (!label.equals(props.get(PROP_LABEL))) {
                return 0;
            }

            return deletePkcs11Entry(dir, id) ? 1 : 0;
        }

        File[] infoFiles = dir.listFiles(INFO_FILENAME_FILTER);
        if (infoFiles == null || infoFiles.length == 0) {
            return 0;
        }

        List<byte[]> ids = new LinkedList<>();

        for (File infoFile : infoFiles) {
            Properties props = loadProperties(infoFile);
            if (label.equals(props.getProperty(PROP_LABEL))) {
                ids.add(getKeyIdFromInfoFilename(infoFile.getName()));
            }
        }

        if (ids.isEmpty()) {
            return 0;
        }

        for (byte[] m : ids) {
            deletePkcs11Entry(dir, m);
        }
        return ids.size();
    }

    private void savePkcs11SecretKey(final byte[] id, final String label,
            final SecretKey secretKey) throws P11TokenException {
        byte[] encrytedValue;
        try {
            KeyStore ks = KeyStore.getInstance("JCEKS");
            ks.load(null, password);
            ks.setKeyEntry("main", secretKey, password, null);
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ks.store(outStream, password);
            outStream.flush();
            encrytedValue = outStream.toByteArray();
        } catch (NoSuchAlgorithmException | KeyStoreException
                | CertificateException | IOException ex) {
            throw new P11TokenException(ex.getClass().getName() + ": " + ex.getMessage(), ex);
        }

        savePkcs11Entry(secKeyDir, id, label, encrytedValue);
    }

    private void savePkcs11PrivateKey(final byte[] id, final String label,
            final PrivateKey privateKey) throws P11TokenException {
        PKCS8EncryptedPrivateKeyInfo encryptedPrivKeyInfo = privateKeyCryptor.encrypt(privateKey);
        byte[] encoded;
        try {
            encoded = encryptedPrivKeyInfo.getEncoded();
        } catch (IOException ex) {
            LogUtil.error(LOG, ex);
            throw new P11TokenException("could not encode PrivateKey");
        }
        savePkcs11Entry(privKeyDir, id, label, encoded);
    }

    private void savePkcs11PublicKey(final byte[] id, final String label, final PublicKey publicKey)
            throws P11TokenException {
        String hexId = Hex.toHexString(id).toLowerCase();

        StringBuilder sb = new StringBuilder(100);
        sb.append(PROP_ID).append('=').append(hexId).append('\n');
        sb.append(PROP_LABEL).append('=').append(label).append('\n');

        if (publicKey instanceof RSAPublicKey) {
            sb.append(PROP_ALGORITHM).append('=');
            sb.append(PKCSObjectIdentifiers.rsaEncryption.getId());
            sb.append('\n');

            sb.append(PROP_RSA_MODUS).append('=');

            RSAPublicKey rsaKey = (RSAPublicKey) publicKey;
            sb.append(Hex.toHexString(rsaKey.getModulus().toByteArray()));
            sb.append('\n');

            sb.append(PROP_RSA_PUBLIC_EXPONENT).append('=');
            sb.append(Hex.toHexString(rsaKey.getPublicExponent().toByteArray()));
            sb.append('\n');
        } else if (publicKey instanceof DSAPublicKey) {
            sb.append(PROP_ALGORITHM).append('=');
            sb.append(X9ObjectIdentifiers.id_dsa.getId());
            sb.append('\n');

            sb.append(PROP_DSA_PRIME).append('=');
            DSAPublicKey dsaKey = (DSAPublicKey) publicKey;
            sb.append(Hex.toHexString(dsaKey.getParams().getP().toByteArray()));
            sb.append('\n');

            sb.append(PROP_DSA_SUBPRIME).append('=');
            sb.append(Hex.toHexString(dsaKey.getParams().getQ().toByteArray()));
            sb.append('\n');

            sb.append(PROP_DSA_BASE).append('=');
            sb.append(Hex.toHexString(dsaKey.getParams().getG().toByteArray()));
            sb.append('\n');

            sb.append(PROP_DSA_VALUE).append('=');
            sb.append(Hex.toHexString(dsaKey.getY().toByteArray()));
            sb.append('\n');
        } else if (publicKey instanceof ECPublicKey) {
            sb.append(PROP_ALGORITHM).append('=');
            sb.append(X9ObjectIdentifiers.id_ecPublicKey.getId());
            sb.append('\n');

            ECPublicKey ecKey = (ECPublicKey) publicKey;
            ECParameterSpec paramSpec = ecKey.getParams();

            // ecdsaParams
            org.bouncycastle.jce.spec.ECParameterSpec bcParamSpec =
                    EC5Util.convertSpec(paramSpec, false);
            ASN1ObjectIdentifier curveOid = ECUtil.getNamedCurveOid(bcParamSpec);
            if (curveOid == null) {
                throw new P11TokenException("EC public key is not of namedCurve");
            }

            byte[] encodedParams;
            try {
                if (namedCurveSupported) {
                    encodedParams = curveOid.getEncoded();
                } else {
                    encodedParams = ECNamedCurveTable.getByOID(curveOid).getEncoded();
                }
            } catch (IOException | NullPointerException ex) {
                throw new P11TokenException(ex.getMessage(), ex);
            }

            sb.append(PROP_EC_ECDSA_PARAMS).append('=');
            sb.append(Hex.toHexString(encodedParams));
            sb.append('\n');

            // EC point
            java.security.spec.ECPoint pointW = ecKey.getW();
            int keysize = (paramSpec.getOrder().bitLength() + 7) / 8;
            byte[] ecPoint = new byte[1 + keysize * 2];
            ecPoint[0] = 4; // uncompressed
            bigIntToBytes("Wx", pointW.getAffineX(), ecPoint, 1, keysize);
            bigIntToBytes("Wy", pointW.getAffineY(), ecPoint, 1 + keysize, keysize);

            byte[] encodedEcPoint;
            try {
                encodedEcPoint = new DEROctetString(ecPoint).getEncoded();
            } catch (IOException ex) {
                throw new P11TokenException("could not ASN.1 encode the ECPoint");
            }
            sb.append(PROP_EC_EC_POINT).append('=');
            sb.append(Hex.toHexString(encodedEcPoint));
            sb.append('\n');
        } else {
            throw new IllegalArgumentException(
                    "unsupported public key " + publicKey.getClass().getName());
        }

        try {
            IoUtil.save(new File(pubKeyDir, hexId + INFO_FILE_SUFFIX), sb.toString().getBytes());
        } catch (IOException ex) {
            throw new P11TokenException(ex.getMessage(), ex);
        }
    }

    private static void bigIntToBytes(final String numName, final BigInteger num, final byte[] dest,
            final int destPos, final int length) throws P11TokenException {
        if (num.signum() != 1) {
            throw new P11TokenException(numName + " is not positive");
        }
        byte[] bytes = num.toByteArray();
        if (bytes.length == length) {
            System.arraycopy(bytes, 0, dest, destPos, length);
        } else if (bytes.length < length) {
            System.arraycopy(bytes, 0, dest, destPos + length - bytes.length, bytes.length);
        } else {
            System.arraycopy(bytes, bytes.length - length, dest, destPos, length);
        }
    }

    private void savePkcs11Cert(final byte[] id, final String label, final X509Certificate cert)
            throws P11TokenException, CertificateException {
        savePkcs11Entry(certDir, id, label, cert.getEncoded());
    }

    private static void savePkcs11Entry(final File dir, final byte[] id, final String label,
            final byte[] value) throws P11TokenException {
        ParamUtil.requireNonNull("dir", dir);
        ParamUtil.requireNonNull("id", id);
        ParamUtil.requireNonBlank("label", label);
        ParamUtil.requireNonNull("value", value);

        String hexId = Hex.toHexString(id).toLowerCase();

        StringBuilder sb = new StringBuilder(200);
        sb.append(PROP_ID).append('=').append(hexId).append('\n');
        sb.append(PROP_LABEL).append('=').append(label).append('\n');
        sb.append(PROP_SHA1SUM).append('=').append(HashAlgoType.SHA1.hexHash(value)).append('\n');

        try {
            IoUtil.save(new File(dir, hexId + INFO_FILE_SUFFIX), sb.toString().getBytes());
            IoUtil.save(new File(dir, hexId + VALUE_FILE_SUFFIX), value);
        } catch (IOException ex) {
            throw new P11TokenException("could not save certificate");
        }
    }

    @Override
    public int removeObjects(final byte[] id, final String label) throws P11TokenException {
        if ((id == null || id.length == 0) && StringUtil.isBlank(label)) {
            throw new IllegalArgumentException("at least one of id and label must not be null");
        }

        int num = deletePkcs11Entry(privKeyDir, id, label);
        num += deletePkcs11Entry(pubKeyDir, id, label);
        num += deletePkcs11Entry(certDir, id, label);
        num += deletePkcs11Entry(secKeyDir, id, label);
        return num;
    }

    @Override
    protected void removeIdentity0(final P11ObjectIdentifier objectId) throws P11TokenException {
        boolean b1 = removePkcs11Entry(certDir, objectId);
        boolean b2 = removePkcs11Entry(privKeyDir, objectId);
        boolean b3 = removePkcs11Entry(pubKeyDir, objectId);
        boolean b4 = removePkcs11Entry(secKeyDir, objectId);
        if (! (b1 || b2 || b3 || b4)) {
            throw new P11UnknownEntityException(slotId, objectId);
        }
    }

    @Override
    protected void removeCerts0(final P11ObjectIdentifier objectId) throws P11TokenException {
        deletePkcs11Entry(certDir, objectId.id());
    }

    @Override
    protected void addCert0(final P11ObjectIdentifier objectId, final X509Certificate cert)
            throws P11TokenException, CertificateException {
        savePkcs11Cert(objectId.id(), objectId.label(), cert);
    }

    @Override
    protected P11Identity generateSecretKey0(long keyType, int keysize, String label,
            P11NewKeyControl control)
            throws P11TokenException {
        if (keysize % 8 != 0) {
            throw new IllegalArgumentException("keysize is not multiple of 8: " + keysize);
        }
        byte[] keyBytes = new byte[keysize / 8];
        random.nextBytes(keyBytes);
        SecretKey key = new SecretKeySpec(keyBytes, getSecretKeyAlgorithm(keyType));
        return saveP11Entity(key, label);
    }

    @Override
    protected P11Identity createSecretKey0(long keyType, byte[] keyValue, String label,
            P11NewKeyControl control)
            throws P11TokenException {
        SecretKey key = new SecretKeySpec(keyValue, getSecretKeyAlgorithm(keyType));
        return saveP11Entity(key, label);
    }

    private static String getSecretKeyAlgorithm(long keyType) {
        String algorithm;
        if (PKCS11Constants.CKK_GENERIC_SECRET == keyType) {
            algorithm = "generic";
        } else if (PKCS11Constants.CKK_AES == keyType) {
            algorithm = "AES";
        } else if (PKCS11Constants.CKK_SHA_1_HMAC == keyType) {
            algorithm = "HMACSHA1";
        } else if (PKCS11Constants.CKK_SHA224_HMAC == keyType) {
            algorithm = "HMACSHA224";
        } else if (PKCS11Constants.CKK_SHA256_HMAC == keyType) {
            algorithm = "HMACSHA256";
        } else if (PKCS11Constants.CKK_SHA384_HMAC == keyType) {
            algorithm = "HMACSHA384";
        } else if (PKCS11Constants.CKK_SHA512_HMAC == keyType) {
            algorithm = "HMACSHA512";
        } else if (PKCS11Constants.CKK_SHA3_224_HMAC == keyType) {
            algorithm = "HMACSHA3-224";
        } else if (PKCS11Constants.CKK_SHA3_256_HMAC == keyType) {
            algorithm = "HMACSHA3-256";
        } else if (PKCS11Constants.CKK_SHA3_384_HMAC == keyType) {
            algorithm = "HMACSHA3-384";
        } else if (PKCS11Constants.CKK_SHA3_512_HMAC == keyType) {
            algorithm = "HMACSHA3-512";
        } else {
            throw new IllegalArgumentException("unsupported keyType " + keyType);
        }
        return algorithm;
    }

    @Override
    protected P11Identity generateRSAKeypair0(final int keysize, final BigInteger publicExponent,
            final String label, P11NewKeyControl control) throws P11TokenException {
        KeyPair keypair;
        try {
            keypair = KeyUtil.generateRSAKeypair(keysize, publicExponent, random);
        } catch (NoSuchAlgorithmException | NoSuchProviderException
                | InvalidAlgorithmParameterException ex) {
            throw new P11TokenException(ex.getMessage(), ex);
        }
        return saveP11Entity(keypair, label);
    }

    @Override
    // CHECKSTYLE:OFF
    protected P11Identity generateDSAKeypair0(final BigInteger p, final BigInteger q,
            final BigInteger g, final String label, P11NewKeyControl control)
            throws P11TokenException {
    // CHECKSTYLE:ON
        DSAParameters dsaParams = new DSAParameters(p, q, g);
        KeyPair keypair;
        try {
            keypair = KeyUtil.generateDSAKeypair(dsaParams, random);
        } catch (NoSuchAlgorithmException | NoSuchProviderException
                | InvalidAlgorithmParameterException ex) {
            throw new P11TokenException(ex.getMessage(), ex);
        }
        return saveP11Entity(keypair, label);
    }

    @Override
    protected P11Identity generateECKeypair0(final ASN1ObjectIdentifier curveId,
            final String label, P11NewKeyControl control) throws P11TokenException {
        KeyPair keypair;
        try {
            keypair = KeyUtil.generateECKeypairForCurveNameOrOid(curveId.getId(), random);
        } catch (NoSuchAlgorithmException | NoSuchProviderException
                | InvalidAlgorithmParameterException ex) {
            throw new P11TokenException(ex.getMessage(), ex);
        }
        return saveP11Entity(keypair, label);
    }

    private P11Identity saveP11Entity(@NonNull final KeyPair keypair, @NonNull final String label)
            throws P11TokenException {
        byte[] id = generateId();
        savePkcs11PrivateKey(id, label, keypair.getPrivate());
        savePkcs11PublicKey(id, label, keypair.getPublic());
        P11EntityIdentifier identityId = new P11EntityIdentifier(slotId,
                new P11ObjectIdentifier(id, label));
        try {
            return new EmulatorP11Identity(this,identityId, keypair.getPrivate(),
                    keypair.getPublic(), null, maxSessions, random);
        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException ex) {
            throw new P11TokenException(
                    "could not construct KeyStoreP11Identity: " + ex.getMessage(), ex);
        }
    }

    private P11Identity saveP11Entity(@NonNull final SecretKey key, @NonNull final String label)
            throws P11TokenException {
        byte[] id = generateId();
        savePkcs11SecretKey(id, label, key);
        P11EntityIdentifier identityId = new P11EntityIdentifier(slotId,
                new P11ObjectIdentifier(id, label));
        try {
            return new EmulatorP11Identity(this,identityId, key, maxSessions, random);
        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException ex) {
            throw new P11TokenException(
                    "could not construct KeyStoreP11Identity: " + ex.getMessage(), ex);
        }
    }

    @Override
    protected void updateCertificate0(final P11ObjectIdentifier objectId,
            final X509Certificate newCert)
            throws P11TokenException, CertificateException {
        removePkcs11Cert(objectId);
        addCert0(objectId, newCert);
    }

}
