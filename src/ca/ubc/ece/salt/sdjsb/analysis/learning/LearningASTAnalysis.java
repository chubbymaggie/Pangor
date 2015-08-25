package ca.ubc.ece.salt.sdjsb.analysis.learning;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mozilla.javascript.ast.AstRoot;

import ca.ubc.ece.salt.gumtree.ast.ClassifiedASTNode.ChangeType;
import ca.ubc.ece.salt.sdjsb.analysis.scope.Scope;
import ca.ubc.ece.salt.sdjsb.analysis.scope.ScopeAnalysis;
import ca.ubc.ece.salt.sdjsb.batch.AnalysisMetaInformation;
import ca.ubc.ece.salt.sdjsb.cfg.CFG;
import ca.ubc.ece.salt.sdjsb.learning.apis.APIFactory;
import ca.ubc.ece.salt.sdjsb.learning.pointsto.PointsToPrediction;

/**
 * Creates a feature vector for a function by visiting its AST node and
 * recording features from modified child nodes.
 */
public class LearningASTAnalysis extends ScopeAnalysis<FeatureVector, LearningDataSet> {

	/** Stores the results for each function. **/
	private Map<Scope, FeatureVector> featureVectors;

	/** Stores the change complexity. **/
	private int changeComplexity;


	/**
	 * The maximum change complexity for the file. If the change cmplexity for
	 * a file is greater than the maximum change complexity, the file is not
	 * analyzed and no feature vectors are generated.
	 */
	private int maxChangeComplexity;

	/**
	 * @param dataSet The data set to register alerts with.
	 * @param ami	The meta information.
	 * @param maxChangeComplexity The maximum change complexity for the file.
	 *   						  Files that have many changes are not likely
	 *   						  to contain repetitive fault patterns.
	 */
	public LearningASTAnalysis(LearningDataSet dataSet, AnalysisMetaInformation ami, int maxChangeComplexity){
		super(dataSet, ami);
		this.featureVectors = new HashMap<Scope, FeatureVector>();
		this.maxChangeComplexity = maxChangeComplexity;
		this.changeComplexity = -1;
	}

	/**
	 * @return the list of feature vectors produced by the analysis.
	 */
	public Map<Scope, FeatureVector> getFeatureVectors() {
		return featureVectors;
	}

	/**
	 * @return The change complexity score for the file.
	 */
	public int getChangeComplexity() {
		return this.changeComplexity;
	}

	@Override
	public void analyze(AstRoot root, List<CFG> cfgs) throws Exception {

		super.analyze(root, cfgs);

		/* Check the change complexity. */
		this.changeComplexity = ChangeComplexityVisitor.getChangeComplexity(root);

		if(this.changeComplexity <= this.maxChangeComplexity) {

			/* Look at each function. */
			this.inspectFunctions(this.dstScope);

		}

	}

	@Override
	public void analyze(AstRoot srcRoot, List<CFG> srcCFGs, AstRoot dstRoot,
			List<CFG> dstCFGs) throws Exception {

		super.analyze(srcRoot, srcCFGs, dstRoot, dstCFGs);

		/* Check the change complexity. */
		int sourceChangeComplexityScore = ChangeComplexityVisitor.getChangeComplexity(srcRoot);
		int destinationChangeComplexityScore = ChangeComplexityVisitor.getChangeComplexity(dstRoot);

		if(sourceChangeComplexityScore > destinationChangeComplexityScore) {
			this.changeComplexity = sourceChangeComplexityScore;
		}
		else {
			this.changeComplexity = destinationChangeComplexityScore;
		}

		if( this.changeComplexity <= this.maxChangeComplexity) {

			/* Look at each function. */
			this.inspectFunctions(this.dstScope);

		}

	}

	/**
	 * Visit all the children of the function (that are not themselves
	 * functions) and record their features.
	 *
	 * @param scope The function to inspect.
	 */
	private void inspectFunctions(Scope scope) {

		/* Initialize the points-to analysis. It may take some time to build the package model.
		 *
		 *  NOTE: In the future, it might be useful to put this inside
		 *  	  ScopeAnalysis so all analyses have access to detailed
		 *   	  points-to info (for APIs at least). */

		FeatureVector classKeywords = LearningAnalysisVisitor.getScriptFeatureVector(this.ami, (AstRoot)this.dstScope.scope);
		PointsToPrediction packageModel = new PointsToPrediction(APIFactory.buildTopLevelAPI(),
				classKeywords.keywordMap);

		/* If the function was inserted or deleted, there is nothing to do. We
		 * only want functions that were repaired. Class-level repairs are left
		 * for later. */
		if(scope.scope.getChangeType() != ChangeType.INSERTED && scope.scope.getChangeType() != ChangeType.REMOVED) {

            /* Visit the function to extract features. */
			FeatureVector featureVector = LearningAnalysisVisitor.getFunctionFeatureVector(this.ami, scope.scope, packageModel);

			/* Add it to our list if there are features. */
			this.featureVectors.put(scope, featureVector);

		}

		/* Visit the child functions. */
		for(Scope child : scope.children) {
			inspectFunctions(child);
		}

	}

}