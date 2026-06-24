package scaiev.backend;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import scaiev.coreconstr.Core;
import scaiev.coreconstr.CoreNode;
import scaiev.frontend.SCAIEVInstr;
import scaiev.frontend.SCAIEVNode;
import scaiev.frontend.SCAL;
import scaiev.pipeline.PipelineStage;
import scaiev.scal.NodeInstanceDesc;
import scaiev.util.FileWriter;
import scaiev.util.Lang;
import scaiev.util.ToWrite;
import scaiev.util.Verilog;

public class CV32E40X extends CoreBackend {
	
	// logging
	protected static final Logger logger = LogManager.getLogger();
	
	static final Path pathSrc = Path.of("CoresSrc/CV32E40X");
	static final Path pathCore = Path.of("rtl");

	static final String topModule = "cv32e40x_core";
	static final String targetModule = "cv32e40x_scaiev_glue";

	private Verilog language = null;
	private FileWriter toFile = new FileWriter(pathSrc.toString());

	static final int stagePos_fetch = 0;
	static final int stagePos_decode = 1;
	static final int stagePos_execute = 2;
	static final int stagePos_writeback = 3;
	private PipelineStage stage_fetch;
	private PipelineStage stage_decode;
	private PipelineStage stage_execute;
	private PipelineStage stage_writeback;
	private PipelineStage[] stages;
	private static final String[] cv32e40xStageNames = new String[] {"fetch", "decode", "execute", "writeback", ""};
	
	private static final String signame_decode_stall_wrPC = "decode_halt_wrPC";
	private static final String signame_execute_stall_wrRD = "execute_halt_wrRD";
	
	
	HashMap<SCAIEVNode, HashMap<PipelineStage, HashSet<String>>> op_stage_instr;
	HashMap<String, SCAIEVInstr> ISAXes;
	

	@Override
	public String getCorePathIn() {
		return pathSrc.toString();
	}
	
	// From CVA5.java
	private void AddLogic(String text) {
		toFile.UpdateContent(this.ModFile(targetModule), "endmodule", new ToWrite(text, false, true, "", true, targetModule));
	}
	
	private void AddLogicAfter(String grep, String text) {
		toFile.UpdateContent(this.ModFile(targetModule), grep, new ToWrite(text, false, true, "", true, targetModule));
	}
	
	// From CVA5.java
	private void AddDeclaration(String text) {
		toFile.UpdateContent(this.ModFile(targetModule), ");", new ToWrite(text, true, false, "module ", false, targetModule));
	}
	
	private void ReplaceLogic(String grep, String replaceWith) {
		toFile.ReplaceContent(this.ModFile(targetModule), grep, new ToWrite(replaceWith, false, true, "", true, targetModule));
	}
	

	// From CVA5.java
	private boolean ContainsOpInStage(SCAIEVNode operation, PipelineStage stage) {
		return op_stage_instr.containsKey(operation) && op_stage_instr.get(operation).containsKey(stage);
	}
	
	// From CVA5.java
	private Stream<SCAIEVInstr> GetISAXesWithOpInAnyStage(SCAIEVNode operation) {
	    return !op_stage_instr.containsKey(operation) ? Stream.<SCAIEVInstr>of()
	                                                  : op_stage_instr.get(operation)
	                                                        .values()
	                                                        .stream()
	                                                        .flatMap(valuesSet -> valuesSet.stream())
	                                                        .map(instr_name -> ISAXes.get(instr_name))
	                                                        .filter(instr -> instr != null)
	                                                        .distinct();
	}
	
	
	// From CVA5.java
	@FunctionalInterface
	private interface RdValidPinConsumer {
	  void accept(SCAIEVNode node, SCAIEVInstr isax, PipelineStage stage);
	}
	
	// From CVA5.java
	private void ForEachRdValidPin(RdValidPinConsumer consumer) {
		Stream<NodeInstanceDesc.Key> ivalidKeyStream;
	
		ivalidKeyStream = ISAXes.entrySet().stream()
		    .filter(isaxEntry -> !isaxEntry.getValue().HasNoOp())
		    .flatMap(isaxEntry -> {
		      return Stream.of(new NodeInstanceDesc.Key(BNode.RdIValid, stage_decode, isaxEntry.getKey()));
		    });
		
		//Call the consumer for each _unique_ pin.
		ivalidKeyStream.distinct().forEach(ivalidKey -> {
		  consumer.accept(ivalidKey.getNode(), ISAXes.get(ivalidKey.getISAX()), ivalidKey.getStage());
		});
	
	}
	
