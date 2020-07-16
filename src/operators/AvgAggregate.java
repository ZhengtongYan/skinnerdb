package operators;

import buffer.BufferManager;
import catalog.CatalogManager;
import catalog.info.ColumnInfo;
import catalog.info.TableInfo;
import data.ColumnData;
import data.DoubleData;
import data.IntData;
import data.LongData;
import query.ColumnRef;
import types.SQLtype;

import java.util.stream.IntStream;

/**
 * Calculates the average (total or per group)
 * from a given input column.
 * 
 * @author Ziyun Wei
 *
 */
public class AvgAggregate {
	/**
	 * Calculates aggregate from source data for each group
	 * (or total if no groups are specified) and stores
	 * result in given target column.
	 * 
	 * @param sourceRef		reference to source column
	 * @param nrGroups		number of groups
	 * @param groupRef		reference to group column
	 * @param targetRef		store results in this column
	 * @throws Exception
	 */
	public static void execute(ColumnRef sourceRef, int nrGroups,
                               ColumnRef groupRef, ColumnRef targetRef) throws Exception {
		// Get information about source column
		String srcRel = sourceRef.aliasName;
		SQLtype srcType = CatalogManager.getColumn(sourceRef).type;
		int srcCard = CatalogManager.getCardinality(srcRel);
		ColumnData srcData = BufferManager.getData(sourceRef);
		// Create row to group assignments
		boolean grouping = groupRef != null;
		int[] groups = grouping?((IntData)
				BufferManager.getData(groupRef)).data:
					new int[srcCard];
		// Generate target column
		int targetCard = grouping ? nrGroups:1;
		ColumnData genericTarget = null;
		IntData intTarget = null;
		LongData longTarget = null;
		DoubleData doubleTarget = null;
		switch (srcType) {
		case INT:
			intTarget = new IntData(targetCard);
			genericTarget = intTarget;
			BufferManager.colToData.put(targetRef, intTarget);
			break;
		case LONG:
			longTarget = new LongData(targetCard);
			genericTarget = longTarget;
			BufferManager.colToData.put(targetRef, longTarget);
			break;
		case DOUBLE:
			doubleTarget = new DoubleData(targetCard);
			genericTarget = doubleTarget;
			BufferManager.colToData.put(targetRef, doubleTarget);
			break;
		default:
			throw new Exception("Error - no sum over " + 
					srcType + " allowed");
		}
		// Register target column in catalog
		String targetRel = targetRef.aliasName;
		String targetCol = targetRef.columnName;
		TableInfo targetRelInfo = CatalogManager.
				currentDB.nameToTable.get(targetRel);
		ColumnInfo targetColInfo = new ColumnInfo(targetCol,
				srcType, false, false, false, false);
		targetRelInfo.addColumn(targetColInfo);
		// Update catalog statistics on result table
		CatalogManager.updateStats(targetRel);
		// Set target values to null
		for (int row=0; row<targetCard; ++row) {
			genericTarget.isNull.set(row);
		}
		int[] numbers = new int[targetCard];
		// Switch according to column type (to avoid casts)
		switch (srcType) {
		case INT:
			IntData intSrc = (IntData)srcData;
			// Iterate over input column
			for (int row = 0; row < srcCard; ++row) {
				// Check for null values
				if (!srcData.isNull.get(row)) {
					int group = groups[row];
					genericTarget.isNull.set(group, false);
					intTarget.data[group] += intSrc.data[row];
					numbers[group]++;
				}
			}
			IntData finalIntTarget = intTarget;
//			IntStream.range(0, targetCard).parallel().forEach(i -> finalIntTarget.data[i] /= numbers[i]);
			IntStream.range(0, targetCard).forEach(i -> finalIntTarget.data[i] /= numbers[i]);

			break;
		case DOUBLE:
			DoubleData doubleSrc = (DoubleData)srcData;
			// Iterate over input column
			for (int row=0; row<srcCard; ++row) {
				// Check for null values
				if (!srcData.isNull.get(row)) {
					int group = groups[row];
					genericTarget.isNull.set(group, false);
					doubleTarget.data[group] += doubleSrc.data[row];
					numbers[group]++;
				}
			}
			DoubleData finalDoubleTarget = doubleTarget;
//			IntStream.range(0, targetCard).parallel().forEach(i -> finalDoubleTarget.data[i] /= numbers[i]);
			IntStream.range(0, targetCard).forEach(i -> finalDoubleTarget.data[i] /= numbers[i]);
			break;
		default:
			throw new Exception("Unsupported type: " + srcType);
		}
	}
}