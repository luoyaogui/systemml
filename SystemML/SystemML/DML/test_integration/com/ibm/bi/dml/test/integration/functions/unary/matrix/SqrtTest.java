/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2013
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */

package com.ibm.bi.dml.test.integration.functions.unary.matrix;

import org.junit.Test;

import com.ibm.bi.dml.runtime.DMLRuntimeException;
import com.ibm.bi.dml.test.integration.AutomatedTestBase;
import com.ibm.bi.dml.test.integration.TestConfiguration;



public class SqrtTest extends AutomatedTestBase 
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2013\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";
	
	@Override
	public void setUp() {
		baseDirectory = SCRIPT_DIR + "functions/unary/matrix/";
		availableTestConfigurations.put("PositiveTest",
				new TestConfiguration("SqrtTest", new String[] { "vector", "matrix" }));
		availableTestConfigurations.put("NegativeVectorTest",
				new TestConfiguration("SqrtSingleTest", new String[] { "out" }));
		availableTestConfigurations.put("NegativeMatrixTest",
				new TestConfiguration("SqrtSingleTest", new String[] { "out" }));
	}
	
	@Test
	public void testPositive() {
		int rows = 10;
		int cols = 10;
		
		TestConfiguration config = availableTestConfigurations.get("PositiveTest");
		config.addVariable("rows", rows);
		config.addVariable("cols", cols);
		
		loadTestConfiguration("PositiveTest");
		
		double[][] vector = getRandomMatrix(rows, 1, 0, 1, 1, -1);
		double[][] SqrtVector = new double[rows][1];
		for(int i = 0; i < rows; i++) {
			SqrtVector[i][0] = Math.sqrt(vector[i][0]);
		}
		writeInputMatrix("vector", vector);
		writeExpectedMatrix("vector", SqrtVector);
		
		double[][] matrix = getRandomMatrix(rows, cols, 0, 1, 1, -1);
		double[][] absMatrix = new double[rows][cols];
		for(int i = 0; i < rows; i++) {
			for(int j = 0; j < cols; j++) {
				absMatrix[i][j] = Math.sqrt(matrix[i][j]);
			}
		}
		writeInputMatrix("matrix", matrix);
		writeExpectedMatrix("matrix", absMatrix);
		
		runTest();
		
		compareResults();
	}
	
	@Test
	public void testNegativeVector() {
		int rows = 10;
		int cols = 1;
		
		TestConfiguration config = availableTestConfigurations.get("NegativeVectorTest");
		config.addVariable("rows", rows);
		config.addVariable("cols", cols);
		
		loadTestConfiguration("NegativeVectorTest");
		
		createRandomMatrix("in", rows, cols, -1, 0, 1, -1);
		
		runTest(false, DMLRuntimeException.class);
	}
	
	@Test
	public void testNegativeMatrix() {
		int rows = 10;
		int cols = 10;
		
		TestConfiguration config = availableTestConfigurations.get("NegativeMatrixTest");
		config.addVariable("rows", rows);
		config.addVariable("cols", cols);
		
		loadTestConfiguration("NegativeMatrixTest");
		
		createRandomMatrix("in", rows, cols, -1, 0, 1, -1);
		
		runTest(false, DMLRuntimeException.class);
	}
	
}