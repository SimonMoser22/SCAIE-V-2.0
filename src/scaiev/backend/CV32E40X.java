package scaiev.backend;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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
	
	
	HashMap<SCAIEVNode, HashMap<PipelineStage, HashSet<String>>> op_stage_instr;
	HashMap<String, SCAIEVInstr> ISAXes;
	

	@Override
	public String getCorePathIn() {
		return pathSrc.toString();
	}
	
	// From CVA5.java
	private void addLogic(String text) {
		toFile.UpdateContent(this.ModFile(targetModule), "endmodule", new ToWrite(text, false, true, "", true, targetModule));
	}

	// From CVA5.java
	private boolean ContainsOpInStage(SCAIEVNode operation, PipelineStage stage) {
		return op_stage_instr.containsKey(operation) && op_stage_instr.get(operation).containsKey(stage);
	}
	
	// From CVA5.java
	private Stream<SCAIEVInstr> getISAXesWithOpInAnyStage(SCAIEVNode operation) {
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
	private void forEachRdValidPin(RdValidPinConsumer consumer) {
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
		
		
		forEachRdValidPin((name, isax, stage) -> {scalAPI.RequestToCorePin(BNode.RdIValid, stage, isax.GetName());});
		
	}

	@Override
	public boolean Generate(HashMap<String, SCAIEVInstr> ISAXes,
			HashMap<SCAIEVNode, HashMap<PipelineStage, HashSet<String>>> op_stage_instr, String extension_name,
			Core core, String out_path) {
		System.out.println("Generating for CV32E40X!");

		ConfigCV32E40X();       
        
		IntegrateISAX_IOs();
        IntegrateISAX_NoIllegalInstr();
        IntegrateISAX_WrStall();
        IntegrateISAX_WrFlush();
        
      
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
        
        // WrRD
        
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
        this.PutNode("logic",  "scaiev.fetch_isHalted", targetModule, BNode.RdStall, stage_fetch);
        this.PutNode("logic",  "scaiev.decode_isHalted", targetModule, BNode.RdStall, stage_decode);
        this.PutNode("logic",  "scaiev.execute_isHalted", targetModule, BNode.RdStall, stage_execute);
        this.PutNode("logic",  "scaiev.writeback_isHalted", targetModule, BNode.RdStall, stage_writeback);
        
        // WrStall
        this.PutNode("logic",  "", targetModule, BNode.WrStall, stage_fetch);
        this.PutNode("logic",  "", targetModule, BNode.WrStall, stage_decode);
        this.PutNode("logic",  "", targetModule, BNode.WrStall, stage_execute);
        this.PutNode("logic",  "", targetModule, BNode.WrStall, stage_writeback);
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
	        

		forEachRdValidPin((node, isax, stage) -> language.UpdateInterface(topModule, node.NodeNegInput(), isax.GetName(), stage, true, false));

	}


	// Largely taken from CVA5.java
	private void IntegrateISAX_NoIllegalInstr() {
		List<String> allISAXes =
	        ISAXes.entrySet().stream().filter(isaxEntry -> !isaxEntry.getValue().HasNoOp()).map(isaxEntry -> isaxEntry.getKey()).toList();
	    
	    
	    List<String> allISAXes_RS1;
	    List<String> allISAXes_RS2;
	    List<String> allISAXes_RD;
	
	    allISAXes_RS1 = getISAXesWithOpInAnyStage(BNode.RdRS1).filter(instr -> !instr.HasNoOp()).map(instr -> instr.GetName()).toList();
	    allISAXes_RS2 = getISAXesWithOpInAnyStage(BNode.RdRS2).filter(instr -> !instr.HasNoOp()).map(instr -> instr.GetName()).toList();
	    allISAXes_RD = getISAXesWithOpInAnyStage(BNode.WrRD).filter(instr -> !instr.HasNoOp()).map(instr -> instr.GetName()).toList();

	    Function<List<String>, String> makeIValidExpression = isaxNames -> {
	      return isaxNames.stream()
	          .map(isaxName -> {
	        	  return language.CreateNodeName(BNode.RdIValid.NodeNegInput(), stage_decode, isaxName);  
	          })
	          .reduce((a, b) -> a + " || " + b)
	          .orElse("1'b0");
	    };
	    
	    addLogic("assign scaiev.decode_isSCAIEV = " + makeIValidExpression.apply(allISAXes) + ";");
	    addLogic("assign scaiev.decode_isSCAIEV_usesRS1 = " + makeIValidExpression.apply(allISAXes_RS1) + ";");
	    addLogic("assign scaiev.decode_isSCAIEV_usesRS2 = " + makeIValidExpression.apply(allISAXes_RS2) + ";");
	    addLogic("assign scaiev.decode_isSCAIEV_usesRD = " + makeIValidExpression.apply(allISAXes_RD) + ";\n");
	}
	
	private void IntegrateISAX_WrStall() {
		
		for (int stagePos = stagePos_fetch; stagePos <= stagePos_execute; stagePos++) {
			PipelineStage stage = stages[stagePos];
			if (ContainsOpInStage(BNode.WrStall, stage)) {
				addLogic("assign scaiev." + cv32e40xStageNames[stagePos] + "_doHalt = " + language.CreateNodeName(BNode.WrStall, stage, "") + ";");
			}
			else {
				addLogic("assign scaiev." + cv32e40xStageNames[stagePos] + "_doHalt = '0;");
			}	
		}
	}
	
	private void IntegrateISAX_WrFlush() {
		
		for (int stagePos = stagePos_fetch; stagePos <= stagePos_execute; stagePos++) {
			PipelineStage stage = stages[stagePos];
			if (ContainsOpInStage(BNode.WrFlush, stage)) {
				addLogic("assign scaiev." + cv32e40xStageNames[stagePos] + "_doKill = " + language.CreateNodeName(BNode.WrFlush, stage, "") + ";");
			}
			else {
				addLogic("assign scaiev." + cv32e40xStageNames[stagePos] + "_doKill = '0;");
			}	
		}
	}
}
