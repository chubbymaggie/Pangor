package ca.ubc.ece.salt.sdjsb.cfg2;

import java.util.LinkedList;
import java.util.List;

import org.mozilla.javascript.Node;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.Assignment;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.Block;
import org.mozilla.javascript.ast.BreakStatement;
import org.mozilla.javascript.ast.CatchClause;
import org.mozilla.javascript.ast.ContinueStatement;
import org.mozilla.javascript.ast.DoLoop;
import org.mozilla.javascript.ast.EmptyStatement;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.ForInLoop;
import org.mozilla.javascript.ast.ForLoop;
import org.mozilla.javascript.ast.FunctionCall;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.IfStatement;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.PropertyGet;
import org.mozilla.javascript.ast.ReturnStatement;
import org.mozilla.javascript.ast.Scope;
import org.mozilla.javascript.ast.ScriptNode;
import org.mozilla.javascript.ast.SwitchCase;
import org.mozilla.javascript.ast.SwitchStatement;
import org.mozilla.javascript.ast.TryStatement;
import org.mozilla.javascript.ast.UnaryExpression;
import org.mozilla.javascript.ast.VariableDeclaration;
import org.mozilla.javascript.ast.WhileLoop;
import org.mozilla.javascript.ast.WithStatement;

/**
 * Builds a control flow graph.
 */
public class CFGFactory {
	
	/**
	 * Builds intra-procedural control flow graphs for the given artifact.
	 * @param root
	 * @return
	 */
	public static List<CFG> createCFGs(AstRoot root) {
		
		/* Store the CFGs from all the functions. */
		List<CFG> cfgs = new LinkedList<CFG>();
		
		/* Start by getting the CFG for the script. */
        cfgs.add(CFGFactory.buildScriptCFG(root));
		
		/* Get the list of functions in the script. */
		List<FunctionNode> functions = FunctionNodeVisitor.getFunctions(root);
		
		/* For each function, generate its CFG. */
		for (FunctionNode function : functions) {
			cfgs.add(CFGFactory.buildScriptCFG(function));
		}
		
		return cfgs;
	}
	
	/**
	 * Builds a CFG for a function or script.
	 * @param scriptNode An ASTRoot node or FunctionNode.
	 * @return The complete CFG.
	 */
	private static CFG buildScriptCFG(ScriptNode scriptNode) {

		/* Start by getting the CFG for the script. There is one entry point
		 * and one exit point for a script and function. */

		CFGNode scriptEntry = new CFGNode(new EmptyStatement());
		CFGNode scriptExit = new CFGNode(new EmptyStatement());
		
        /* Build the CFG for the script. */
        CFG cfg = new CFG(scriptEntry);
        cfg.addExitNode(scriptExit);
        
        /* Build the CFG subgraph for the script body. */
        CFG subGraph = CFGFactory.build(scriptNode);

        /* The next node in the graph is first node of the subgraph. */
        scriptEntry.addEdge(null, subGraph.getEntryNode());
        
        /* Merge the subgraph's exit nodes into the script exit node. */
        for(CFGNode exitNode : subGraph.getExitNodes()) {
        	exitNode.addEdge(null, scriptExit);
        }
        
        return cfg;
		
	}
	
	/**
	 * Builds a CFG for a block.
	 * @param block The block statement.
	 */
	private static CFG build(Block block) {
		return CFGFactory.buildBlock(block);
	}

	/**
	 * Builds a CFG for a block.
	 * @param block The block statement.
	 */
	private static CFG build(Scope scope) {
		return CFGFactory.buildBlock(scope);
	}
	
	/**
	 * Builds a CFG for a script or function.
	 * @param script The block statement ({@code AstRoot} or {@code FunctionNode}).
	 */
	private static CFG build(ScriptNode script) {
		if(script instanceof AstRoot) {
            return CFGFactory.buildBlock(script);
		}
		return CFGFactory.buildSwitch(((FunctionNode)script).getBody());
	}