	// From CVA5.java
	private Stream<SCAIEVInstr> GetISAXesWithOpInStage(SCAIEVNode operation, PipelineStage stage) {
		return (!op_stage_instr.containsKey(operation) || !op_stage_instr.get(operation).containsKey(stage))
		    ? Stream.<SCAIEVInstr>of()
			: op_stage_instr.get(operation).get(stage).stream().map(instr_name -> ISAXes.get(instr_name)).filter(instr -> instr != null);
	  }
	
	
	@Override
	public void Prepare(HashMap<String, SCAIEVInstr> ISAXes,
			HashMap<SCAIEVNode, HashMap<PipelineStage, HashSet<String>>> op_stage_instr, Core core,
			SCALBackendAPI scalAPI, scaiev.backend.BNode user_BNode) {
		// TODO Auto-generated method stub
		System.out.println("Preparing CV32E40X!");

		super.Prepare(ISAXes, op_stage_instr, core, scalAPI, user_BNode);
		
		this.op_stage_instr = op_stage_instr;
		this.ISAXes = ISAXes;
		

		this.stage_fetch = core.getRootStage().getChildren().get(0);
		this.stage_decode = stage_fetch.getNext().get(0);
		this.stage_execute = stage_decode.getNext().get(0);
		this.stage_writeback = stage_execute.getNext().get(0);
		this.stages = new PipelineStage[] {stage_fetch, stage_decode, stage_execute, stage_writeback};
		
		this.language = new Verilog(user_BNode, toFile, this);
		this.language.clk = "clk";
		this.language.reset = "rst_n";
		
		
		ForEachRdValidPin((name, isax, stage) -> {scalAPI.RequestToCorePin(BNode.RdIValid, stage, isax.GetName());});
		
		core.putNode(BNode.RdInStageValid, new CoreNode(stagePos_fetch, 0, stagePos_execute, stagePos_execute + 1, BNode.RdInStageValid.name));
		
	}

	@Override
	public boolean Generate(HashMap<String, SCAIEVInstr> ISAXes,
			HashMap<SCAIEVNode, HashMap<PipelineStage, HashSet<String>>> op_stage_instr, String extension_name,
			Core core, String out_path) {
		System.out.println("Generating for CV32E40X!");
		
		this.op_stage_instr = op_stage_instr;
		this.ISAXes = ISAXes;

		ConfigCV32E40X();       
        
		IntegrateISAX_IOs();
        IntegrateISAX_NoIllegalInstr();
        IntegrateISAX_WrStall();
        IntegrateISAX_WrFlush();
        IntegrateISAX_WrRD();
        IntegrateISAX_WrPC();
        
      
        language.FinalizeInterfaces();
        
        toFile.WriteFiles(language.GetDictModule(), language.GetDictEndModule(), out_path);

		return true;
	}
	
	
	
