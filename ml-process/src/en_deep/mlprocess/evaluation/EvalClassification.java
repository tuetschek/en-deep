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

import en_deep.mlprocess.Pair;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.computation.WekaClassifier;
import en_deep.mlprocess.exception.TaskException;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;

/**
 * This computes accuracy, precision, recall and F1 (labeled and unlabeled) for the
 * given attribute in gold standard and test data.
 *
 * @author Ondrej Dusek
 */
public class EvalClassification extends Task {

    /* CONSTANTS */
    
    /** The name of the "class_arg" parameter */
    private static String CLASS_ARG = WekaClassifier.CLASS_ARG;
    
    /** The value that's used as "empty" */
    private static String EMPTY = "_";

    /* DATA */

    /* METHODS */

    /**
     * This creates a new instance of {@link EvalClassification} and checks the parameters. 
     * <p> The input must contain exactly two files, first of which is considered to
     * be the gold standard and the second to be the test data. The output must contain just one
     * file name, in which the statistics are written.
     * </p><p>
     * There is one compulsory parameter:
     * <ul>
     * <li><tt>class_arg</tt> -- the name of the class attribute that is to be checked.</li>
     * </ul>
     *
     * @param id
     * @param parameters
     * @param input
     * @param output
     */
    public EvalClassification(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (this.parameters.get(CLASS_ARG) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id);
        }
        if (this.input.size() != 2){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
    }

    @Override
    public void perform() throws TaskException {

        try {
            this.eval(this.input.get(0), this.input.get(1), this.output.get(0));
        }
        catch (TaskException e){
            throw e;
        }
        catch (Exception e){
            e.printStackTrace();
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id);
        }
    }

    /**
     * The actual evaluation. This reads all input data and runs all the tests.
     *
     * @param goldFile the gold standard input file
     * @param testFile the test input file
     * @param outFile the statistics output file
     */
    private void eval(String goldFile, String testFile, String outFile) throws Exception {
        
        // read the gold data
        ConverterUtils.DataSource dataIn = new ConverterUtils.DataSource(goldFile);
        Instances gold = dataIn.getDataSet();
        dataIn.reset();
        // read the test data
        dataIn = new ConverterUtils.DataSource(testFile);
        Instances test = dataIn.getDataSet();
        dataIn.reset();

        // find the attribute to evaluate
        String attr = this.parameters.get(CLASS_ARG);
        Attribute attrGold = null, attrTest = null;
        
        if ((attrGold = gold.attribute(attr)) == null || (attrTest = test.attribute(attr)) == null
                || !attrGold.isNominal() || !attrTest.isNominal()
                || gold.numInstances() != test.numInstances()){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id);
        }

        // test everything
        double acc = this.accuracy(gold.attributeToDoubleArray(attrGold.index()),
                test.attributeToDoubleArray(attrTest.index()));

        Pair<Double, Double> labeled = this.precRec(gold.attributeToDoubleArray(attrGold.index()),
                test.attributeToDoubleArray(attrTest.index()), attrGold.indexOfValue(EMPTY), true);

        Pair<Double, Double> unlabeled = this.precRec(gold.attributeToDoubleArray(attrGold.index()),
                test.attributeToDoubleArray(attrTest.index()), attrGold.indexOfValue(EMPTY), false);

        // output the results
        PrintStream out = new PrintStream(outFile);

        out.println("accuracy:" + acc);
        out.println("labelled precision:" + labeled.first);
        out.println("labelled recall:" + labeled.second);
        out.println("labelled f1:" + ((labeled.second + labeled.second) / 2));
        out.println("unlabelled precision:" + unlabeled.first);
        out.println("unlabelled recall:" + unlabeled.second);
        out.println("unlabelled f1:" + ((unlabeled.first + unlabeled.second) / 2));

        out.close();
    }

    /**
     * Computes the accuracy, given the data as arrays.
     * @param gold the gold standard values
     * @param eval the test data values
     * @return the accuracy
     */
    private double accuracy(double[] gold, double[] eval) {

        int good = 0;

        for (int i = 0; i < gold.length; ++i){
            if (gold[i] == eval[i]){
                ++good;
            }
        }
        return (double) good / (double) gold.length;
    }


    /**
     * Computes precision and recall, given the data as arrays. Works for labeled and unlabeled version
     * (unlabeled checks against the EMPTY value only, labeled checks for wrong values, too).
     *
     * @param gold the gold standard values
     * @param test the test data values
     * @param emptyVal the value that is treated as "empty"
     * @param labeled should we consider labels ?
     * @return the precision and recall values, respectively
     */
    private Pair<Double, Double> precRec(double[] gold, double[] test, int emptyVal, boolean labeled) {

        int tp = 0, fp = 0, tn = 0, fn = 0;
        

        for (int i = 0; i < gold.length; ++i){
            if (gold[i] == emptyVal && test[i] == emptyVal){
                ++tn;
            }
            else if (gold[i] == emptyVal && test[i] != emptyVal){
                ++fp;
            }
            else if (gold[i] != emptyVal && test[i] == emptyVal){
                ++fn;
            }
            else if (!labeled || gold[i] == test[i]){
                ++tp;
            }
            else { // same as CoNLL evaluation: wrong label on a right place is a false positive AND negative
                ++fp; ++fn;
            }
        }

        // first - precision, second - recall
        return new Pair<Double, Double>((double) tp / (tp + fp), (double) tp / (tp + fn));
    }

}
