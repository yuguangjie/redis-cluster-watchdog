package com.moilioncircle.replicator.cluster.gossip;

import com.moilioncircle.replicator.cluster.ClusterNode;
import com.moilioncircle.replicator.cluster.Server;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;

import static com.moilioncircle.replicator.cluster.ClusterConstants.CLUSTER_BLACKLIST_TTL;

/**
 * Created by Baoyi Chen on 2017/7/13.
 */
public class ClusterBlacklistManager {

    private Server server;
    private ThinGossip gossip;

    public ClusterBlacklistManager(ThinGossip gossip) {
        this.gossip = gossip;
        this.server = gossip.server;
    }

    public void clusterBlacklistCleanup() {
        Iterator<Map.Entry<Long, ClusterNode>> it = server.cluster.nodesBlackList.values().iterator();
        while (it.hasNext()) {
            if (it.next().getKey() < System.currentTimeMillis()) it.remove();
        }
    }

    public void clusterBlacklistAddNode(ClusterNode node) {
        clusterBlacklistCleanup();
        Map.Entry<Long, ClusterNode> entry = new AbstractMap.SimpleEntry<>(System.currentTimeMillis() + CLUSTER_BLACKLIST_TTL, node);
        server.cluster.nodesBlackList.put(node.name, entry);
    }

    public boolean clusterBlacklistExists(String nodename) {
        clusterBlacklistCleanup();
        return server.cluster.nodesBlackList.containsKey(nodename);
    }
}