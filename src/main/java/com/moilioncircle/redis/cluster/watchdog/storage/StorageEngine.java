/*
 * Copyright 2016-2017 Leon Chen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.moilioncircle.redis.cluster.watchdog.storage;

import com.moilioncircle.redis.cluster.watchdog.Resourcable;

import java.util.Iterator;

import static com.moilioncircle.redis.cluster.watchdog.ClusterConstants.CLUSTER_SLOTS;
import static com.moilioncircle.redis.cluster.watchdog.util.CRC16.crc16;

/**
 * @author Leon Chen
 * @since 1.0.0
 */
public interface StorageEngine extends Resourcable {

    long size();

    void clear();

    long size(int slot);

    void clear(int slot);

    Iterator<byte[]> keys();

    Iterator<byte[]> keys(int slot);

    Iterator<byte[]> keys(long max);

    Iterator<byte[]> keys(int slot, long max);

    /**
     *
     */
    void delete(byte[] key);

    Object load(byte[] key);

    boolean exist(byte[] key);

    Class<?> type(byte[] key);

    void save(byte[] key, Object value, long expire, boolean force);

    /**
     *
     */
    byte[] dump(byte[] key);

    void restore(byte[] key, byte[] serialized, long expire, boolean force);

    static int keyHashSlot(byte[] key) {
        if (key == null) return 0;
        int st = -1, ed = -1;
        for (int i = 0, len = key.length; i < len; i++) {
            if (key[i] == '{' && st == -1) st = i;
            if (key[i] == '}' && st >= 0) {
                ed = i; break;
            }
        }
        if (st >= 0 && ed >= 0 && ed > st + 1)
            return crc16(key, st + 1, ed) & (CLUSTER_SLOTS - 1);
        return crc16(key) & (CLUSTER_SLOTS - 1);
    }
}