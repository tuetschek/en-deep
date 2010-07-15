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
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.MathUtils;
import en_deep.mlprocess.utils.StringUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;
import weka.core.Instances;

/**
 * This is a simple implementation of a bootstrap results confidence intervals estimation.
 * @author Ondrej Dusek
 */
public class BootstrapTest extends AbstractEvaluation {
    
    /* CONSTANTS */

    /** The samples parameter name */
    private static final String SAMPLES = "samples";
    /** The size parameter name */
    private static final String SIZE = "size";
    /** The size_pc parameter name */
    private static final String SIZE_PC = "size_pc";

    /* DATA */

    /** The golden class values */
    private double [] gold;
    /** The evaluation class values */
    private double [] test;
    /** Number of samples to create */
    private int samplesNo;
    /** The sample size */
    private int sampleSize;
    /** The percentage of sample:data size */
    private double samplePerc;
    /** Random number generator */
    private Random rnd;

    /* METHODS */

    /**
     * This creates a new {@link BootstrapTest} task. It checks the numbers of inputs and outputs
     * (inputs must be pairs: 1st half is supposed to be gold and the 2nd test data; there must be
     * only 1 output) and the necessary parameters:
     * <ul>
     * <li><tt>class_arg</tt> -- the class attribute that is used for testing</li>
     * <li><tt>samples</tt> -- number of bootstrap samples to be created</li>
     * <li><tt>size</tt> -- sample size</li>
     * <li><tt>size_pc</tt> -- sample size (percentage)</tt>
     * </ul>
     * The parameters <tt>size</tt> and <tt>size_pc</tt> are mutually exclusive.
     */
    public BootstrapTest(String id, Hashtable<String, String> parameters,
            Vector<String> input, Vector<String> output) throws TaskException {

        super(id, parameters, input, output);

        if (this.output.size() != 1){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id, "There must be 1 output.");
        }
        if (!this.hasParameter(SAMPLES) || (!this.hasParameter(SIZE) && !this.hasParameter(SIZE_PC))){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }
        if (this.hasParameter(SIZE) && this.hasParameter(SIZE_PC)){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Parameters " + SIZE + " and "
                    + SIZE_PC + " are mutually exclusive.");
        }
        if (this.hasParameter(SIZE_PC)){
            this.samplePerc = this.getDoubleParameterVal(SIZE_PC);
            this.sampleSize = -1;
        }
        else {
            this.sampleSize = (int) this.getIntParameterVal(SIZE);
        }
        this.samplesNo = (int) this.getIntParameterVal(SAMPLES);

        // initialize the random number generator
        this.rnd = new Random();
    }



    @Override
    public void perform() throws TaskException {
        
        try {
            this.readAllData();
            if (this.sampleSize == -1){
                this.sampleSize = (int) ((this.samplePerc * this.gold.length) / 100.0);
            }
            Logger.getInstance().message(this.id + " loaded data. Performing bootstrap with "
                    + this.samplesNo + " sets of " + this.sampleSize + " samples...", Logger.V_DEBUG);
            this.bootstrap(this.output.get(0));
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
     * Read all pairs of data and store them in the {@link #gold} and {@link #test} members. All empty
     * values are set to 0 (substituting 0 for the empty value).
     */
    private void readAllData() throws Exception {

        ArrayList<double []> golds = new ArrayList<double[]>();
        ArrayList<double []> tests = new ArrayList<double[]>();
        int totalLen = 0;

        // accumulate all the data
        for (int i = 0; i < this.input.size() / 2; ++i){
            Pair<Instances, Instances> curData = this.readData(this.input.get(i), this.input.get(input.size()/2 + i));
            int emptyVal = StringUtils.find(this.getLabels(curData.first.classAttribute()), EMPTY);
            double [] goldVals = curData.first.attributeToDoubleArray(curData.first.classIndex());
            double [] testVals = curData.second.attributeToDoubleArray(curData.second.classIndex());

            MathUtils.swapValues(goldVals, 0, emptyVal); // if there's no empty value, 0 will be subst. for -1
            MathUtils.swapValues(testVals, 0, emptyVal);
            golds.add(goldVals);
            tests.add(testVals);
            totalLen += goldVals.length;
        }

        this.gold = new double [totalLen];
        this.test = new double [totalLen];
        int curPos = 0;

        for (int i = 0; i < golds.size(); ++i){
            System.arraycopy(golds.get(i), 0, this.gold, curPos, golds.get(i).length);
            System.arraycopy(tests.get(i), 0, this.test, curPos, tests.get(i).length);
            curPos += golds.get(i).length;
        }       
    }

    /**
     * This performs the bootstrapping and saves the results into the given file.
     * @param outFile the name of the output file
     */
    private void bootstrap(String outFile) throws IOException {

        double [] f1 = new double [this.samplesNo];
        double [] acc = new double [this.samplesNo];
        double [] pre = new double [this.samplesNo];
        double [] rec = new double [this.samplesNo];

        double [] sampleGold = new double [this.sampleSize];
        double [] sampleTest = new double [this.sampleSize];

        for (int i = 0; i < this.samplesNo; ++i){

            this.sample(sampleGold, sampleTest);

            Stats stats = this.getStats(sampleGold, sampleTest, 0, true);

            f1[i] = stats.getF1();
            acc[i] = stats.getAcc();
            pre[i] = stats.getPrec();
            rec[i] = stats.getRecall();
        }

        PrintStream out = new PrintStream(outFile);

        this.printQuantiles(acc, "accuracy", out);
        this.printQuantiles(pre, "precision", out);
        this.printQuantiles(rec, "recall", out);
        this.printQuantiles(f1, "f1", out);
        out.close();
    }

    /**
     * This saves one bootstrapping sample into the given array.
     * @param sampleGold place to save the gold sample values
     * @param sampleTest place to save the test sample values
     */
    private void sample(double [] sampleGold, double [] sampleTest) {

        for (int i = 0; i < this.sampleSize; ++i){
            int idx = this.rnd.nextInt(this.gold.length);

            sampleGold[i] = this.gold[idx];
            sampleTest[i] = this.test[idx];
        }
    }

    /**
     * This prints the 5%, 50% and 95% quantile of metrics values with the given label into the given
     * (open) output stream.
     * @param results the values of some metrics
     * @param label the name of the metrics
     */
    private void printQuantiles(double[] results, String label, PrintStream out) throws IOException {

        Arrays.sort(results);
        int perc5 = (int) (results.length * 0.05);
        int perc50 = (int) (results.length * 0.5);
        int perc95 = (int) (results.length * 0.95);

        out.print(label + ":");
        out.print(" 5% - " + results[perc5]);
        out.print(" / 50% - " + results[perc50]);
        out.print(" / 95% - " + results[perc95]);
        out.println();
    }


}
