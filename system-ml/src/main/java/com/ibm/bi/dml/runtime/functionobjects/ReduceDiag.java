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

package com.ibm.bi.dml.runtime.functionobjects;

import com.ibm.bi.dml.runtime.DMLRuntimeException;
import com.ibm.bi.dml.runtime.matrix.MatrixCharacteristics;
import com.ibm.bi.dml.runtime.matrix.data.MatrixIndexes;
import com.ibm.bi.dml.runtime.matrix.data.MatrixValue.CellIndex;


public class ReduceDiag extends IndexFunction
{

	private static final long serialVersionUID = 734843929521413928L;

	private static ReduceDiag singleObj = null;
	
	private ReduceDiag() {
		// nothing to do here
	}
	
	public static ReduceDiag getReduceDiagFnObject() {
		if ( singleObj == null )
			singleObj = new ReduceDiag();
		return singleObj;
	}
	
	public Object clone() throws CloneNotSupportedException {
		// cloning is not supported for singleton classes
		throw new CloneNotSupportedException();
	}
	
	/*
	 * NOTE: index starts from 1 for cells in a matrix, but index starts from 0 for cells inside a block
	 */
	
	@Override
	public void execute(MatrixIndexes in, MatrixIndexes out) {
		out.setIndexes(1, 1);
	}

	@Override
	public void execute(CellIndex in, CellIndex out) {
		out.set(0, 0);
	}

	@Override
	public boolean computeDimension(int row, int col, CellIndex retDim) {
		retDim.set(1, 1);
		return true;
	}
	
	public boolean computeDimension(MatrixCharacteristics in, MatrixCharacteristics out) throws DMLRuntimeException
	{
		out.set(1, 1, 1, 1);
		return true;
	}
}
