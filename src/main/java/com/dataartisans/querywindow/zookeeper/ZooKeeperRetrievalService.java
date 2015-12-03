/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dataartisans.querywindow.zookeeper;

import com.dataartisans.querywindow.RetrievalService;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZooKeeperRetrievalService<K> implements RetrievalService<K> {

	private final Object lock = new Object();
	private final ZooKeeperConfiguration configuration;
	private CuratorFramework client;
	private Map<Integer, String> actorMap;

	public ZooKeeperRetrievalService(ZooKeeperConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void start() throws Exception {
		client = ZooKeeperUtils.startCuratorFramework(
			configuration.getRootPath(),
			configuration.getZkQuorum(),
			configuration.getSessionTimeout(),
			configuration.getConnectionTimeout(),
			configuration.getRetryWait(),
			configuration.getMaxRetryAttempts());

		refreshActorCache();
	}

	@Override
	public void stop() {
		if (client != null) {
			client.close();
			client = null;
		}
	}

	@Override
	public String retrieveActorURL(K key) {
		synchronized (lock) {
			if (actorMap != null) {
				int partition = key.hashCode() % actorMap.size();
				return actorMap.get(partition);
			} else {
				return null;
			}
		}
	}

	@Override
	public void refreshActorCache() throws Exception {
		if (client != null) {
			List<String> children = client.getChildren().forPath("/");

			Map<Integer, String> newActorMap = new HashMap<>();

			for (String child : children) {
				try {
					byte[] data = client.getData().forPath("/" + child);

					newActorMap.put(Integer.parseInt(child), new String(data));
				} catch (KeeperException.NoNodeException ex) {
					//ignore
				}
			}

			synchronized (lock) {
				if (newActorMap.size() > 0) {
					actorMap = newActorMap;
				} else {
					actorMap = null;
				}
			}
		} else {
			throw new RuntimeException("The CuratorFramework client has not been properly initialized.");
		}
	}
}