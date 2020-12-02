/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see LICENSE.txt file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.phoenix;

import com.google.common.base.Optional;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.phoenix.jdbc.PhoenixDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class HBaseMini {
    static final Logger logger = LoggerFactory.getLogger(HBaseMini.class);

    static final int HTTP_PORT = 18080;
    static final String LOCALHOST = "localhost";
    static final String JDBC_PHOENIX = "jdbc:phoenix:";

    final HBaseTestingUtility hbase = new HBaseTestingUtility();
    final AtomicBoolean ready = new AtomicBoolean(false);
    final HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);

    final String hbaseZookeeperQuorum = Optional.fromNullable(
        System.getenv("HBASE_ZOOKEEPER_QUORUM")).or(LOCALHOST);
    final String hbaseMasterHostname = Optional.fromNullable(
        System.getenv("HBASE_MASTER_HOSTNAME")).or(LOCALHOST);
    final String hbaseRegionServerHostname = Optional.fromNullable(
        System.getenv("HBASE_REGIONSERVER_HOSTNAME")).or(LOCALHOST);

    public HBaseMini() throws IOException {
        server.createContext("/health", httpExchange -> {
            int httpStatus = ready.get() ? 200 : 503;
            httpExchange.sendResponseHeaders(httpStatus, 0);
            httpExchange.close();
        });
        server.setExecutor(null);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
    }

    public static void main(String[] args) throws Exception {
        HBaseMini hBaseMini = new HBaseMini();
        hBaseMini.start();
    }

    public List<Path> getSchemaMigrations() throws IOException {
        Path schemaPath = Paths.get("schema");
        if (!Files.exists(schemaPath)) {
            return Collections.emptyList();
        }

        return Files.list(schemaPath)
            .filter(s -> s.toString().endsWith(".sql"))
            .sorted()
            .collect(Collectors.toList());
    }

    public Connection getPhoenixConnection(int port) throws SQLException {
        return DriverManager.getConnection(JDBC_PHOENIX + hbaseZookeeperQuorum + ":" + port);
    }

    public void applySchemaMigrations(HBaseTestingUtility hbase) throws SQLException, IOException {
        List<Path> schemaFiles = getSchemaMigrations();
        if (schemaFiles.isEmpty()) {
            logger.info("No schema migrations found.");
            return;
        }
        logger.info("Executing schema migrations : {}", schemaFiles);
        try (Connection connection = getPhoenixConnection(hbase.getZkCluster().getClientPort())) {
            connection.setAutoCommit(true);
            schemaFiles.forEach(schema -> {
                try {
                    logger.info("Executing {}", schema);
                    String sql = IOUtils.toString(schema.toUri());
                    try (PreparedStatement ps = connection.prepareStatement(sql)) {
                        ps.execute();
                    }
                } catch (SQLException | IOException e) {
                    logger.error("Unable to execute " + schema, e);
                }
            });
        }
    }

    public Path getScript(String scriptName) throws IOException {
        Path schemaPath = Paths.get("scripts", scriptName);
        if (!Files.exists(schemaPath)) {
            logger.info("Path {} does not exist", schemaPath);
            return null;
        }
        return schemaPath;
    }

    public void setConfiguration() {
        Configuration conf = hbase.getConfiguration();
        conf.set("hbase.zookeeper.quorum", hbaseZookeeperQuorum);
        conf.set("test.hbase.zookeeper.property.clientPort", "2181");
        conf.set("hbase.localcluster.assign.random.ports", "false");
        conf.set("hbase.master.hostname", hbaseMasterHostname);
        conf.set("hbase.master.ipc.address ", "0.0.0.0");
        conf.set("hbase.regionserver.hostname", hbaseRegionServerHostname);
        conf.set("hbase.regionserver.ipc.address", "0.0.0.0");

        logger.debug("=== HBase cluster configuration ===");
        for (Map.Entry<String, String> entry : conf) {
            logger.debug("{} : {}", entry.getKey(), entry.getValue());
        }
    }

    public void start() throws Exception {
        setConfiguration();

        MiniHBaseCluster cluster = hbase.startMiniCluster();

        logger.info("HBase Mini started. ClusterId : {}", cluster.getMaster().getClusterId());
        logger.info("HBase cluster status : {}", hbase.getHBaseCluster().getClusterStatus());
        logger.info("Zookeeper client port : {}", hbase.getZkCluster().getClientPort());
        logger.info("Phoenix connection url : jdbc:phoenix:" + hbaseZookeeperQuorum);
        logger.info("Phoenix driver : {}", PhoenixDriver.class.getName());

        applySchemaMigrations(hbase);

        ready.set(true);

        logger.info("HBase ready");
    }
}
