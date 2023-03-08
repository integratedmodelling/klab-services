package org.integratedmodelling.klab.api.lang.kim;

import java.util.List;

public interface KimTable extends KimStatement {
	/**
	 * The headers for the table, or null if not specified. Also null if the table is
	 * two-ways.
	 * 
	 * @return headers or null
	 */
	List<String> getHeaders();

	/**
	 * If the table is two-way, the headers are mandatory and describe both rows and columns.
	 * 
	 * @return
	 */
	boolean isTwoWay();
	
	/**
	 * Only not-null if the table is two-ways.
	 * 
	 * @return
	 */
	List<KimClassifier> getRowClassifiers();

    /**
     * Only not-null if the table is two-ways.
     * 
     * @return
     */
	List<KimClassifier> getColumnClassifiers();

	int getRowCount();

	int getColumnCount();

	KimClassifier[] row(int i);
	
	/**
	 * Row classifiers start from the second element if the table is two-way.
	 * 
	 * @return
	 */
	List<KimClassifier[]> rows();

}
