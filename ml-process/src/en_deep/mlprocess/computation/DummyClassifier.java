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

import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.FileUtils;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import weka.core.Instance;
import weka.core.Instances;

/**
 * This is just a dummy classifier that assigns the most common value of the target attribute in the training data.
 * If there are several possible most common values, it picks one at random.
 * @author Ondrej Dusek
 */
public class DummyClassifier extends GeneralClassifier {

    /**
     * This creates a new DummyClasifier. Just the inputs and outputs are checked.
     * There is one voluntary parameter:
     * <ul>
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
    public DummyClassifier(String id, Hashtable<String, String> parameters, Vector<String> input,
            Vector<String> output) throws TaskException {

        super(id, parameters, input, output);
    }

    @Override
    protected void classify(String trainFile, List<String> evalFiles, List<String> outputFiles) throws Exception {

        // read the training data
        Instances train = FileUtils.readArff(trainFile);        
        double bestVal = Double.NaN;
        
        for (int i = 0; i < evalFiles.size(); ++i){

            Instances eval = FileUtils.readArff(evalFiles.get(i));

            if (i == 0){ // find the best value (only the first time)
                this.findClassFeature(train, eval);
                bestVal = this.getBestValue(train);
            }
            else {
                this.setClassFeature(train, eval);
            }
            // apply it to the evaluation data
            Enumeration instances = eval.enumerateInstances();
            while(instances.hasMoreElements()){

                Instance inst = (Instance) instances.nextElement();
                inst.setClassValue(bestVal);
            }

            // write the result
            FileUtils.writeArff(outputFiles.get(i), eval);
        }        
    }

    /**
     * Find the most often used value in the training data.
     * @param train the training data
     * @return the most often used value in the training data
     */
    private double getBestValue(Instances train) {

        Hashtable<Double,Integer> stats = new Hashtable<Double, Integer>();

        // make statistics about the training data
        double[] values = train.attributeToDoubleArray(train.classIndex());
        for (int i = 0; i < values.length; ++i) {
            if (stats.get(values[i]) != null) {
                stats.put(values[i], stats.get(values[i]) + 1);
            } else {
                stats.put(values[i], 1);
            }
        }
        // find the most often used value
        double bestVal = Double.NaN;
        int highestCount = -1;

        Enumeration<Double> valKeys = stats.keys();
        while (valKeys.hasMoreElements()) {
            Double val = valKeys.nextElement();
            if (stats.get(val) > highestCount) {
                highestCount = stats.get(val);
                bestVal = val;
            }
        }

        return bestVal;
    }

}
