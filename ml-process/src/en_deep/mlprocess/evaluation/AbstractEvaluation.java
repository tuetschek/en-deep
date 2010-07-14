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

package en_deep.mlprocess.evaluation;

import en_deep.mlprocess.Task;
import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Pair;
import en_deep.mlprocess.computation.GeneralClassifier;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.SortLabels;

/**
 * This unifies some very basic functions needed for classification evaluation.
 * @author Ondrej Dusek
 */
public abstract class AbstractEvaluation extends Task {
    
    /* CONSTANTS */

    /** The name of the "class_arg" parameter */
    protected static final String CLASS_ARG = GeneralClassifier.CLASS_ARG;
    /** The value that's used as "empty" */
    protected static String EMPTY = "_";

    /* DATA */

    /* METHODS */

    /**
     * This just checks for the {@link #CLASS_ARG} parameter and that there are pairs of inputs and outputs.
     */
    protected AbstractEvaluation(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (this.parameters.get(CLASS_ARG) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameter class_arg is missing.");
        }
        if (this.input.size() % 2 != 0){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "There must be pairs of inputs.");
        }
    }

    /**
     * This returns all the labels for the given attribute, in the correct order.
     * @param attr the attribute to be processed
     * @return all the labels for the values of the given attribute
     */
    protected String [] getLabels(Attribute attr) {

        String [] labels = new String [attr.numValues()];

        for (int i = 0; i < attr.numValues(); i++) {
            labels[i] = attr.value(i);
        }
        return labels;
    }

    /**
     * This reads one gold and test data file pair. If the class attribute is not present or not equal
     * in both files, an exception is raised. If the nominal attribute labels are in different order in
     * both files, the order will be corrected.
     * 
     * @param goldFile the golden data file name
     * @param testFile the test data file name
     * @return the golden and test data, respectively
     */
    protected Pair<Instances,Instances> readData(String goldFile, String testFile) throws TaskException, Exception{

        // read the gold data
        Instances gold = FileUtils.readArff(goldFile);
        // read the test data
        Instances test = FileUtils.readArff(testFile);

        // find the attribute to evaluate
        String attr = this.parameters.get(CLASS_ARG);
        Attribute attrGold = null, attrTest = null;

        if ((attrGold = gold.attribute(attr)) == null || (attrTest = test.attribute(attr)) == null
                || !attrGold.isNominal() || !attrTest.isNominal()
                || gold.numInstances() != test.numInstances()){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id,
                    "Attribute for evaluation not found or not nominal, or the numbers of instances in gold " +
                    "and evaluation data mismatch (" + goldFile + " / " + testFile + ").");
        }
        if (!attrGold.equals(attrTest) && attrGold.numValues() == attrTest.numValues()){

            gold = this.sortLabels(gold, attrGold);
            test = this.sortLabels(test, attrTest);
            attrGold = gold.attribute(attr);
            attrTest = test.attribute(attr);
        }
        if (!attrGold.equals(attrTest)){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id,
                    "The class attributes in " + goldFile + " and " + testFile + " are not the same.");
        }
        gold.setClass(attrGold);
        test.setClass(attrTest);

        return new Pair<Instances, Instances> (gold, test);
    }

    
    /**
     * This sorts the labels of the given attribute alphabetically (changing the data values)
     * @param data the data to be processed
     * @param attr the attribute whose labels should be sorted
     * @return the data with the attribute labels sorted
     */
    private Instances sortLabels(Instances data, Attribute attr) throws Exception {

        SortLabels sorting = new SortLabels();
        sorting.setAttributeIndices(Integer.toString(attr.index() + 1));
        sorting.setInputFormat(data);

        data = Filter.useFilter(data, sorting);
        return data;
    }


    /**
     * Collects the {@link Stats} values, given the data as arrays. Works for labeled and unlabeled version
     * (unlabeled checks against the EMPTY value only, labeled checks for wrong values, too).
     *
     * @param gold the gold standard values
     * @param test the test data values
     * @param emptyVal the value that is treated as "empty"
     * @param labeled should we consider labels ?
     * @return the precision and recall values, respectively
     */
    protected Stats getStats(double[] gold, double[] test, int emptyVal, boolean labeled) {

        Stats stats = new Stats();

        stats.n = gold.length;

        for (int i = 0; i < gold.length; ++i){
            if (gold[i] == emptyVal && test[i] == emptyVal){
                ++stats.tn;
            }
            else if (gold[i] == emptyVal && test[i] != emptyVal){
                ++stats.fp;
            }
            else if (gold[i] != emptyVal && test[i] == emptyVal){
                ++stats.fn;
            }
            else if (!labeled || gold[i] == test[i]){
                ++stats.tp;
            }
            else { // same as CoNLL evaluation: wrong label on a right place is a false positive AND negative
                ++stats.fp; ++stats.fn;
            }
        }

        // first - precision, second - recall
        return stats;
    }
}
