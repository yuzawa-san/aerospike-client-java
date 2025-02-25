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
package com.aerospike.client.async;

import java.util.List;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.BatchRead;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.Record;
import com.aerospike.client.cluster.Cluster;
import com.aerospike.client.command.BatchNode;
import com.aerospike.client.command.BatchNodeList;
import com.aerospike.client.command.Command;
import com.aerospike.client.listener.BatchListListener;
import com.aerospike.client.listener.BatchSequenceListener;
import com.aerospike.client.listener.ExistsArrayListener;
import com.aerospike.client.listener.ExistsSequenceListener;
import com.aerospike.client.listener.RecordArrayListener;
import com.aerospike.client.listener.RecordSequenceListener;
import com.aerospike.client.policy.BatchPolicy;
import com.aerospike.client.policy.ReadModeSC;
import com.aerospike.client.policy.Replica;

public final class AsyncBatch {
	//-------------------------------------------------------
	// ReadList
	//-------------------------------------------------------

	public static final class ReadListExecutor extends AsyncMultiExecutor {
		private final BatchListListener listener;
		private final List<BatchRead> records;

		public ReadListExecutor(
			EventLoop eventLoop,
			Cluster cluster,
			BatchPolicy policy,
			BatchListListener listener,
			List<BatchRead> records
		) {
			super(eventLoop, cluster);
			this.listener = listener;
			this.records = records;

			// Create commands.
			List<BatchNode> batchNodes = BatchNodeList.generate(cluster, policy, records);
			AsyncMultiCommand[] tasks = new AsyncMultiCommand[batchNodes.size()];
			int count = 0;

			for (BatchNode batchNode : batchNodes) {
				tasks[count++] = new ReadListCommand(this, batchNode, policy, records);
			}
			// Dispatch commands to nodes.
			execute(tasks, 0);
		}

		protected void onSuccess() {
			listener.onSuccess(records);
		}

		protected void onFailure(AerospikeException ae) {
			listener.onFailure(ae);
		}
	}

	private static final class ReadListCommand extends AsyncBatchCommand {
		private final List<BatchRead> records;

		public ReadListCommand(
			AsyncMultiExecutor parent,
			BatchNode batch,
			BatchPolicy batchPolicy,
			List<BatchRead> records
		) {
			super(parent, batch, batchPolicy, true);
			this.records = records;
		}

		@Override
		protected void writeBuffer() {
			setBatchRead(batchPolicy, records, batch);
		}

		@Override
		protected void parseRow(Key key) {
			if (resultCode == 0) {
				BatchRead record = records.get(batchIndex);
				record.record = parseRecord();
			}
		}

		@Override
		protected AsyncBatchCommand createCommand(BatchNode batchNode)
		{
			return new ReadListCommand(parent, batchNode, batchPolicy, records);
		}

		@Override
		protected List<BatchNode> generateBatchNodes()
		{
			return BatchNodeList.generate(parent.cluster, batchPolicy, records, sequenceAP, sequenceSC, batch);
		}
	}

	//-------------------------------------------------------
	// ReadSequence
	//-------------------------------------------------------

	public static final class ReadSequenceExecutor extends AsyncMultiExecutor {
		private final BatchSequenceListener listener;

		public ReadSequenceExecutor(
			EventLoop eventLoop,
			Cluster cluster,
			BatchPolicy policy,
			BatchSequenceListener listener,
			List<BatchRead> records
		) {
			super(eventLoop, cluster, false);
			this.listener = listener;

			// Assign keys to nodes.
			BatchNodeList bnl = new BatchNodeList(cluster, policy, records, false);
			bnl.validate();
			super.setException(bnl.exception);

			// Create commands.
			List<BatchNode> batchNodes = bnl.list;
			AsyncMultiCommand[] tasks = new AsyncMultiCommand[batchNodes.size()];
			int count = 0;

			for (BatchNode batchNode : batchNodes) {
				tasks[count++] = new ReadSequenceCommand(this, batchNode, policy, listener, records);
			}
			// Dispatch commands to nodes.
			execute(tasks, 0);
		}

		protected void onSuccess() {
			listener.onSuccess();
		}

		protected void onFailure(AerospikeException ae) {
			listener.onFailure(ae);
		}
	}

	private static final class ReadSequenceCommand extends AsyncBatchCommand {
		private final BatchSequenceListener listener;
		private final List<BatchRead> records;

		public ReadSequenceCommand(
			AsyncMultiExecutor parent,
			BatchNode batch,
			BatchPolicy batchPolicy,
			BatchSequenceListener listener,
			List<BatchRead> records
		) {
			super(parent, batch, batchPolicy, true);
			this.listener = listener;
			this.records = records;
		}

