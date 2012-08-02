package com.ibm.bi.dml.api;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.nimble.configuration.NimbleConfig;
import org.nimble.control.DAGQueue;
import org.nimble.control.PMLDriver;
import org.nimble.exception.ConfigurationException;
import org.xml.sax.SAXException;

import com.ibm.bi.dml.lops.Lops;
import com.ibm.bi.dml.packagesupport.PackageRuntimeException;
import com.ibm.bi.dml.parser.DMLProgram;
import com.ibm.bi.dml.parser.DMLQLParser;
import com.ibm.bi.dml.parser.DMLTranslator;
import com.ibm.bi.dml.parser.ParseException;
import com.ibm.bi.dml.runtime.controlprogram.CacheableData;
import com.ibm.bi.dml.runtime.controlprogram.LocalVariableMap;
import com.ibm.bi.dml.runtime.controlprogram.Program;
import com.ibm.bi.dml.runtime.controlprogram.ProgramBlock;
import com.ibm.bi.dml.runtime.controlprogram.WhileProgramBlock;
import com.ibm.bi.dml.runtime.controlprogram.parfor.util.ConfigurationManager;
import com.ibm.bi.dml.runtime.controlprogram.parfor.util.IDHandler;
import com.ibm.bi.dml.runtime.instructions.Instruction.INSTRUCTION_TYPE;
import com.ibm.bi.dml.runtime.matrix.mapred.MRJobConfiguration;
import com.ibm.bi.dml.runtime.util.MapReduceTool;
import com.ibm.bi.dml.sql.sqlcontrolprogram.ExecutionContext;
import com.ibm.bi.dml.sql.sqlcontrolprogram.NetezzaConnector;
import com.ibm.bi.dml.sql.sqlcontrolprogram.SQLProgram;
import com.ibm.bi.dml.utils.DMLException;
import com.ibm.bi.dml.utils.DMLRuntimeException;
import com.ibm.bi.dml.utils.HopsException;
import com.ibm.bi.dml.utils.LanguageException;
import com.ibm.bi.dml.utils.Statistics;
import com.ibm.bi.dml.utils.configuration.DMLConfig;
import com.ibm.bi.dml.utils.visualize.DotGraph;


public class DMLScript {
	//TODO: Change these static variables to non-static, and create corresponding access methods
	public static boolean DEBUG = false;
	public static boolean VISUALIZE = false;
	public static boolean LOG = false;	
	public enum RUNTIME_PLATFORM { HADOOP, SINGLE_NODE, HYBRID, NZ, INVALID };
	// We should assume the default value is HYBRID
	public static RUNTIME_PLATFORM rtplatform = RUNTIME_PLATFORM.HYBRID;
	public static final String _uuid = IDHandler.createDistributedUniqueID(); 
	
	private String _dmlScriptString;
	// stores name of the OPTIONAL config file
	private String _optConfig;
	// stores optional args to parameterize DML script 
	private HashMap<String, String> _argVals;
	
	private Logger _mapredLogger;
	private Logger _mmcjLogger;
	
	public static final String DEFAULT_SYSTEMML_CONFIG_FILEPATH = "./SystemML-config.xml";
	private static final String DEFAULT_MAPRED_LOGGER = "org.apache.hadoop.mapred";
	private static final String DEFAULT_MMCJMR_LOGGER = "dml.runtime.matrix.MMCJMR";
	private static final String LOG_FILE_NAME = "SystemML.log";
	// stores the path to the source
	private static final String PATH_TO_SRC = "./";
	
