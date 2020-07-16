package indexing;

import com.koloboke.collect.map.DoubleIntCursor;
import com.koloboke.collect.map.DoubleIntMap;
import com.koloboke.collect.map.hash.HashDoubleIntMaps;

import config.LoggingConfig;
import config.ParallelConfig;
import data.DoubleData;
import joining.join.IndexAccessInfo;
import statistics.JoinStats;

/**
 * Indexes double values (not necessarily unique).
 * 
 * @author immanueltrummer
 *
 */
public class DoubleIndex extends Index {
	/**
	 * Double data that the index refers to.
	 */
	public final DoubleData doubleData;
	/**
	 * After indexing: maps search key to index
	 * of first position at which associated
	 * information is stored.
	 */
	public DoubleIntMap keyToPositions;
	/**
	 * Assign a group id to each key if the column
	 * is dense.
	 */
	private DoubleIntMap keyToGroups;
	/**
	 * Create index on the given double column.
	 * 
	 * @param doubleData	double data to index
	 */
	public DoubleIndex(DoubleData doubleData) {
		super(doubleData.cardinality);
		long startMillis = System.currentTimeMillis();
		// Extract info
		int nrThreads = ParallelConfig.JOIN_THREADS;
		this.doubleData = doubleData;
		double[] data = doubleData.data;
		// Count number of occurrences for each value
		DoubleIntMap keyToNr = HashDoubleIntMaps.newMutableMap();
		for (int i=0; i<cardinality; ++i) {
			// Don't index null values
			if (!doubleData.isNull.get(i)) {
				double value = data[i];
				int nr = keyToNr.getOrDefault(value, 0);
				keyToNr.put(value, nr+1);				
			}
		}
		// Assign each key to the appropriate position offset
		int nrKeys = keyToNr.size();
		log("Number of keys:\t" + nrKeys);
		keyToPositions = HashDoubleIntMaps.newMutableMap(nrKeys);
		int prefixSum = 0;
		DoubleIntCursor keyToNrCursor = keyToNr.cursor();
		while (keyToNrCursor.moveNext()) {
			double key = keyToNrCursor.key();
			keyToPositions.put(key, prefixSum);
			// Advance offset taking into account
			// space for row indices and one field
			// storing the number of following indices.
			int nrFields = keyToNrCursor.value() + 1;
			prefixSum += nrFields;
		}
		log("Prefix sum:\t" + prefixSum);
		// Generate position information
		positions = new int[prefixSum];
		threadForRows = new byte[cardinality];
		for (int i=0; i<cardinality; ++i) {
			if (!doubleData.isNull.get(i)) {
				double key = data[i];
				int startPos = keyToPositions.get(key);
				positions[startPos] += 1;
				int offset = positions[startPos];
				threadForRows[i] = (byte) ((offset - 1) % nrThreads);
				int pos = startPos + offset;
				positions[pos] = i;				
			}
		}
		this.nrKeys = nrKeys;
		if (nrKeys < 10) {
			keyToGroups = HashDoubleIntMaps.newMutableMap(nrKeys);
			int groupID = 0;
			DoubleIntCursor keyToPosCursor = keyToPositions.cursor();
			while (keyToPosCursor.moveNext()) {
				double key = keyToPosCursor.key();
				keyToGroups.put(key, groupID);
				groupID++;
			}
		}
		// Output statistics for performance tuning
		if (LoggingConfig.INDEXING_VERBOSE) {
			long totalMillis = System.currentTimeMillis() - startMillis;
			log("Created index for integer column with cardinality " + 
					cardinality + " in " + totalMillis + " ms.");
		}
	}
	/**
	 * Returns index of next tuple with given value
	 * or cardinality of indexed table if no such
	 * tuple exists.
	 * 
	 * @param value			indexed value
	 * @param prevTuple		index of last tuple
	 * @return 	index of next tuple or cardinality
	 */
	public int nextTuple(double value, int prevTuple) {
		// Get start position for indexed values
		int firstPos = keyToPositions.getOrDefault(value, -1);
		// No indexed values?
		if (firstPos < 0) {
			JoinStats.nrUniqueIndexLookups += 1;
			return cardinality;
		}
		// Can we return first indexed value?
		int firstTuple = positions[firstPos+1];
		if (firstTuple>prevTuple) {
			return firstTuple;
		}
		// Get number of indexed values
		int nrVals = positions[firstPos];

		// Restrict search range via binary search
		int lowerBound = firstPos + 1;
		int upperBound = firstPos + nrVals;
		while (upperBound-lowerBound>1) {
			int middle = lowerBound + (upperBound-lowerBound)/2;
			if (positions[middle] > prevTuple) {
				upperBound = middle;
			} else {
				lowerBound = middle;
			}
		}
		// Get next tuple
		for (int pos=lowerBound; pos<=upperBound; ++pos) {
			if (positions[pos] > prevTuple) {
				return positions[pos];
			}
		}
		// No suitable tuple found
		return cardinality;
	}

