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

package org.xipki.security;

import java.io.IOException;
import java.io.OutputStream;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bouncycastle.asn1.crmf.POPOSigningKey;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.cmp.CMPException;
import org.bouncycastle.cert.cmp.ProtectedPKIMessage;
import org.bouncycastle.cert.cmp.ProtectedPKIMessageBuilder;
import org.bouncycastle.cert.crmf.ProofOfPossessionSigningKeyBuilder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.common.util.LogUtil;
import org.xipki.common.util.ParamUtil;
import org.xipki.password.PasswordResolver;
import org.xipki.security.exception.NoIdleSignerException;
import org.xipki.security.exception.XiSecurityException;
import org.xipki.security.util.AlgorithmUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class DefaultConcurrentContentSigner implements ConcurrentContentSigner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultConcurrentContentSigner.class);

    private static final AtomicInteger NAME_INDEX = new AtomicInteger(1);

    private static int defaultSignServiceTimeout = 10000; // 10 seconds

    private final String name;

    private final String algorithmName;

    private final BlockingDeque<ContentSigner> idleSigners = new LinkedBlockingDeque<>();

    private final BlockingDeque<ContentSigner> busySigners = new LinkedBlockingDeque<>();

    private final boolean mac;

    private byte[] sha1DigestOfMacKey;

    private final Key signingKey;

    private final AlgorithmCode algorithmCode;

    private PublicKey publicKey;

    private X509Certificate[] certificateChain;

    private X509CertificateHolder[] certificateChainAsBcObjects;

    static {
        final String propKey = "org.xipki.security.signservice.timeout";
        String str = System.getProperty(propKey);
        if (str != null) {
            int vi = Integer.parseInt(str);
            // valid value is between 0 and 60 seconds
            if (vi < 0 || vi > 60 * 1000) {
                LOG.error("invalid {}: {}", propKey, vi);
            } else {
                LOG.info("use {}: {}", propKey, vi);
                defaultSignServiceTimeout = vi;
            }
        }
    }

    public DefaultConcurrentContentSigner(final boolean mac,
            final List<ContentSigner> signers)
            throws NoSuchAlgorithmException {
        this(mac, signers, null);
    }

    public DefaultConcurrentContentSigner(final boolean mac,
            final List<ContentSigner> signers, final Key signingKey)
            throws NoSuchAlgorithmException {
        ParamUtil.requireNonEmpty("signers", signers);

        this.mac = mac;
        AlgorithmIdentifier algorithmIdentifier = signers.get(0).getAlgorithmIdentifier();
        this.algorithmName = AlgorithmUtil.getSigOrMacAlgoName(algorithmIdentifier);
        this.algorithmCode = AlgorithmUtil.getSigOrMacAlgoCode(algorithmIdentifier);

        for (ContentSigner signer : signers) {
            idleSigners.addLast(signer);
        }

        this.signingKey = signingKey;
        this.name = "defaultSigner-" + NAME_INDEX.getAndIncrement();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isMac() {
        return mac;
    }

    public void setSha1DigestOfMacKey(byte[] sha1Digest) {
        if (sha1Digest == null) {
            this.sha1DigestOfMacKey = null;
        } else if (sha1Digest.length == 20) {
            this.sha1DigestOfMacKey = Arrays.copyOf(sha1Digest, 20);
        } else {
            throw new IllegalArgumentException("invalid sha1Digest.length ("
                    + sha1Digest.length + " != 20)");
        }
    }

    @Override
    public byte[] getSha1DigestOfMacKey() {
        return (sha1DigestOfMacKey == null) ? null : Arrays.copyOf(sha1DigestOfMacKey, 20);
    }

    @Override
    public AlgorithmCode algorithmCode() {
        return algorithmCode;
    }

    private ContentSigner borrowContentSigner() throws NoIdleSignerException {
        return borrowContentSigner(defaultSignServiceTimeout);
    }

    /**
     * @param timeout timeout in milliseconds, 0 for infinitely.
     */
    private ContentSigner borrowContentSigner(final int soTimeout) throws NoIdleSignerException {
        ContentSigner signer = null;
        try {
            signer = (soTimeout == 0) ? idleSigners.takeFirst()
                    : idleSigners.pollFirst(soTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) { // CHECKSTYLE:SKIP
        }

        if (signer == null) {
            throw new NoIdleSignerException("no idle signer available");
        }

        busySigners.addLast(signer);
        return signer;
    }

    private void returnContentSigner(final ContentSigner signer) {
        ParamUtil.requireNonNull("signer", signer);

        boolean isBusySigner = busySigners.remove(signer);
        if (isBusySigner) {
            idleSigners.addLast(signer);
        } else {
            final String msg =
                    "signer has not been borrowed before or has been returned more than once: "
                    + signer;
            LOG.error(msg);
            throw new IllegalStateException(msg);
        }
    }

    @Override
    public void initialize(final String conf, final PasswordResolver passwordResolver)
            throws XiSecurityException {
    }

    @Override
    public Key getSigningKey() {
        return signingKey;
    }

    @Override
    public void setCertificateChain(final X509Certificate[] certificateChain) {
        if (certificateChain == null || certificateChain.length == 0) {
            this.certificateChain = null;
            this.certificateChainAsBcObjects = null;
            return;
        }

        this.certificateChain = certificateChain;
        setPublicKey(certificateChain[0].getPublicKey());
        final int n = certificateChain.length;

        this.certificateChainAsBcObjects = new X509CertificateHolder[n];
        for (int i = 0; i < n; i++) {
            X509Certificate cert = this.certificateChain[i];
            try {
                this.certificateChainAsBcObjects[i] = new X509CertificateHolder(cert.getEncoded());
            } catch (CertificateEncodingException | IOException ex) {
                throw new IllegalArgumentException(
                        String.format("%s occurred while parsing certificate at index %d: %s",
                                ex.getClass().getName(), i, ex.getMessage()), ex);
            }
        }
    }

    @Override
    public PublicKey getPublicKey() {
        return publicKey;
    }

    @Override
    public void setPublicKey(final PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public X509Certificate getCertificate() {
        return (certificateChain != null && certificateChain.length > 0)
                ? certificateChain[0] : null;
    }

    @Override
    public X509CertificateHolder getCertificateAsBcObject() {
        return (certificateChainAsBcObjects != null && certificateChainAsBcObjects.length > 0)
                ? certificateChainAsBcObjects[0] : null;
    }

    @Override
    public X509Certificate[] getCertificateChain() {
        return certificateChain;
    }

    @Override
    public X509CertificateHolder[] getCertificateChainAsBcObjects() {
        return certificateChainAsBcObjects;
    }

    @Override
    public boolean isHealthy() {
        ContentSigner signer = null;
        try {
            signer = borrowContentSigner();
            OutputStream stream = signer.getOutputStream();
            stream.write(new byte[]{1, 2, 3, 4});
            byte[] signature = signer.getSignature();
            return signature != null && signature.length > 0;
        } catch (Exception ex) {
            LogUtil.error(LOG, ex);
            return false;
        } finally {
            if (signer != null) {
                returnContentSigner(signer);
            }
        }
    }

    @Override
    public String getAlgorithmName() {
        return algorithmName;
    }

    @Override
    public void shutdown() {
    }

    @Override
    public POPOSigningKey build(final ProofOfPossessionSigningKeyBuilder builder)
            throws NoIdleSignerException {
        ContentSigner contentSigner = borrowContentSigner();
        try {
            return builder.build(contentSigner);
        } finally {
            returnContentSigner(contentSigner);
        }
    }

    @Override
    public ProtectedPKIMessage build(final ProtectedPKIMessageBuilder builder)
            throws NoIdleSignerException, CMPException {
        ContentSigner contentSigner = borrowContentSigner();
        try {
            return builder.build(contentSigner);
        } finally {
            returnContentSigner(contentSigner);
        }
    }

    @Override
    public X509CRLHolder build(final X509v2CRLBuilder builder) throws NoIdleSignerException {
        ContentSigner contentSigner = borrowContentSigner();
        try {
            return builder.build(contentSigner);
        } finally {
            returnContentSigner(contentSigner);
        }
    }

    @Override
    public X509CertificateHolder build(final X509v3CertificateBuilder builder)
            throws NoIdleSignerException {
        ContentSigner contentSigner = borrowContentSigner();
        try {
            return builder.build(contentSigner);
        } finally {
            returnContentSigner(contentSigner);
        }
    }

    @Override
    public OCSPReq build(final OCSPReqBuilder builder, final X509CertificateHolder[] chain)
            throws NoIdleSignerException, OCSPException {
        ContentSigner contentSigner = borrowContentSigner();
        try {
            return builder.build(contentSigner, chain);
        } finally {
            returnContentSigner(contentSigner);
        }
    }

    @Override
    public BasicOCSPResp build(final BasicOCSPRespBuilder builder,
            final X509CertificateHolder[] chain, final Date producedAt)
            throws NoIdleSignerException, OCSPException {
        ContentSigner contentSigner = borrowContentSigner();
        try {
            return builder.build(contentSigner, chain, producedAt);
        } finally {
            returnContentSigner(contentSigner);
        }
    }

    @Override
    public PKCS10CertificationRequest build(final PKCS10CertificationRequestBuilder builder)
            throws NoIdleSignerException {
        ContentSigner contentSigner = borrowContentSigner();
        try {
            return builder.build(contentSigner);
        } finally {
            returnContentSigner(contentSigner);
        }
    }

    @Override
    public byte[] sign(final byte[] data) throws NoIdleSignerException, IOException {
        ContentSigner contentSigner = borrowContentSigner();
        try {
            OutputStream signatureStream = contentSigner.getOutputStream();
            signatureStream.write(data);
            return contentSigner.getSignature();
        } finally {
            returnContentSigner(contentSigner);
        }
    }

}