	/**
	 * Builds a CFG for a block, function or script.
	 * @param block
	 * @return The CFG for the block.
	 */
	private static CFG buildBlock(Iterable<Node> block) {
		/* Special cases:
		 * 	- First statement in block (set entry point for the CFG and won't need to merge previous into it).
		 * 	- Last statement: The exit nodes for the block will be the same as the exit nodes for this statement.
		 */
		
		CFG cfg = null;
		CFG previous = null;
		
		for(Node statement : block) {
			
			assert(statement instanceof AstNode);

			CFG subGraph = CFGFactory.buildSwitch((AstNode)statement);
			
			if(subGraph != null) {

				if(previous == null) {
					/* The first subgraph we find is the entry point to this graph. */
                    cfg = new CFG(subGraph.getEntryNode());
				}
				else {
					/* Merge the previous subgraph into the entry point of this subgraph. */
					assert(previous.getExitNodes().size() == 1);
					for(CFGNode exitNode : previous.getExitNodes()) {
						exitNode.addEdge(null, subGraph.getEntryNode());
						
					}
				}

                /* Propagate return, continue and break nodes. */
                cfg.addAllReturnNodes(subGraph.getReturnNodes());
                cfg.addAllBreakNodes(subGraph.getBreakNodes());
                cfg.addAllContinueNodes(subGraph.getContinueNodes());

                previous = subGraph;
			}
			
		}
		
		if(previous != null) {

            /* Propagate exit nodes from the last node in the block. */
            cfg.addAllExitNodes(previous.getExitNodes());
		}
		else {
			assert(cfg == null);
		}
		
		return cfg;
	}
	
	/**
	 * Builds a control flow subgraph for an if statement.
	 * @param ifStatement
	 * @return
	 */
	private static CFG build(IfStatement ifStatement) {
		
		CFGNode ifNode = new CFGNode(new EmptyStatement());
		CFG cfg = new CFG(ifNode);
		
		/* Build the true branch. */
		
		CFG trueBranch = CFGFactory.buildSwitch(ifStatement.getThenPart());
		
		if(trueBranch == null) {
			CFGNode empty = new CFGNode(new EmptyStatement());
			trueBranch = new CFG(empty);
			trueBranch.addExitNode(empty);
		}
		
		ifNode.addEdge(ifStatement.getCondition(), trueBranch.getEntryNode());

        /* Propagate exit, return, continue and break nodes. */
        cfg.addAllExitNodes(trueBranch.getExitNodes());
        cfg.addAllReturnNodes(trueBranch.getReturnNodes());
        cfg.addAllBreakNodes(trueBranch.getBreakNodes());
        cfg.addAllContinueNodes(trueBranch.getContinueNodes());
        
        /* Build the false branch. */

		CFG falseBranch = CFGFactory.buildSwitch(ifStatement.getElsePart());

		if(falseBranch == null) {
			CFGNode empty = new CFGNode(new EmptyStatement());
			falseBranch = new CFG(empty);
			falseBranch.addExitNode(empty);
		}

		ifNode.addEdge(new UnaryExpression(Token.NOT, 0, ifStatement.getElsePart()), trueBranch.getEntryNode());

        /* Propagate exit, return, continue and break nodes. */
        cfg.addAllExitNodes(falseBranch.getExitNodes());
        cfg.addAllReturnNodes(falseBranch.getReturnNodes());
        cfg.addAllBreakNodes(falseBranch.getBreakNodes());
        cfg.addAllContinueNodes(falseBranch.getContinueNodes());
		
		return cfg;
		
	}

	/**
	 * Builds a control flow subgraph for a while statement.
	 * @param whileLoop
	 * @return The CFG for the while loop.
	 */
	private static CFG build(WhileLoop whileLoop) {
		
		CFGNode whileNode = new CFGNode(new EmptyStatement());
		CFG cfg = new CFG(whileNode);

		/* Build the true branch. */
		
		CFG trueBranch = CFGFactory.buildSwitch(whileLoop.getBody());

		if(trueBranch == null) {
			CFGNode empty = new CFGNode(new EmptyStatement());
			trueBranch = new CFG(empty);
			trueBranch.addExitNode(empty);
		}
		
		whileNode.addEdge(whileLoop.getCondition(), trueBranch.getEntryNode());

        /* Propagate return nodes. */
        cfg.addAllExitNodes(trueBranch.getExitNodes());
        
        /* The break nodes are exit nodes for this loop. */
        cfg.addAllExitNodes(trueBranch.getBreakNodes());
        
        /* Exit nodes point back to the start of the loop. */
        for(CFGNode exitNode : trueBranch.getExitNodes()) {
        	exitNode.addEdge(null, whileNode);
        }
        
        /* Continue nodes point back to the start of the loop. */
        for(CFGNode continueNode : trueBranch.getContinueNodes()) {
        	continueNode.addEdge(null, whileNode);
        }
        
        /* Build the false branch. */
        
        CFGNode empty = new CFGNode(new EmptyStatement());
		whileNode.addEdge(new UnaryExpression(Token.NOT, 0, whileLoop.getCondition()), empty);
		cfg.addExitNode(empty);
		
		return cfg;
		
	}

