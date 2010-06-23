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

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Pair;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.computation.GeneralClassifier;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;

/**
 * This computes accuracy, precision, recall and F1 (labeled and unlabeled) for the
 * given attribute in gold standard and test data.
 *
 * @author Ondrej Dusek
 */
public class EvalClassification extends Task {

    /* CONSTANTS */
    
    /** The name of the "class_arg" parameter */
    private static String CLASS_ARG = GeneralClassifier.CLASS_ARG;
    
    /** The value that's used as "empty" */
    private static String EMPTY = "_";

    /* DATA */

    /* METHODS */

    /**
     * This creates a new instance of {@link EvalClassification} and checks the parameters. 
     * <p>The number of inputs must be divisible by two, the first half of them is considered
     * to be the gold standard data, the second half is supposed to be the output of a classification.
     * The output specification must contain just one file name, in which the statistics are written.
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
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameter class_arg is missing.");
        }
        if (this.input.size() % 2 != 0){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id, "There must be pairs of inputs.");
        }
        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "There must be 1 output.");
        }
    }

    @Override
    public void perform() throws TaskException {

        try {
            Stats labeled = new Stats(), unlabeled = new Stats();

            for (int i = 0; i < this.input.size() / 2; i++) {

                Pair<Stats,Stats> results = this.eval(this.input.get(i), this.input.get(this.input.size() / 2 + i));
                labeled.add(results.first);
                unlabeled.add(results.second);

                Logger.getInstance().message("Evaluated " + this.input.get(i) + " against "
                        + this.input.get(this.input.size()/ 2 + i) + " : " + results.first.toString() + " / "
                        + results.second.toString(), Logger.V_DEBUG);

            }

            this.printStats(labeled, unlabeled, this.output.get(0));
        }
        catch (TaskException e){
            throw e;
        }
        catch (Exception e){
            Logger.getInstance().logStackTrace(e, Logger.V_DEBUG);
            throw new TaskException(TaskException.ERR_IO_ERROR, this.id, e.getMessage());
        }
    }

    /**
     * The actual evaluation of one file. This reads all input data and runs all the tests.
     *
     * @param goldFile the gold standard input file
     * @param testFile the test input file
     * @returns the evaluation statistics, first is labeled, second is unlabeled
     */
    private Pair<Stats,Stats> eval(String goldFile, String testFile) throws Exception {
        
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

        // test everything
        Stats labeled = this.getStats(gold.attributeToDoubleArray(attrGold.index()),
                test.attributeToDoubleArray(attrTest.index()), attrGold.indexOfValue(EMPTY), true);
        Stats unlabeled = this.getStats(gold.attributeToDoubleArray(attrGold.index()),
                test.attributeToDoubleArray(attrTest.index()), attrGold.indexOfValue(EMPTY), false);

        return new Pair<Stats, Stats>(labeled, unlabeled);
    }


    /**
     * This prints the evaluation statistics to a file.
     * @param labeled the labeled statistics
     * @param unlabeled the unlabeled statistics
     * @param fileName the output file name
     * @throws IOException
     */
    private void printStats(Stats labeled, Stats unlabeled, String fileName) throws IOException {

        PrintStream out = new PrintStream(fileName);

        out.println(labeled.toString());
        out.println(unlabeled.toString());
        out.println("accuracy:" + labeled.getAcc());
        out.println("labeled precision:" + labeled.getPrec());
        out.println("labeled recall:" + labeled.getRecall());
        out.println("labeled f1:" + labeled.getF1());
        out.println("unlabeled precision:" + unlabeled.getPrec());
        out.println("unlabeled recall:" + unlabeled.getRecall());
        out.println("unlabeled f1:" + unlabeled.getF1());

        out.close();
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
    private Stats getStats(double[] gold, double[] test, int emptyVal, boolean labeled) {

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
