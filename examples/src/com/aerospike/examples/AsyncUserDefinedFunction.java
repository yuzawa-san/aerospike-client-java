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
package com.aerospike.examples;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Language;
import com.aerospike.client.Value;
import com.aerospike.client.async.AsyncClient;
import com.aerospike.client.listener.ExecuteListener;
import com.aerospike.client.task.RegisterTask;

public class AsyncUserDefinedFunction extends AsyncExample {
		
	private boolean completed;

	public AsyncUserDefinedFunction(Console console) {
		super(console);
	}

	/**
	 * Asynchronous query example.
	 */
	@Override
	public void runExample(AsyncClient client, Parameters params) throws Exception {
		if (! params.hasUdf) {
			console.info("Execute functions are not supported by the connected Aerospike server.");
			return;
		}
		
		register(client, params);
		writeUsingUdfAsync(client, params);
		waitTillComplete();
		completed = false;
	}
	
	private void register(AerospikeClient client, Parameters params) throws Exception {
		RegisterTask task = client.register(params.policy, "udf/record_example.lua", "record_example.lua", Language.LUA);
		task.waitTillComplete();
	}

	private void writeUsingUdfAsync(final AsyncClient client, final Parameters params) {
		final Key key = new Key(params.namespace, params.set, "audfkey1");
		final Bin bin = new Bin(params.getBinName("audfbin1"), "string value");		
		
		console.info("Write with udf: namespace=%s set=%s key=%s value=%s", key.namespace, key.setName, key.userKey, bin.value);

		client.execute(params.writePolicy, new ExecuteListener() {
			
			public void onSuccess(final Key key, final Object obj) {
				try {
					// Write succeeded.  Now call read using udf.
					console.info("Get: namespace=%s set=%s key=%s", key.namespace, key.setName, key.userKey);

					client.execute(params.writePolicy, new ExecuteListener() {
						
						public void onSuccess(final Key key, final Object received) {
							Object expected = bin.value.getObject();	

							if (received != null && received.equals(expected)) {
								console.info("Data matched: namespace=%s set=%s key=%s bin=%s value=%s", 
									key.namespace, key.setName, key.userKey, bin.name, received);
							}
							else {
								console.error("Data mismatch: Expected %s. Received %s.", expected, received);
							}
							notifyCompleted();
						}
						
						public void onFailure(AerospikeException e) {
							console.error("Failed to get: namespace=%s set=%s key=%s exception=%s", key.namespace, key.setName, key.userKey, e.getMessage());
							notifyCompleted();
						}
						
					}, key, "record_example", "readBin", Value.get(bin.name));
				}
				catch (Exception e) {				
					console.error("Failed to read: namespace=%s set=%s key=%s exception=%s", key.namespace, key.setName, key.userKey, e.getMessage());
				}
			}
			
			public void onFailure(AerospikeException e) {
				console.error("Failed to write: namespace=%s set=%s key=%s exception=%s", key.namespace, key.setName, key.userKey, e.getMessage());
				notifyCompleted();
			}
		
		}, key, "record_example", "writeBin", Value.get(bin.name), bin.value);
	}	

	private synchronized void waitTillComplete() {
		while (! completed) {
			try {
				super.wait();
			}
			catch (InterruptedException ie) {
			}
		}
	}

	private synchronized void notifyCompleted() {
		completed = true;
		super.notify();
	}
}
