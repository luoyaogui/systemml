/**
 * (C) Copyright IBM Corp. 2010, 2015
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.ibm.bi.dml.test.integration.functions.append;

import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.bi.dml.api.DMLScript;
import com.ibm.bi.dml.api.DMLScript.RUNTIME_PLATFORM;
import com.ibm.bi.dml.lops.LopProperties.ExecType;
import com.ibm.bi.dml.runtime.instructions.Instruction;
import com.ibm.bi.dml.runtime.matrix.MatrixCharacteristics;
import com.ibm.bi.dml.runtime.matrix.data.MatrixValue.CellIndex;
import com.ibm.bi.dml.test.integration.AutomatedTestBase;
import com.ibm.bi.dml.test.integration.TestConfiguration;
import com.ibm.bi.dml.test.utils.TestUtils;
import com.ibm.bi.dml.utils.Statistics;

public class RBindCBindMatrixTest extends AutomatedTestBase
{	
	private final static String TEST_NAME1 = "RBindMatrixTest";      //basic rbind test
	private final static String TEST_NAME2 = "RBindCBindMatrixTest"; //cbind rewritten to rbind
	private final static String TEST_DIR = "functions/append/";
	private final static String TEST_CLASS_DIR = TEST_DIR + RBindCBindMatrixTest.class.getSimpleName() + "/";
	
	private final static double epsilon=0.0000000001;
	private final static int min=1;
	private final static int max=100;
	
	private final static int rows1 = 1059;
	private final static int rows2 = 1010;
	private final static int cols = 73; //single block
		
	private final static double sparsity1 = 0.45;
	private final static double sparsity2 = 0.01;
	
	@Override
	public void setUp() {
		TestUtils.clearAssertionInformation();
		addTestConfiguration(TEST_NAME1, new TestConfiguration(TEST_CLASS_DIR, TEST_NAME1, new String[] {"C"}));
		addTestConfiguration(TEST_NAME2, new TestConfiguration(TEST_CLASS_DIR, TEST_NAME2, new String[] {"C"}));
	}

	@Test
	public void testRBindDenseCP() {
		runRBindTest(TEST_NAME1, false, ExecType.CP);
	}
	
	@Test
	public void testRBindSparseCP() {
		runRBindTest(TEST_NAME1, true, ExecType.CP);
	}
	
	@Test
	public void testCBindDenseCP() {
		runRBindTest(TEST_NAME2, false, ExecType.CP);
	}
	
	@Test
	public void testCBindSparseCP() {
		runRBindTest(TEST_NAME2, true, ExecType.CP);
	}
	
	
	@Test
	public void testRBindDenseSP() {
		runRBindTest(TEST_NAME1, false, ExecType.SPARK);
	}
	
	@Test
	public void testRBindSparseSP() {
		runRBindTest(TEST_NAME1, true, ExecType.SPARK);
	}
	
	@Test
	public void testCBindDenseSP() {
		runRBindTest(TEST_NAME2, false, ExecType.SPARK);
	}
	
	@Test
	public void testCBindSparseSP() {
		runRBindTest(TEST_NAME2, true, ExecType.SPARK);
	}
	

	@Test
	public void testRBindDenseMR() {
		runRBindTest(TEST_NAME1, false, ExecType.MR);
	}
	
	@Test
	public void testRBindSparseMR() {
		runRBindTest(TEST_NAME1, true, ExecType.MR);
	}
	
	@Test
	public void testCBindDenseMR() {
		runRBindTest(TEST_NAME2, false, ExecType.MR);
	}
	
	@Test
	public void testCBindSparseMR() {
		runRBindTest(TEST_NAME2, true, ExecType.MR);
	}
	
	
	/**
	 * 
	 * @param platform
	 * @param rows
	 * @param cols1
	 * @param cols2
	 * @param sparse
	 */
	public void runRBindTest(String testname, boolean sparse, ExecType et)
	{		
		RUNTIME_PLATFORM platformOld = rtplatform;
		switch( et ){
			case MR: rtplatform = RUNTIME_PLATFORM.HADOOP; break;
			case SPARK: rtplatform = RUNTIME_PLATFORM.SPARK; break;
			default: rtplatform = RUNTIME_PLATFORM.HYBRID; break;
		}
	
		boolean sparkConfigOld = DMLScript.USE_LOCAL_SPARK_CONFIG;
		if( rtplatform == RUNTIME_PLATFORM.SPARK )
			DMLScript.USE_LOCAL_SPARK_CONFIG = true;

		String TEST_NAME = testname;
		TestConfiguration config = getTestConfiguration(TEST_NAME);
		loadTestConfiguration(config);
		double sparsity = (sparse) ? sparsity2 : sparsity1; 
		
		try
		{	          
			String RI_HOME = SCRIPT_DIR + TEST_DIR;
			fullDMLScriptName = RI_HOME + TEST_NAME + ".dml";
			//stats required for opcode checks
			programArgs = new String[]{"-stats","-args",  input("A"), input("B"), output("C") };
			
			fullRScriptName = RI_HOME + TEST_NAME + ".R";
			rCmd = "Rscript" + " " + fullRScriptName + " " + inputDir() + " "+ expectedDir();
			
			double[][] A = getRandomMatrix(rows1, cols, min, max, sparsity, 823);
	        writeInputMatrixWithMTD("A", A, true);
	        double[][] B= getRandomMatrix(rows2, cols, min, max, sparsity, 923);
	        writeInputMatrixWithMTD("B", B, true);
	        
	        //execute dml and r scripts
	        runTest(true, false, null, -1);
			runRScript(true);
	
			//compare results
			HashMap<CellIndex, Double> dmlfile = readDMLMatrixFromHDFS("C");
			HashMap<CellIndex, Double> rfile = readRMatrixFromFS("C");
			TestUtils.compareMatrices(dmlfile, rfile, epsilon, "DML", "R");
			
			//check dml output meta data
			checkDMLMetaDataFile("C", new MatrixCharacteristics(rows1+rows2,cols,1,1));
			
			//check applied rewrite t(cbind(t(A),t(B)) --> rbind(A,B)
			if( testname.equals(TEST_NAME2) ){
				String opcode = ((et==ExecType.SPARK)?Instruction.SP_INST_PREFIX:"")+"r'";
				Assert.assertTrue("Rewrite not applied", !Statistics.getCPHeavyHitterOpCodes().contains(opcode) );
			}
		}
		finally
		{
			//reset execution platform
			rtplatform = platformOld;
			DMLScript.USE_LOCAL_SPARK_CONFIG = sparkConfigOld;
		}
	}
}
