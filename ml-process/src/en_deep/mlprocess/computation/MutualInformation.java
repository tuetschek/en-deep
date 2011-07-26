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

import en_deep.mlprocess.utils.Pair;
import en_deep.mlprocess.utils.MathUtils;
import java.util.Arrays;
import java.util.Hashtable;
import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.AttributeEvaluator;
import weka.core.Capabilities;
import weka.core.Instances;

/**
 * This evaluates the attributes based on their mutual information with the class attribute.
 * @author Ondrej Dusek
 */
public class MutualInformation extends ASEvaluation implements AttributeEvaluator {

    /* DATA */

    /** The data for the computation */
    Instances data;


    /* METHODS */

    /**
     * This computes the mutual information of the x-th and y-th attribute of data.
     *
     * @param data the data to be used for computation
     * @param x index of the first attribute
     * @param y index of the second attribute
     * @return the mutual information of the two attributes
     */
    static double mutualInformation(Instances data, int x, int y){

        Pair <Integer, int []> valuesX = attributeToIntArray(data, x);
        Pair <Integer, int []> valuesY = attributeToIntArray(data, y);
        int [][] contingency = contingencyTable(valuesX, valuesY);
        int [] tableX = valueOccurrences(valuesX);
        int [] tableY = valueOccurrences(valuesY);
        double miSum = 0.0;

        for (int i = 0; i < contingency.length; ++i){ // lines
            for (int j = 0; j < contingency[0].length; ++j){ // columns

                if (contingency[i][j] != 0){

                    miSum += (contingency[i][j] / (double)data.numInstances())
                            * MathUtils.log2((contingency[i][j] * (double)data.numInstances())
                            / ((double)tableX[i] * (double)tableY[j]));
                }
            }
        }
        return miSum;
    }

    /**
     * Returns the contingency table for the given two attributes (that have the
     * same number of instances).
     *
     * @param x the first attribute (number of possible values + data)
     * @param y the second attribute (number of possible values + data)
     * @return the contingency table x:y (values of x are in the first dimension)
     */
    private static int[][] contingencyTable(Pair<Integer, int []> x, Pair<Integer, int []> y) {

        int [][] table = new int [x.first] [];

        for (int i = 0; i < x.first; ++i){
            table[i] = new int [y.first];
        }

        for (int i = 0; i < x.second.length; ++i){
            table[x.second[i]] [y.second[i]] ++;
        }
        return table;
    }

    /**
     * This counts the number of occurrences for each possible value of the given data.
     * @param data the data, along with the number of possible values
     * @return list of numbers of occurrences for all the values in the data
     */
    private static int[] valueOccurrences(Pair<Integer, int []> data) {

        int [] occNums = new int [data.first];

        for (int i = 0; i < data.second.length; ++i){
            occNums[data.second[i]]++;
        }
        return occNums;
    }

    /**
     * This returns the list of all orders-of-values of the given attribute (i.e. all possible
     * values are ordered and their orders substituted for them in the array of values).
     *
     * @param data the data
     * @param index the number of the attribute
     * @return the number of possible values and the list of orders of values
     */
    private static Pair<Integer, int[]> attributeToIntArray(Instances data, int index) {

        Hashtable<Double, Integer> valOrder = new Hashtable<Double, Integer>();
        double [] origVals = data.attributeToDoubleArray(index);

        for (int i = 0; i < origVals.length; ++i){
            valOrder.put(origVals[i], -1);
        }

        Double [] possibleVals = valOrder.keySet().toArray(new Double[0]);
        Arrays.sort(possibleVals);

        for (int i = 0; i < possibleVals.length; ++i){
            valOrder.put(possibleVals[i], i);
        }

        int [] orders = new int [origVals.length];
        for (int i = 0; i < origVals.length; ++i){
            orders[i] = valOrder.get(origVals[i]);
        }

        return new Pair(possibleVals.length, orders);
    }

    @Override
    public void buildEvaluator(Instances data) throws Exception {
        
        if (data.classIndex() == -1){
            throw new Exception("Class attribute must be set.");
        }
        this.data = data;
    }

    /**
     * Computes the mutual information of the given attribute and the class attribute.
     * @param attribute the attribute to evaluate
     * @return the mutual information of the evaluated attribute and the class attribute
     * @throws Exception
     */
    public double evaluateAttribute(int attribute) throws Exception {

        if (attribute == data.classIndex()){
            throw new Exception("Cannot evaluate class attribute.");
        }
        return mutualInformation(this.data, attribute, data.classIndex());
    }

    @Override
    public Capabilities getCapabilities(){
        Capabilities ret = new Capabilities(this);

        ret.enable(Capabilities.Capability.NOMINAL_ATTRIBUTES);
        ret.enable(Capabilities.Capability.NUMERIC_ATTRIBUTES);
        ret.enable(Capabilities.Capability.NOMINAL_CLASS);
        ret.enable(Capabilities.Capability.NUMERIC_CLASS);

        return ret;
    }
}
