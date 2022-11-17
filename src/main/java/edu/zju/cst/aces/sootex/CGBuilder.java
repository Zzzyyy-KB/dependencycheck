package edu.zju.cst.aces.sootex;

import edu.zju.cst.aces.sootex.callgraph.SimpleCallGraphFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.io.File;
import java.util.*;


public class CGBuilder {

    public static final Logger LOGGER = LoggerFactory.getLogger(CGBuilder.class);
    public static final Logger NODE_LOGGER = LoggerFactory.getLogger("Node");
    public static final Logger EDGE_LOGGER = LoggerFactory.getLogger("Edge");


    public static void main(String[] args) throws Exception {
//        RunConfig runConfig = new RunConfig(args[0]);
        RunConfig runConfig = new RunConfig("D:\\java\\JavaCallGraph-master\\src\\test\\resources\\test.conf");

        String classPath = runConfig.getClassPath();
//        classPath = classPath + File.pathSeparator + "C:\\Users\\chenzhi\\.jdks\\azul-1.8.0_352\\jre\\lib\\rt.jar";
        SootExecutorUtil.setDefaultSootOptions(classPath);
        Set<String> entryPoints = runConfig.buildEntrance(classPath);
        SootExecutorUtil.setSootEntryPoints(entryPoints);

        SootExecutorUtil.doFastSparkPointsToAnalysis(new HashMap<String, String>(), runConfig.getCgType(), null);

        CallGraph cg = Scene.v().getCallGraph();
        LOGGER.info("Original size of CallGraph {}", cg.size());

        SimpleCallGraphFilter refiner = new SimpleCallGraphFilter();
        CallGraph newCg = refiner.refine(cg);
        Scene.v().setCallGraph(newCg);
        Scene.v().setReachableMethods(null);   //update reachable methods

        for (Iterator<MethodOrMethodContext> iterator = Scene.v().getReachableMethods().listener(); iterator.hasNext(); ) {
            SootMethod method = (SootMethod) iterator.next();
            NODE_LOGGER.info("{}: {}", method.getBytecodeSignature(), method.getNumber());
            for (Iterator<Edge> it = newCg.edgesOutOf(method); it.hasNext(); ) {
                Edge edge = it.next();
                EDGE_LOGGER.info("{} -> {}", edge.src().getNumber(), edge.tgt().getNumber());
            }
        }
    }

}