	public static String USAGE = "Usage is " + DMLScript.class.getCanonicalName() 
			+ " [-f | -s] <filename>" + /*"-exec <runtime>" +  " (-nz)?" + */ " [-d | -debug]?" + " [-l | -log]?" + " (-config=<config_filename>)? (-args)? <args-list>? \n" 
			+ " -f: <filename> will be interpreted as a filename path + \n"
			+ "     <filename> prefixed with hdfs: is hdfs file, otherwise it is local file + \n" 
			+ " -s: <filename> will be interpreted as a DML script string \n"
			//+ " -exec: <runtime> runtime platform (hadoop, nz, sequential)\n"
			+ " [-d | -debug]: (optional) output debug info \n"
			// TODO: COMMENT OUT -v option before RELEASE
			+ " [-v | -visualize]: (optional) use visualization of DAGs \n"
			+ " [-l | -log]: (optional) output log info \n"
			+ " -config: (optional) use config file <config_filename> (default: use parameter values in default SystemML-config.xml config file) \n" 
			+ "          <config_filename> prefixed with hdfs: is hdfs file, otherwise it is local file + \n"
			+ " -args: (optional) parameterize DML script with contents of [args list], ALL args after -args flag \n"
			+ "    1st value after -args will replace $1 in DML script, 2nd value will replace $2 in DML script, and so on."
			+ "<args-list>: (optional) args to DML script \n" ;
	

	
	public DMLScript(String dmlScript, boolean debug, boolean log, boolean visualize, RUNTIME_PLATFORM rt, String config, HashMap<String, String> argVals){
		_dmlScriptString = dmlScript;
		DMLScript.DEBUG = debug;
		LOG = log;
		VISUALIZE = visualize;
		rtplatform = rt;
		
		_optConfig = config;
		_argVals = argVals;
		
		_mapredLogger = Logger.getLogger(DEFAULT_MAPRED_LOGGER);
		_mmcjLogger = Logger.getLogger(DEFAULT_MMCJMR_LOGGER);
	}
	
	/**
	 * createDMLScript: Do the diligent work to parse the parameters provided by the user and create a DMLScript object
	 * @param args[] parameters array 
	 * @throws IOException 
	 */
	public static DMLScript createDMLScript(String[] args) throws IOException{
		DMLScript d = null;
		
		RUNTIME_PLATFORM rt = RUNTIME_PLATFORM.HYBRID;
		
		// stores the (filename | DMLScript string) passed
		String fileName = null;
		
		// stores if <filename> arg is file (if true) or is a string (if false)
		boolean fromFile = false;
		
		/////////// if the args is incorrect, print usage /////////////
		if (args.length < 2){
			System.err.println(USAGE);
			return d;
		}
		
		//////////// process -f | -s to set dmlScriptString ////////////////
		if (!(args[0].equals("-f") || args[0].equals("-s"))){
			System.err.println("ERROR: first argument must be either -f or -s");
			System.err.println(USAGE);
			return d;
		}
		
		StringBuilder dmlScriptString = new StringBuilder();
		fromFile = (args[0].equals("-f")) ? true : false;
		fileName = args[1];

		// DML script can be from local or from hdfs
		// from local file - e.g., command invocation of DML
		// hdfs file - e.g., application oozie workflow.xml -> Jaql -> DML
		if (fromFile){
			String s1 = null;
			BufferedReader in = null;
			//TODO: update this hard coded line
			if (fileName.startsWith("hdfs:")){ // from hdfs 	
                FileSystem hdfs = FileSystem.get(new Configuration());
                Path scriptPath = new Path(fileName);
                in = new BufferedReader(new InputStreamReader(hdfs.open(scriptPath)));
			}
			else { // from local file system
				in = new BufferedReader(new FileReader(fileName));
			}
			while ((s1 = in.readLine()) != null)
				dmlScriptString.append(s1 + "\n");
			in.close();	
		}
		else {
			dmlScriptString.append(fileName);
		}
		
		//////////////////process rest of args list ////////////////////////
		int argid = 2;
		boolean debug = false, log = false, visualize = false;
		String optConfig = null;
		HashMap<String, String> argVals = new HashMap<String, String>();
		while (argid < args.length) {
			if (args[argid].equalsIgnoreCase("-d") || args[argid].equalsIgnoreCase("-debug")) {
				debug = true;
			} else if (args[argid].equalsIgnoreCase("-l") || args[argid].equalsIgnoreCase("-log")) {
				log = true;
			} else if (args[argid].equalsIgnoreCase("-v") || args[argid].equalsIgnoreCase("-visualize")) {
				visualize = true;
			} else if ( args[argid].equalsIgnoreCase("-exec")) {
				argid++;
				if ( args[argid].equalsIgnoreCase("hadoop")) 
					rt = RUNTIME_PLATFORM.HADOOP;
				else if ( args[argid].equalsIgnoreCase("singlenode"))
					rt = RUNTIME_PLATFORM.SINGLE_NODE;
				else if ( args[argid].equalsIgnoreCase("hybrid"))
					rt = RUNTIME_PLATFORM.HYBRID;
				else if ( args[argid].equalsIgnoreCase("nz"))
					rt = RUNTIME_PLATFORM.NZ;
				else {
					System.err.println("ERROR: Unknown runtime platform: " + args[argid]);
					return d;
				}
			// handle config file
			} else if (args[argid].startsWith("-config=")){
				optConfig = args[argid].substring(8).replaceAll("\"", "");
			}
			// handle the args to DML Script -- rest of args will be passed here to 
			else if (args[argid].startsWith("-args")) {
				argid++;
				int index = 1;
				while( argid < args.length){
					argVals.put("$"+index ,args[argid]);
					argid++;
					index++;
				}
			} 
			argid++;
		} 
		
		d = new DMLScript(dmlScriptString.toString(), debug, log, visualize, rt, optConfig, argVals);
		//////////////// for DEBUG, dump arguments /////////////////////////////
		//if (debug){
			System.out.println("INFO: ****** args to DML Script ****** ");
			System.out.println("INFO: UUID: " + getUUID());
			System.out.println("INFO: FROM-FILE: " + fromFile);
			System.out.println("INFO: SCRIPT: " + fileName);
			//System.out.println("INFO: DMLSTRING: " + dmlScriptString);
			System.out.println("INFO: DEBUG: "  + debug);
			System.out.println("INFO: LOG: "  + log);
			System.out.println("INFO: VISUALIZE: "  + visualize);
			System.out.println("INFO: RUNTIME: " + rt);
			System.out.println("INFO: BUILTIN CONFIG: " + DEFAULT_SYSTEMML_CONFIG_FILEPATH);
			System.out.println("INFO: OPTIONAL CONFIG: " + optConfig);
			
			if (argVals.size() > 0)
				System.out.println("INFO: Value for script parameter args: ");
			for (int i=1; i<= argVals.size(); i++)
				System.out.println("INFO: $" + i + " = " + argVals.get("$" + i) );
		//}
		return d;
	}

