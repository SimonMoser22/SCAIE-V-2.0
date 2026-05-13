package scaiev.backend;

import java.util.HashMap;
import java.util.HashSet;

import scaiev.coreconstr.Core;
import scaiev.frontend.SCAIEVInstr;
import scaiev.frontend.SCAIEVNode;
import scaiev.pipeline.PipelineStage;

public class CV32E40X extends CoreBackend {
	
	public String pathCore = "CoresSrc/CV32E40X";
	

	@Override
	public String getCorePathIn() {
		return pathCore;
	}

	@Override
	public void Prepare(HashMap<String, SCAIEVInstr> ISAXes,
			HashMap<SCAIEVNode, HashMap<PipelineStage, HashSet<String>>> op_stage_instr, Core core,
			SCALBackendAPI scalAPI, scaiev.backend.BNode user_BNode) {
		// TODO Auto-generated method stub
		System.out.println("Preparing CV32E40X!");
	}

	@Override
	public boolean Generate(HashMap<String, SCAIEVInstr> ISAXes,
			HashMap<SCAIEVNode, HashMap<PipelineStage, HashSet<String>>> op_stage_instr, String extension_name,
			Core core, String out_path) {
		// TODO Auto-generated method stub
		System.out.println("Generating for CV32E40X!");
		return false;
	}

}
