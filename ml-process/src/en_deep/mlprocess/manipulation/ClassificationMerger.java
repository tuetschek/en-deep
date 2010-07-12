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

package en_deep.mlprocess.manipulation;

import en_deep.mlprocess.Task;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.computation.EvalSelector;
import en_deep.mlprocess.computation.GeneralClassifier;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import en_deep.mlprocess.utils.MathUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import weka.core.Instances;

/**
 * This tries to merge the classification of several classifiers and create a better classification out of it.
 * It works on the principle of voting -- the class which gets the most votes wins. If there's a tie, the classifier
 * with best rankings is chosen.
 * @author Ondrej Dusek
 */
public class ClassificationMerger extends Task {
    
    /* CONSTANTS */

    /** The measure parameter name */
    private static final String MEASURE = EvalSelector.MEASURE;
    /** The class_arg parameter name */
    private static final String CLASS_ARG = GeneralClassifier.CLASS_ARG;

    /* DATA */

    /** The name of the class attribute */
    private String classArgName;
    /* The order of classifier rankings */
    private int [] classifierOrder;
    /** The data of the input files */
    private Instances [] data;
    /** The indexes of the class attribute in the individual classifications */
    private int [] classArgIndexes;

    /* METHODS */

    /**
     * This creates a new {@link ClassificationMerger} task, checking the numbers of inputs and outputs
     * and the necessary parameters:
     * <ul>
     * <li><tt>class_arg</tt> -- the class attribute name
     * <tt><tt>measure</tt> -- the measure of classifier comparison
     * </ul>
     * The first half of inputs is assumed to be classifications of the same file, the second half the
     * rankings of the classifiers that produced the classifications, in the same order.
     * The output is just one file -- the final classification.
     */
    public ClassificationMerger(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (!this.hasParameter(CLASS_ARG) || !this.hasParameter(MEASURE)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }
        this.classArgName = this.getParameterVal(CLASS_ARG);
        
        if (this.input.isEmpty() || this.input.size() % 2 != 0){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
    }



    @Override
    public void perform() throws TaskException {
        
        try {
            this.getClassifierRankings(this.input.subList(this.input.size()/2, this.input.size()));
            this.loadData(this.input.subList(0, this.input.size()/2));
            this.processData(this.output.get(0));
        }
        catch (TaskException e){
            throw e;
        }
        catch (Exception e) {
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }

    /**
     * This reads all the classifier ranking files and orders the classifiers by their performance.
     * @param rankings the rankings list
     */
    private void getClassifierRankings(List<String> rankings) throws IOException, TaskException {

        double [] ranks = new double [rankings.size()];
        Iterator<String> it = rankings.iterator();
        int pos = 0;
        String measure = this.getParameterVal(MEASURE);

        while(it.hasNext()){
            String file = it.next();
            try {
                Double val = FileUtils.readValue(file, measure);
                if (val == null){
                    throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Value of " + measure +
                            " not found in " + file);
                }
                ranks[pos] = val;
            }
            catch (NumberFormatException e){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Invalid " + measure
                        + " specification in " + file);
            }
            pos++;
        }
        this.classifierOrder = MathUtils.getOrder(ranks);
    }

    /**
     * This loads the headers all data files and checks if they contain the class
     * argument and on which position (this is saved to {@link #classArgIndexes}. It also checks the number
     * of instances in the data.
     * 
     * @param classifications
     */
    private void loadData(List<String> classifications) throws Exception {

        Iterator<String> it = classifications.iterator();
        int pos = 0;
        this.data = new Instances [classifications.size()];
        this.classArgIndexes = new int [classifications.size()];

        while (it.hasNext()){
            String file = it.next();

            this.data[pos] = FileUtils.readArff(file);
            if (this.data[pos].attribute(this.classArgName) == null){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Class attribute not found in "
                        + file + ".");
            }
            this.classArgIndexes[pos] = this.data[pos].attribute(this.classArgName).index();

            if (pos > 0 && this.data[pos].numInstances() != this.data[0].numInstances()){
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Numbers of instances differ.");
            }

            pos++;
        }
    }

    /**
     * This creates the output file, using the predictions loaded in {@link #data}.
     * @param fileName the output file name
     */
    private void processData(String fileName) throws Exception {

        Instances out = new Instances(data[0]);
        HashMap<String, Integer> curVals = new HashMap<String, Integer>(data.length);
        ArrayList<String> bestLabels = new ArrayList<String>(data.length);
        int bestVote;

        for (int i = 0; i < out.numInstances(); ++i){

            curVals.clear();
            for (int j = 0; j < data.length; ++j){ // collect predictions
                String val = data[j].get(i).stringValue(this.classArgIndexes[j]);
                if (curVals.containsKey(val)){
                    curVals.put(val, curVals.get(val) + 1);
                }
                else {
                    curVals.put(val, 1);
                }
            }

            // find the most wanted
            bestVote = 0;
            for (String label: curVals.keySet()){
                if (curVals.get(label) > bestVote){
                    bestVote = curVals.get(label);
                    bestLabels.clear();
                    bestLabels.add(label);
                }
                else if (curVals.get(label) == bestVote){
                    bestLabels.add(label);
                }
            }

            // there are more labels that got the same number of votes -> select the one provided by the
            // most successful classifier
            if (bestLabels.size() > 1){

                int bestClassifRank = Integer.MAX_VALUE;
                String bestLabel = null;

                for (int j = 0; j < data.length; ++j){ // collect predictions
                    String val = data[j].get(i).stringValue(this.classArgIndexes[j]);
                    if (bestLabels.contains(val) && this.classifierOrder[j] < bestClassifRank){
                        bestClassifRank = this.classifierOrder[j];
                        bestLabel = val;
                    }
                }
                out.get(i).setValue(this.classArgIndexes[0], bestLabel);
            }
            // only one best label, set it
            else {
                // TODO possibly add label, if not present (would need a filter)
                out.get(i).setValue(this.classArgIndexes[0], bestLabels.get(0));
            }
        }

        FileUtils.writeArff(fileName, out);
    }


}
