package edu.zju.cst.aces.sootex.hammock;

import soot.SootMethod;
import soot.toolkits.graph.UnitGraph;

/**
 *
 */
public interface CFGProvider {
	public UnitGraph getCFG(SootMethod m);
	
	public void release(SootMethod m);
}
