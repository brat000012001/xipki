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

package org.xipki.commons.datasource;

import java.io.PrintWriter;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.common.LruCache;
import org.xipki.commons.common.util.LogUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.common.util.StringUtil;
import org.xipki.commons.datasource.internal.SqlErrorCodes;
import org.xipki.commons.datasource.internal.SqlStateCodes;
import org.xipki.commons.datasource.springframework.dao.CannotAcquireLockException;
import org.xipki.commons.datasource.springframework.dao.CannotSerializeTransactionException;
import org.xipki.commons.datasource.springframework.dao.ConcurrencyFailureException;
import org.xipki.commons.datasource.springframework.dao.DataAccessException;
import org.xipki.commons.datasource.springframework.dao.DataAccessResourceFailureException;
import org.xipki.commons.datasource.springframework.dao.DataIntegrityViolationException;
import org.xipki.commons.datasource.springframework.dao.DeadlockLoserDataAccessException;
import org.xipki.commons.datasource.springframework.dao.PermissionDeniedDataAccessException;
import org.xipki.commons.datasource.springframework.dao.QueryTimeoutException;
import org.xipki.commons.datasource.springframework.dao.TransientDataAccessResourceException;
import org.xipki.commons.datasource.springframework.jdbc.BadSqlGrammarException;
import org.xipki.commons.datasource.springframework.jdbc.DuplicateKeyException;
import org.xipki.commons.datasource.springframework.jdbc.InvalidResultSetAccessException;
import org.xipki.commons.datasource.springframework.jdbc.UncategorizedSqlException;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public abstract class DataSourceWrapper {

    // CHECKSTYLE:SKIP
    private static class MySQL extends DataSourceWrapper {

        MySQL(final String name, final HikariDataSource service) {
            super(name, service, DatabaseType.MYSQL);
        }

        MySQL(final String name, final HikariDataSource service, final DatabaseType type) {
            super(name, service, type);
        }

        @Override
        public String buildSelectFirstSql(final int rows, final String orderBy,
                final String coreSql) {
            // 'SELECT ': 7
            // ' LIMIT ': 7
            // rows (till 9999): 4
            int size = coreSql.length() + 18;
            if (StringUtil.isNotBlank(orderBy)) {
                // ' ORDER BY ': 10
                size += 10;
                size += orderBy.length();
            }
            StringBuilder sql = new StringBuilder(size);
            sql.append("SELECT ").append(coreSql);
            if (StringUtil.isNotBlank(orderBy)) {
                sql.append(" ORDER BY ").append(orderBy);
            }
            return sql.append(" LIMIT ").append(rows).toString();
        }

        @Override
        protected String buildCreateSequenceSql(final String sequenceName, final long startValue) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 75);
            sql.append("INSERT INTO SEQ_TBL (SEQ_NAME,SEQ_VALUE) VALUES('");
            return sql.append(sequenceName).append("', ").append(startValue).append(")").toString();
        }

        @Override
        protected String buildDropSequenceSql(final String sequenceName) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 40);
            sql.append("DELETE FROM SEQ_TBL WHERE SEQ_NAME='").append(sequenceName).append("'");
            return sql.toString();
        }

        @Override
        protected String buildNextSeqValueSql(final String sequenceName) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 75);
            sql.append("UPDATE SEQ_TBL SET SEQ_VALUE=(@cur_value:=SEQ_VALUE)+1 WHERE SEQ_NAME='");
            return sql.append(sequenceName).append("'").toString();
        }

        @Override
        public long nextSeqValue(final Connection conn, final String sequenceName)
            throws DataAccessException {
            final String sqlUpdate = buildAndCacheNextSeqValueSql(sequenceName);
            final String sqlSelect = "SELECT @cur_value";
            String sql = null;

            boolean newConn = (conn == null);
            Connection tmpConn = (conn != null) ? conn : getConnection();

            Statement stmt = null;
            ResultSet rs = null;

            long ret;
            try {
                stmt = tmpConn.createStatement();
                sql = sqlUpdate;
                stmt.executeUpdate(sql);

                sql = sqlSelect;
                rs = stmt.executeQuery(sql);
                if (rs.next()) {
                    ret = rs.getLong(1);
                } else {
                    throw new DataAccessException(
                            "could not increment the sequence " + sequenceName);
                }
            } catch (SQLException ex) {
                throw translate(sqlUpdate, ex);
            } finally {
                if (newConn) {
                    releaseResources(stmt, rs);
                } else {
                    super.releaseStatementAndResultSet(stmt, rs);
                }
            }

            LOG.debug("datasource {} NEXVALUE({}): {}", name, sequenceName, ret);
            return ret;
        } // method nextSeqValue

        @Override
        protected String getSqlToDropForeignKeyConstraint(final String constraintName,
                final String baseTable) throws DataAccessException {
            StringBuilder sb = new StringBuilder(baseTable.length() + constraintName.length() + 30);
            return sb.append("ALTER TABLE ").append(baseTable).append(" DROP FOREIGN KEY ")
                    .append(constraintName).toString();
        }

        @Override
        protected String getSqlToDropIndex(final String table, final String indexName) {
            StringBuilder sb = new StringBuilder(indexName.length() + table.length() + 15);
            return sb.append("DROP INDEX ").append(indexName).append(" ON ").append(table)
                    .toString();
        }

        @Override
        protected String getSqlToDropUniqueConstraint(final String constraintName,
                final String table) {
            StringBuilder sb = new StringBuilder(constraintName.length() + table.length() + 22);
            return sb.append("ALTER TABLE ").append(table).append(" DROP KEY ")
                    .append(constraintName).toString();
        }

    } // class MySQL

    // CHECKSTYLE:SKIP
    private static class MariaDB extends MySQL {

        MariaDB(String name, HikariDataSource service) {
            super(name, service, DatabaseType.MARIADB);
        }

    }

    // CHECKSTYLE:SKIP
    private static class DB2 extends DataSourceWrapper {

        DB2(final String name, final HikariDataSource service) {
            super(name, service, DatabaseType.DB2);
        }

        @Override
        public String buildSelectFirstSql(final int rows, final String orderBy,
                final String coreSql) {
            // 'SELECT ': 7
            // ' FETCH FIRST ': 15
            // ' ROWS ONLY' : 10
            // rows (till 9999): 4
            int size = coreSql.length() + 36;
            if (StringUtil.isNotBlank(orderBy)) {
                // ' ORDER BY ': 10
                size += 10;
                size += orderBy.length();
            }
            StringBuilder sql = new StringBuilder(size);

            sql.append("SELECT ").append(coreSql);

            if (StringUtil.isNotBlank(orderBy)) {
                sql.append(" ORDER BY ").append(orderBy);
            }

            return sql.append(" FETCH FIRST ").append(rows).append(" ROWS ONLY").toString();
        }

        @Override
        protected String buildCreateSequenceSql(final String sequenceName, final long startValue) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 80);
            sql.append("CREATE SEQUENCE ").append(sequenceName).append(" AS BIGINT START WITH ");
            return sql.append(startValue).append(" INCREMENT BY 1 NO CYCLE NO CACHE").toString();
        }

        @Override
        protected String buildDropSequenceSql(final String sequenceName) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 14);
            return sql.append("DROP SEQUENCE ").append(sequenceName).toString();
        }

        @Override
        protected String buildNextSeqValueSql(final String sequenceName) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 44);
            sql.append("SELECT NEXT VALUE FOR ").append(sequenceName)
                .append(" FROM sysibm.sysdummy1");
            return sql.toString();
        }

    } // class DB2

    // CHECKSTYLE:SKIP
    private static class PostgreSQL extends DataSourceWrapper {

        PostgreSQL(final String name, final HikariDataSource service) {
            super(name, service, DatabaseType.POSTGRES);
        }

        @Override
        public String buildSelectFirstSql(final int rows, final String orderBy,
                final String coreSql) {
            // 'SELECT ': 7
            // ' FETCH FIRST ': 13
            // ' ROWS ONLY': 10
            // rows (till 9999): 4
            int size = coreSql.length() + 34;
            if (StringUtil.isNotBlank(orderBy)) {
                // ' ORDER BY ': 10
                size += 10;
                size += orderBy.length();
            }
            StringBuilder sql = new StringBuilder(size);

            sql.append("SELECT ").append(coreSql);

            if (StringUtil.isNotBlank(orderBy)) {
                sql.append(" ORDER BY ").append(orderBy);
            }

            return sql.append(" FETCH FIRST ").append(rows).append(" ROWS ONLY").toString();
        }

        @Override
        protected String buildCreateSequenceSql(final String sequenceName, final long startValue) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 70);
            sql.append("CREATE SEQUENCE ").append(sequenceName).append(" START WITH ");
            return sql.append(startValue).append(" INCREMENT BY 1 NO CYCLE").toString();
        }

        @Override
        protected String buildDropSequenceSql(final String sequenceName) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 14);
            return sql.append("DROP SEQUENCE ").append(sequenceName).toString();
        }

        @Override
        protected String buildNextSeqValueSql(final String sequenceName) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 20);
            return sql.append("SELECT NEXTVAL ('").append(sequenceName).append("')").toString();
        }

        @Override
        protected boolean isUseSqlStateAsCode() {
            return true;
        }

        @Override
        protected String getSqlToDropPrimaryKey(final String primaryKeyName, final String table) {
            StringBuilder sb = new StringBuilder(500);
            sb.append("DO $$ DECLARE constraint_name varchar;\n");
            sb.append("BEGIN\n");
            sb.append("  SELECT tc.CONSTRAINT_NAME into strict constraint_name\n");
            sb.append("  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc\n");
            sb.append("  WHERE CONSTRAINT_TYPE='PRIMARY KEY'\n");
            sb.append("  AND TABLE_NAME='").append(table.toLowerCase())
                    .append("' AND TABLE_SCHEMA='public';\n");
            sb.append("  EXECUTE 'alter table public.").append(table.toLowerCase())
                    .append(" drop constraint ' || constraint_name;\n");
            sb.append("END $$;");
            return sb.toString();
        }

    } // class PostgreSQL

    private static class Oracle extends DataSourceWrapper {

        Oracle(final String name, final HikariDataSource service) {
            super(name, service, DatabaseType.ORACLE);
        }

        /*
         * Oracle: http://www.oracle.com/technetwork/issue-archive/2006/06-sep/o56asktom-086197.html
         *
         */
        @Override
        public String buildSelectFirstSql(final int rows, final String orderBy,
                final String coreSql) {
            int size = coreSql.length() + 18;
            size += StringUtil.isBlank(orderBy) ? 14 : orderBy.length() + 40;

            // ' ROWNUM < ': 10
            // rows (till 9999): 4
            size += 14;
            StringBuilder sql = new StringBuilder(size);

            if (StringUtil.isBlank(orderBy)) {
                sql.append("SELECT ").append(coreSql);
                if (coreSql.contains(" WHERE")) {
                    sql.append(" AND");
                } else {
                    sql.append(" WHERE");
                }
            } else {
                sql.append("SELECT * FROM (SELECT ");
                sql.append(coreSql);
                sql.append(" ORDER BY ").append(orderBy).append(" ) WHERE");
            }

            return sql.append(" ROWNUM<").append(rows + 1).toString();
        }

        @Override
        protected String buildCreateSequenceSql(final String sequenceName, final long startValue) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 59);
            sql.append("CREATE SEQUENCE ").append(sequenceName);
            sql.append(" START WITH ").append(startValue);
            return sql.append(" INCREMENT BY 1 NOCYCLE NOCACHE").toString();
        }

        @Override
        protected String buildDropSequenceSql(final String sequenceName) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 14);
            return sql.append("DROP SEQUENCE ").append(sequenceName).toString();
        }

        @Override
        protected String buildNextSeqValueSql(final String sequenceName) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 21);
            sql.append("SELECT ").append(sequenceName).append(".NEXTVAL FROM DUAL");
            return sql.toString();
        }

        @Override
        protected String getSqlToDropPrimaryKey(final String primaryKeyName, final String table) {
            return getSqlToDropUniqueConstraint(primaryKeyName, table);
        }

        @Override
        protected String getSqlToDropUniqueConstraint(final String contraintName,
                final String table) {
            StringBuilder sql = new StringBuilder(table.length() + contraintName.length() + 40);
            return sql.append("ALTER TABLE ").append(table)
                    .append(" DROP CONSTRAINT ").append(contraintName)
                    .append(" DROP INDEX").toString();
        }

        @Override
        protected String getSqlToAddForeignKeyConstraint(final String constraintName,
                final String baseTable, final String baseColumn, final String referencedTable,
                final String referencedColumn, final String onDeleteAction,
                final String onUpdateAction) {
            final StringBuilder sb = new StringBuilder(100);
            sb.append("ALTER TABLE ").append(baseTable);
            sb.append(" ADD CONSTRAINT ").append(constraintName);
            sb.append(" FOREIGN KEY (").append(baseColumn).append(")");
            sb.append(" REFERENCES ").append(referencedTable);
            sb.append(" (").append(referencedColumn).append(")");
            return sb.append(" ON DELETE ").append(onDeleteAction).toString();
        }

        @Override
        protected String getSqlToAddPrimaryKey(final String primaryKeyName, final String table,
                final String... columns) {
            StringBuilder sb = new StringBuilder(100);
            sb.append("ALTER TABLE ").append(table);
            sb.append(" ADD CONSTRAINT ").append(primaryKeyName);
            sb.append(" PRIMARY KEY(");
            final int n = columns.length;
            for (int i = 0; i < n; i++) {
                if (i != 0) {
                    sb.append(",");
                }
                sb.append(columns[i]);
            }
            sb.append(")");
            return sb.toString();
        }

    } // class Oracle

    private static class H2 extends DataSourceWrapper {

        H2(final String name, final HikariDataSource service) {
            super(name, service, DatabaseType.H2);
        }

        @Override
        public String buildSelectFirstSql(final int rows, final String orderBy,
                final String coreSql) {
            // 'SELECT ': 7
            // ' LIMIT ': 7
            // rows (till 9999): 4
            int size = coreSql.length() + 18;
            if (StringUtil.isNotBlank(orderBy)) {
                // ' ORDER BY ': 10
                size += 10;
                size += orderBy.length();
            }
            StringBuilder sql = new StringBuilder(size);

            sql.append("SELECT ").append(coreSql);

            if (StringUtil.isNotBlank(orderBy)) {
                sql.append(" ORDER BY ").append(orderBy);
            }

            sql.append(" LIMIT ").append(rows);
            return sql.toString();
        }

        @Override
        protected String buildCreateSequenceSql(final String sequenceName, final long startValue) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 80);
            sql.append("CREATE SEQUENCE ").append(sequenceName);
            sql.append(" START WITH ").append(startValue);
            return sql.append(" INCREMENT BY 1 NO CYCLE NO CACHE").toString();
        }

        @Override
        protected String buildDropSequenceSql(final String sequenceName) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 14);
            return sql.append("DROP SEQUENCE ").append(sequenceName).toString();
        }

        @Override
        protected String buildNextSeqValueSql(final String sequenceName) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 20);
            return sql.append("SELECT NEXTVAL ('").append(sequenceName).append("')").toString();
        }

    } // class H2

    // CHECKSTYLE:SKIP
    private static class HSQL extends DataSourceWrapper {

        HSQL(final String name, final HikariDataSource service) {
            super(name, service, DatabaseType.HSQL);
        }

        @Override
        public String buildSelectFirstSql(final int rows, final String orderBy,
                final String coreSql) {
            // 'SELECT ': 7
            // ' LIMIT ': 7
            // rows (till 9999): 4
            int size = coreSql.length() + 18;
            if (StringUtil.isNotBlank(orderBy)) {
                // ' ORDER BY ': 10
                size += 10;
                size += orderBy.length();
            }
            StringBuilder sql = new StringBuilder(size);

            sql.append("SELECT ").append(coreSql);

            if (StringUtil.isNotBlank(orderBy)) {
                sql.append(" ORDER BY ").append(orderBy);
            }

            return sql.append(" LIMIT ").append(rows).toString();
        }

        @Override
        protected String buildCreateSequenceSql(final String sequenceName, final long startValue) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 70);
            sql.append("CREATE SEQUENCE ").append(sequenceName);
            sql.append(" AS BIGINT START WITH ").append(startValue);
            return sql.append(" INCREMENT BY 1").toString();
        }

        @Override
        protected String buildDropSequenceSql(final String sequenceName) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 14);
            return sql.append("DROP SEQUENCE ").append(sequenceName).toString();
        }

        @Override
        protected String buildNextSeqValueSql(final String sequenceName) {
            StringBuilder sql = new StringBuilder(sequenceName.length() + 20);
            return sql.append("SELECT NEXTVAL ('").append(sequenceName).append("')").toString();
        }

    } // class HSQL

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceWrapper.class);

    /**
     * References the real data source implementation this class acts as pure
     * proxy for. Derived classes must set this field at construction time.
     */
    protected final HikariDataSource service;

    protected final String name;

    private final Object lastUsedSeqValuesLock = new Object();

    private final ConcurrentHashMap<String, Long> lastUsedSeqValues
            = new ConcurrentHashMap<String, Long>();

    private final SqlErrorCodes sqlErrorCodes;

    private final SqlStateCodes sqlStateCodes;

    private final DatabaseType databaseType;

    private final LruCache<String, String> cacheSeqNameSqls;

    private DataSourceWrapper(final String name, final HikariDataSource service,
            final DatabaseType dbType) {
        this.service = ParamUtil.requireNonNull("service", service);
        this.databaseType = ParamUtil.requireNonNull("dbType", dbType);
        this.name = name;
        this.sqlErrorCodes = SqlErrorCodes.newInstance(dbType);
        this.sqlStateCodes = SqlStateCodes.newInstance(dbType);
        this.cacheSeqNameSqls = new LruCache<>(100);
    }

    public final String getDatasourceName() {
        return name;
    }

    public final DatabaseType getDatabaseType() {
        return this.databaseType;
    }

    public final int getMaximumPoolSize() {
        return service.getMaximumPoolSize();
    }

    public final Connection getConnection() throws DataAccessException {
        try {
            return service.getConnection();
        } catch (Exception ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof SQLException) {
                ex = (SQLException) cause;
            }
            LogUtil.error(LOG, ex, "could not create connection to database");
            if (ex instanceof SQLException) {
                throw translate(null, (SQLException) ex);
            } else {
                throw new DataAccessException("error occured while getting Connection: "
                        + ex.getMessage(), ex);
            }
        }
    }

    public void returnConnection(final Connection conn) {
        if (conn == null) {
            return;
        }

        try {
            conn.close();
        } catch (Exception ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof SQLException) {
                ex = (SQLException) cause;
            }
            LogUtil.error(LOG, ex, "could not close connection to database {}");
        }
    }

    public void shutdown() {
        try {
            service.close();
        } catch (Exception ex) {
            LOG.warn("could not shutdown datasource: {}", ex.getMessage());
            LOG.debug("could not close datasource", ex);
        }
    }

    public final PrintWriter getLogWriter() throws SQLException {
        return service.getLogWriter();
    }

    public Statement createStatement(final Connection conn) throws DataAccessException {
        ParamUtil.requireNonNull("conn", conn);
        try {
            return conn.createStatement();
        } catch (SQLException ex) {
            throw translate(null, ex);
        }
    }

    public PreparedStatement prepareStatement(final Connection conn, final String sqlQuery)
            throws DataAccessException {
        ParamUtil.requireNonNull("conn", conn);
        try {
            return conn.prepareStatement(sqlQuery);
        } catch (SQLException ex) {
            throw translate(sqlQuery, ex);
        }
    }

    public void releaseResources(final Statement ps, final ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Throwable th) {
                LOG.warn("could not close ResultSet", th);
            }
        }

        if (ps != null) {
            Connection conn = null;
            try {
                conn = ps.getConnection();
            } catch (SQLException ex) {
                LOG.error("could not get connection from statement: {}", ex.getMessage());
            }

            try {
                ps.close();
            } catch (Throwable th) {
                LOG.warn("could not close statement", th);
            } finally {
                if (conn != null) {
                    returnConnection(conn);
                }
            }
        }
    }

    private void releaseStatementAndResultSet(final Statement ps, final ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Throwable th) {
                LOG.warn("could not close ResultSet", th);
            }
        }

        if (ps != null) {
            try {
                ps.close();
            } catch (Throwable th) {
                LOG.warn("could not close statement", th);
            }
        }
    }

    public String buildSelectFirstSql(final int rows, final String coreSql) {
        return buildSelectFirstSql(rows, null, coreSql);
    }

    public abstract String buildSelectFirstSql(final int rows, final String orderBy,
            final String coreSql);

    public long getMin(final Connection conn, final String table, final String column)
            throws DataAccessException {
        return getMin(conn, table, column, null);
    }

    public long getMin(final Connection conn, final String table, final String column,
            final String condition) throws DataAccessException {
        ParamUtil.requireNonBlank("table", table);
        ParamUtil.requireNonBlank("column", column);

        int size = column.length() + table.length() + 20;
        if (StringUtil.isNotBlank(condition)) {
            size += 7 + condition.length();
        }

        StringBuilder sqlBuilder = new StringBuilder(size);
        sqlBuilder.append("SELECT MIN(").append(column).append(") FROM ").append(table);
        if (StringUtil.isNotBlank(condition)) {
            sqlBuilder.append(" WHERE ").append(condition);
        }

        final String sql = sqlBuilder.toString();

        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = (conn != null) ? conn.createStatement() : getConnection().createStatement();
            rs = stmt.executeQuery(sql);
            rs.next();
            return rs.getLong(1);
        } catch (SQLException ex) {
            throw translate(sql, ex);
        } finally {
            if (conn == null) {
                releaseResources(stmt, rs);
            } else {
                releaseStatementAndResultSet(stmt, rs);
            }
        }
    }

    public int getCount(final Connection conn, final String table) throws DataAccessException {
        ParamUtil.requireNonBlank("table", table);

        StringBuilder sqlBuilder = new StringBuilder(table.length() + 21);
        sqlBuilder.append("SELECT COUNT(*) FROM ").append(table);
        final String sql = sqlBuilder.toString();

        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = (conn != null) ? conn.createStatement() : getConnection().createStatement();
            rs = stmt.executeQuery(sql);
            rs.next();
            return rs.getInt(1);
        } catch (SQLException ex) {
            throw translate(sql, ex);
        } finally {
            if (conn == null) {
                releaseResources(stmt, rs);
            } else {
                releaseStatementAndResultSet(stmt, rs);
            }
        }
    }

    public long getMax(final Connection conn, final String table, final String column)
            throws DataAccessException {
        return getMax(conn, table, column, null);
    }

    public long getMax(final Connection conn, final String table, final String column,
            final String condition) throws DataAccessException {
        ParamUtil.requireNonBlank("table", table);
        ParamUtil.requireNonBlank("column", column);
        int size = column.length() + table.length() + 20;
        if (StringUtil.isNotBlank(condition)) {
            size += 7 + condition.length();
        }

        StringBuilder sqlBuilder = new StringBuilder(size);
        sqlBuilder.append("SELECT MAX(").append(column).append(") FROM ").append(table);
        if (StringUtil.isNotBlank(condition)) {
            sqlBuilder.append(" WHERE ").append(condition);
        }
        final String sql = sqlBuilder.toString();

        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = (conn != null) ? conn.createStatement() : getConnection().createStatement();
            rs = stmt.executeQuery(sql);
            rs.next();
            return rs.getLong(1);
        } catch (SQLException ex) {
            throw translate(sql, ex);
        } finally {
            if (conn == null) {
                releaseResources(stmt, rs);
            } else {
                releaseStatementAndResultSet(stmt, rs);
            }
        }
    }

    public boolean deleteFromTable(final Connection conn, final String table, final String idColumn,
            final long id) {
        ParamUtil.requireNonBlank("table", table);
        ParamUtil.requireNonBlank("idColumn", idColumn);
        final StringBuilder sb = new StringBuilder(table.length() + idColumn.length() + 35);
        sb.append("DELETE FROM ").append(table).append(" WHERE ")
            .append(idColumn).append("=").append(id);
        final String sql = sb.toString();

        Connection tmpConn;
        if (conn != null) {
            tmpConn = conn;
        } else {
            try {
                tmpConn = getConnection();
            } catch (Throwable th) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("datasource {} could not get connection: {}", name, th.getMessage());
                }
                return false;
            }
        }

        Statement stmt = null;
        try {
            stmt = tmpConn.createStatement();
            stmt.execute(sql);
        } catch (Throwable th) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("datasource {} could not deletefrom table {}: {}", name, table,
                        th.getMessage());
            }
            return false;
        } finally {
            if (conn == null) {
                releaseResources(stmt, null);
            } else {
                releaseStatementAndResultSet(stmt, null);
            }
        }

        return true;
    }

    public boolean columnExists(final Connection conn, final String table, final String column,
            final Object value) throws DataAccessException {
        ParamUtil.requireNonBlank("table", table);
        ParamUtil.requireNonBlank("column", column);
        ParamUtil.requireNonNull("value", value);

        StringBuilder sb = new StringBuilder(2 * column.length() + 15);
        sb.append(column).append(" FROM ").append(table);
        sb.append(" WHERE ").append(column).append("=?");
        String sql = buildSelectFirstSql(1, sb.toString());

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = (conn != null) ? conn.prepareStatement(sql)
                    : getConnection().prepareStatement(sql);
            if (value instanceof Integer) {
                stmt.setInt(1, (Integer) value);
            } else if (value instanceof Long) {
                stmt.setLong(1, (Long) value);
            } else if (value instanceof String) {
                stmt.setString(1, (String) value);
            } else {
                stmt.setString(1, value.toString());
            }
            rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException ex) {
            throw translate(sql, ex);
        } finally {
            if (conn == null) {
                releaseResources(stmt, rs);
            } else {
                releaseStatementAndResultSet(stmt, rs);
            }
        }
    } // method columnExists

    public boolean tableHasColumn(final Connection conn, final String table, final String column)
            throws DataAccessException {
        ParamUtil.requireNonBlank("table", table);
        ParamUtil.requireNonBlank("column", column);

        Statement stmt;
        try {
            stmt = (conn != null) ? conn.createStatement() : getConnection().createStatement();
        } catch (SQLException ex) {
            throw translate(null, ex);
        }

        StringBuilder sqlBuilder = new StringBuilder(column.length() + table.length() + 20);
        sqlBuilder.append(column).append(" FROM ").append(table);
        final String sql = buildSelectFirstSql(1, sqlBuilder.toString());

        try {
            stmt.execute(sql);
            return true;
        } catch (SQLException ex) {
            return false;
        } finally {
            if (conn == null) {
                releaseResources(stmt, null);
            } else {
                releaseStatementAndResultSet(stmt, null);
            }
        }
    }

    public boolean tableExists(final Connection conn, final String table)
            throws DataAccessException {
        ParamUtil.requireNonBlank("table", table);

        Statement stmt;
        try {
            stmt = (conn != null) ? conn.createStatement() : getConnection().createStatement();
        } catch (SQLException ex) {
            throw translate(null, ex);
        }

        StringBuilder sqlBuilder = new StringBuilder(table.length() + 10);
        sqlBuilder.append("1 FROM ").append(table);
        final String sql = buildSelectFirstSql(1, sqlBuilder.toString());

        try {
            stmt.execute(sql);
            return true;
        } catch (SQLException ex) {
            return false;
        } finally {
            if (conn == null) {
                releaseResources(stmt, null);
            } else {
                releaseStatementAndResultSet(stmt, null);
            }
        }
    }

    protected abstract String buildCreateSequenceSql(String sequenceName, long startValue);

    protected abstract String buildDropSequenceSql(String sequenceName);

    protected abstract String buildNextSeqValueSql(String sequenceName);

    protected final String buildAndCacheNextSeqValueSql(String sequenceName) {
        String sql = cacheSeqNameSqls.get(sequenceName);
        if (sql == null) {
            sql = buildNextSeqValueSql(sequenceName);
            cacheSeqNameSqls.put(sequenceName, sql);
        }
        return sql;
    }

    protected boolean isUseSqlStateAsCode() {
        return false;
    }

    public void dropAndCreateSequence(final String sequenceName, final long startValue)
            throws DataAccessException {
        try {
            dropSequence(sequenceName);
        } catch (DataAccessException ex) {
            LOG.error("could not drop sequence {}: {}", sequenceName, ex.getMessage());
        }

        createSequence(sequenceName, startValue);
    }

    public void createSequence(final String sequenceName, final long startValue)
            throws DataAccessException {
        ParamUtil.requireNonBlank("sequenceName", sequenceName);
        final String sql = buildCreateSequenceSql(sequenceName, startValue);
        Connection conn = getConnection();
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            stmt.execute(sql);
            LOG.info("datasource {} CREATESEQ {} START {}", name, sequenceName, startValue);
        } catch (SQLException ex) {
            throw translate(sql, ex);
        } finally {
            releaseResources(stmt, null);
        }

    }

    public void dropSequence(final String sequenceName) throws DataAccessException {
        ParamUtil.requireNonBlank("sequenceName", sequenceName);
        final String sql = buildDropSequenceSql(sequenceName);
        Connection conn = getConnection();
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            stmt.execute(sql);
            LOG.info("datasource {} DROPSEQ {}", name, sequenceName);
        } catch (SQLException ex) {
            throw translate(sql, ex);
        } finally {
            releaseResources(stmt, null);
        }
    }

    public void setLastUsedSeqValue(final String sequenceName, final long sequenceValue) {
        ParamUtil.requireNonBlank("sequenceName", sequenceName);
        lastUsedSeqValues.put(sequenceName, sequenceValue);
    }

    public long nextSeqValue(final Connection conn, final String sequenceName)
            throws DataAccessException {
        ParamUtil.requireNonBlank("sequenceName", sequenceName);
        final String sql = buildAndCacheNextSeqValueSql(sequenceName);
        boolean newConn = (conn == null);
        Connection tmpConn = (conn != null) ? conn : getConnection();
        Statement stmt = null;

        long next;
        try {
            stmt = tmpConn.createStatement();

            while (true) {
                ResultSet rs = stmt.executeQuery(sql);
                try {
                    if (rs.next()) {
                        next = rs.getLong(1);
                        synchronized (lastUsedSeqValuesLock) {
                            Long lastValue = lastUsedSeqValues.get(sequenceName);
                            if (lastValue == null || next > lastValue) {
                                lastUsedSeqValues.put(sequenceName, next);
                                break;
                            }
                        }
                    } else {
                        throw new DataAccessException(
                                "could not increment the sequence " + sequenceName);
                    }
                } finally {
                    releaseStatementAndResultSet(null, rs);
                }
            }
        } catch (SQLException ex) {
            throw translate(sql, ex);
        } finally {
            if (newConn) {
                releaseResources(stmt, null);
            } else {
                releaseStatementAndResultSet(stmt, null);
            }
        }

        LOG.debug("datasource {} NEXVALUE({}): {}", name, sequenceName, next);
        return next;
    } // method nextSeqValue

    protected String getSqlToDropPrimaryKey(final String primaryKeyName, final String table) {
        ParamUtil.requireNonBlank("primaryKeyName", primaryKeyName);
        ParamUtil.requireNonBlank("table", table);
        StringBuilder sql = new StringBuilder(table.length() + 30);
        return sql.append("ALTER TABLE ").append(table).append(" DROP PRIMARY KEY ").toString();
    }

    public void dropPrimaryKey(final Connection conn, final String primaryKeyName,
            final String table) throws DataAccessException {
        executeUpdate(conn, getSqlToDropPrimaryKey(primaryKeyName, table));
    }

    protected String getSqlToAddPrimaryKey(final String primaryKeyName, final String table,
            final String... columns) {
        ParamUtil.requireNonBlank("primaryKeyName", primaryKeyName);
        ParamUtil.requireNonBlank("table", table);

        final StringBuilder sb = new StringBuilder(100);
        sb.append("ALTER TABLE ").append(table);
        sb.append(" ADD CONSTRAINT ").append(primaryKeyName);
        sb.append(" PRIMARY KEY (");
        final int n = columns.length;
        for (int i = 0; i < n; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append(columns[i]);
        }
        sb.append(")");

        return sb.toString();
    }

    public void addPrimaryKey(final Connection conn, final String primaryKeyName,
            final String table, final String... columns) throws DataAccessException {
        executeUpdate(conn, getSqlToAddPrimaryKey(primaryKeyName, table, columns));
    }

    protected String getSqlToDropForeignKeyConstraint(final String constraintName,
            final String baseTable) throws DataAccessException {
        ParamUtil.requireNonBlank("constraintName", constraintName);
        ParamUtil.requireNonBlank("baseTable", baseTable);

        StringBuilder sb = new StringBuilder(baseTable.length() + constraintName.length() + 30);
        return sb.append("ALTER TABLE ").append(baseTable).append(" DROP CONSTRAINT ")
                .append(constraintName).toString();
    }

    public void dropForeignKeyConstraint(final Connection conn, final String constraintName,
            final String baseTable) throws DataAccessException {
        executeUpdate(conn, getSqlToDropForeignKeyConstraint(constraintName, baseTable));
    }

    protected String getSqlToAddForeignKeyConstraint(final String constraintName,
            final String baseTable, final String baseColumn, final String referencedTable,
            final String referencedColumn, final String onDeleteAction,
            final String onUpdateAction) {
        ParamUtil.requireNonBlank("constraintName", constraintName);
        ParamUtil.requireNonBlank("baseTable", baseTable);
        ParamUtil.requireNonBlank("baseColumn", baseColumn);
        ParamUtil.requireNonBlank("referencedTable", referencedTable);
        ParamUtil.requireNonBlank("referencedColumn", referencedColumn);
        ParamUtil.requireNonBlank("onDeleteAction", onDeleteAction);
        ParamUtil.requireNonBlank("onUpdateAction", onUpdateAction);

        final StringBuilder sb = new StringBuilder(100);
        sb.append("ALTER TABLE ").append(baseTable);
        sb.append(" ADD CONSTRAINT ").append(constraintName);
        sb.append(" FOREIGN KEY (").append(baseColumn).append(")");
        sb.append(" REFERENCES ").append(referencedTable);
        sb.append(" (").append(referencedColumn).append(")");
        sb.append(" ON DELETE ").append(onDeleteAction);
        sb.append(" ON UPDATE ").append(onUpdateAction);
        return sb.toString();
    }

    public void addForeignKeyConstraint(final Connection conn, final String constraintName,
            final String baseTable, final String baseColumn, final String referencedTable,
            final String referencedColumn, final String onDeleteAction, final String onUpdateAction)
            throws DataAccessException {
        final String sql = getSqlToAddForeignKeyConstraint(constraintName, baseTable, baseColumn,
                referencedTable, referencedColumn, onDeleteAction, onUpdateAction);
        executeUpdate(conn, sql);
    }

    protected String getSqlToDropIndex(final String table, final String indexName) {
        ParamUtil.requireNonBlank("indexName", indexName);
        return "DROP INDEX " + indexName;
    }

    public void dropIndex(final Connection conn, final String table, final String indexName)
            throws DataAccessException {
        executeUpdate(conn, getSqlToDropIndex(table, indexName));
    }

    protected String getSqlToCreateIndex(final String indexName, final String table,
            final String... columns) {
        ParamUtil.requireNonBlank("indexName", indexName);
        ParamUtil.requireNonBlank("table", table);
        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("columns must not be null and empty");
        }

        final StringBuilder sb = new StringBuilder(200);
        sb.append("CREATE INDEX ").append(indexName);
        sb.append(" ON ").append(table).append("(");
        for (String column : columns) {
            ParamUtil.requireNonBlank("column", column);
            sb.append(column).append(',');
        }
        sb.deleteCharAt(sb.length() - 1); // delete the last ","
        sb.append(")");
        return sb.toString();
    }

    public void createIndex(final Connection conn, final String indexName, final String table,
            final String... columns) throws DataAccessException {
        executeUpdate(conn, getSqlToCreateIndex(indexName, table, columns));
    }

    protected String getSqlToDropUniqueConstraint(final String constraintName, final String table) {
        ParamUtil.requireNonBlank("table", table);
        ParamUtil.requireNonBlank("constraintName", constraintName);

        StringBuilder sb = new StringBuilder(table.length() + constraintName.length() + 30);
        return sb.append("ALTER TABLE ").append(table).append(" DROP CONSTRAINT ")
                .append(constraintName).toString();
    }

    public void dropUniqueConstrain(final Connection conn, final String constraintName,
            final String table) throws DataAccessException {
        executeUpdate(conn, getSqlToDropUniqueConstraint(constraintName, table));
    }

    protected String getSqlToAddUniqueConstrain(final String constraintName, final String table,
            final String... columns) {
        ParamUtil.requireNonBlank("constraintName", constraintName);
        ParamUtil.requireNonBlank("table", table);

        final StringBuilder sb = new StringBuilder(100);
        sb.append("ALTER TABLE ").append(table);
        sb.append(" ADD CONSTRAINT ").append(constraintName);
        sb.append(" UNIQUE (");
        final int n = columns.length;
        for (int i = 0; i < n; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append(columns[i]);
        }
        sb.append(")");
        return sb.toString();
    }

    public void addUniqueConstrain(final Connection conn, final String constraintName,
            final String table, final String... columns) throws DataAccessException {
        executeUpdate(conn, getSqlToAddUniqueConstrain(constraintName, table, columns));
    }

    public DataAccessException translate(final String sql, final SQLException ex) {
        ParamUtil.requireNonNull("ex", ex);

        String tmpSql = sql;
        if (tmpSql == null) {
            tmpSql = "";
        }

        SQLException sqlEx = ex;
        if (sqlEx instanceof BatchUpdateException && sqlEx.getNextException() != null) {
            SQLException nestedSqlEx = sqlEx.getNextException();
            if (nestedSqlEx.getErrorCode() > 0 || nestedSqlEx.getSQLState() != null) {
                LOG.debug("Using nested SQLException from the BatchUpdateException");
                sqlEx = nestedSqlEx;
            }
        }

        // Check SQLErrorCodes with corresponding error code, if available.
        String errorCode;
        String sqlState;

        if (sqlErrorCodes.isUseSqlStateForTranslation()) {
            errorCode = sqlEx.getSQLState();
            sqlState = null;
        } else {
            // Try to find SQLException with actual error code, looping through the causes.
            // E.g. applicable to java.sql.DataTruncation as of JDK 1.6.
            SQLException current = sqlEx;
            while (current.getErrorCode() == 0 && current.getCause() instanceof SQLException) {
                current = (SQLException) current.getCause();
            }
            errorCode = Integer.toString(current.getErrorCode());
            sqlState = current.getSQLState();
        }

        if (errorCode != null) {
            // look for grouped error codes.
            if (sqlErrorCodes.getBadSqlGrammarCodes().contains(errorCode)) {
                logTranslation(tmpSql, sqlEx);
                return new BadSqlGrammarException(buildMessage(tmpSql, sqlEx), sqlEx);
            } else if (sqlErrorCodes.getInvalidResultSetAccessCodes().contains(errorCode)) {
                logTranslation(tmpSql, sqlEx);
                return new InvalidResultSetAccessException(buildMessage(tmpSql, sqlEx), sqlEx);
            } else if (sqlErrorCodes.getDuplicateKeyCodes().contains(errorCode)) {
                logTranslation(tmpSql, sqlEx);
                return new DuplicateKeyException(buildMessage(tmpSql, sqlEx), sqlEx);
            } else if (sqlErrorCodes.getDataIntegrityViolationCodes().contains(errorCode)) {
                logTranslation(tmpSql, sqlEx);
                return new DataIntegrityViolationException(buildMessage(tmpSql, sqlEx), sqlEx);
            } else if (sqlErrorCodes.getPermissionDeniedCodes().contains(errorCode)) {
                logTranslation(tmpSql, sqlEx);
                return new PermissionDeniedDataAccessException(buildMessage(tmpSql, sqlEx), sqlEx);
            } else if (sqlErrorCodes.getDataAccessResourceFailureCodes().contains(errorCode)) {
                logTranslation(tmpSql, sqlEx);
                return new DataAccessResourceFailureException(buildMessage(tmpSql, sqlEx), sqlEx);
            } else if (sqlErrorCodes.getTransientDataAccessResourceCodes().contains(errorCode)) {
                logTranslation(tmpSql, sqlEx);
                return new TransientDataAccessResourceException(buildMessage(tmpSql, sqlEx), sqlEx);
            } else if (sqlErrorCodes.getCannotAcquireLockCodes().contains(errorCode)) {
                logTranslation(tmpSql, sqlEx);
                return new CannotAcquireLockException(buildMessage(tmpSql, sqlEx), sqlEx);
            } else if (sqlErrorCodes.getDeadlockLoserCodes().contains(errorCode)) {
                logTranslation(tmpSql, sqlEx);
                return new DeadlockLoserDataAccessException(buildMessage(tmpSql, sqlEx), sqlEx);
            } else if (sqlErrorCodes.getCannotSerializeTransactionCodes().contains(errorCode)) {
                logTranslation(tmpSql, sqlEx);
                return new CannotSerializeTransactionException(buildMessage(tmpSql, sqlEx), sqlEx);
            }
        } // end if (errorCode)

        // try SQLState
        if (sqlState != null && sqlState.length() >= 2) {
            String classCode = sqlState.substring(0, 2);
            if (sqlStateCodes.getBadSqlGrammarCodes().contains(classCode)) {
                return new BadSqlGrammarException(buildMessage(tmpSql, sqlEx), ex);
            } else if (sqlStateCodes.getDataIntegrityViolationCodes().contains(classCode)) {
                return new DataIntegrityViolationException(buildMessage(tmpSql, ex), ex);
            } else if (sqlStateCodes.getDataAccessResourceFailureCodes().contains(classCode)) {
                return new DataAccessResourceFailureException(buildMessage(tmpSql, ex), ex);
            } else if (sqlStateCodes.getTransientDataAccessResourceCodes().contains(classCode)) {
                return new TransientDataAccessResourceException(buildMessage(tmpSql, ex), ex);
            } else if (sqlStateCodes.getConcurrencyFailureCodes().contains(classCode)) {
                return new ConcurrencyFailureException(buildMessage(tmpSql, ex), ex);
            }
        }

        // For MySQL: exception class name indicating a timeout?
        // (since MySQL doesn't throw the JDBC 4 SQLTimeoutException)
        if (ex.getClass().getName().contains("Timeout")) {
            return new QueryTimeoutException(buildMessage(tmpSql, ex), ex);
        }

        // We couldn't identify it more precisely
        if (LOG.isDebugEnabled()) {
            String codes;
            if (sqlErrorCodes.isUseSqlStateForTranslation()) {
                codes = new StringBuilder(60).append("SQL state '").append(sqlEx.getSQLState())
                        .append("', error code '").append(sqlEx.getErrorCode()).toString();
            } else {
                codes = "Error code '" + sqlEx.getErrorCode() + "'";
            }
            LOG.debug("Unable to translate SQLException with " + codes);
        }

        return new UncategorizedSqlException(buildMessage(tmpSql, sqlEx), sqlEx);
    } // method translate

    private void logTranslation(final String sql, final SQLException sqlEx) {
        if (!LOG.isDebugEnabled()) {
            return;
        }

        LOG.debug(
            "Translating SQLException: SQL state '{}', error code '{}', message [{}]; SQL was [{}]",
            sqlEx.getSQLState(), sqlEx.getErrorCode(), sqlEx.getMessage(), sql);
    }

    private String buildMessage(final String sql, final SQLException ex) {
        String msg = ex.getMessage();
        StringBuilder sb = new StringBuilder(msg.length() + sql.length() + 8);
        return sb.append("SQL [").append(sql).append("]; ").append(ex.getMessage()).toString();
    }

    private void executeUpdate(Connection conn, String sql) throws DataAccessException {
        Statement stmt = null;
        try {
            stmt = (conn != null) ? conn.createStatement() : getConnection().createStatement();
            stmt.executeUpdate(sql);
        } catch (SQLException ex) {
            throw translate(sql, ex);
        } finally {
            if (conn == null) {
                releaseResources(stmt, null);
            } else {
                releaseStatementAndResultSet(stmt, null);
            }
        }
    }

    static DataSourceWrapper createDataSource(final String name, final Properties props,
            final DatabaseType databaseType) {
        ParamUtil.requireNonNull("props", props);
        ParamUtil.requireNonNull("databaseType", databaseType);

        // The DB2 schema name is case-sensitive, and must be specified in uppercase characters
        String datasourceClassName = props.getProperty("dataSourceClassName");
        if (datasourceClassName != null) {
            if (datasourceClassName.contains(".db2.")) {
                String propName = "dataSource.currentSchema";
                String schema = props.getProperty(propName);
                if (schema != null) {
                    String upperCaseSchema = schema.toUpperCase();
                    if (!schema.equals(upperCaseSchema)) {
                        props.setProperty(propName, upperCaseSchema);
                    }
                }
            }
        } else {
            String propName = "jdbcUrl";
            final String url = props.getProperty(propName);
            if (StringUtil.startsWithIgnoreCase(url, "jdbc:db2:")) {
                String sep = ":currentSchema=";
                int idx = url.indexOf(sep);
                if (idx != 1) {
                    String schema = url.substring(idx + sep.length());
                    if (schema.endsWith(";")) {
                        schema = schema.substring(0, schema.length() - 1);
                    }

                    String upperCaseSchema = schema.toUpperCase();
                    if (!schema.equals(upperCaseSchema)) {
                        String newUrl = url.replace(sep + schema, sep + upperCaseSchema);
                        props.setProperty(propName, newUrl);
                    }
                }
            }
        } // end if

        if (databaseType == DatabaseType.DB2 || databaseType == DatabaseType.H2
                || databaseType == DatabaseType.HSQL || databaseType == DatabaseType.MYSQL
                || databaseType == DatabaseType.MARIADB || databaseType == DatabaseType.ORACLE
                || databaseType == DatabaseType.POSTGRES) {
            HikariConfig conf = new HikariConfig(props);
            HikariDataSource service = new HikariDataSource(conf);
            switch (databaseType) {
            case DB2:
                return new DB2(name, service);
            case H2:
                return new H2(name, service);
            case HSQL:
                return new HSQL(name, service);
            case MYSQL:
                return new MySQL(name, service);
            case MARIADB:
                return new MariaDB(name, service);
            case ORACLE:
                return new Oracle(name, service);
            default: // POSTGRESQL:
                return new PostgreSQL(name, service);
            }
        } else {
            throw new IllegalArgumentException("unknown datasource type " + databaseType);
        }
    } // method createDataSource

}
