package dml.hops;

import dml.lops.Aggregate;
import dml.lops.Group;
import dml.lops.Lops;
import dml.lops.RangeBasedReIndex;
import dml.lops.LopProperties.ExecType;
import dml.parser.Expression.DataType;
import dml.parser.Expression.ValueType;
import dml.sql.sqllops.SQLLops;
import dml.utils.HopsException;

//for now only works for range based indexing op
public class IndexingOp extends Hops {

	public IndexingOp(String l, DataType dt, ValueType vt, Hops inpMatrix, Hops inpRowL, Hops inpRowU, Hops inpColL, Hops inpColU) {
		super(Kind.Indexing, l, dt, vt);
		/*
		if(inpRowL==null)
			inpRowL=new DataOp("1", DataType.SCALAR, ValueType.INT, DataOpTypes.PERSISTENTREAD, "1", -1, -1, -1, -1);
		if(inpRowU==null)
			inpRowU=new DataOp(Long.toString(get_dim1()), DataType.SCALAR, ValueType.INT, DataOpTypes.PERSISTENTREAD, Long.toString(get_dim1()), -1, -1, -1, -1);
		if(inpColL==null)
			inpColL=new DataOp("1", DataType.SCALAR, ValueType.INT, DataOpTypes.PERSISTENTREAD, "1", -1, -1, -1, -1);
		if(inpColU==null)
			inpColU=new DataOp(Long.toString(get_dim2()), DataType.SCALAR, ValueType.INT, DataOpTypes.PERSISTENTREAD, Long.toString(get_dim2()), -1, -1, -1, -1);
	*/
		getInput().add(0, inpMatrix);
		getInput().add(1, inpRowL);
		getInput().add(2, inpRowU);
		getInput().add(3, inpColL);
		getInput().add(4, inpColU);
		
		// create hops if one of them is null
		inpMatrix.getParent().add(this);
		inpRowL.getParent().add(this);
		inpRowU.getParent().add(this);
		inpColL.getParent().add(this);
		inpColU.getParent().add(this);

	}

	public Lops constructLops()
			throws HopsException {
		if (get_lops() == null) {
			try {
				ExecType et = optFindExecType();
				if(et!= ExecType.MR)
					throw new HopsException("Indexing is not supporte in CP yet!");
				
				RangeBasedReIndex reindex = new RangeBasedReIndex(
						getInput().get(0).constructLops(), getInput().get(1).constructLops(), getInput().get(2).constructLops(),
						getInput().get(3).constructLops(), getInput().get(4).constructLops(), get_dim1(), get_dim2(),
						DataType.MATRIX, get_valueType());

				Group group1 = new Group(
						reindex, Group.OperationTypes.Sort, DataType.MATRIX,
						get_valueType());
				group1.getOutputParameters().setDimensions(get_dim1(),
						get_dim2(), get_rows_per_block(), get_cols_per_block());

				Aggregate agg1 = new Aggregate(
						group1, Aggregate.OperationTypes.Sum, DataType.MATRIX,
						get_valueType(), et);
				agg1.getOutputParameters().setDimensions(get_dim1(),
						get_dim2(), get_rows_per_block(), get_cols_per_block());

				set_lops(agg1);
			} catch (Exception e) {
				throw new HopsException(e);
			}

		}
		return get_lops();
	}

	@Override
	public String getOpString() {
		String s = new String("");
		s += "Indexing";
		return s;
	}

	public void printMe() throws HopsException {
		if (get_visited() != VISIT_STATUS.DONE) {
			super.printMe();
			for (Hops h : getInput()) {
				h.printMe();
			}
			;
		}
		set_visited(VISIT_STATUS.DONE);
	}

	public SQLLops constructSQLLOPs() throws HopsException {
		throw new HopsException("IndexingOp.constructSQLLOPs shoule not be called");
	}
	
	@Override
	protected ExecType optFindExecType() throws HopsException {
		return ExecType.MR;
	}
}