	private void ConfigCV32E40X() {
		this.PopulateNodesMap();    
        
		// Register relevant modules and files
        PutModule(pathCore.resolve("cv32e40x_core.sv"), topModule, pathCore.resolve("cv32e40x_core.sv"), "", topModule);
        PutModule(pathCore.resolve("cv32e40x_scaiev_glue.sv"), targetModule, pathCore.resolve("cv32e40x_scaiev_glue.sv"), topModule, targetModule);

        
        // Add pins as well as assignment statements (if applicable) for the SCAIE-V interfaces per stage.
        // If an interface in a stage is not needed by any ISAX it is not generated automatically
        
        // RdRS1
        this.PutNode("logic", "scaiev.decode_RS1", targetModule, BNode.RdRS1, stage_decode);
        this.PutNode("logic", "scaiev.execute_RS1", targetModule, BNode.RdRS1, stage_execute);
        
        // RdRS2
        this.PutNode("logic", "scaiev.decode_RS2", targetModule, BNode.RdRS2, stage_decode);
        this.PutNode("logic", "scaiev.execute_RS2", targetModule, BNode.RdRS2, stage_execute);
        
        // RdPC
        this.PutNode("logic", "scaiev.fetch_PC", targetModule, BNode.RdPC, stage_fetch);
        this.PutNode("logic", "scaiev.decode_PC", targetModule, BNode.RdPC, stage_decode);
        this.PutNode("logic", "scaiev.execute_PC", targetModule, BNode.RdPC, stage_execute);
        this.PutNode("logic", "scaiev.writeback_PC", targetModule, BNode.RdPC, stage_writeback);
        
        // RdInstr
        this.PutNode("logic",  "scaiev.fetch_Instr", targetModule, BNode.RdInstr, stage_fetch);
        this.PutNode("logic",  "scaiev.decode_Instr", targetModule, BNode.RdInstr, stage_decode);
        this.PutNode("logic",  "scaiev.execute_Instr", targetModule, BNode.RdInstr, stage_execute);
        this.PutNode("logic",  "scaiev.writeback_Instr", targetModule, BNode.RdInstr, stage_writeback);
        
        // RdIValid
        this.PutNode("logic",  "", targetModule, BNode.RdIValid, stage_decode);
        
        // WrPC
        this.PutNode("logic", "", targetModule, BNode.WrPC, stage_decode);
        this.PutNode("logic", "", targetModule, BNode.WrPC_valid, stage_decode);
        this.PutNode("logic", "", targetModule, BNode.WrPC, stage_execute);
        this.PutNode("logic", "", targetModule, BNode.WrPC_valid, stage_execute);
        
        // WrRD
        this.PutNode("logic", "", targetModule, BNode.WrRD, stage_execute);
        this.PutNode("logic", "", targetModule, BNode.WrRD_valid, stage_execute);
        
        // RdFlush
        this.PutNode("logic",  "scaiev.fetch_isKilled", targetModule, BNode.RdFlush, stage_fetch);
        this.PutNode("logic",  "scaiev.decode_isKilled", targetModule, BNode.RdFlush, stage_decode);
        this.PutNode("logic",  "scaiev.execute_isKilled", targetModule, BNode.RdFlush, stage_execute);
        this.PutNode("logic",  "scaiev.writeback_isKilled", targetModule, BNode.RdFlush, stage_writeback);
        
        // WrFlush
        this.PutNode("logic",  "", targetModule, BNode.WrFlush, stage_fetch);
        this.PutNode("logic",  "", targetModule, BNode.WrFlush, stage_decode);
        this.PutNode("logic",  "", targetModule, BNode.WrFlush, stage_execute);
        this.PutNode("logic",  "", targetModule, BNode.WrFlush, stage_writeback);
        
        // RdStall
        // Decode and execute stage halt signals need to report implicit stall signals
        // generated in the glue module
        this.PutNode("logic",  "scaiev.fetch_isHalted", targetModule, BNode.RdStall, stage_fetch);
        this.PutNode("logic",  "decode_isHalted", targetModule, BNode.RdStall, stage_decode);
        this.PutNode("logic",  "execute_isHalted", targetModule, BNode.RdStall, stage_execute);
        this.PutNode("logic",  "scaiev.writeback_isHalted", targetModule, BNode.RdStall, stage_writeback);
        
        // WrStall
        this.PutNode("logic",  "", targetModule, BNode.WrStall, stage_fetch);
        this.PutNode("logic",  "", targetModule, BNode.WrStall, stage_decode);
        this.PutNode("logic",  "", targetModule, BNode.WrStall, stage_execute);
        this.PutNode("logic",  "", targetModule, BNode.WrStall, stage_writeback);
        
        // RdInStageValid
        this.PutNode("logic",  "scaiev.fetch_valid", targetModule, BNode.RdInStageValid, stage_fetch);
        this.PutNode("logic",  "scaiev.decode_valid", targetModule, BNode.RdInStageValid, stage_decode);
        this.PutNode("logic",  "scaiev.execute_valid", targetModule, BNode.RdInStageValid, stage_execute);
	}

	
	private void IntegrateISAX_IOs() {
		language.GenerateAllInterfaces(
			topModule,
			op_stage_instr.entrySet()
				.stream()
				.filter(entry_ -> !entry_.getKey().equals(BNode.RdIValid))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, HashMap::new)),
			ISAXes,
			core,
			null
		);
	        

		ForEachRdValidPin((node, isax, stage) -> language.UpdateInterface(topModule, node.NodeNegInput(), isax.GetName(), stage, true, false));

	}


	// Largely taken from CVA5.java
	private void IntegrateISAX_NoIllegalInstr() {
		List<String> allISAXes =
	        ISAXes.entrySet().stream().filter(isaxEntry -> !isaxEntry.getValue().HasNoOp()).map(isaxEntry -> isaxEntry.getKey()).toList();
	    
	    
	    List<String> allISAXes_RS1;
	    List<String> allISAXes_RS2;
	    List<String> allISAXes_RD;
	
	    allISAXes_RS1 = GetISAXesWithOpInAnyStage(BNode.RdRS1).filter(instr -> !instr.HasNoOp()).map(instr -> instr.GetName()).toList();
	    allISAXes_RS2 = GetISAXesWithOpInAnyStage(BNode.RdRS2).filter(instr -> !instr.HasNoOp()).map(instr -> instr.GetName()).toList();
	    allISAXes_RD = GetISAXesWithOpInAnyStage(BNode.WrRD).filter(instr -> !instr.HasNoOp()).map(instr -> instr.GetName()).toList();

	    Function<List<String>, String> makeIValidExpression = isaxNames -> {
	        return isaxNames.stream()
	            .map(isaxName -> {
	        	    return language.CreateNodeName(BNode.RdIValid.NodeNegInput(), stage_decode, isaxName);  
	            })
	            .reduce((a, b) -> a + " || " + b)
	            .orElse("1'b0");
	    };
	    
	    ReplaceLogic("assign scaiev.decode_isSCAIEV",         "assign scaiev.decode_isSCAIEV = " + makeIValidExpression.apply(allISAXes) + ";");
	    ReplaceLogic("assign scaiev.decode_isSCAIEV_usesRS1", "assign scaiev.decode_isSCAIEV_usesRS1 = " + makeIValidExpression.apply(allISAXes_RS1) + ";");
	    ReplaceLogic("assign scaiev.decode_isSCAIEV_usesRS2", "assign scaiev.decode_isSCAIEV_usesRS2 = " + makeIValidExpression.apply(allISAXes_RS2) + ";");
	    ReplaceLogic("assign scaiev.decode_isSCAIEV_usesRD",  "assign scaiev.decode_isSCAIEV_usesRD = " + makeIValidExpression.apply(allISAXes_RD) + ";");
	}
	
	private void IntegrateISAX_WrStall() {
		
		for (int stagePos = stagePos_fetch; stagePos <= stagePos_execute; stagePos++) {
			PipelineStage stage = stages[stagePos];
			String stallLogic = "";
			if (ContainsOpInStage(BNode.WrStall, stage)) {
				stallLogic += language.CreateNodeName(BNode.WrStall, stage, "") + " || ";
			}
			if (stage == stage_decode && ContainsOpInStage(BNode.WrPC, stage_decode)) {
				stallLogic += signame_decode_stall_wrPC + " || ";
			}
			else if (stage == stage_execute && ContainsOpInStage(BNode.WrRD, stage_execute)) {
				stallLogic += signame_execute_stall_wrRD + " || ";
			}
			stallLogic += "1'b0";
			String grep = "assign scaiev." + cv32e40xStageNames[stagePos] + "_doHalt";
			ReplaceLogic(grep, "assign scaiev." + cv32e40xStageNames[stagePos] + "_doHalt = " + stallLogic + ";");
		}
	}
	
	private void IntegrateISAX_WrFlush() {
		String flushLogic = "1'b0";
		for (int stagePos = stagePos_execute; stagePos >= stagePos_fetch; stagePos--) {
			PipelineStage stage = stages[stagePos];
			String grep = "assign scaiev." + cv32e40xStageNames[stagePos] + "_doKill";
			if (ContainsOpInStage(BNode.WrFlush, stage)) {
				flushLogic += " || " + language.CreateNodeName(BNode.WrFlush, stage, ""); 
				ReplaceLogic(grep, "assign scaiev." + cv32e40xStageNames[stagePos] + "_doKill = " + flushLogic + ";");
			
			} else {
				ReplaceLogic(grep, "assign scaiev." + cv32e40xStageNames[stagePos] + "_doKill = " + flushLogic + ";");
			}
		}
	}
	
	private void IntegrateISAX_WrRD() {
		if (ContainsOpInStage(BNode.WrRD, stage_execute)) {
			ArrayList<String> logicStatement = new ArrayList<>();
			String tab = "    ";
			String valid_signame = language.CreateNodeName(BNode.WrRD_valid, stage_execute, "");
			String value_signame = language.CreateNodeName(BNode.WrRD, stage_execute, "");
			
			logicStatement.add("if ( " + valid_signame + " ) begin");
			logicStatement.add(tab + "scaiev.execute_RD = " + value_signame + ";");
			logicStatement.add(tab + "scaiev.execute_RD_valid = 1'b1;");
			logicStatement.add("end");
			
			String toWrite = String.join("\n", logicStatement);
			
			ReplaceLogic("SCAIEV_INSERT_WRRD", toWrite);
			
			AddDeclaration("logic " + signame_execute_stall_wrRD + ";");
			String execute_stall_wrRD_cond = "scaiev.execute_isSCAIEV_usesRD && !" + valid_signame;
			AddLogic("assign " + signame_execute_stall_wrRD + " = " + execute_stall_wrRD_cond + ";");
			
			ReplaceLogic("assign execute_isHalted", "assign execute_isHalted = scaiev.execute_isHalted || " + signame_execute_stall_wrRD + ";");
		}
		
		
		
		if (op_stage_instr.containsKey(BNode.WrRD)) {
			for (PipelineStage stage : op_stage_instr.get(BNode.WrRD).keySet()) {
				if (!stage.equals(stage_execute) && !op_stage_instr.get(BNode.WrRD).get(stage).isEmpty()) {
					logger.fatal("WrRD cannot be used in stage " + stage + ". Only supported in stage " + stage_execute + "!\n" +
							"Please adjust ISAX(es): " +
							GetISAXesWithOpInStage(BNode.WrRD, stage).map(isax -> isax.GetName()).reduce((a, b) -> a + ", " + b).orElse(""));
				}
			}
			
		}
	}
	
	private void IntegrateISAX_WrPC() {
		List<String> allISAXes_jmp;
		List<String> allISAXes_bch;
		
		allISAXes_jmp = GetISAXesWithOpInStage(BNode.WrPC, stage_decode).filter(instr -> !instr.HasNoOp()).map(instr -> instr.GetName()).toList();
		allISAXes_bch = GetISAXesWithOpInStage(BNode.WrPC, stage_execute).filter(instr -> !instr.HasNoOp()).map(instr -> instr.GetName()).toList();
		
		Function<List<String>, String> makeIValidExpression = isaxNames -> {
		    return isaxNames.stream()
				.map(isaxName -> {
					return language.CreateNodeName(BNode.RdIValid.NodeNegInput(), stage_decode, isaxName);  
				})
				.reduce((a, b) -> a + " || " + b)
				.orElse("1'b0");
		};
		
		ReplaceLogic("assign scaiev.decode_isSCAIEV_jmp", "assign scaiev.decode_isSCAIEV_jmp = " + makeIValidExpression.apply(allISAXes_jmp) + ";");
		ReplaceLogic("assign scaiev.decode_isSCAIEV_bch", "assign scaiev.decode_isSCAIEV_bch = " + makeIValidExpression.apply(allISAXes_bch) + ";");
		
		
		Function<Integer, String> generateWrPCLogic = stagePos -> {
		    ArrayList<String> logic_statement = new ArrayList<>();
		    String tab = "    ";
		    PipelineStage stage = stages[stagePos];
		    String valid_signame = language.CreateNodeName(BNode.WrPC_valid, stage, "");
		    String value_signame = language.CreateNodeName(BNode.WrPC, stage, "");
		    
		    logic_statement.add("if ( " + valid_signame + " ) begin");
		    String var_name = "scaiev." + cv32e40xStageNames[stagePos];
		    if (stagePos == stagePos_decode) {
		    	var_name += "_jmp";
		    }
		    else if (stagePos == stagePos_execute) {
		    	var_name += "_bch";
		    }
		    var_name += "_target";
		    logic_statement.add(tab + var_name + " = " + value_signame + ";");
		    logic_statement.add(tab + var_name + "_valid = 1'b1;");
		    logic_statement.add("end");
		    
		    return String.join("\n", logic_statement);            
		};
		
		
		if (ContainsOpInStage(BNode.WrPC, stage_decode)) {
			
			ReplaceLogic("SCAIEV_INSERT_WRPC_ID", generateWrPCLogic.apply(stagePos_decode));
			
			AddDeclaration("logic " + signame_decode_stall_wrPC + ";");
			String decode_stall_wrPC_cond = "scaiev.decode_isSCAIEV && scaiev.decode_isSCAIEV_jmp && !scaiev.decode_jmp_target_valid";
			AddLogic("assign " + signame_decode_stall_wrPC + " = " + decode_stall_wrPC_cond + ";");
			
			
			ReplaceLogic("assign decode_isHalted", "assign decode_isHalted = scaiev.decode_isHalted || " + signame_decode_stall_wrPC + ";");
			
		}
		
		if (ContainsOpInStage(BNode.WrPC, stage_execute)) {		
			ReplaceLogic("SCAIEV_INSERT_WRPC_EX", generateWrPCLogic.apply(stagePos_execute));
		}
	}
	
}
