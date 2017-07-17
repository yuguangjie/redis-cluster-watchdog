package com.moilioncircle.replicator.cluster.gossip;

import com.moilioncircle.replicator.cluster.ClusterNode;
import com.moilioncircle.replicator.cluster.ClusterNodeFailReport;
import com.moilioncircle.replicator.cluster.Server;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.moilioncircle.replicator.cluster.ClusterConstants.*;

/**
 * Created by Baoyi Chen on 2017/7/12.
 */
public class ClusterNodeManager {
    private static final Log logger = LogFactory.getLog(ClusterConfigManager.class);
    private Server server;
    private ThinGossip gossip;

    public ClusterNodeManager(ThinGossip gossip) {
        this.gossip = gossip;
        this.server = gossip.server;
    }

    public void freeClusterNode(ClusterNode n) {
        if (nodeIsSlave(n) && n.slaveof != null) clusterNodeRemoveSlave(n.slaveof, n);
        server.cluster.nodes.remove(n.name);
        if (n.link != null) gossip.connectionManager.freeClusterLink(n.link);
        n.failReports.clear();
        n.slaves.clear();
    }

    public boolean clusterAddNode(ClusterNode node) {
        return server.cluster.nodes.put(node.name, node) == null;
    }

    public void clusterDelNode(ClusterNode delnode) {
        for (int i = 0; i < CLUSTER_SLOTS; i++) {
            if (!server.cluster.slots[i].equals(delnode)) continue;
            gossip.slotManger.clusterDelSlot(i);
        }

        server.cluster.nodes.values().stream().
                filter(x -> !x.equals(delnode)).
                forEach(x -> clusterNodeDelFailureReport(x, delnode));
        freeClusterNode(delnode);
    }

    public ClusterNode clusterLookupNode(String name) {
        return server.cluster.nodes.get(name);
    }

    public void clusterRenameNode(ClusterNode node, String newname) {
        logger.info("Renaming node " + node.name + " into " + newname);
        server.cluster.nodes.remove(node.name);
        node.name = newname;
        clusterAddNode(node);
    }

    public ClusterNode createClusterNode(String nodename, int flags) {
        ClusterNode node = new ClusterNode();
        if (nodename != null) {
            node.name = nodename;
        } else {
            node.name = getRandomHexChars();
        }

        node.ctime = System.currentTimeMillis();
        node.configEpoch = 0;
        node.flags = flags;
        node.numslots = 0;
        node.numslaves = 0;
        node.slaves = new ArrayList<>();
        node.slaveof = null;
        node.pingSent = node.pongReceived = 0;
        node.failTime = 0;
        node.link = null;
        node.ip = null;
        node.port = 0;
        node.cport = 0;
        node.failReports = new ArrayList<>();
        node.orphanedTime = 0;
        node.replOffset = 0;
        return node;
    }

    public boolean clusterNodeAddFailureReport(ClusterNode failing, ClusterNode sender) {
        for (ClusterNodeFailReport n : failing.failReports) {
            if (!n.node.equals(sender)) continue;
            n.time = System.currentTimeMillis();
            return false;
        }

        ClusterNodeFailReport fr = new ClusterNodeFailReport();
        fr.node = sender;
        fr.time = System.currentTimeMillis();
        failing.failReports.add(fr);
        return true;
    }

    public void clusterNodeCleanupFailureReports(ClusterNode node) {
        List<ClusterNodeFailReport> l = node.failReports;
        long max = gossip.configuration.getClusterNodeTimeout() * CLUSTER_FAIL_REPORT_VALIDITY_MULT;
        long now = System.currentTimeMillis();
        Iterator<ClusterNodeFailReport> it = l.iterator();
        while (it.hasNext()) {
            if (now - it.next().time > max) it.remove();
        }
    }

    public boolean clusterNodeDelFailureReport(ClusterNode node, ClusterNode sender) {
        ClusterNodeFailReport fr = node.failReports.stream().filter(x -> x.node.equals(sender)).findFirst().orElse(null);
        if (fr == null) return false;
        node.failReports.remove(fr);
        clusterNodeCleanupFailureReports(node);
        return true;
    }

    public int clusterNodeFailureReportsCount(ClusterNode node) {
        clusterNodeCleanupFailureReports(node);
        return node.failReports.size();
    }

    public boolean clusterNodeRemoveSlave(ClusterNode master, ClusterNode slave) {
        Iterator<ClusterNode> it = master.slaves.iterator();
        while (it.hasNext()) {
            if (!it.next().equals(slave)) continue;
            it.remove();
            if (--master.numslaves == 0) {
                master.flags &= ~CLUSTER_NODE_MIGRATE_TO;
            }
            return true;
        }
        return false;
    }

    public boolean clusterNodeAddSlave(ClusterNode master, ClusterNode slave) {
        if (master.slaves.stream().anyMatch(e -> e.equals(slave))) return false;
        master.slaves.add(slave);
        master.numslaves++;
        master.flags |= CLUSTER_NODE_MIGRATE_TO;
        return true;
    }

    public int clusterCountNonFailingSlaves(ClusterNode n) {
        return (int) n.slaves.stream().filter(x -> !nodeFailed(x)).count();
    }

    public int clusterGetSlaveRank() {
        int rank = 0;
        ClusterNode master = server.myself.slaveof;
        if (master == null) return rank;
        long myoffset = gossip.replicationManager.replicationGetSlaveOffset();
        if (server.myself.slaveof == null) return 0;
        return (int) server.myself.slaveof.slaves.stream().filter(x -> !x.equals(server.myself) && x.replOffset > myoffset).count();
    }

    public static final char[] chars = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public String getRandomHexChars() {
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < CLUSTER_NAMELEN; i++) {
            r.append(chars[ThreadLocalRandom.current().nextInt(chars.length)]);
        }
        return r.toString();
    }
}