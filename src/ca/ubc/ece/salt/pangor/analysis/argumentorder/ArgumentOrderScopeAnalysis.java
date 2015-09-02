package ca.ubc.ece.salt.pangor.analysis.argumentorder;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.FunctionNode;

import ca.ubc.ece.salt.pangor.analysis.classify.ClassifierDataSet;
import ca.ubc.ece.salt.pangor.analysis.scope.Scope;
import ca.ubc.ece.salt.pangor.analysis.scope.ScopeAnalysis;
import ca.ubc.ece.salt.pangor.batch.AnalysisMetaInformation;
import ca.ubc.ece.salt.pangor.cfg.CFG;
import ca.ubc.ece.salt.pangor.classify.alert.ClassifierAlert;

public class ArgumentOrderScopeAnalysis extends ScopeAnalysis<ClassifierAlert, ClassifierDataSet> {
	protected static final Logger logger = LogManager.getLogger(ArgumentOrderScopeAnalysis.class);

	public InsertedIfVisitor visitor = new InsertedIfVisitor();

	public ArgumentOrderScopeAnalysis(ClassifierDataSet dataSet, AnalysisMetaInformation ami) {
		super(dataSet, ami);
	}

	@Override
	public void analyze(AstRoot root, List<CFG> cfgs) throws Exception {
		super.analyze(root, cfgs);

		/*
		 * Minified files gave a lot of false positives! Skipping them
		 */
		if (ami.repairedFile.endsWith(".min.js")) {
			logger.info("Skipping minimifed file file: " + ami.repairedFile);
			return;
		}

		/* Look at each function. */
		this.inspectFunctions(this.dstScope);
	}

	@Override
	public void analyze(AstRoot srcRoot, List<CFG> srcCFGs, AstRoot dstRoot, List<CFG> dstCFGs) throws Exception {
		super.analyze(srcRoot, srcCFGs, dstRoot, dstCFGs);

		/* Look at each function. */
		this.inspectFunctions(this.dstScope);
	}

	/**
	 * @param scope The function to inspect.
	 */
	private void inspectFunctions(Scope scope) {
		/* Visit the function and look for STH patterns. */
		if (scope.scope instanceof FunctionNode) {
			FunctionNode function = (FunctionNode) scope.scope;
			function.getBody().visit(visitor);
		} else {
			scope.scope.visit(visitor);
		}

		/*
		 * This method was copied and pasted from somewhere else. But Why should
		 * we visit the child functions, if visitor is already going to do it?
		 */
		// for (Scope child : scope.children) {
		// // inspectFunctions(child);
		// }

	}

}