	/**
	 * Builds a control flow subgraph for a do loop.
	 * @param doLoop
	 * @return The CFG for the do loop.
	 */
	private static CFG build(DoLoop doLoop) {
		
		CFGNode doNode = new CFGNode(new EmptyStatement());
		CFGNode whileNode = new CFGNode(new EmptyStatement());
		CFG cfg = new CFG(doNode);
		
		/* Build the loop branch. */
		
		CFG loopBranch = CFGFactory.buildSwitch(doLoop.getBody());
		
		if(loopBranch == null) {
			CFGNode empty = new CFGNode(new EmptyStatement());
			loopBranch = new CFG(empty);
			loopBranch.addExitNode(empty);
		}
		
		/* We always execute the do block at least once. */
		doNode.addEdge(null, loopBranch.getEntryNode());

		/* Add edges from exit nodes from the loop to the while node. */
		for(CFGNode exitNode : loopBranch.getExitNodes()) {
			exitNode.addEdge(null, whileNode);
		}

        /* Propagate return nodes. */
        cfg.addAllReturnNodes(loopBranch.getReturnNodes());

        /* The break nodes are exit nodes for this loop. */
        cfg.addAllExitNodes(loopBranch.getBreakNodes());

        /* Continue nodes have edges to the while condition. */
        for(CFGNode continueNode : loopBranch.getContinueNodes()) {
        	continueNode.addEdge(null, whileNode);
        }
		
		/* Add edge for true condition back to the start of the loop. */
		whileNode.addEdge(doLoop.getCondition(), loopBranch.getEntryNode());
		
		/* Add edge for false condition. */

        CFGNode empty = new CFGNode(new EmptyStatement());
		whileNode.addEdge(new UnaryExpression(Token.NOT, 0, doLoop.getCondition()), empty);
		cfg.addExitNode(empty);

		return cfg;

	}

	/**
	 * Builds a control flow subgraph for a for statement. A for statement is
	 * simply a while statement with an expression before and after the loop
	 * body.
	 * @param forLoop
	 * @return The CFG for the for loop.
	 */
	private static CFG build(ForLoop forLoop) {
		
		CFGNode forNode = new CFGNode(forLoop.getInitializer());
		CFGNode increment = new CFGNode(forLoop.getIncrement());
		increment.addEdge(null, forNode);
		CFG cfg = new CFG(forNode);
		
		/* Build the true branch. */
		
		CFG trueBranch = CFGFactory.buildSwitch(forLoop.getBody());

		if(trueBranch == null) {
			CFGNode empty = new CFGNode(new EmptyStatement());
			trueBranch = new CFG(empty);
			trueBranch.addExitNode(empty);
		}
		
		forNode.addEdge(forLoop.getCondition(), trueBranch.getEntryNode());

        /* Propagate return nodes. */
        cfg.addAllExitNodes(trueBranch.getExitNodes());
        
        /* The break nodes are exit nodes for this loop. */
        cfg.addAllExitNodes(trueBranch.getBreakNodes());
        
        /* Exit nodes point to the increment node. */
        for(CFGNode exitNode : trueBranch.getExitNodes()) {
        	exitNode.addEdge(null, increment);
        }
        
        /* Continue nodes point to the increment. */
        for(CFGNode continueNode : trueBranch.getContinueNodes()) {
        	continueNode.addEdge(null, increment);
        }
        
        /* Build the false branch. */
        
        CFGNode empty = new CFGNode(new EmptyStatement());
		forNode.addEdge(new UnaryExpression(Token.NOT, 0, forLoop.getCondition()), empty);
		cfg.addExitNode(empty);
		
		return cfg;
		
	}

