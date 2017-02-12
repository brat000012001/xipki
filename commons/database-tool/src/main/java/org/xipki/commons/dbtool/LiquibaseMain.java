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

package org.xipki.commons.dbtool;

import java.util.Objects;

import liquibase.CatalogAndSchema;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.diff.compare.CompareControl;
import liquibase.diff.output.DiffOutputControl;
import liquibase.exception.CommandLineParsingException;
import liquibase.exception.DatabaseException;
import liquibase.integration.commandline.CommandLineResourceAccessor;
import liquibase.integration.commandline.CommandLineUtils;
import liquibase.lockservice.LockService;
import liquibase.lockservice.LockServiceFactory;
import liquibase.logging.LogFactory;
import liquibase.logging.Logger;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;

/**
 * Class for executing Liquibase via the command line.
 *
 * @author Lijun Liao
 * @since 2.0.0
 */

public class LiquibaseMain {

    private final LiquibaseDatabaseConf dbConf;

    private final String changeLogFile;

    private Database database;

    private Liquibase liquibase;

    public LiquibaseMain(final LiquibaseDatabaseConf dbConf, final String changeLogFile) {
        Objects.requireNonNull(dbConf, "dbConf must not be null");
        if (changeLogFile == null || changeLogFile.isEmpty()) {
            throw new IllegalArgumentException("changeLogFile must not be empty");
        }

        this.dbConf = dbConf;
        this.changeLogFile = changeLogFile;
    }

    public void changeLogLevel(final String logLevel, String logFile)
    throws CommandLineParsingException {
        Objects.requireNonNull(logLevel, "logLevel must not be null");
        try {
            Logger log = LogFactory.getInstance().getLog();
            if (logFile != null && logFile.length() > 0) {
                log.setLogLevel(logLevel, logFile);
            } else {
                log.setLogLevel(logLevel);
            }
        } catch (IllegalArgumentException ex) {
            throw new CommandLineParsingException(ex.getMessage(), ex);
        }
    }

    public void init(final String logLevel, String logFile) throws Exception {
        changeLogLevel(logLevel, logFile);

        FileSystemResourceAccessor fsOpener = new FileSystemResourceAccessor();
        ClassLoader classLoader = getClass().getClassLoader();
        ResourceAccessor clOpener = new CommandLineResourceAccessor(classLoader);

        String defaultSchemaName = dbConf.getSchema();
        this.database = CommandLineUtils.createDatabaseObject(
            clOpener, // resourceAccessor
            dbConf.getUrl(), // url
            dbConf.getUsername(), // username
            dbConf.getPassword(), // password
            dbConf.getDriver(), // driver
            (String) null, // defaultCatalogName
            defaultSchemaName, // defaultSchemaName
            false, // outputDefaultCatalog
            false, // outputDefaultSchema
            (String) null, // databaseClass
            (String) null, // driverPropertiesFile
            (String) null, // propertyProviderClass
            (String) null, // liquibaseCatalogName
            (String) null, // liquibaseSchemaName
            (String) null, // databaseChangeLogTableName
            (String) null); // databaseChangeLogLockTableName
        try {
            CompositeResourceAccessor fileOpener = new CompositeResourceAccessor(fsOpener,
                    clOpener);

            DiffOutputControl diffOutputControl = new DiffOutputControl(false, // includeCatalog
                    false, // includeSchema
                    false, // includeTablespace
                    null); // schemaComparisons

            CompareControl.SchemaComparison[] finalSchemaComparisons;
            finalSchemaComparisons = new CompareControl.SchemaComparison[] {
                new CompareControl.SchemaComparison(new CatalogAndSchema(null, defaultSchemaName),
                        new CatalogAndSchema(null, defaultSchemaName))};

            for (CompareControl.SchemaComparison schema : finalSchemaComparisons) {
                diffOutputControl.addIncludedSchema(schema.getReferenceSchema());
                diffOutputControl.addIncludedSchema(schema.getComparisonSchema());
            }

            this.liquibase = new Liquibase(changeLogFile, fileOpener, database);
        } catch (Exception ex) {
            try {
                database.rollback();
                database.close();
            } catch (Exception e2) {
                LogFactory.getInstance().getLog().warning("problem closing connection", ex);
            }
            throw ex;
        }
    } // method init

    public void releaseLocks() throws Exception {
        LockService lockService = LockServiceFactory.getInstance().getLockService(database);
        lockService.forceReleaseLock();
        System.out.println("successfully released the database");
    }

    public void dropAll() throws Exception {
        liquibase.dropAll();
        System.out.println("successfully  dropped the database");
    }

    public void update() throws Exception {
        liquibase.update((String) null);
        System.out.println("successfully  updated the database");
    }

    public void shutdown() {
        try {
            if (database != null) {
                database.rollback();
                database.close();
            }
        } catch (DatabaseException ex) {
            LogFactory.getInstance().getLog().warning("problem closing connection", ex);
        } finally {
            database = null;
            liquibase = null;
        }
    }

    public static boolean loglevelIsSevereOrOff(final String logLevel) {
        return "off".equalsIgnoreCase(logLevel) || "severe".equalsIgnoreCase(logLevel);
    }

}
