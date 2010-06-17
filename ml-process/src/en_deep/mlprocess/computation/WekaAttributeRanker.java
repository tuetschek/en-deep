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
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.MathUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.ASSearch;
import weka.attributeSelection.AttributeEvaluator;
import weka.attributeSelection.RankedOutputSearch;
import weka.core.Instance;
import weka.core.Instances;

/**
 * This uses some of the classes in {@link weka.attributeSelection} to rank a list of attributes in ARFF file(s) and
 * produces a file with the list of attribute numbers ordered by the ranking on the first line.
 *
 * @author Ondrej Dusek
 */
public class WekaAttributeRanker extends GeneralClassifier {

    /* CONSTANTS */

    /** Name of the ranker parameter */
    private static final String RANKER = "ranker";
    /** Name of the evaluator parameter */
    private static final String EVALUATOR = "evaluator";
    /** Evaluator parameter prefix */
    private static final String EVALUATOR_PARAM_PREFIX = "E_";
    /** Ranker parameter prefix */
    private static final String RANKER_PARAM_PREFIX = "R_";

    private static final String LF = System.getProperty("line.separator");


    /* DATA */

    /** The WEKA attribute search with an evaluator */
    private ASSearch searcher;
    /** WEKA attribute (subset) evaluator for {@link #searcher} */
    private ASEvaluation searcherEval;
    /** The WEKA attribute ranker */
    private RankedOutputSearch ranker;
    /** The WEKA attribute evaluator */
    private AttributeEvaluator evaluator;

    /* METHODS */

    /**
     * This just checks the compulsory parameters and the inputs and outputs.
     * There must be two inputs and one output. There are two compulsory parameters:
     * <ul>
     * <li><tt>ranker</tt> -- the desired WEKA attribute ranker to be used</li>
     * <li><tt>class_arg</tt> -- the name of the target argument used for classification. If the parameter
     * is not specified, the one argument that is missing from the evaluation data will be selected. If
     * the training and evaluation data have the same arguments, the last one is used.</li>
     * </ul>
     *
     * @param id
     * @param parameters
     * @param input
     * @param output
     * @throws TaskException
     */
    public WekaAttributeRanker(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {
        super(id, parameters, input, output);

        if (this.getParameterVal(CLASS_ARG) == null 
                || (this.getParameterVal(RANKER) == null && this.getParameterVal(EVALUATOR) == null)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }
        
    }

    /**
     * This processes the data and evaluation data using the given WEKA attribute ranker (all data is
     * used for attribute ranking).
     *
     * @param trainFile the training data
     * @param evalFile the evaluation data (used for training as well)
     * @param outputFile the output of the ranking
     * @throws Exception
     */
    @Override
    protected void classify(String trainFile, String evalFile, String outputFile) throws Exception {
        
        Logger.getInstance().message(this.id + ": attribute ranking on " + trainFile + " and " + evalFile + "...",
                Logger.V_DEBUG);

        // read the data and find out the target class
        Instances train = FileUtils.readArff(trainFile);
        Instances eval = FileUtils.readArff(evalFile);

        this.findTargetFeature(train, eval);

        // merge the data
        Enumeration<Instance> evalInst = eval.enumerateInstances();
        while (evalInst.hasMoreElements()){
            train.add(evalInst.nextElement());
        }

        this.initRanker(train);

        // rank all the attributes and store the values
        String outText = null;
        if (this.searcher != null){
            int [] order = this.searcher.search(this.searcherEval, eval);
            outText = this.getAttribList(train, order);
        }
        else if (this.ranker != null){
            double [][] order = this.ranker.rankedAttributes();
            outText = this.getAttribList(train, order);
        }
        else { // evaluator != null
            double [] merits = new double [train.numAttributes()];

            for (int i = 0; i < train.numAttributes(); ++i){
                if (i != train.classIndex()){
                    merits[i] = this.evaluator.evaluateAttribute(i);
                }
                else {
                    merits[i] = Double.NEGATIVE_INFINITY; // this will eliminate the class attribute itself
                }
            }
            outText = this.sortByMerits(train, merits);
        }

        // sort the output and write it down
        FileUtils.writeString(outputFile, outText);

        Logger.getInstance().message(this.id + ": results saved to " + outputFile + ".", Logger.V_DEBUG);
    }


    /**
     * Initialize the attribute ranker and set its parameters. For details on ranker parameters,
     * see {@link #WekaAttributeRanker(String, Hashtable, Vector, Vector)}.
     *
     * @throws TaskException
     */
    private void initRanker(Instances data) throws TaskException {

        String rankerName = this.parameters.remove(RANKER);
        String evalName = this.parameters.remove(EVALUATOR);

        String [] rankerParams = null, evalParams = null;

        // both ranker and evaluator are set --> need prefixes in parameters
        if (rankerName != null && evalName != null){
            
            rankerParams = StringUtils.getWekaOptions(StringUtils.getPrefixParams(this.parameters, RANKER_PARAM_PREFIX));
            evalParams = StringUtils.getWekaOptions(StringUtils.getPrefixParams(this.parameters, EVALUATOR_PARAM_PREFIX));
        }
        else if (rankerName != null){
            rankerParams = StringUtils.getWekaOptions(this.parameters);
        }
        else {
            evalParams = StringUtils.getWekaOptions(this.parameters);
        }

        // try to create the ranker /evaluator / searcher corresponding to the given WEKA class name
        try {
            if (rankerName != null){                
                if (evalName == null){
                    this.ranker = (RankedOutputSearch) this.initWekaAS(rankerName, rankerParams, data);
                }
                else {
                    ASEvaluation evalInit = this.initWekaAS(evalName, evalParams, data);
                    this.evaluator = (AttributeEvaluator) evalInit;
                    this.searcher = ASSearch.forName(rankerName, rankerParams);
                }
            }
            else if (evalName != null){
                this.evaluator = (AttributeEvaluator) this.initWekaAS(evalName, evalParams, data);
            }
        }
        catch (Exception e) {
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id,
                    "Could not create ranker or evaluator: " + e.getMessage());
        }
    }