	/**
	 * Builds a control flow subgraph for a for in statement. A for in statement
	 * is a loop that iterates over the keys of an object. The Rhino IR 
	 * represents this using the Node labeled "ENUM_INIT_KEYS". Here, we make
	 * a fake function that returns an object's keys.
	 * @param forInLoop
	 * @return The CFG for the for-in loop.
	 */
	private static CFG build(ForInLoop forInLoop) {

		/* To represent key iteration, we make up two functions:
		 *  
		 * 	~getNextKey() - iterates through each key in an object. 
		 *	~hasNextKey() - true if there is another key to iterate. 
		 *
         * These names are invalid in JavaScript to ensure that there isn't
         * another function with the same name. Since we're not producing code,
         * this is ok. */
		
		/* Start with the variable declaration. */
		AstNode iterator = forInLoop.getIterator();
		CFGNode forInNode = new CFGNode(iterator);
		CFG cfg = new CFG(forInNode);
		
		/* Get the variable being assigned. */
		AstNode target;
		if(iterator instanceof VariableDeclaration) {
            target = ((VariableDeclaration) iterator).getVariables().get(0).getTarget();
		}
		else if (iterator instanceof Name) {
			target = iterator;
		}
		else {
			target = new Name(0, "~error~");
		}

		/* Create the node that gets the next key in an object and assigns the
		 * value to the iterator variable. */

        PropertyGet keyIteratorMethod = new PropertyGet(forInLoop.getIteratedObject(), new Name(0, "~getNextkey"));
        FunctionCall keyIteratorFunction = new FunctionCall();
        keyIteratorFunction.setTarget(keyIteratorMethod);
        Assignment targetAssignment = new Assignment(target, keyIteratorFunction);
        targetAssignment.setType(Token.ASSIGN);
		
        CFGNode assignment = new CFGNode(targetAssignment);

        /* Create the the condition that checks if an object still has keys.
         * The condition is assigned to the true/false loop branches. */

        PropertyGet keyConditionMethod = new PropertyGet(forInLoop.getIteratedObject(), new Name(0, "~hasNextKey"));
        FunctionCall keyConditionFunction = new FunctionCall();
        keyConditionFunction.setTarget(keyConditionMethod);

		CFGNode condition = new CFGNode(new EmptyStatement());
		
        /* Create the CFG for the loop body. */
		
		CFG trueBranch = CFGFactory.buildSwitch(forInLoop.getBody());

		if(trueBranch == null) {
			CFGNode empty = new CFGNode(new EmptyStatement());
			trueBranch = new CFG(empty);
			trueBranch.addExitNode(empty);
		}
		
        /* Propagate return nodes. */
        cfg.addAllReturnNodes(trueBranch.getReturnNodes());

        /* The break nodes are exit nodes for this loop. */
        cfg.addAllExitNodes(trueBranch.getBreakNodes());

        /* The exit nodes point back to the assignment node. */
        for(CFGNode exitNode : trueBranch.getExitNodes()) {
            exitNode.addEdge(null, assignment);
        }
        
        /* The continue nodes point back to the assignment node. */
        for(CFGNode continueNode : trueBranch.getContinueNodes()) {
        	continueNode.addEdge(null, assignment);
        }
        
        /* Create a node for the false branch to exit the loop. */
        CFGNode falseBranch = new CFGNode(new EmptyStatement());
        cfg.addExitNode(falseBranch);

        /* Add the edges from the condition node to the start of the loop. */
        condition.addEdge(keyConditionFunction, trueBranch.getEntryNode());
		condition.addEdge(new UnaryExpression(Token.NOT, 0, keyConditionFunction), falseBranch);
		
		return cfg;
		
	}

