package com.aerospike.examples;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.ResultCode;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.WritePolicy;

public class Generation extends Example {

	public Generation(Console console) {
		super(console);
	}

	/**
	 * Exercise record generation functionality.
	 */
	@Override
	public void runExample(AerospikeClient client, Parameters params) throws Exception {
		Key key = new Key(params.namespace, params.set, "genkey");
		Bin bin = new Bin(params.getBinName("genbin"), "genvalue1");

		// Delete record if it already exists.
		client.delete(params.writePolicy, key);

		// Set some values for the same record.
		console.info("Put: namespace=%s set=%s key=%s bin=%s value=%s",
			key.namespace, key.setName, key.userKey, bin.name, bin.value);
		
		client.put(params.writePolicy, key, bin);

		bin.value = "genvalue2";
		console.info("Put: namespace=%s set=%s key=%s bin=%s value=%s",
			key.namespace, key.setName, key.userKey, bin.name, bin.value);

		client.put(params.writePolicy, key, bin);

		// Retrieve record and its generation count.
		Record record = client.get(params.policy, key, bin.name);

		if (record == null) {
			throw new Exception(String.format(
				"Failed to get: namespace=%s set=%s key=%s",
				key.namespace, key.setName, key.userKey));
		}

		Object received = record.getValue(bin.name);
		String expected = (String)bin.value;
		
		if (received.equals(expected)) {
			console.info("Get successful: namespace=%s set=%s key=%s bin=%s value=%s generation=%d", 
				key.namespace, key.setName, key.userKey, bin.name, received, record.generation);
		}
		else {
			throw new Exception(String.format("Get mismatch: Expected %s. Received %s.",
				expected, received));
		}

		// Set record and fail if it's not the expected generation.
		bin.value = "genvalue3";
		console.info("Put: namespace=%s set=%s key=%s bin=%s value=%s expected generation=%d",
			key.namespace, key.setName, key.userKey, bin.name, bin.value, record.generation);

		WritePolicy writePolicy = new WritePolicy();
		writePolicy.recordExistsAction = RecordExistsAction.EXPECT_GEN_EQUAL;
		writePolicy.generation = record.generation;
		client.put(writePolicy, key, bin);

		// Set record with invalid generation and check results .
		bin.value = "genvalue4";
		writePolicy.generation = 9999;
		console.info("Put: namespace=%s set=%s key=%s bin=%s value=%s expected generation=%d",
			key.namespace, key.setName, key.userKey, bin.name, bin.value, writePolicy.generation);

		try {
			client.put(writePolicy, key, bin);
			throw new Exception("Should have received generation error instead of success.");				
		}
		catch (AerospikeException ae) {
			if (ae.getResultCode() == ResultCode.GENERATION_ERROR) {
				console.info("Success: Generation error returned as expected.");				
			}
			else {
				throw new Exception(String.format(
					"Unexpected set return code: namespace=%s set=%s key=%s bin=%s value=%s code=%s", 
					key.namespace, key.setName, key.userKey, bin.name, bin.value, ae.getResultCode()));				
			}
		}

		// Verify results.
		record = client.get(params.policy, key, bin.name);

		if (record == null) {
			throw new Exception(String.format(
				"Failed to get: namespace=%s set=%s key=%s",
				key.namespace, key.setName, key.userKey));
		}

		received = record.getValue(bin.name);
		expected = "genvalue3";
		
		if (received.equals(expected)) {
			console.info("Get successful: namespace=%s set=%s key=%s bin=%s value=%s generation=%d", 
				key.namespace, key.setName, key.userKey, bin.name, received, record.generation);
		}
		else {
			throw new Exception(String.format("Get mismatch: Expected %s. Received %s.",
				expected, received));
		}		
	}
}