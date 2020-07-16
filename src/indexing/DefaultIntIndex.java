package indexing;

import com.koloboke.collect.map.IntIntCursor;
import com.koloboke.collect.map.IntIntMap;
import com.koloboke.collect.map.hash.HashIntIntMaps;

import config.LoggingConfig;
import config.ParallelConfig;
import data.IntData;
import joining.join.IndexAccessInfo;

/**
 * Indexes integer values (not necessarily unique).
 * 
 * @author immanueltrummer
 *
 */
public class DefaultIntIndex extends IntIndex {
	/**
	 * Integer data that the index refers to.
	 */
	public final IntData intData;
	/**
	 * After indexing: maps search key to index
	 * of first position at which associated
	 * information is stored.
	 */
	public IntIntMap keyToPositions;
	/**
	 * Cache value of last index access.
	 */
	int lastValue = -1;
	/**
	 * Cache position returned during last
	 * index access.
	 */
	int lastPos = -1;
	/**
	 * Cache tuple returned during 
	 * last index access.
	 */
	int lastTuple = -1;
	/**
	 * Assign a group id to each key if the column
	 * is dense.
	 */
	private IntIntMap keyToGroups;
	/**
	 * Counts number of occurrences for each key within
	 * a given data batch, returns a map from keys to
	 * counts.
	 * 
	 * @param batchID	count key occurrences in this data batch
	 * @return			map from keys to counts
	 */
	/*
	IntIntMap countKeysInBatch(int batchID) {
		int[] data = intData.data;
		IntIntMap keyToNr = HashIntIntMaps.newMutableMap();
		int batchSize = cardinality / 500;
		int startRow = batchID * batchSize;
		int endRow = Math.min(cardinality-1, startRow + batchSize-1);
		for (int i=startRow; i<=endRow; ++i) {
			// Don't index null values
			if (!intData.isNull.get(i)) {
				int value = data[i];
				int nr = keyToNr.getOrDefault(value, 0);
				keyToNr.put(value, nr+1);
			}
		}
		return keyToNr;
	}
	*/
	/**
	 * Combines key counts from two batches.
	 * 
	 * @param keyCount1	key occurrence frequencies in first batch
	 * @param keyCount2	key occurrence frequencies in second batch	
	 * @return			combined key count
	 */
	/*
	IntIntMap combineKeyCounts(IntIntMap keyCount1, IntIntMap keyCount2) {
		IntIntMap newKeyCount = HashIntIntMaps.newMutableMap();
		newKeyCount.putAll(keyCount1);
		IntIntCursor keyToNrCursor = keyCount2.cursor();
		while (keyToNrCursor.moveNext()) {
			int key = keyToNrCursor.key();
			int curCount2 = keyToNrCursor.value();
			int curCount1 = keyCount1.getOrDefault(key, 0);
			newKeyCount.put(key, curCount1 + curCount2);
		}
		return newKeyCount;
	}
	*/
	/**
	 * Create index on the given integer column.
	 * 
	 * @param intData	integer data to index
	 */
	public DefaultIntIndex(IntData intData) {
		super(intData.cardinality);
		long startMillis = System.currentTimeMillis();
		// Extract info
		this.intData = intData;
		int nrThreads = ParallelConfig.JOIN_THREADS;
		int[] data = intData.data;
		// Count for each key the number of occurrences
		/*
		int[] sortedData = Arrays.copyOf(data, cardinality);
		Arrays.parallelSort(sortedData);
		IntIntMap keyToNr = HashIntIntMaps.newMutableMap();
		int nrOccurrences = 0;
		int curKey = sortedData[0];
		for (int i=0; i<cardinality; ++i) {
			nrOccurrences++;
			// About to start new value?
			if (i==cardinality-1 || curKey != sortedData[i+1]) {
				keyToNr.put(curKey, nrOccurrences);
				nrOccurrences = 0;
				curKey = i<cardinality-1?sortedData[i+1]:0;
			}
		}
		*/
		/*
		if (cardinality > 10000 && ParallelConfig.PARALLEL) {
			int batchSize = cardinality/500;
			int nrBatches = (int)Math.ceil((double)cardinality/batchSize);
			keyToNr = IntStream.range(0, nrBatches).parallel().
					mapToObj(b -> countKeysInBatch(b)).
					reduce(HashIntIntMaps.newMutableMap(), 
						(m1, m2) -> combineKeyCounts(m1,m2));
		} else {
		*/
		IntIntMap keyToNr = HashIntIntMaps.newMutableMap();
		for (int i=0; i<cardinality; ++i) {
			// Don't index null values
			if (!intData.isNull.get(i)) {
				int value = data[i];
				//keyToNr.merge(value, 1, Integer::sum);
				int nr = keyToNr.getOrDefault(value, 0);
				keyToNr.put(value, nr+1);
			}
		}
		// Assign each key to the appropriate position offset
		int nrKeys = keyToNr.size();
		log("Number of keys:\t" + nrKeys);
		keyToPositions = HashIntIntMaps.newMutableMap(nrKeys);
		int prefixSum = 0;
		IntIntCursor keyToNrCursor = keyToNr.cursor();
		while (keyToNrCursor.moveNext()) {
			int key = keyToNrCursor.key();
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
			if (!intData.isNull.get(i)) {
				int key = data[i];
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
			keyToGroups = HashIntIntMaps.newMutableMap(nrKeys);
			int groupID = 0;
			IntIntCursor keyToPosCursor = keyToPositions.cursor();
			while (keyToPosCursor.moveNext()) {
				int key = keyToPosCursor.key();
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
		// Check index if enabled
		IndexChecker.checkIndex(intData, this);
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
	@Override
	public int nextTuple(int value, int prevTuple) {
		// Get start position for indexed values
		int firstPos = keyToPositions.getOrDefault(value, -1);
		// No indexed values?
		if (firstPos < 0) {
//			JoinStats.nrUniqueIndexLookups += 1;
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
		// Exploit lookup cache if possible
		if (lastPos != -1 && lastValue == value && 
				lastTuple <= prevTuple) {
			lowerBound = lastPos + 1;
		}
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
				// Cache details about lookup
				lastValue = value;
				lastPos = pos;
				lastTuple = positions[pos];
				// Return tuple at position
				return lastTuple;
			}
		}
		// No suitable tuple found
		lastPos = -1;
		return cardinality;
	}

	@Override
	public int nextTuple(int value, int prevTuple, IndexAccessInfo accessInfo) {
		// Get start position for indexed values
		int firstPos = keyToPositions.getOrDefault(value, -1);
		// No indexed values?
		if (firstPos < 0) {
//			JoinStats.nrUniqueIndexLookups += 1;
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
		// Exploit lookup cache if possible
		int lastPos = accessInfo.lastPos;
		int lastValue = accessInfo.lastValue;
		int lastTuple = accessInfo.lastTuple;
		if (lastPos != -1 && lastValue == value &&
				lastTuple <= prevTuple) {
			lowerBound = lastPos + 1;
		}
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
			int newTuple = positions[pos];
			if (newTuple > prevTuple) {
				// Cache details about lookup
				accessInfo.lastValue = value;
				accessInfo.lastPos = pos;
				accessInfo.lastTuple = newTuple;
				// Return tuple at position
				return newTuple;
			}
		}
		// No suitable tuple found
		accessInfo.lastPos = -1;
		return cardinality;
	}

	@Override
	public int nextTuple(int value, int prevTuple, int priorIndex, int tid,
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
	@Override
	public int nrIndexed(int value) {
		int firstPos = keyToPositions.getOrDefault(value, -1);
		if (firstPos<0) {
			return 0;
		} else {
			return positions[firstPos];
		}
	}

	@Override
	public int getGroupID(int row) {
		int value = intData.data[row];
		return keyToGroups.getOrDefault(value, 0);
	}
}