		@Override
		protected void writeBuffer() {
			setBatchRead(batchPolicy, records, batch);
		}

		@Override
		protected void parseRow(Key key) {
			BatchRead record = records.get(batchIndex);

			if (resultCode == 0) {
				record.record = parseRecord();
			}
			listener.onRecord(record);
		}

		@Override
		protected AsyncBatchCommand createCommand(BatchNode batchNode)
		{
			return new ReadSequenceCommand(parent, batchNode, batchPolicy, listener, records);
		}

		@Override
		protected List<BatchNode> generateBatchNodes()
		{
			BatchNodeList bnl = new BatchNodeList(parent.cluster, batchPolicy, records, sequenceAP, sequenceSC, batch, false);
			parent.resetException(bnl.exception);
			return bnl.list;
		}
	}

	//-------------------------------------------------------
	// GetArray
	//-------------------------------------------------------

	public static final class GetArrayExecutor extends AsyncMultiExecutor {
		private final RecordArrayListener listener;
		private final Key[] keys;
		private final Record[] recordArray;

		public GetArrayExecutor(
			EventLoop eventLoop,
			Cluster cluster,
			BatchPolicy policy,
			RecordArrayListener listener,
			Key[] keys,
			String[] binNames,
			Operation[] ops,
			int readAttr,
			boolean isOperation
		) {
			super(eventLoop, cluster, true);
			this.listener = listener;
			this.keys = keys;
			this.recordArray = new Record[keys.length];

			// Assign keys to nodes.
			List<BatchNode> batchNodes = BatchNodeList.generate(cluster, policy, keys);

			// Create commands.
			AsyncMultiCommand[] tasks = new AsyncMultiCommand[batchNodes.size()];
			int count = 0;

			for (BatchNode batchNode : batchNodes) {
				tasks[count++] = new GetArrayCommand(this, batchNode, policy, keys, binNames, ops, recordArray, readAttr, isOperation);
			}
			// Dispatch commands to nodes.
			execute(tasks, 0);
		}

		protected void onSuccess() {
			listener.onSuccess(keys, recordArray);
		}

		protected void onFailure(AerospikeException ae) {
			listener.onFailure(ae);
		}
	}

	private static final class GetArrayCommand extends AsyncBatchCommand {
		private final Key[] keys;
		private final String[] binNames;
		private final Operation[] ops;
		private final Record[] records;
		private final int readAttr;

		public GetArrayCommand(
			AsyncMultiExecutor parent,
			BatchNode batch,
			BatchPolicy batchPolicy,
			Key[] keys,
			String[] binNames,
			Operation[] ops,
			Record[] records,
			int readAttr,
			boolean isOperation
		) {
			super(parent, batch, batchPolicy, isOperation);
			this.keys = keys;
			this.binNames = binNames;
			this.ops = ops;
			this.records = records;
			this.readAttr = readAttr;
		}

		@Override
		protected void writeBuffer() {
			setBatchRead(batchPolicy, keys, batch, binNames, ops, readAttr);
		}

		@Override
		protected void parseRow(Key key) {
			if (resultCode == 0) {
				records[batchIndex] = parseRecord();
			}
		}

		@Override
		protected AsyncBatchCommand createCommand(BatchNode batchNode)
		{
			return new GetArrayCommand(parent, batchNode, batchPolicy, keys, binNames, ops, records, readAttr, isOperation);
		}

		@Override
		protected List<BatchNode> generateBatchNodes()
		{
			return BatchNodeList.generate(parent.cluster, batchPolicy, keys, sequenceAP, sequenceSC, batch);
		}
	}

	//-------------------------------------------------------
	// GetSequence
	//-------------------------------------------------------

	public static final class GetSequenceExecutor extends AsyncMultiExecutor {
		private final RecordSequenceListener listener;

		public GetSequenceExecutor(
			EventLoop eventLoop,
			Cluster cluster,
			BatchPolicy policy,
			RecordSequenceListener listener,
			Key[] keys,
			String[] binNames,
			Operation[] ops,
			int readAttr,
			boolean isOperation
		) {
			super(eventLoop, cluster, false);
			this.listener = listener;

			// Assign keys to nodes.
			BatchNodeList bnl = new BatchNodeList(cluster, policy, keys, false);
			bnl.validate();
			super.setException(bnl.exception);

			// Create commands.
			List<BatchNode> batchNodes = bnl.list;
			AsyncMultiCommand[] tasks = new AsyncMultiCommand[batchNodes.size()];
			int count = 0;

			for (BatchNode batchNode : batchNodes) {
				tasks[count++] = new GetSequenceCommand(this, batchNode, policy, keys, binNames, ops, listener, readAttr, isOperation);
			}
			// Dispatch commands to nodes.
			execute(tasks, 0);
		}