	/**
	 * Returns index of next tuple with given value
	 * or cardinality of indexed table if no such
	 * tuple exists. In order to apply cache for multi-threads,
	 * cached statistics for the indexes are moved to the
	 * according join operator.
	 *
	 * @param value			indexed value
	 * @param prevTuple		index of last tuple
	 * @param accessInfo	index access information
	 *
	 * @return 	index of next tuple or cardinality
	 */
	public int nextTuple(double value, int prevTuple, IndexAccessInfo accessInfo) {
		// Get start position for indexed values
		int firstPos = keyToPositions.getOrDefault(value, -1);
		// No indexed values?
		if (firstPos < 0) {
			JoinStats.nrUniqueIndexLookups += 1;
			return cardinality;
		}
		// Can we return first indexed value?
		int firstTuple = positions[firstPos+1];
		if (firstTuple>prevTuple) {
			return firstTuple;
		}
		// Get number of indexed values
		int nrVals = positions[firstPos];
		accessInfo.lastNrVals = nrVals;

		// Restrict search range via binary search
		int lowerBound = firstPos + 1;
		int upperBound = firstPos + nrVals;
		while (upperBound-lowerBound>1) {
			int middle = lowerBound + (upperBound-lowerBound)/2;
			if (positions[middle] > prevTuple) {
				upperBound = middle;
			} else {
				lowerBound = middle;
			}
		}
		// Get next tuple
		for (int pos=lowerBound; pos<=upperBound; ++pos) {
			if (positions[pos] > prevTuple) {
				return positions[pos];
			}
		}
		// No suitable tuple found
		return cardinality;
	}

	/**
	 * Returns index of next tuple with given value
	 * or cardinality of indexed table if no such
	 * tuple exists in the thread's partition.
	 *
	 * @param value			indexed value
	 * @param prevTuple		index of last tuple
	 * @param priorIndex	index of last tuple in the prior table
	 * @param tid			thread id
	 * @param accessInfo	index access information
	 * @return 	index of next tuple or cardinality
	 */
	public int nextTuple(double value, int prevTuple, int priorIndex, int tid,
						 IndexAccessInfo accessInfo) {
		int nrThreads = ParallelConfig.JOIN_THREADS;
		// make sure the first tuple doesn't always start from thread 0.
		tid = (priorIndex + tid) % nrThreads;
		// get start position for indexed values
		int firstPos = keyToPositions.getOrDefault(value, -1);
		// no indexed values?
		if (firstPos < 0) {
			return cardinality;
		}
		// can we return the first indexed value?
		int nrVals = positions[firstPos];
		accessInfo.lastNrVals = nrVals;

		int firstOffset = tid + 1;
		if (firstOffset > nrVals) {
			return cardinality;
		}
		int firstTuple = positions[firstPos + firstOffset];
		if (firstTuple > prevTuple) {
			return firstTuple;
		}
		// get number of indexed values in the partition
		int lastOffset = (nrVals - 1) / nrThreads * nrThreads + tid + 1;
		// if the offset is beyond the array?
		if (lastOffset > nrVals) {
			lastOffset -= nrThreads;
		}
		int threadVals = (lastOffset - firstOffset) / nrThreads + 1;
		// update index-related statistics
		// restrict search range via binary search in the partition
		int lowerBound = 0;
		int upperBound = threadVals - 1;
		while (upperBound - lowerBound > 1) {
			int middle = lowerBound + (upperBound - lowerBound) / 2;
			int middleOffset = firstPos + middle * nrThreads + tid + 1;
			if (positions[middleOffset] > prevTuple) {
				upperBound = middle;
			} else {
				lowerBound = middle;
			}
		}
		// get next tuple
		for (int pos = lowerBound; pos <= upperBound; ++pos) {
			int offset = firstPos + pos * nrThreads + tid + 1;
			int nextTuple = positions[offset];
			if (nextTuple > prevTuple) {
				return nextTuple;
			}
		}
		// no suitable tuple found
		return cardinality;
	}

	/**
	 * Returns the number of entries indexed
	 * for the given value.
	 * 
	 * @param value	count indexed tuples for this value
	 * @return		number of indexed values
	 */
	public int nrIndexed(double value) {
		int firstPos = keyToPositions.getOrDefault(value, -1);
		if (firstPos<0) {
			return 0;
		} else {
			return positions[firstPos];
		}
	}

	@Override
	public int getGroupID(int row) {
		double value = doubleData.data[row];
		return keyToGroups.getOrDefault(value, 0);
	}
}
