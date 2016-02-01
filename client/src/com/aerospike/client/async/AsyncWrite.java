/*
 * Copyright 2012-2016 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more contributor
 * license agreements WHICH ARE COMPATIBLE WITH THE APACHE LICENSE, VERSION 2.0.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.aerospike.client.async;

import java.nio.ByteBuffer;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.cluster.Partition;
import com.aerospike.client.listener.WriteListener;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.WritePolicy;

public final class AsyncWrite extends AsyncSingleCommand {
	private final WritePolicy policy;
	private final WriteListener listener;
	private final Key key;
	private final Partition partition;
	private final Bin[] bins;
	private final Operation.Type operation;
		
	public AsyncWrite(AsyncCluster cluster, WritePolicy policy, WriteListener listener, Key key, Bin[] bins, Operation.Type operation) {
		super(cluster);
		this.policy = policy;
		this.listener = listener;
		this.key = key;
		this.partition = new Partition(key);
		this.bins = bins;
		this.operation = operation;
	}

	@Override
	protected Policy getPolicy() {
		return policy;
	}

	@Override
	protected void writeBuffer() {
		setWrite(policy, operation, key, bins);
	}

	@Override
	protected AsyncNode getNode() {	
		return (AsyncNode)cluster.getMasterNode(partition);
	}

	@Override
	protected void parseResult(ByteBuffer byteBuffer) {
		int resultCode = byteBuffer.get(5) & 0xFF;
		
		if (resultCode != 0) {
			throw new AerospikeException(resultCode);
		}
	}

	@Override
	protected void onSuccess() {
		if (listener != null) {
			listener.onSuccess(key);
		}
	}	

	@Override
	protected void onFailure(AerospikeException e) {
		if (listener != null) {
			listener.onFailure(e);
		}
	}
}
