package com.moilioncircle.replicator.cluster.codec;

import com.moilioncircle.replicator.cluster.message.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.moilioncircle.replicator.cluster.ClusterConstants.*;

/**
 * Created by Baoyi Chen on 2017/7/12.
 */
public class ClusterMsgDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        ClusterMsg msg = decode(in);
        if (msg != null) {
            out.add(msg);
        }
    }

    protected ClusterMsg decode(ByteBuf in) {
        in.markReaderIndex();
        try {
            ClusterMsg msg = new ClusterMsg();
            msg.sig = (String) in.readCharSequence(4, Charset.forName("UTF-8"));
            msg.totlen = in.readInt();
            if (in.readableBytes() < msg.totlen - 8) {
                in.resetReaderIndex();
                return null;
            }
            msg.ver = in.readShort() & 0xFFFF;
            msg.port = in.readShort() & 0xFFFF;
            msg.type = in.readShort() & 0xFFFF;
            msg.count = in.readShort() & 0xFFFF;
            msg.currentEpoch = in.readLong();
            msg.configEpoch = in.readLong();
            msg.offset = in.readLong();
            byte[] sender = new byte[40];
            in.readBytes(sender);
            if (!Arrays.equals(sender, CLUSTER_NODE_NULL_NAME)) {
                msg.sender = new String(sender);
            }
            in.readBytes(msg.myslots);

            byte[] slaveof = new byte[40];
            in.readBytes(slaveof);
            if (!Arrays.equals(slaveof, CLUSTER_NODE_NULL_NAME)) {
                msg.slaveof = new String(slaveof);
            }
            byte[] myip = new byte[46];
            in.readBytes(myip);
            if (!Arrays.equals(myip, CLUSTER_NODE_NULL_IP)) {
                msg.myip = getMyIP(myip);
            }
            in.readBytes(msg.notused);
            msg.cport = in.readShort() & 0xFFFF;
            msg.flags = in.readShort() & 0xFFFF;
            msg.state = in.readByte();
            in.readBytes(msg.mflags);
            if (msg.type == CLUSTERMSG_TYPE_PING || msg.type == CLUSTERMSG_TYPE_PONG || msg.type == CLUSTERMSG_TYPE_MEET) {
                msg.data = new ClusterMsgData();
                msg.data.gossip = new ArrayList<>();
                for (int i = 0; i < msg.count; i++) {
                    ClusterMsgDataGossip gossip = new ClusterMsgDataGossip();
                    byte[] nodename = new byte[40];
                    in.readBytes(nodename);
                    if (!Arrays.equals(nodename, CLUSTER_NODE_NULL_NAME)) {
                        gossip.nodename = new String(nodename);
                    }
                    gossip.pingSent = in.readInt() * 1000;
                    gossip.pongReceived = in.readInt() * 1000;
                    byte[] ip = new byte[46];
                    in.readBytes(ip);
                    if (!Arrays.equals(ip, CLUSTER_NODE_NULL_IP)) {
                        gossip.ip = getMyIP(ip);
                    }
                    gossip.port = in.readShort() & 0xFFFF;
                    gossip.cport = in.readShort() & 0xFFFF;
                    gossip.flags = in.readShort() & 0xFFFF;
                    in.readBytes(gossip.notused1);
                    msg.data.gossip.add(gossip);
                }
            } else if (msg.type == CLUSTERMSG_TYPE_FAIL) {
                msg.data = new ClusterMsgData();
                msg.data.about = new ClusterMsgDataFail();
                byte[] nodename = new byte[40];
                in.readBytes(nodename);
                if (!Arrays.equals(nodename, CLUSTER_NODE_NULL_NAME)) {
                    msg.data.about.nodename = new String(nodename);
                }
            } else if (msg.type == CLUSTERMSG_TYPE_PUBLISH) {
                msg.data = new ClusterMsgData();
                msg.data.msg = new ClusterMsgDataPublish();
                msg.data.msg.channelLen = in.readInt();
                msg.data.msg.messageLen = in.readInt();
                byte[] bulkData = new byte[8];
                in.readBytes(bulkData);
                msg.data.msg.bulkData = bulkData;
            } else if (msg.type == CLUSTERMSG_TYPE_UPDATE) {
                msg.data = new ClusterMsgData();
                msg.data.nodecfg = new ClusterMsgDataUpdate();
                msg.data.nodecfg.configEpoch = in.readLong();
                byte[] nodename = new byte[40];
                in.readBytes(nodename);
                if (!Arrays.equals(nodename, CLUSTER_NODE_NULL_NAME)) {
                    msg.data.nodecfg.nodename = new String(nodename);
                }
                in.readBytes(msg.data.nodecfg.slots);
            }
            return msg;
        } catch (Exception e) {
            in.resetReaderIndex();
            return null;
        }
    }

    public String getMyIP(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                return new String(bytes, 0, i);
            }
        }
        return new String(bytes);
    }
}