	/**
	 * run: Execute a DML script
	 * @throws ParseException 
	 * @throws IOException 
	 * @throws DMLException 
	 */
	public void run()throws IOException, ParseException, DMLException {
		
		/////////////// set logger level //////////////////////////////////////
		if (rtplatform == RUNTIME_PLATFORM.HADOOP || rtplatform == RUNTIME_PLATFORM.HYBRID){
			if (DEBUG)
				_mapredLogger.setLevel(Level.WARN);
			else {
				_mapredLogger.setLevel(Level.WARN);
				_mmcjLogger.setLevel(Level.WARN);
			}
		}
		////////////// handle log output //////////////////////////
		BufferedWriter out = null;
		if (LOG && (rtplatform == RUNTIME_PLATFORM.HADOOP || rtplatform == RUNTIME_PLATFORM.HYBRID)) {
			// copy the input DML script to ./log folder
			String hadoop_home = System.getenv("HADOOP_HOME");
			File logfile = new File(hadoop_home + "/" + LOG_FILE_NAME);
			if (!logfile.exists()) {
				boolean success = logfile.createNewFile(); // creates the file
				// if it does not
				if (success == false) 
					System.err.println("ERROR: Failed to create log file: " + hadoop_home + "/" + LOG_FILE_NAME);	
			}

			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logfile, true)));
			out.write("BEGIN DMLRun " + getDateTime() + "\n");
			out.write("BEGIN DMLScript\n");
			// No need to reopen the dml script, just print out dmlScriptString
			out.write(_dmlScriptString);
			out.write("END DMLScript\n");
		}
		
		// optional config specified overwrites/merge into the default config
		DMLConfig defaultConfig = null;
		DMLConfig optionalConfig = null;
		
		if (_optConfig != null) { // the optional config is specified
			try { // try to get the default config first 
				defaultConfig = new DMLConfig(DEFAULT_SYSTEMML_CONFIG_FILEPATH);
			} catch (Exception e) { // it is ok to not have the default
				defaultConfig = null;
			}
			try { // try to get the optional config next
				optionalConfig = new DMLConfig(_optConfig);	
			} catch (Exception e) { // it is not ok as the specification is wrong
				optionalConfig = null;
				System.err.println("ERROR: Error parsing optional configuration file: " + _optConfig);
				System.exit(-1);
			}
			if (defaultConfig != null) {
				try {
					defaultConfig.merge(optionalConfig);
				}
				catch(Exception e){
					System.err.println("ERROR: failed to merge default ");
					System.exit(-1);
				}
			}
			else {
				defaultConfig = optionalConfig;
			}
		}
		else { // the optional config is not specified
			try { // try to get the default config 
				defaultConfig = new DMLConfig(DEFAULT_SYSTEMML_CONFIG_FILEPATH);
			} catch (Exception e) { // it is not OK to not have the default
				defaultConfig = null;
				System.out.println("ERROR: Error parsing default configuration file: " + DEFAULT_SYSTEMML_CONFIG_FILEPATH);
				System.exit(-1);
			}
		}
		ConfigurationManager.setConfig(defaultConfig);
		
		////////////////print config file parameters /////////////////////////////
		if (DEBUG){
			System.out.println("INFO: ****** DMLConfig parameters *****");			
			System.out.println("INFO: " + DMLConfig.SCRATCH_SPACE  + ": " + ConfigurationManager.getConfig().getTextValue(DMLConfig.SCRATCH_SPACE));
			System.out.println("INFO: " + DMLConfig.NUM_REDUCERS   + ": " + ConfigurationManager.getConfig().getTextValue(DMLConfig.NUM_REDUCERS));
			System.out.println("INFO: " + DMLConfig.DEF_BLOCK_SIZE + ": " + ConfigurationManager.getConfig().getTextValue(DMLConfig.DEF_BLOCK_SIZE));
			System.out.println("INFO: " + DMLConfig.NUM_MERGE_TASKS      + ": "+ ConfigurationManager.getConfig().getTextValue(DMLConfig.NUM_MERGE_TASKS));
			System.out.println("INFO: " + DMLConfig.NUM_SOW_THREADS      + ": "+ ConfigurationManager.getConfig().getTextValue(DMLConfig.NUM_SOW_THREADS));
			System.out.println("INFO: " + DMLConfig.NUM_REAP_THREADS     + ": "+ ConfigurationManager.getConfig().getTextValue(DMLConfig.NUM_REAP_THREADS ));
			System.out.println("INFO: " + DMLConfig.SOWER_WAIT_INTERVAL  + ": "+ ConfigurationManager.getConfig().getTextValue(DMLConfig.SOWER_WAIT_INTERVAL ));
			System.out.println("INFO: " + DMLConfig.REAPER_WAIT_INTERVAL + ": "+ ConfigurationManager.getConfig().getTextValue(DMLConfig.REAPER_WAIT_INTERVAL));
			System.out.println("INFO: " + DMLConfig.NIMBLE_SCRATCH       + ": "+ ConfigurationManager.getConfig().getTextValue(DMLConfig.NIMBLE_SCRATCH ));
			System.out.println("INFO: " + DMLConfig.REAPER_WAIT_INTERVAL + ": "+ ConfigurationManager.getConfig().getTextValue(DMLConfig.REAPER_WAIT_INTERVAL));	
		}

		///////////////////////////////////// parse script ////////////////////////////////////////////
		DMLProgram prog = null;
		DMLQLParser parser = new DMLQLParser(_dmlScriptString, _argVals);
		prog = parser.parse();

		if (prog == null){
			System.err.println("ERROR: Parsing failed");
			System.exit(-1);
		}

		if (DEBUG) {
			System.out.println("********************** PARSER *******************");
			System.out.println(prog.toString());
		}
		///////////////////////////////////// construct HOPS ///////////////////////////////
		
		DMLTranslator dmlt = new DMLTranslator(prog);
		dmlt.validateParseTree(prog);
		dmlt.liveVariableAnalysis(prog);

		if (DEBUG) {
			System.out.println("********************** COMPILER *******************");
			System.out.println(prog.toString());
		}
		dmlt.constructHops(prog);

		if (DEBUG) {
			System.out.println("********************** HOPS DAG (Before Rewrite) *******************");
			// print
			dmlt.printHops(prog);
			dmlt.resetHopsDAGVisitStatus(prog);

			// visualize
			DotGraph gt = new DotGraph();
			//
			// last parameter: the path of DML source directory. If dml source
			// is at /path/to/dml/src then it should be /path/to/dml.
			//
			gt.drawHopsDAG(prog, "HopsDAG Before Rewrite", 50, 50, PATH_TO_SRC, VISUALIZE);
			dmlt.resetHopsDAGVisitStatus(prog);
		}

		// rewrite HOPs DAGs
		if (DEBUG) {
			System.out.println("********************** Rewriting HOPS DAG *******************");
		}

		// defaultConfig contains reconciled information for config
		dmlt.rewriteHopsDAG(prog, defaultConfig);
		dmlt.resetHopsDAGVisitStatus(prog);

		if (DEBUG) {
			System.out.println("********************** HOPS DAG (After Rewrite) *******************");
			// print
			dmlt.printHops(prog);
			dmlt.resetHopsDAGVisitStatus(prog);

			// visualize
			DotGraph gt = new DotGraph();
			gt.drawHopsDAG(prog, "HopsDAG After Rewrite", 100, 100, PATH_TO_SRC, VISUALIZE);
			dmlt.resetHopsDAGVisitStatus(prog);
		}

		executeHadoop(dmlt, prog, defaultConfig, out);

	}
	
	

	
	/**
	 * @param args
	 * @throws ParseException
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 */
	public static void main(String[] args) throws IOException, ParseException, DMLException {
		// This is a show case how to create a DMLScript object to accept a DML script provided by the user,
		// and how to run it.
		DMLScript d = DMLScript.createDMLScript(args);
		d.run();		
	} ///~ end main

	
	/**
	 * executeHadoop: Handles execution on the Hadoop Map-reduce runtime
	 * @param dmlt DML Translator 
	 * @param prog DML Program object from parsed DML script
	 * @param config read from provided configuration file (e.g., config.xml)
	 * @param out writer for log output 
	 * @throws ParseException 
	 * @throws IOException 
	 * @throws DMLException 
	 */
	private static void executeHadoop(DMLTranslator dmlt, DMLProgram prog, DMLConfig config, BufferedWriter out) throws ParseException, IOException, DMLException{
		
		/////////////////////// construct the lops ///////////////////////////////////
		dmlt.constructLops(prog);

		if (DEBUG) {
			System.out.println("********************** LOPS DAG *******************");
			dmlt.printLops(prog);
			dmlt.resetLopsDAGVisitStatus(prog);

			DotGraph gt = new DotGraph();
			gt.drawLopsDAG(prog, "LopsDAG", 150, 150, PATH_TO_SRC, VISUALIZE);
			dmlt.resetLopsDAGVisitStatus(prog);
		}

		////////////////////// generate runtime program ///////////////////////////////
		Program rtprog = prog.getRuntimeProgram(DEBUG, config);
		DAGQueue dagQueue = setupNIMBLEQueue(config);
		if (DEBUG && config == null){
			System.out.println("INFO: config is null -- you may need to verify config file path");
		}
		if (DEBUG && dagQueue == null){
			System.out.println("INFO: dagQueue is not set");
		}
		
		/*
		if (DEBUG) {
			System.out.println("********************** PIGGYBACKING DAG *******************");
			dmlt.printLops(prog);
			dmlt.resetLopsDAGVisitStatus(prog);

			DotGraph gt = new DotGraph();
			gt.drawLopsDAG(prog, "PiggybackingDAG", 200, 200, path_to_src, VISUALIZE);
			dmlt.resetLopsDAGVisitStatus(prog);
		}
		*/
		
		rtprog.setDAGQueue(dagQueue);

		
		// Count number compiled MR jobs
		int jobCount = 0;
		for (ProgramBlock blk : rtprog.getProgramBlocks()) 
			jobCount += countCompiledJobs(blk); 		
		Statistics.setNoOfCompiledMRJobs(jobCount);

		
		// TODO: DRB --- graph is definitely broken; printMe() is okay
		if (DEBUG) {
			System.out.println("********************** Instructions *******************");
			System.out.println(rtprog.toString());
			rtprog.printMe();

			// visualize
			//DotGraph gt = new DotGraph();
			//gt.drawInstructionsDAG(rtprog, "InstructionsDAG", 200, 200, path_to_src);

			System.out.println("********************** Execute *******************");
		}

		if (LOG) 
			out.write("Compile Status OK\n");
		

		/////////////////////////// execute program //////////////////////////////////////
		Statistics.startRunTimer();		
		try 
		{   
			//init caching
			CacheableData.createCacheDir();
			
			//run execute (w/ exception handling to ensure proper shutdown)
			rtprog.execute (new LocalVariableMap (), null);  
		}
		catch(DMLException ex)
		{
			throw ex;
		}
		finally //ensure cleanup/shutdown
		{			
			Statistics.stopRunTimer();
	
			if (DEBUG) 
				System.out.println(Statistics.display());
			
			if (LOG) {
				out.write("END DMLRun " + getDateTime() + "\n");
				out.close();
			}
	
			//cleanup all nimble threads
			if(rtprog.getDAGQueue() != null)
		  	    rtprog.getDAGQueue().forceShutDown();
			
			//cleanup scratch space (everything for current uuid) 
			//(required otherwise export to hdfs would skip assumed unnecessary writes if same name)
			MapReduceTool.deleteFileIfExistOnHDFS( config.getTextValue(DMLConfig.SCRATCH_SPACE)+
					                               Lops.FILE_SEPARATOR+Lops.PROCESS_PREFIX+getUUID()  );
			//cleanup working dirs (hadoop, cache)
			MapReduceTool.deleteFileIfExistOnHDFS( DMLConfig.LOCAL_MR_MODE_STAGING_DIR + //staging dir (for local mode only) //TODO: check if this is required at all
		                                           Lops.FILE_SEPARATOR + Lops.PROCESS_PREFIX + DMLScript.getUUID()  );
			MapReduceTool.deleteFileIfExistOnHDFS( MRJobConfiguration.getStagingWorkingDirPrefix() + //staging dir
                                                   Lops.FILE_SEPARATOR + Lops.PROCESS_PREFIX + DMLScript.getUUID()  );
			MapReduceTool.deleteFileIfExistOnHDFS( MRJobConfiguration.getLocalWorkingDirPrefix() + //local dir
                                                   Lops.FILE_SEPARATOR + Lops.PROCESS_PREFIX + DMLScript.getUUID()  );
			MapReduceTool.deleteFileIfExistOnHDFS( MRJobConfiguration.getSystemWorkingDirPrefix() + //system dir
                    							   Lops.FILE_SEPARATOR + Lops.PROCESS_PREFIX + DMLScript.getUUID()  );
			CacheableData.cleanupCacheDir();			
		}
	} // end executeHadoop

	
	/**
	 * executeNetezza: handles execution on Netezza runtime
	 * @param dmlt DML Translator
	 * @param prog DML program from parsed DML script
	 * @param config from parsed config file (e.g., config.xml)
	 * @throws ParseException 
	 * @throws HopsException 
	 * @throws DMLRuntimeException 
	 */
	private static void executeNetezza(DMLTranslator dmlt, DMLProgram prog, DMLConfig config, String fileName)
	throws HopsException, LanguageException, ParseException, DMLRuntimeException
	{
	
		dmlt.constructSQLLops(prog);
	
		SQLProgram sqlprog = dmlt.getSQLProgram(prog);
		String[] split = fileName.split("/");
		String name = split[split.length-1].split("\\.")[0];
		sqlprog.set_name(name);
	
		dmlt.resetSQLLopsDAGVisitStatus(prog);
		DotGraph g = new DotGraph();
		g.drawSQLLopsDAG(prog, "SQLLops DAG", 100, 100, PATH_TO_SRC, VISUALIZE);
		dmlt.resetSQLLopsDAGVisitStatus(prog);
	
		String sql = sqlprog.generateSQLString();
	
		Program pr = sqlprog.getProgram();
		pr.printMe();
	
		if (true) {
			System.out.println(sql);
		}
	
		NetezzaConnector con = new NetezzaConnector();
		try
		{
			ExecutionContext ec = new ExecutionContext(con);
			ec.setDebug(false);
	
			LocalVariableMap vm = new LocalVariableMap ();
			ec.set_variables (vm);
			con.connect();
			long time = System.currentTimeMillis();
			pr.execute (vm, ec);
			long end = System.currentTimeMillis() - time;
			System.out.println("Control program took " + ((double)end / 1000) + " seconds");
			con.disconnect();
			System.out.println("Done");
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		/*
		// Code to execute the stored procedure version of the DML script
		try
		{
			con.connect();
			con.executeSQL(sql);
			long time = System.currentTimeMillis();
			con.callProcedure(name);
			long end = System.currentTimeMillis() - time;
			System.out.println("Stored procedure took " + ((double)end / 1000) + " seconds");
			System.out.println(String.format("Procedure %s was executed on Netezza", name));
			con.disconnect();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}*/

	} // end executeNetezza
	
	
	
	/**
	 * Method to setup the NIMBLE task queue. 
	 * This will be used in future external function invocations
	 * @param dmlCfg DMLConfig object
	 * @return NIMBLE task queue
	 */
	static DAGQueue setupNIMBLEQueue(DMLConfig dmlCfg) {

		//config not provided
		if (dmlCfg == null) 
			return null;
		
		// read in configuration files
		NimbleConfig config = new NimbleConfig();

		try {
			config.parseSystemDocuments(dmlCfg.getConfig_file_name());
		} catch (ConfigurationException e) {
			throw new PackageRuntimeException ("Error parsing Nimble configuration files");
		}

		// get threads configuration and validate
		int numSowThreads = 1;
		int numReapThreads = 1;

		numSowThreads = Integer.parseInt
				(NimbleConfig.getTextValue(config.getSystemConfig().getParameters(), "NumberOfSowThreads"));
		numReapThreads = Integer.parseInt
				(NimbleConfig.getTextValue(config.getSystemConfig().getParameters(), "NumberOfReapThreads"));

		if (numSowThreads < 1 || numReapThreads < 1){
			throw new PackageRuntimeException("Illegal values for thread count (must be > 0)");
		}

		// Initialize an instance of the driver.
		PMLDriver driver = null;
		try {
			driver = new PMLDriver(numSowThreads, numReapThreads, config);
			driver.startEmptyDriver(config);
		} catch (Exception e) {

			throw new PackageRuntimeException("Problem starting nimble driver");
		} 

		return driver.getDAGQueue();
	}

	private static String getDateTime() {
		DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		Date date = new Date();
		return dateFormat.format(date);
	}

	private static int countCompiledJobs(ProgramBlock blk) {

		int jobCount = 0;

		if (blk instanceof WhileProgramBlock){	
			ArrayList<ProgramBlock> childBlocks = ((WhileProgramBlock) blk).getChildBlocks();
			for (ProgramBlock pb : childBlocks){
				jobCount += countCompiledJobs(pb);
			}

			if (blk.getNumInstructions() > 0){
				System.out.println("error:  while programBlock should not have instructions ");
			}
		}
		else {

			for (int i = 0; i < blk.getNumInstructions(); i++)
				if (blk.getInstruction(i).getType() == INSTRUCTION_TYPE.MAPREDUCE_JOB)
					jobCount++;
		}
		return jobCount;
	}
	
	public void setDMLScriptString(String dmlScriptString){
		_dmlScriptString = dmlScriptString;
	}
	
	public String getDMLScriptString (){
		return _dmlScriptString;
	}
	
	public void setOptConfig (String optConfig){
		_optConfig = optConfig;
	}
	
	public String getOptConfig (){
		return _optConfig;
	}
	
	public void setArgVals (HashMap<String, String> argVals){
		_argVals = argVals;
	}
	
	public HashMap<String, String> getArgVals() {
		return _argVals;
	}
	
	public static String getUUID()
	{
		return _uuid;
	}
	
}  ///~ end class
