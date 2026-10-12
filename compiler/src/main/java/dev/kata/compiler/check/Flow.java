package dev.kata.compiler.check;

import dev.kata.compiler.syntax.Ast;

/** Control-flow facts shared by the checker and the code generator. */
public final class Flow {
    private Flow() {}

    /** Spec 04 #return: the statement always ends the method. Loops never do. */
    public static boolean terminates(Ast.Stmt stmt) {
        if (stmt instanceof Ast.Return) {
            return true;
        }
        if (stmt instanceof Ast.Block block) {
            return !block.stmts().isEmpty() && terminates(block.stmts().get(block.stmts().size() - 1));
        }
        if (stmt instanceof Ast.If branch) {
            return branch.orElse() != null && terminates(branch.then()) && terminates(branch.orElse());
        }
        if (stmt instanceof Ast.Match match) {
            // A match is exhaustive, so it ends the method when every arm does.
            return !match.arms().isEmpty() && match.arms().stream().allMatch(arm -> terminates(arm.body()));
        }
        return false;
    }
}
