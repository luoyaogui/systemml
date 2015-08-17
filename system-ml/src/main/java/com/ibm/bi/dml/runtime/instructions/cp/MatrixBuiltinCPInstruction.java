/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2015
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */

package com.ibm.bi.dml.runtime.instructions.cp;

import com.ibm.bi.dml.runtime.DMLRuntimeException;
import com.ibm.bi.dml.runtime.DMLUnsupportedOperationException;
import com.ibm.bi.dml.runtime.controlprogram.caching.MatrixObject;
import com.ibm.bi.dml.runtime.controlprogram.context.ExecutionContext;
import com.ibm.bi.dml.runtime.matrix.data.LibCommonsMath;
import com.ibm.bi.dml.runtime.matrix.data.MatrixBlock;
import com.ibm.bi.dml.runtime.matrix.operators.Operator;
import com.ibm.bi.dml.runtime.matrix.operators.UnaryOperator;


public class MatrixBuiltinCPInstruction extends BuiltinUnaryCPInstruction
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2015\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";
	
	public MatrixBuiltinCPInstruction(Operator op,
									  CPOperand in,
									  CPOperand out,
									  String opcode,
									  String instr){
		super(op, in, out, 1, opcode, instr);
	}

	@Override 
	public void processInstruction(ExecutionContext ec) 
		throws DMLRuntimeException, DMLUnsupportedOperationException 
	{	
		UnaryOperator u_op = (UnaryOperator) _optr;
		String output_name = output.getName();
		MatrixBlock resultBlock = null;
		
		String opcode = getOpcode();
		if(LibCommonsMath.isSupportedUnaryOperation(opcode)) {
			resultBlock = LibCommonsMath.unaryOperations((MatrixObject)ec.getVariable(input1.getName()),getOpcode());
			ec.setMatrixOutput(output_name, resultBlock);
		}
		else {
			MatrixBlock matBlock = ec.getMatrixInput(input1.getName());
			resultBlock = (MatrixBlock) (matBlock.unaryOperations(u_op, new MatrixBlock()));
			
			ec.setMatrixOutput(output_name, resultBlock);
			ec.releaseMatrixInput(input1.getName());
		}
		
	}
}