	/**
	 * Builds a control flow subgraph for a switch statement. A for statement is
	 * simply a while statement with an expression before and after the loop
	 * body.
	 * @param forLoop
	 * @return The CFG for the for loop.
	 */
	private static CFG build(SwitchStatement switchStatement) {
		
		/* Create the switch node. The expression is the value to switch on. */
		SwitchNode switchNode = new SwitchNode(switchStatement.getExpression());

		CFG cfg = new CFG(switchNode);
		cfg.addExitNode(switchNode);
		
		/* Build the subgraphs for the cases. */
		CFG previousSubGraph = null;
		List<SwitchCase> switchCases = switchStatement.getCases();
	 
		for(SwitchCase switchCase : switchCases) {
			
			/* Build the subgraph for the case. */
            CFG subGraph = null;
			if(switchCase.getStatements() != null) {
                List<Node> statements = new LinkedList<Node>(switchCase.getStatements());
                subGraph = CFGFactory.buildBlock(statements);
			}
			
			/* If it is an empty case, make our lives easier by adding an
			 * empty statement as the entry and exit node. */
			if(subGraph == null) {

				StatementNode emptyCase = new StatementNode(new EmptyStatement());
				subGraph = new CFG(emptyCase);
				subGraph.addExitNode(emptyCase);
				
			}
            
            /* Add the node to the switch statement. */
            switchNode.setCase(switchCase.getExpression(), subGraph.getEntryNode());
			
			/* Propagate return nodes. */
			cfg.addAllReturnNodes(subGraph.getReturnNodes());

            /* Propagate continue nodes. */
            cfg.addAllContinueNodes(subGraph.getContinueNodes());

			/* The break nodes are exit nodes for this loop. */
			cfg.addAllExitNodes(subGraph.getBreakNodes());

			if(previousSubGraph != null) {

                /* Merge the exit nodes into the next case. */
                for(Node exitNode : previousSubGraph.getExitNodes()) {
                    exitNode.mergeInto(subGraph.getEntryNode());
                }

			}
			
			previousSubGraph = subGraph;
			
		}

        /* The rest of the exit nodes are exit nodes for the statement. */
        cfg.addAllExitNodes(previousSubGraph.getExitNodes());
		
		return cfg;
		
	}

	/**
	 * Builds a control flow subgraph for a with statement.
	 * @param withStatement
	 * @return The CFG for the while loop.
	 */
	private static CFG build(WithStatement withStatement) {
		
		WithNode node = new WithNode(withStatement.getExpression());
		CFG cfg = new CFG(node);
        cfg.addExitNode(node);
		
		CFG scopeBlock = CFGFactory.buildSwitch(withStatement.getStatement());
		
		if(scopeBlock != null) {

			node.setScopeBlock(scopeBlock.getEntryNode());
			
			/* Propagate return nodes. */
			cfg.addAllReturnNodes(scopeBlock.getReturnNodes());

			/* Propagate break nodes. */
			cfg.addAllBreakNodes(scopeBlock.getBreakNodes());
			
			/* Propagate the exit nodes. */
			cfg.addAllExitNodes(scopeBlock.getExitNodes());
			
			/* Propagate continue nodes. */
			cfg.addAllContinueNodes(scopeBlock.getContinueNodes());
			
		} 		
		
		return cfg;
		
	}

	/**
	 * Builds a control flow subgraph for a try/catch statement.
	 * 
	 * NOTE: The resulting graph will not be accurate when there are jump
	 * 		 statements in the try or catch blocks and a finally block.
	 * 
	 * @param tryStatement
	 * @return The CFG for the while loop.
	 */
	private static CFG build(TryStatement tryStatement) {
		
		TryNode node = new TryNode(tryStatement);
		CFG cfg = new CFG(node);
		cfg.addExitNode(node);
		
		/* Start by setting up the finally block. */

		CFG finallyBlock = CFGFactory.buildSwitch(tryStatement.getFinallyBlock());

		if(finallyBlock == null) { 
			Node empty = new StatementNode(new EmptyStatement());
			finallyBlock = new CFG(empty);
			finallyBlock.addExitNode(empty);
		}
		else {
            /* Propagate all nodes. */
            cfg.addAllReturnNodes(finallyBlock.getReturnNodes());
            cfg.addAllBreakNodes(finallyBlock.getBreakNodes());
            cfg.addAllContinueNodes(finallyBlock.getContinueNodes());
            cfg.addAllExitNodes(finallyBlock.getExitNodes());
		}
		
		node.setFinallyBranch(finallyBlock.getEntryNode());
		
		/* Set up the try block. */
		
		CFG tryBlock = CFGFactory.buildSwitch(tryStatement.getTryBlock());
		
		if(tryBlock == null) {
			Node empty = new StatementNode(new EmptyStatement());
			tryBlock = new CFG(empty);
			tryBlock.addExitNode(empty);
		}
		else {
            /* Propagate all nodes. */
            cfg.addAllReturnNodes(tryBlock.getReturnNodes());
            cfg.addAllBreakNodes(tryBlock.getBreakNodes());
            cfg.addAllContinueNodes(tryBlock.getContinueNodes());
            
            /* Exit nodes exit to the finally block. */
            for(Node exitNode : tryBlock.getExitNodes()) {
            	exitNode.mergeInto(finallyBlock.getEntryNode());
            }
		}
		
		node.setTryBranch(tryBlock.getEntryNode());
		
		/* Set up the catch clauses. */

		List<CatchClause> catchClauses = tryStatement.getCatchClauses();
		for(CatchClause catchClause : catchClauses) {

			CFG catchBlock = CFGFactory.buildSwitch(catchClause.getBody());
			
			if(catchBlock == null) {
				Node empty = new StatementNode(new EmptyStatement());
				catchBlock = new CFG(empty);
				catchBlock.addExitNode(empty);
			}
			else {
                /* Propagate all nodes. */
                cfg.addAllReturnNodes(catchBlock.getReturnNodes());
                cfg.addAllBreakNodes(catchBlock.getBreakNodes());
                cfg.addAllContinueNodes(catchBlock.getContinueNodes());
                
                /* Exit nodes exit to the finally block. */
                for(Node exitNode : catchBlock.getExitNodes()) {
                    exitNode.mergeInto(finallyBlock.getEntryNode());
                }
				
			}
			
			node.addCatchClause(catchClause.getCatchCondition(), catchBlock.getEntryNode());
			
		}

		return cfg;
		
	}

