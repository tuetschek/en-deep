/*
 *  Copyright (c) 2010 Ondrej Dusek
 *  All rights reserved.
 * 
 *  Redistribution and use in source and binary forms, with or without modification, 
 *  are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright notice, this list 
 *  of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this 
 *  list of conditions and the following disclaimer in the documentation and/or other 
 *  materials provided with the distribution.
 *  Neither the name of Ondrej Dusek nor the names of their contributors may be
 *  used to endorse or promote products derived from this software without specific 
 *  prior written permission.
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 *  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 *  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package en_deep.mlprocess.computation;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Pair;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.MathUtils;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;
import lpsolve.LpSolve;
import weka.core.Attribute;

/**
 * This implements the semantic resolution using Integer Linear Programming inference (via the LpSolve implementation).
 * @author Ondrej Dusek
 */
public class ILPSemanticResolver extends AbstractSemanticResolver {

    /* CONSTANTS */

    /** The 'threshold' parameter name */
    private static final String THRESHOLD = "threshold";

    /* DATA */

    /** Minimum probability so that a SR assignment is not blocked */
    private final double threshold;


    /* METHODS */

    /**
     * This creates a new {@link ILPSemanticResolver} task, checking the numbers of inputs and outputs (must be both 1)
     * and the necessary parameters:
     * <ul>
     * <li><tt>sentence</tt> -- the name of the attribute whose identical values indicate the membership of the same
     * sentence</li>
     * <li><tt>distr</tt> -- prefix for the attributes of the probability distribution</li>
     * <li><tt>no_duplicate</tt> (optional) -- list of no-duplicate semantic roles</li>
     * <li><tt>threshold</tt> (optional) -- minimum probability that the most
     * likely instance in a sentence must have so that this semantic role is set at all</li>
     * </ul>
     */
    public ILPSemanticResolver(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (this.getParameterVal(THRESHOLD) != null){
            try {
                this.threshold = Double.parseDouble(this.getParameterVal(THRESHOLD));
            }
            catch (NumberFormatException e){
                throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Threshold must be numeric.");
            }
        }
        else {
            this.threshold = 0.0;
        }
    }

    /**
     * This resolves the semantic roles using a binary linear programming model via LpSolve.
     * @throws Exception if something goes wrong with the LP model
     */
    @Override
    protected void resolve() throws Exception {

        int [] noDupRoles = this.findNoDupRoles();

        while (this.loadNextSentence()){

            Logger.getInstance().message("Creating an ILP problem for " + this.data.relationName() 
                    + ", sent-base " + this.curSentBase + " ...", Logger.V_INFO);

            // build an ILP problem
            LpSolve solver = LpSolve.makeLp(0, this.curSentLen * this.distrAttribs.size());
            solver.setVerbose(LpSolve.IMPORTANT);

            // set all to binary
            for (int colNo = 1; colNo <= solver.getNcolumns(); ++colNo){
                solver.setBinary(colNo, true);
            }

            // constraints: assign one role for each word
            for (int wordNo = 0; wordNo < this.curSentLen; ++wordNo){
                int [] colNos = MathUtils.sequence(wordNo * this.distrAttribs.size() + 1, this.distrAttribs.size(), 1);
                double [] vals = new double [this.distrAttribs.size()];
                Arrays.fill(vals, 1.0);
                solver.addConstraintex(vals.length, vals, colNos, LpSolve.EQ, 1);
            }

            // constraints: no-duplicate roles
            for (int role = 0; role < noDupRoles.length; ++role){
                int [] colNos = MathUtils.sequence(noDupRoles[role] + 1, this.curSentLen, this.distrAttribs.size());
                double [] vals = new double [this.curSentLen];
                Arrays.fill(vals, 1.0);
                solver.addConstraintex(vals.length, vals, colNos, LpSolve.LE, 1);
            } 

            // the objective function
            Pair<int [], double []> distrValues = this.getSentenceProbs();
            solver.setObjFnex(distrValues.first.length, distrValues.second, distrValues.first);
            solver.setMaxim();

            // solve the problem
            int result = solver.solve();
            if (result != LpSolve.SUBOPTIMAL && result != LpSolve.OPTIMAL){
                throw new TaskException(TaskException.ERR_IO_ERROR, this.id, "Could not solve ILP problem for "
                        + this.data.relationName() + ", sent-base " + this.curSentBase + ":" 
                        + solver.getStatustext(result));
            }

            Logger.getInstance().message("ILP problem solved: " + solver.getStatustext(result) + " -- "
                    + solver.getObjective(), result);

            // set the result
            this.assignSemRoles(solver.getPtrVariables());

            solver.deleteLp();
        }
    }

    /**
     * This finds the indexes of all no-duplicate roles in the {@link #distrAttribs} field, so that those roles
     * may be marked as no-duplicate in the LP problem.
     * @return indexes of all no-duplicate possible roles, or an empty array if not found
     */
    private int[] findNoDupRoles() throws TaskException {

        if (this.getParameterVal(NO_DUP) == null){
            return new int [0];
        }
        String [] noDupRoleNames = this.getParameterVal(NO_DUP).split("\\s+");
        int [] noDupRoleIdxs = new int [noDupRoleNames.length];

        for (int i = 0; i < noDupRoleNames.length; ++i){
            if (this.data.classAttribute().indexOfValue(noDupRoleNames[i]) == -1){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "No-duplicate role not found: "
                        + noDupRoleNames[i]);
            }
            Attribute attr = this.data.attribute(this.distrPrefix + "_" + noDupRoleNames[i]);
            noDupRoleIdxs[i] = this.distrAttribs.indexOf(attr); // this must not be -1 if the attribute exists
        }
        return noDupRoleIdxs;
    }

    /**
     * This retrieves all the probabilities of the words in the sentence greater than the set-up {@link #threshold}.
     * Their indexes (1-based for LpSolve) and values are returned.
     * @return the indexes and values of non-zero SR probabilities of all the words in the current sentence
     */
    private Pair<int[], double[]> getSentenceProbs() {

        Vector<Integer> nonZeroIndexes = new Vector<Integer>();
        Vector<Double> nonZeroProbs = new Vector<Double>();

        for (int word = 0; word < this.curSentLen; ++word){
            for (int role = 0; role < this.distrAttribs.size(); ++role){

                double value = data.get(this.curSentBase + word).value(this.distrAttribs.get(role));

                if (value > this.threshold){
                    nonZeroIndexes.add(word * this.distrAttribs.size() + role + 1);
                    nonZeroProbs.add(value);
                }
            }
        }

        Pair<int [], double []> ret = new Pair<int [], double []>(new int [nonZeroIndexes.size()],
                new double [nonZeroIndexes.size()]);

        for (int i = 0; i < nonZeroIndexes.size(); ++i){
            ret.first[i] = nonZeroIndexes.get(i);
            ret.second[i] = nonZeroProbs.get(i);
        }
        return ret;
    }

    /**
     * This assigns the values of the class attribute based on the results of the LP inference.
     * @param optimum the optimal solution found by the LP solver
     */
    private void assignSemRoles(double[] optimum) {

        for (int wordNo = 0; wordNo < this.curSentLen; ++wordNo){
            for (int roleNo = 0; roleNo < this.distrAttribs.size(); ++roleNo){
                if (optimum[wordNo * this.distrAttribs.size() + roleNo] > 0.0){
                    this.data.get(this.curSentBase + wordNo).setClassValue(roleNo);
                    break;
                }
            }
        }
    }
}
