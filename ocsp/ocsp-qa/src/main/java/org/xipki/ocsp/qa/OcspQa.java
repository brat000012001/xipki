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

package org.xipki.ocsp.qa;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers;
import org.bouncycastle.asn1.isismtt.ocsp.CertHash;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.ocsp.ResponderID;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.xipki.common.qa.ValidationIssue;
import org.xipki.common.qa.ValidationResult;
import org.xipki.common.util.ParamUtil;
import org.xipki.security.CrlReason;
import org.xipki.security.HashAlgoType;
import org.xipki.security.IssuerHash;
import org.xipki.security.ObjectIdentifiers;
import org.xipki.security.SecurityFactory;
import org.xipki.security.util.AlgorithmUtil;
import org.xipki.security.util.KeyUtil;
import org.xipki.security.util.X509Util;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class OcspQa {

    private final SecurityFactory securityFactory;

    public OcspQa(final SecurityFactory securityFactory) {
        this.securityFactory = ParamUtil.requireNonNull("securityFactory", securityFactory);
    }

    public ValidationResult checkOcsp(final OCSPResp response, final IssuerHash issuerHash,
            final List<BigInteger> serialNumbers, final Map<BigInteger, byte[]> encodedCerts,
            final OcspError expectedOcspError,
            final Map<BigInteger, OcspCertStatus> expectedOcspStatuses,
            final OcspResponseOption responseOption) {
        ParamUtil.requireNonNull("response", response);
        ParamUtil.requireNonEmpty("serialNumbers", serialNumbers);
        ParamUtil.requireNonEmpty("expectedOcspStatuses", expectedOcspStatuses);
        ParamUtil.requireNonNull("responseOption", responseOption);

        List<ValidationIssue> resultIssues = new LinkedList<ValidationIssue>();

        int status = response.getStatus();

        // Response status
        ValidationIssue issue = new ValidationIssue("OCSP.STATUS", "response.status");
        resultIssues.add(issue);
        if (expectedOcspError != null) {
            if (status != expectedOcspError.status()) {
                issue.setFailureMessage("is '" + status + "', but expected '"
                        + expectedOcspError.status() + "'");
            }
        } else {
            if (status != 0) {
                issue.setFailureMessage("is '" + status + "', but expected '0'");
            }
        }

        if (status != 0) {
            return new ValidationResult(resultIssues);
        }

        ValidationIssue encodingIssue = new ValidationIssue("OCSP.ENCODING", "response encoding");
        resultIssues.add(encodingIssue);

        BasicOCSPResp basicResp;
        try {
            basicResp = (BasicOCSPResp) response.getResponseObject();
        } catch (OCSPException ex) {
            encodingIssue.setFailureMessage(ex.getMessage());
            return new ValidationResult(resultIssues);
        }

        SingleResp[] singleResponses = basicResp.getResponses();

        issue = new ValidationIssue("OCSP.RESPONSES.NUM", "number of single responses");
        resultIssues.add(issue);

        if (singleResponses == null || singleResponses.length == 0) {
            issue.setFailureMessage("received no status from server");
            return new ValidationResult(resultIssues);
        }

        final int n = singleResponses.length;
        if (n != serialNumbers.size()) {
            issue.setFailureMessage("is '" + n + "', but expected '" + serialNumbers.size() + "'");
            return new ValidationResult(resultIssues);
        }

        boolean hasSignature = basicResp.getSignature() != null;

        // check the signature if available
        issue = new ValidationIssue("OCSP.SIG", "signature presence");
        resultIssues.add(issue);
        if (!hasSignature) {
            issue.setFailureMessage("response is not signed");
        }

        if (hasSignature) {
            // signature algorithm
            issue = new ValidationIssue("OCSP.SIG.ALG", "signature algorithm");
            resultIssues.add(issue);

            String expectedSigalgo = responseOption.signatureAlgName();
            if (expectedSigalgo != null) {
                AlgorithmIdentifier sigAlg = basicResp.getSignatureAlgorithmID();
                try {
                    String sigAlgName = AlgorithmUtil.getSignatureAlgoName(sigAlg);
                    if (!AlgorithmUtil.equalsAlgoName(sigAlgName, expectedSigalgo)) {
                        issue.setFailureMessage("is '" + sigAlgName + "', but expected '"
                                + expectedSigalgo + "'");
                    }
                } catch (NoSuchAlgorithmException ex) {
                    issue.setFailureMessage("could not extract the signature algorithm");
                }
            } // end if (expectedSigalgo != null)

            // signer certificate
            ValidationIssue sigSignerCertIssue = new ValidationIssue("OCSP.SIGNERCERT",
                    "signer certificate");
            resultIssues.add(sigSignerCertIssue);

            // signature validation
            ValidationIssue sigValIssue = new ValidationIssue("OCSP.SIG.VALIDATION",
                    "signature validation");
            resultIssues.add(sigValIssue);

            X509CertificateHolder respSigner = null;

            X509CertificateHolder[] responderCerts = basicResp.getCerts();
            if (responderCerts == null || responderCerts.length < 1) {
                sigSignerCertIssue.setFailureMessage(
                        "no responder certificate is contained in the response");
                sigValIssue.setFailureMessage("could not find certificate to validate signature");
            } else {
                ResponderID respId = basicResp.getResponderId().toASN1Primitive();
                X500Name respIdByName = respId.getName();
                byte[] respIdByKey = respId.getKeyHash();

                for (X509CertificateHolder cert : responderCerts) {
                    if (respIdByName != null) {
                        if (cert.getSubject().equals(respIdByName)) {
                            respSigner = cert;
                        }
                    } else {
                        byte[] spkiSha1 = HashAlgoType.SHA1.hash(
                                cert.getSubjectPublicKeyInfo().getPublicKeyData().getBytes());
                        if (Arrays.equals(respIdByKey, spkiSha1)) {
                            respSigner = cert;
                        }
                    }

                    if (respSigner != null) {
                        break;
                    }
                }

                if (respSigner == null) {
                    sigSignerCertIssue.setFailureMessage(
                            "no responder certificate match the ResponderId");
                    sigValIssue.setFailureMessage("could not find certificate matching the"
                            + " ResponderId to validate signature");
                }
            }

            if (respSigner != null) {
                issue = new ValidationIssue("OCSP.SIGNERCERT.TRUST",
                        "signer certificate validation");
                resultIssues.add(issue);

                for (int i = 0; i < singleResponses.length; i++) {
                    SingleResp singleResp = singleResponses[i];
                    if (!respSigner.isValidOn(singleResp.getThisUpdate())) {
                        issue.setFailureMessage(String.format(
                                "responder certificate is not valid on the thisUpdate[%d]: %s",
                                i, singleResp.getThisUpdate()));
                    }
                } // end for

                X509Certificate respIssuer = responseOption.respIssuer();
                if (!issue.isFailed() && respIssuer != null) {
                    X509Certificate jceRespSigner;
                    try {
                        jceRespSigner = X509Util.toX509Cert(respSigner.toASN1Structure());
                        if (X509Util.issues(respIssuer, jceRespSigner)) {
                            jceRespSigner.verify(respIssuer.getPublicKey());
                        } else {
                            issue.setFailureMessage("responder signer is not trusted");
                        }
                    } catch (Exception ex) {
                        issue.setFailureMessage("responder signer is not trusted");
                    }
                }

                try {
                    PublicKey responderPubKey = KeyUtil.generatePublicKey(
                            respSigner.getSubjectPublicKeyInfo());
                    ContentVerifierProvider cvp = securityFactory.getContentVerifierProvider(
                            responderPubKey);
                    boolean sigValid = basicResp.isSignatureValid(cvp);
                    if (!sigValid) {
                        sigValIssue.setFailureMessage("signature is invalid");
                    }
                } catch (Exception ex) {
                    sigValIssue.setFailureMessage("could not validate signature");
                }
            } // end if
        } // end if (hasSignature)

        // nonce
        Extension nonceExtn = basicResp.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);
        resultIssues.add(checkOccurrence("OCSP.NONCE", nonceExtn,
                responseOption.nonceOccurrence()));

        boolean extendedRevoke = basicResp.getExtension(
                ObjectIdentifiers.id_pkix_ocsp_extendedRevoke) != null;

        for (int i = 0; i < singleResponses.length; i++) {
            SingleResp singleResp = singleResponses[i];
            BigInteger serialNumber = singleResp.getCertID().getSerialNumber();
            OcspCertStatus expectedStatus = expectedOcspStatuses.get(serialNumber);

            byte[] encodedCert = null;
            if (encodedCerts != null) {
                encodedCert = encodedCerts.get(serialNumber);
            }

            List<ValidationIssue> issues = checkSingleCert(i, singleResp, issuerHash,
                    expectedStatus, encodedCert, extendedRevoke,
                    responseOption.nextUpdateOccurrence(),
                    responseOption.certhashOccurrence(), responseOption.certhashAlgId());
            resultIssues.addAll(issues);
        } // end for

        return new ValidationResult(resultIssues);
    } // method checkOcsp

    private List<ValidationIssue> checkSingleCert(final int index, final SingleResp singleResp,
            final IssuerHash issuerHash, final OcspCertStatus expectedStatus,
            final byte[] encodedCert, final boolean extendedRevoke,
            final Occurrence nextupdateOccurrence, final Occurrence certhashOccurrence,
            final ASN1ObjectIdentifier certhashAlg) {
        List<ValidationIssue> issues = new LinkedList<>();

        // issuer hash
        ValidationIssue issue = new ValidationIssue("OCSP.RESPONSE." + index + ".ISSUER",
                "certificate issuer");
        issues.add(issue);

        CertificateID certId = singleResp.getCertID();
        HashAlgoType hashAlgo = HashAlgoType.getHashAlgoType(certId.getHashAlgOID());
        if (hashAlgo == null) {
            issue.setFailureMessage("unknown hash algorithm " + certId.getHashAlgOID().getId());
        } else {
            if (!issuerHash.match(hashAlgo, certId.getIssuerNameHash(),
                    certId.getIssuerKeyHash())) {
                issue.setFailureMessage("issuer not match");
            }
        }

        // status
        issue = new ValidationIssue("OCSP.RESPONSE." + index + ".STATUS", "certificate status");
        issues.add(issue);

        CertificateStatus singleCertStatus = singleResp.getCertStatus();

        OcspCertStatus status = null;
        if (singleCertStatus == null) {
            status = OcspCertStatus.good;
        } else if (singleCertStatus instanceof RevokedStatus) {
            RevokedStatus revStatus = (RevokedStatus) singleCertStatus;
            Date revTime = revStatus.getRevocationTime();

            if (revStatus.hasRevocationReason()) {
                int reason = revStatus.getRevocationReason();
                if (extendedRevoke && reason == CrlReason.CERTIFICATE_HOLD.code()
                        && revTime.getTime() == 0) {
                    status = OcspCertStatus.unknown;
                } else {
                    CrlReason revocationReason = CrlReason.forReasonCode(reason);
                    switch (revocationReason) {
                    case UNSPECIFIED:
                        status = OcspCertStatus.unspecified;
                        break;
                    case KEY_COMPROMISE:
                        status = OcspCertStatus.keyCompromise;
                        break;
                    case CA_COMPROMISE:
                        status = OcspCertStatus.cACompromise;
                        break;
                    case AFFILIATION_CHANGED:
                        status = OcspCertStatus.affiliationChanged;
                        break;
                    case SUPERSEDED:
                        status = OcspCertStatus.superseded;
                        break;
                    case CERTIFICATE_HOLD:
                        status = OcspCertStatus.certificateHold;
                        break;
                    case REMOVE_FROM_CRL:
                        status = OcspCertStatus.removeFromCRL;
                        break;
                    case PRIVILEGE_WITHDRAWN:
                        status = OcspCertStatus.privilegeWithdrawn;
                        break;
                    case AA_COMPROMISE:
                        status = OcspCertStatus.aACompromise;
                        break;
                    default:
                        issue.setFailureMessage(
                                "should not reach here, unknown CRLReason " + revocationReason);
                        break;
                    }
                } // end if
            } else {
                status = OcspCertStatus.rev_noreason;
            } // end if (revStatus.hasRevocationReason())
        } else if (singleCertStatus instanceof UnknownStatus) {
            status = OcspCertStatus.issuerUnknown;
        } else {
            issue.setFailureMessage("unknown certstatus: " + singleCertStatus.getClass().getName());
        }

        if (!issue.isFailed() && expectedStatus != status) {
            issue.setFailureMessage("is='" + status + "', but expected='" + expectedStatus + "'");
        }

        // nextUpdate
        Date nextUpdate = singleResp.getNextUpdate();
        checkOccurrence("OCSP.RESPONSE." + index + ".NEXTUPDATE", nextUpdate, nextupdateOccurrence);

        Extension extension = singleResp.getExtension(
                ISISMTTObjectIdentifiers.id_isismtt_at_certHash);
        checkOccurrence("OCSP.RESPONSE." + index + ".CERTHASH", extension, certhashOccurrence);

        if (extension != null) {
            ASN1Encodable extensionValue = extension.getParsedValue();
            CertHash certHash = CertHash.getInstance(extensionValue);
            ASN1ObjectIdentifier hashAlgOid = certHash.getHashAlgorithm().getAlgorithm();
            if (certhashAlg != null) {
                // certHash algorithm
                issue = new ValidationIssue(
                        "OCSP.RESPONSE." + index + ".CHASH.ALG", "certhash algorithm");
                issues.add(issue);

                ASN1ObjectIdentifier is = certHash.getHashAlgorithm().getAlgorithm();
                if (!certhashAlg.equals(is)) {
                    issue.setFailureMessage("is '" + is.getId()
                        + "', but expected '" + certhashAlg.getId() + "'");
                }
            }

            byte[] hashValue = certHash.getCertificateHash();
            if (encodedCert != null) {
                issue = new ValidationIssue(
                        "OCSP.RESPONSE." + index + ".CHASH.VALIDITY", "certhash validity");
                issues.add(issue);

                try {
                    MessageDigest md = MessageDigest.getInstance(hashAlgOid.getId());
                    byte[] expectedHashValue = md.digest(encodedCert);
                    if (!Arrays.equals(expectedHashValue, hashValue)) {
                        issue.setFailureMessage(
                                "certhash does not match the requested certificate");
                    }
                } catch (NoSuchAlgorithmException ex) {
                    issue.setFailureMessage("NoSuchAlgorithm " + hashAlgOid.getId());
                }
            } // end if(encodedCert != null)
        } // end if (extension != null)

        return issues;
    } // method checkSingleCert

    private static ValidationIssue checkOccurrence(final String targetName, final Object target,
            final Occurrence occurrence) {
        ValidationIssue issue = new ValidationIssue("OCSP." + targetName, targetName);
        if (occurrence == Occurrence.forbidden) {
            if (target != null) {
                issue.setFailureMessage(" is present, but none is expected");
            }
        } else if (occurrence == Occurrence.required) {
            if (target == null) {
                issue.setFailureMessage(" is absent, but it is expected");
            }
        }
        return issue;
    }

}
