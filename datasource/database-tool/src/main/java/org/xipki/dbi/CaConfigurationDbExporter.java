/*
 * Copyright (c) 2014 Lijun Liao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 *
 */

package org.xipki.dbi;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;

import org.xipki.database.api.DataSource;
import org.xipki.dbi.ca.jaxb.CAConfigurationType;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.CaHasCertprofiles;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.CaHasPublishers;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.CaHasRequestors;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Caaliases;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Cas;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Certprofiles;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Crlsigners;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Environments;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Publishers;
import org.xipki.dbi.ca.jaxb.CAConfigurationType.Requestors;
import org.xipki.dbi.ca.jaxb.CaHasCertprofileType;
import org.xipki.dbi.ca.jaxb.CaHasPublisherType;
import org.xipki.dbi.ca.jaxb.CaHasRequestorType;
import org.xipki.dbi.ca.jaxb.CaType;
import org.xipki.dbi.ca.jaxb.CaaliasType;
import org.xipki.dbi.ca.jaxb.CertprofileType;
import org.xipki.dbi.ca.jaxb.CmpcontrolType;
import org.xipki.dbi.ca.jaxb.CrlsignerType;
import org.xipki.dbi.ca.jaxb.EnvironmentType;
import org.xipki.dbi.ca.jaxb.ObjectFactory;
import org.xipki.dbi.ca.jaxb.PublisherType;
import org.xipki.dbi.ca.jaxb.RequestorType;
import org.xipki.dbi.ca.jaxb.ResponderType;
import org.xipki.security.api.PasswordResolverException;
import org.xipki.security.common.ParamChecker;

/**
 * @author Lijun Liao
 */

class CaConfigurationDbExporter extends DbPorter
{
    private final Marshaller marshaller;

    CaConfigurationDbExporter(DataSource dataSource, Marshaller marshaller, String destDir)
    throws SQLException, PasswordResolverException, IOException
    {
        super(dataSource, destDir);
        ParamChecker.assertNotNull("marshaller", marshaller);
        this.marshaller = marshaller;
    }

    public void export()
    throws Exception
    {
        CAConfigurationType caconf = new CAConfigurationType();
        caconf.setVersion(VERSION);

        System.out.println("Exporting CA configuration from database");

        export_cmpcontrol(caconf);
        export_responder(caconf);
        export_environment(caconf);
        export_crlsigner(caconf);
        export_requestor(caconf);
        export_publisher(caconf);
        export_ca(caconf);
        export_certprofile(caconf);
        export_caalias(caconf);
        export_ca_has_requestor(caconf);
        export_ca_has_publisher(caconf);
        export_ca_has_certprofile(caconf);

        JAXBElement<CAConfigurationType> root = new ObjectFactory().createCAConfiguration(caconf);
        marshaller.marshal(root, new File(baseDir + File.separator + FILENAME_CA_Configuration));

        System.out.println(" Exported CA configuration from database");
    }