    /**
     * This initializes any WEKA class for attribute evaluation.
     *
     * @param className the name of the class
     * @param params the class parameters
     * @param data the data to initialize the class
     * @return the attribute evaluator object
     * @throws Exception if the class is not found or is not a derivee of {@link ASEvaluation}
     */
    private ASEvaluation initWekaAS(String className, String[] params, Instances data) throws Exception {

        ASEvaluation asObject = ASEvaluation.forName(className, params);
        asObject.buildEvaluator(data);

        return asObject;
    }

    /**
     * This sorts the attributes by their merits and produces a list of their numbers sorted on the first
     * line and their names with merits on the following lines.
     *
     * @param data the data which need to have their attributes sorted
     * @param merits the merits of the individual attributes in the data
     * @return the order of the attributes by their merits
     */
    private String sortByMerits(Instances data, double [] merits){

        int [] order = MathUtils.getOrder(merits);
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < order.length - 1; ++i){ // assume the class attribute itself is at the end
            out.append(order[i]);
            if (i < order.length-2){
                out.append(" ");
            }
        }
        out.append(LF);

        for (int i = 0; i < order.length -1; ++i){
            out.append(order[i] + " " + data.attribute(order[i]).name() + ": " + merits[i] + LF);
        }

        return out.toString();
    }

    /**
     * This returns the attribute list based on the given order (as output by {@link ASSearch}).
     * @param data the data the search was performed on
     * @param order the attribute order
     * @return the list of the attributes by their order
     */
    private String getAttribList(Instances data, int[] order) {
        
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < order.length; ++i){
            out.append(order[i]);
            if (i < order.length-1){
                out.append(" ");
            }
        }
        out.append(LF);
        for (int i = 0; i < order.length; ++i){
            out.append(order[i] + " " + data.attribute(order[i]).name() + LF);
        }
        return out.toString();
    }

    /**
     * This returns the attribute list based on the given order (as output by {@link RankedOutputSearch}).
     * @param data the data the search was performed on
     * @param order the attribute order
     * @return the list of the attributes by their order
     */
    private String getAttribList(Instances data, double[][] order) {

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < order.length; ++i){
            out.append((int) order[i][0]);
            if (i < order.length-1){
                out.append(" ");
            }
        }
        out.append(LF);
        for (int i = 0; i < order.length; ++i){
            out.append((int) order[i][0] + " " + data.attribute((int) order[i][0]).name() + ": " + order[i][1] + LF);
        }
        return out.toString();
    }

}