		@Override
		protected void onSuccess() {
			listener.onSuccess();
		}

		@Override
		protected void onFailure(AerospikeException ae) {
			listener.onFailure(ae);
		}
	}

	private static final class GetSequenceCommand extends AsyncBatchCommand {
		private final Key[] keys;
		private final String[] binNames;
		private final Operation[] ops;
		private final RecordSequenceListener listener;
		private final int readAttr;

		public GetSequenceCommand(
			AsyncMultiExecutor parent,
			BatchNode batch,
			BatchPolicy batchPolicy,
			Key[] keys,
			String[] binNames,
			Operation[] ops,
			RecordSequenceListener listener,
			int readAttr,
			boolean isOperation
		) {
			super(parent, batch, batchPolicy, isOperation);
			this.keys = keys;
			this.binNames = binNames;
			this.ops = ops;
			this.listener = listener;
			this.readAttr = readAttr;
		}

		@Override
		protected void writeBuffer() {
			setBatchRead(batchPolicy, keys, batch, binNames, ops, readAttr);
		}

		@Override
		protected void parseRow(Key key) {
			Key keyOrig = keys[batchIndex];

			if (resultCode == 0) {
				Record record = parseRecord();
				listener.onRecord(keyOrig, record);
			}
			else {
				listener.onRecord(keyOrig, null);
			}
		}

		@Override
		protected AsyncBatchCommand createCommand(BatchNode batchNode)
		{
			return new GetSequenceCommand(parent, batchNode, batchPolicy, keys, binNames, ops, listener, readAttr, isOperation);
		}

		@Override
		protected List<BatchNode> generateBatchNodes()
		{
			BatchNodeList bnl = new BatchNodeList(parent.cluster, batchPolicy, keys, sequenceAP, sequenceSC, batch, false);
			parent.resetException(bnl.exception);
			return bnl.list;
		}
	}

	//-------------------------------------------------------
	// ExistsArray
	//-------------------------------------------------------

	public static final class ExistsArrayExecutor extends AsyncMultiExecutor {
		private final ExistsArrayListener listener;
		private final Key[] keys;
		private final boolean[] existsArray;

		public ExistsArrayExecutor(
			EventLoop eventLoop,
			Cluster cluster,
			BatchPolicy policy,
			Key[] keys,
			ExistsArrayListener listener
		) {
			super(eventLoop, cluster, true);
			this.listener = listener;
			this.keys = keys;
			this.existsArray = new boolean[keys.length];

			// Assign keys to nodes.
			List<BatchNode> batchNodes = BatchNodeList.generate(cluster, policy, keys);

			// Create commands.
			AsyncMultiCommand[] tasks = new AsyncMultiCommand[batchNodes.size()];
			int count = 0;

			for (BatchNode batchNode : batchNodes) {
				tasks[count++] = new ExistsArrayCommand(this, batchNode, policy, keys, existsArray);
			}
			// Dispatch commands to nodes.
			execute(tasks, 0);
		}

		protected void onSuccess() {
			listener.onSuccess(keys, existsArray);
		}

		protected void onFailure(AerospikeException ae) {
			listener.onFailure(ae);
		}
	}

	private static final class ExistsArrayCommand extends AsyncBatchCommand {
		private final Key[] keys;
		private final boolean[] existsArray;

		public ExistsArrayCommand(
			AsyncMultiExecutor parent,
			BatchNode batch,
			BatchPolicy batchPolicy,
			Key[] keys,
			boolean[] existsArray
		) {
			super(parent, batch, batchPolicy, false);
			this.keys = keys;
			this.existsArray = existsArray;
		}

		@Override
		protected void writeBuffer() {
			setBatchRead(batchPolicy, keys, batch, null, null, Command.INFO1_READ | Command.INFO1_NOBINDATA);
		}

		@Override
		protected void parseRow(Key key) {
			if (opCount > 0) {
				throw new AerospikeException.Parse("Received bins that were not requested!");
			}

			existsArray[batchIndex] = resultCode == 0;
		}

		@Override
		protected AsyncBatchCommand createCommand(BatchNode batchNode)
		{
			return new ExistsArrayCommand(parent, batchNode, batchPolicy, keys, existsArray);
		}

		@Override
		protected List<BatchNode> generateBatchNodes()
		{
			return BatchNodeList.generate(parent.cluster, batchPolicy, keys, sequenceAP, sequenceSC, batch);
		}
	}

