/*
 * Copyright 2012-2021 Aerospike, Inc.
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
package com.aerospike.client.query;

import java.io.Serializable;

import com.aerospike.client.Key;
import com.aerospike.client.cluster.Partition;

/**
 * Partition filter used in scan/query.
 */
public final class PartitionFilter implements Serializable {
	private static final long serialVersionUID = 3L;

	/**
	 * Read all partitions.
	 */
	public static PartitionFilter all() {
		return new PartitionFilter(0, 4096);
	}

	/**
	 * Filter by partition id.
	 *
	 * @param id		partition id (0 - 4095)
	 */
	public static PartitionFilter id(int id) {
		return new PartitionFilter(id, 1);
	}

	/**
	 * Return records after key's digest in partition containing the digest.
	 * Note that digest order is not the same as userKey order.
	 *
	 * @param key		return records after this key's digest
	 */
	public static PartitionFilter after(Key key) {
		return new PartitionFilter(key.digest);
	}

	/**
	 * Filter by partition range.
	 *
	 * @param begin		start partition id (0 - 4095)
	 * @param count		number of partitions
	 */
	public static PartitionFilter range(int begin, int count) {
		return new PartitionFilter(begin, count);
	}

	final int begin;
	final int count;
	final byte[] digest;
	PartitionStatus[] partitions; // Initialized in PartitionTracker.
	boolean done;

	private PartitionFilter(int begin, int count) {
		this.begin = begin;
		this.count = count;
		this.digest = null;
	}

	private PartitionFilter(byte[] digest) {
		this.begin = Partition.getPartitionId(digest);
		this.count = 1;
		this.digest = digest;
	}

	/**
	 * Return first partition id.
	 */
	public int getBegin() {
		return begin;
	}

	/**
	 * Return count of partitions.
	 */
	public int getCount() {
		return count;
	}

	/**
	 * Return resume after digest.
	 */
	public byte[] getDigest() {
		return digest;
	}

	/**
	 * Return status of each partition after scan termination.
	 * Useful for external retry of partially completed scans at a later time.
	 * <p>
	 * The partition status is accurate for sync/async scanPartitions and async queryPartitions.
	 * <p>
	 * The partition status is not accurate for
	 * {@link com.aerospike.client.AerospikeClient#queryPartitions(com.aerospike.client.policy.QueryPolicy, Statement, PartitionFilter)}
	 * because the last digest received is set during query parsing, but the user may not have retrieved
	 * that digest from the RecordSet yet.
	 */
	public PartitionStatus[] getPartitions() {
		return partitions;
	}

	/**
	 * Set status of each partition after scan termination.
	 * Useful for external retry of partially completed scans at a later time.
	 */
	public void setPartitions(PartitionStatus[] partitions) {
		this.partitions = partitions;
	}

	/**
	 * If using {@link com.aerospike.client.policy.ScanPolicy#maxRecords} or
	 * {@link com.aerospike.client.policy.QueryPolicy#maxRecords},
	 * did previous paginated scans with this partition filter instance return all records?
	 */
	public boolean isDone() {
		return done;
	}
}
