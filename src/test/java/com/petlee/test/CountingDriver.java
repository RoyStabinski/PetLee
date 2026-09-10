package com.petlee.test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * A JDBC driver that counts the statements the provider actually issues, so the N+1 test can assert
 * on a number instead of on a hope.
 *
 * <h2>Why a driver and not the provider's own counter</h2>
 * EclipseLink and Hibernate both expose statistics, by different provider-specific APIs. A driver
 * that delegates to the PostgreSQL one and counts on the way past is about the same amount of code,
 * measures the thing the criterion actually names — SQL statements — and does not tie the test
 * suite to the provider ADR-004 happens to have chosen.
 *
 * <p>{@link DatabaseTest} routes its connections through it by rewriting the URL:
 * {@code jdbc:postgresql://…} becomes {@code jdbc:counting:postgresql://…}. Recording is
 * <strong>off</strong> until a test calls {@link #startRecording()}, so nothing pays for it.
 */
public final class CountingDriver implements Driver {

    /** The prefix that routes a connection through this driver. */
    static final String PREFIX = "jdbc:counting:";

    private static final CountingDriver INSTANCE = new CountingDriver();

    private static final List<String> STATEMENTS = Collections.synchronizedList(new ArrayList<>());

    private static volatile boolean recording;

    static {
        try {
            DriverManager.registerDriver(INSTANCE);
        } catch (SQLException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    private CountingDriver() {
    }

    /** Registers the driver — calling any static method is enough to trigger the initialiser. */
    public static void register() {
        // Deliberately empty.
    }

    /** Starts recording, discarding anything already counted. */
    public static void startRecording() {
        STATEMENTS.clear();
        recording = true;
    }

    /** @return the statements issued since {@link #startRecording()}, in order */
    public static List<String> recorded() {
        return List.copyOf(STATEMENTS);
    }

    /** Stops recording and forgets what was counted. */
    public static void stopRecording() {
        recording = false;
        STATEMENTS.clear();
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        Connection real = DriverManager.getConnection("jdbc:" + url.substring(PREFIX.length()), info);
        return CountingConnection.wrap(real, CountingDriver::record);
    }

    private static void record(String sql) {
        if (recording && sql != null) {
            STATEMENTS.add(sql);
        }
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith(PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger("com.petlee.test");
    }
}