	//-------------------------------------------------------
	// ExistsSequence
	//-------------------------------------------------------

	public static final class ExistsSequenceExecutor extends AsyncMultiExecutor {
		private final ExistsSequenceListener listener;

		public ExistsSequenceExecutor(
			EventLoop eventLoop,
			Cluster cluster,
			BatchPolicy policy,
			Key[] keys,
			ExistsSequenceListener listener
		) {
			super(eventLoop, cluster, false);
			this.listener = listener;

			// Assign keys to nodes.
			BatchNodeList bnl = new BatchNodeList(cluster, policy, keys, false);
			bnl.validate();
			super.setException(bnl.exception);

			// Create commands.
			List<BatchNode> batchNodes = bnl.list;
			AsyncMultiCommand[] tasks = new AsyncMultiCommand[batchNodes.size()];
			int count = 0;

			for (BatchNode batchNode : batchNodes) {
				tasks[count++] = new ExistsSequenceCommand(this, batchNode, policy, keys, listener);
			}
			// Dispatch commands to nodes.
			execute(tasks, 0);
		}

		protected void onSuccess() {
			listener.onSuccess();
		}

		protected void onFailure(AerospikeException ae) {
			listener.onFailure(ae);
		}
	}

	private static final class ExistsSequenceCommand extends AsyncBatchCommand {
		private final Key[] keys;
		private final ExistsSequenceListener listener;

		public ExistsSequenceCommand(
			AsyncMultiExecutor parent,
			BatchNode batch,
			BatchPolicy batchPolicy,
			Key[] keys,
			ExistsSequenceListener listener
		) {
			super(parent, batch, batchPolicy, false);
			this.keys = keys;
			this.listener = listener;
		}

		@Override
		protected void writeBuffer() {
			setBatchRead(batchPolicy, keys, batch, null, null, Command.INFO1_READ | Command.INFO1_NOBINDATA);
		}

		@Override
		protected void parseRow(Key key) {
			if (opCount > 0) {
				throw new AerospikeException.Parse("Received bins that were not requested!");
			}

			Key keyOrig = keys[batchIndex];
			listener.onExists(keyOrig, resultCode == 0);
		}

		@Override
		protected AsyncBatchCommand createCommand(BatchNode batchNode)
		{
			return new ExistsSequenceCommand(parent, batchNode, batchPolicy, keys, listener);
		}

		@Override
		protected List<BatchNode> generateBatchNodes()
		{
			BatchNodeList bnl = new BatchNodeList(parent.cluster, batchPolicy, keys, sequenceAP, sequenceSC, batch, false);
			parent.resetException(bnl.exception);
			return bnl.list;
		}
	}

	//-------------------------------------------------------
	// Batch Base Command
	//-------------------------------------------------------

	private static abstract class AsyncBatchCommand extends AsyncMultiCommand {
		final BatchNode batch;
		final BatchPolicy batchPolicy;
		int sequenceAP;
		int sequenceSC;

		public AsyncBatchCommand(AsyncMultiExecutor parent, BatchNode batch, BatchPolicy batchPolicy, boolean isOperation) {
			super(parent, batch.node, batchPolicy, isOperation);
			this.batch = batch;
			this.batchPolicy = batchPolicy;
		}

		@Override
		protected boolean prepareRetry(boolean timeout) {
			if (parent.done || ! (policy.replica == Replica.SEQUENCE || policy.replica == Replica.PREFER_RACK)) {
				// Perform regular retry to same node.
				return true;
			}
			sequenceAP++;

			if (! timeout || policy.readModeSC != ReadModeSC.LINEARIZE) {
				sequenceSC++;
			}
			return false;
		}

		@Override
		protected boolean retryBatch(Runnable other, long deadline) {
			// Retry requires keys for this node to be split among other nodes.
			// This can cause an exponential number of commands.
			List<BatchNode> batchNodes = generateBatchNodes();

			if (batchNodes.size() == 0 || (batchNodes.size() == 1 && batchNodes.get(0).node == batch.node)) {
				// Go through normal retry.
				return false;
			}

			AsyncMultiCommand[] cmds = new AsyncMultiCommand[batchNodes.size()];
			int count = 0;

			for (BatchNode batchNode : batchNodes) {
				AsyncBatchCommand cmd = createCommand(batchNode);
				cmd.sequenceAP = sequenceAP;
				cmd.sequenceSC = sequenceSC;
				cmds[count++] = cmd;
			}
			parent.executeBatchRetry(cmds, this, other, deadline);
			return true;
		}

		abstract AsyncBatchCommand createCommand(BatchNode batchNode);
		abstract List<BatchNode> generateBatchNodes();
	}
}
