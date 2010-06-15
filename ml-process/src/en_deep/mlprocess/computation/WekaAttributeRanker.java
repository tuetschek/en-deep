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
import weka.attributeSelection.AttributeEvaluator;
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

    /** Name of the weka_class parameter */
    private static final String WEKA_CLASS = "weka_class";

    private static final String LF = System.getProperty("line.separator");


    /* DATA */

    /** The WEKA attribute ranker */
    private AttributeEvaluator ranker;

    /* METHODS */

    /**
     * This just checks the compulsory parameters and the inputs and outputs.
     * There must be two inputs and one output. There are two compulsory parameters:
     * <ul>
     * <li><tt>weka_class</tt> -- the desired WEKA classifier to be used</li>
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

        if (this.getParameterVal(CLASS_ARG) == null || this.getParameterVal(WEKA_CLASS) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }
        
    }

    /**
     * This processes the train and evaluation data using the given WEKA attribute ranker (all data is
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
        double [] merits = new double [train.numAttributes()];

        for (int i = 0; i < train.numAttributes(); ++i){
            if (i != train.classIndex()){
                merits[i] = this.ranker.evaluateAttribute(i);
            }
            else {
                merits[i] = Double.NEGATIVE_INFINITY; // this will eliminate the class attribute itself
            }
        }

        // sort the output and write it down
        FileUtils.writeString(outputFile, this.sortByMerits(train, merits));

        Logger.getInstance().message(this.id + ": results saved to " + outputFile + ".", Logger.V_DEBUG);
    }


    /**
     * Initialize the attribute ranker and set its parameters. For details on ranker parameters,
     * see {@link #WekaAttributeRanker(String, Hashtable, Vector, Vector)}.
     *
     * @throws TaskException
     */
    private void initRanker(Instances data) throws TaskException {

        String rankerName = this.parameters.remove(WEKA_CLASS);
        String [] rankerParams = StringUtils.getWekaOptions(this.parameters);

        // try to create the ranker corresponding to the given WEKA class name
        try {
            ASEvaluation rankerInit = ASEvaluation.forName(rankerName, rankerParams);
            rankerInit.buildEvaluator(data);
            this.ranker = (AttributeEvaluator) rankerInit;
        }
        catch (Exception e) {
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id,
                    "WEKA class not found or not valid: " + rankerName + " -- " + e.getMessage());
        }
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
            if (i < order.length-1){
                out.append(" ");
            }
        }
        out.append(LF);

        for (int i = 0; i < order.length -1; ++i){
            out.append(order[i] + " " + data.attribute(order[i]).name() + ": " + merits[i] + LF);
        }

        return out.toString();
    }

}