    private void export_cmpcontrol(CAConfigurationType caconf)
    throws SQLException
    {
        CmpcontrolType cmpcontrol = null;
        System.out.println("Exporting table CMPCONTROL");
        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT REQUIRE_CONFIRM_CERT, SEND_CA_CERT, SEND_RESPONDER_CERT, "
                    + " REQUIRE_MESSAGE_TIME, MESSAGE_TIME_BIAS, CONFIRM_WAIT_TIME"
                    + " FROM CMPCONTROL";
            rs = stmt.executeQuery(sql);

            if(rs.next())
            {
                boolean requireConfirmCert = rs.getBoolean("REQUIRE_CONFIRM_CERT");
                boolean sendCaCert = rs.getBoolean("SEND_CA_CERT");
                boolean sendResponderCert = rs.getBoolean("SEND_RESPONDER_CERT");
                boolean requireMessageTime = rs.getBoolean("REQUIRE_MESSAGE_TIME");
                int messageTimeBias = rs.getInt("MESSAGE_TIME_BIAS");
                int confirmWaitTime = rs.getInt("CONFIRM_WAIT_TIME");

                cmpcontrol = new CmpcontrolType();
                cmpcontrol.setRequireConfirmCert(requireConfirmCert);
                cmpcontrol.setSendCaCert(sendCaCert);
                cmpcontrol.setSendResponderCert(sendResponderCert);
                cmpcontrol.setRequireMessageTime(requireMessageTime);
                cmpcontrol.setMessageTimeBias(messageTimeBias);
                cmpcontrol.setConfirmWaitTime(confirmWaitTime);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        caconf.setCmpcontrol(cmpcontrol);
        System.out.println(" Exported table CMPCONTROL");
    }

    private void export_environment(CAConfigurationType caconf)
    throws SQLException
    {
        System.out.println("Exporting table ENVIRONMENT");
        Environments environments = new Environments();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();

            String sql = "SELECT NAME, VALUE2 FROM ENVIRONMENT";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String name = rs.getString("NAME");
                String value = rs.getString("VALUE2");

                EnvironmentType environment = new EnvironmentType();
                environment.setName(name);
                environment.setValue(value);
                environments.getEnvironment().add(environment);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        caconf.setEnvironments(environments);
        System.out.println(" Exported table ENVIRONMENT");
    }

    private void export_crlsigner(CAConfigurationType caconf)
    throws SQLException
    {
        System.out.println("Exporting table CRLSIGNER");
        Crlsigners crlsigners = new Crlsigners();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();

            String sql = "SELECT NAME, SIGNER_TYPE, SIGNER_CONF, SIGNER_CERT, PERIOD," +
                    " OVERLAP, INCLUDE_CERTS_IN_CRL, INCLUDE_EXPIRED_CERTS" +
                    " FROM CRLSIGNER";

            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String name = rs.getString("NAME");
                String signer_type = rs.getString("SIGNER_TYPE");
                String signer_conf = rs.getString("SIGNER_CONF");
                String signer_cert = rs.getString("SIGNER_CERT");
                int period = rs.getInt("PERIOD");
                int overlap = rs.getInt("OVERLAP");
                boolean include_certs_in_crl = rs.getBoolean("INCLUDE_CERTS_IN_CRL");

                CrlsignerType crlsigner = new CrlsignerType();
                crlsigner.setName(name);
                crlsigner.setSignerType(signer_type);
                crlsigner.setSignerConf(signer_conf);
                crlsigner.setSignerCert(signer_cert);
                crlsigner.setPeriod(period);
                crlsigner.setOverlap(overlap);
                crlsigner.setIncludeCertsInCrl(include_certs_in_crl);

                boolean includeExpiredCerts = rs.getBoolean("INCLUDE_EXPIRED_CERTS");
                crlsigner.setIncludeExpiredCerts(includeExpiredCerts);

                crlsigners.getCrlsigner().add(crlsigner);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        caconf.setCrlsigners(crlsigners);
        System.out.println(" Exported table CRLSIGNER");
    }

    private void export_caalias(CAConfigurationType caconf)
    throws SQLException
    {
        System.out.println("Exporting table CAALIAS");
        Caaliases caaliases = new Caaliases();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT NAME, CA_NAME FROM CAALIAS";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String name = rs.getString("NAME");
                String caName = rs.getString("CA_NAME");

                CaaliasType caalias = new CaaliasType();
                caalias.setName(name);
                caalias.setCaName(caName);

                caaliases.getCaalias().add(caalias);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        caconf.setCaaliases(caaliases);
        System.out.println(" Exported table CAALIAS");
    }

    private void export_requestor(CAConfigurationType caconf)
    throws SQLException
    {
        System.out.println("Exporting table REQUESTOR");
        Requestors requestors = new Requestors();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT NAME, CERT FROM REQUESTOR";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String name = rs.getString("NAME");
                String cert = rs.getString("CERT");

                RequestorType requestor = new RequestorType();
                requestor.setName(name);
                requestor.setCert(cert);

                requestors.getRequestor().add(requestor);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        caconf.setRequestors(requestors);
        System.out.println(" Exported table REQUESTOR");
    }

    private void export_responder(CAConfigurationType caconf)
    throws SQLException
    {
        System.out.println("Exporting table RESPONDER");
        ResponderType responder = null;

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT TYPE, CERT, CONF FROM RESPONDER";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String type = rs.getString("TYPE");
                String conf = rs.getString("CONF");
                String cert = rs.getString("CERT");

                responder = new ResponderType();
                responder.setType(type);
                responder.setConf(conf);
                responder.setCert(cert);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        caconf.setResponder(responder);
        System.out.println(" Exported table RESPONDER");
    }

    private void export_publisher(CAConfigurationType caconf)
    throws SQLException
    {
        System.out.println("Exporting table PUBLISHER");
        Publishers publishers = new Publishers();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT NAME, TYPE, CONF FROM PUBLISHER";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String name = rs.getString("NAME");
                String type = rs.getString("TYPE");
                String conf = rs.getString("CONF");

                PublisherType publisher = new PublisherType();
                publisher.setName(name);
                publisher.setType(type);
                publisher.setConf(conf);

                publishers.getPublisher().add(publisher);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        caconf.setPublishers(publishers);
        System.out.println(" Exported table PUBLISHER");
    }

    private void export_certprofile(CAConfigurationType caconf)
    throws SQLException
    {
        System.out.println("Exporting table CERTPROFILE");
        Certprofiles certprofiles = new Certprofiles();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT NAME, TYPE, CONF FROM CERTPROFILE";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String name = rs.getString("NAME");
                String type = rs.getString("TYPE");
                String conf = rs.getString("CONF");

                CertprofileType certprofile = new CertprofileType();
                certprofile.setName(name);
                certprofile.setType(type);
                certprofile.setConf(conf);

                certprofiles.getCertprofile().add(certprofile);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        caconf.setCertprofiles(certprofiles);
        System.out.println(" Exported table CERTPROFILE");
    }

    private void export_ca(CAConfigurationType caconf)
    throws SQLException
    {
        System.out.println("Exporting table CA");
        Cas cas = new Cas();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();

            String sql = "SELECT NAME, NEXT_SERIAL, STATUS, CRL_URIS, OCSP_URIS, MAX_VALIDITY, "
                    + "CERT, SIGNER_TYPE, SIGNER_CONF, CRLSIGNER_NAME, "
                    + "DUPLICATE_KEY_MODE, DUPLICATE_SUBJECT_MODE, PERMISSIONS, NUM_CRLS, "
                    + "EXPIRATION_PERIOD, REVOKED, REV_REASON, REV_TIME, REV_INVALIDITY_TIME "
                    + "FROM CA";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String name = rs.getString("NAME");
                long next_serial = rs.getLong("NEXT_SERIAL");
                String status = rs.getString("STATUS");
                String crl_uris = rs.getString("CRL_URIS");
                String ocsp_uris = rs.getString("OCSP_URIS");
                int max_validity = rs.getInt("MAX_VALIDITY");
                String cert = rs.getString("CERT");
                String signer_type = rs.getString("SIGNER_TYPE");
                String signer_conf = rs.getString("SIGNER_CONF");
                String crlsigner_name = rs.getString("CRLSIGNER_NAME");
                int duplicateKeyMode = rs.getInt("DUPLICATE_KEY_MODE");
                int duplicateSubjectMode = rs.getInt("DUPLICATE_SUBJECT_MODE");

                String permissions = rs.getString("PERMISSIONS");
                int expirationPeriod = rs.getInt("EXPIRATION_PERIOD");

                CaType ca = new CaType();
                ca.setName(name);
                ca.setNextSerial(next_serial);
                ca.setStatus(status);
                ca.setCrlUris(crl_uris);
                ca.setOcspUris(ocsp_uris);
                ca.setMaxValidity(max_validity);
                ca.setCert(cert);
                ca.setSignerType(signer_type);
                ca.setSignerConf(signer_conf);
                ca.setCrlsignerName(crlsigner_name);
                ca.setDuplicateKeyMode(duplicateKeyMode);
                ca.setDuplicateSubjectMode(duplicateSubjectMode);
                ca.setPermissions(permissions);
                ca.setExpirationPeriod(expirationPeriod);

                int numCrls = rs.getInt("num_crls");
                ca.setNumCrls(numCrls);

                boolean revoked = rs.getBoolean("REVOKED");
                ca.setRevoked(revoked);
                if(revoked)
                {
                    int reason = rs.getInt("REV_REASON");
                    long rev_time = rs.getLong("REV_TIME");
                    long rev_invalidity_time = rs.getLong("REV_INVALIDITY_TIME");
                    ca.setRevReason(reason);
                    ca.setRevTime(rev_time);
                    ca.setRevInvalidityTime(rev_invalidity_time);
                }

                cas.getCa().add(ca);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        caconf.setCas(cas);
        System.out.println(" Exported table CA");
    }

    private void export_ca_has_requestor(CAConfigurationType caconf)
    throws SQLException
    {
        System.out.println("Exporting table CA_HAS_REQUESTOR");
        CaHasRequestors ca_has_requestors = new CaHasRequestors();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();

            String sql = "SELECT CA_NAME, REQUESTOR_NAME, RA, PERMISSIONS, PROFILES FROM CA_HAS_REQUESTOR";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String ca_name = rs.getString("CA_NAME");
                String requestor_name = rs.getString("REQUESTOR_NAME");
                boolean ra = rs.getBoolean("RA");
                String permissions = rs.getString("PERMISSIONS");
                String profiles = rs.getString("PROFILES");

                CaHasRequestorType ca_has_requestor = new CaHasRequestorType();
                ca_has_requestor.setCaName(ca_name);
                ca_has_requestor.setRequestorName(requestor_name);
                ca_has_requestor.setRa(ra);
                ca_has_requestor.setPermissions(permissions);
                ca_has_requestor.setProfiles(profiles);

                ca_has_requestors.getCaHasRequestor().add(ca_has_requestor);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        caconf.setCaHasRequestors(ca_has_requestors);
        System.out.println(" Exported table CA_HAS_REQUESTOR");
    }

    private void export_ca_has_publisher(CAConfigurationType caconf)
    throws SQLException
    {
        System.out.println("Exporting table CA_HAS_PUBLISHER");
        CaHasPublishers ca_has_publishers = new CaHasPublishers();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();

            String sql = "SELECT CA_NAME, PUBLISHER_NAME FROM CA_HAS_PUBLISHER";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String ca_name = rs.getString("CA_NAME");
                String publisher_name = rs.getString("PUBLISHER_NAME");

                CaHasPublisherType ca_has_publisher = new CaHasPublisherType();
                ca_has_publisher.setCaName(ca_name);
                ca_has_publisher.setPublisherName(publisher_name);;

                ca_has_publishers.getCaHasPublisher().add(ca_has_publisher);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        caconf.setCaHasPublishers(ca_has_publishers);
        System.out.println(" Exported table CA_HAS_PUBLISHER");
    }

    private void export_ca_has_certprofile(CAConfigurationType caconf)
    throws SQLException
    {
        System.out.println("Exporting table CA_HAS_CERTPROFILE");
        CaHasCertprofiles ca_has_certprofiles = new CaHasCertprofiles();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();

            String sql = "SELECT CA_NAME, CERTPROFILE_NAME FROM CA_HAS_CERTPROFILE";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                String ca_name = rs.getString("CA_NAME");
                String certprofile_name = rs.getString("CERTPROFILE_NAME");

                CaHasCertprofileType ca_has_certprofile = new CaHasCertprofileType();
                ca_has_certprofile.setCaName(ca_name);
                ca_has_certprofile.setCertprofileName(certprofile_name);

                ca_has_certprofiles.getCaHasCertprofile().add(ca_has_certprofile);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        caconf.setCaHasCertprofiles(ca_has_certprofiles);
        System.out.println(" Exported table CA_HAS_CERTPROFILE");
    }

}