	/**
	 * Builds a control flow subgraph for a break statement.
	 * @param entry The entry point for the subgraph.
	 * @param exit The exit point for the subgraph.
	 * @return A list of exit nodes for the subgraph.
	 */
	private static CFG build(BreakStatement breakStatement) {
		
		Node node = new JumpNode(breakStatement);
		CFG cfg = new CFG(node);
		cfg.addBreakNode(node);
		return cfg;

	}

	/**
	 * Builds a control flow subgraph for a continue statement.
	 * @param entry The entry point for the subgraph.
	 * @param exit The exit point for the subgraph.
	 * @return A list of exit nodes for the subgraph.
	 */
	private static CFG build(ContinueStatement continueStatement) {
		
		Node node = new JumpNode(continueStatement);
		CFG cfg = new CFG(node);
		cfg.addContinueNode(node);
		return cfg;

	}

	/**
	 * Builds a control flow subgraph for a return statement.
	 * @param entry The entry point for the subgraph.
	 * @param exit The exit point for the subgraph.
	 * @return A list of exit nodes for the subgraph.
	 */
	private static CFG build(ReturnStatement returnStatement) {
		
		Node node = new JumpNode(returnStatement);
		CFG cfg = new CFG(node);
		cfg.addReturnNode(node);
		return cfg;

	}
	
	/**
	 * Builds a control flow subgraph for a statement.
	 * @param entry The entry point for the subgraph.
	 * @param exit The exit point for the subgraph.
	 * @return A list of exit nodes for the subgraph.
	 */
	private static CFG build(AstNode statement) {
		
		Node node = new StatementNode(statement);
		CFG cfg = new CFG(node);
		cfg.addExitNode(node);
		return cfg;

	}
	
	/**
	 * Calls the appropriate build method for the node type.
	 */
	private static CFG buildSwitch(AstNode node) {
		
		if(node == null) return null;

		if (node instanceof Block) {
			return CFGFactory.build((Block) node);
		} else if (node instanceof IfStatement) {
			return CFGFactory.build((IfStatement) node);
		} else if (node instanceof WhileLoop) {
			return CFGFactory.build((WhileLoop) node);
		} else if (node instanceof DoLoop) {
			return CFGFactory.build((DoLoop) node);
		} else if (node instanceof ForLoop) {
			return CFGFactory.build((ForLoop) node);
		} else if (node instanceof ForInLoop) {
			return CFGFactory.build((ForInLoop) node);
		} else if (node instanceof SwitchStatement) {
			return CFGFactory.build((SwitchStatement) node);
		} else if (node instanceof WithStatement) {
			return CFGFactory.build((WithStatement) node);
		} else if (node instanceof TryStatement) {
			return CFGFactory.build((TryStatement) node);
		} else if (node instanceof BreakStatement) {
			return CFGFactory.build((BreakStatement) node);
		} else if (node instanceof ContinueStatement) {
			return CFGFactory.build((ContinueStatement) node);
		} else if (node instanceof ReturnStatement) {
			return CFGFactory.build((ReturnStatement) node);
		} else if (node instanceof FunctionNode) {
			return null; // Function declarations shouldn't be part of the CFG.
		} else if (node instanceof Scope) {
			return CFGFactory.build((Scope) node);
		} else {
			return CFGFactory.build(node);
		}

	}
	
}
