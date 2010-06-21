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

import java.util.Arrays;
import java.util.BitSet;
import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.RankedOutputSearch;
import weka.attributeSelection.SubsetEvaluator;
import weka.core.Capabilities;
import weka.core.CapabilitiesHandler;
import weka.core.Instances;

/**
 * This implements the min-Redundancy Max-Relevance attribute search.
 * @todo this should most probably be a subset evaluator, not ranked output search.
 * @author Ondrej Dusek
 */
public class mRMR extends ASEvaluation implements RankedOutputSearch, CapabilitiesHandler, SubsetEvaluator {

    /* DATA */

    /** The number of attributes to select */
    private int numAttrib;
    /** The current data */
    private Instances data;
    /** Show ranking ? */
    private boolean ranking;
    /* MI's of all attributes */
    private double [][] miMatrix;

    /* METHODS */

    /**
     * This computes the minimum redundancy-maximum relevance score for all the attributes of the
     * given data, against the class attribute (must be set in the data)
     *
     * @param data the data to be used for computation
     * @return mRMR ranking of all the attributes in the data
     */
    private double[][] computemRMR(){

        // initialize the return value -- attribute indexes with mRMR values
        double [][] ret = new double [this.numAttrib] [];
        for (int i = 0; i < ret.length; ++i){
            ret[i] = new double [2];
            ret[i][0] = -1;
            ret[i][1] = Double.NEGATIVE_INFINITY;
        }

        // compute the mututal information for all attributes against the class attribute
        // and get the mRMR of the first best attribute
        for (int i = 0; i < data.numAttributes(); ++i){

            if (i == data.classIndex()){
                continue;
            }

            double mi = miMatrix[i][data.classIndex()];

            if (Double.isNaN(mi)){
                mi = MutualInformation.mutualInformation(data, i, data.classIndex());
            }
            miMatrix[i][data.classIndex()] = miMatrix[data.classIndex()][i] = mi;

            if (mi > ret[0][1]){
                ret[0][1] = mi;
                ret[0][0] = i;
            }
        }

        BitSet usedAttrib = new BitSet(data.numAttributes());
        usedAttrib.set((int) ret[0][0]);
        usedAttrib.set(data.classIndex());

        // round by round, select the attribute with the best relevance-redundancy score
        for (int round = 1; round < this.numAttrib; ++round){

            for (int j = 0; j < data.numAttributes(); j++){

                if (usedAttrib.get(j)){ // skip already selected attributes & the class attribute
                    continue;
                }

                // compute the mutual information sum against all already selected
                double miSum = 0.0; 
                for (int i = 0; i < round; ++i){
                    int k = (int) ret[i][0];
                    if (Double.isNaN(miMatrix[k][j])){
                        miMatrix[k][j] = miMatrix[j][k] = MutualInformation.mutualInformation(data, k, j);
                    }
                    miSum += miMatrix[k][j];
                }
                // use it for computing the relevance-redundancy score for the given attribute
                double rr = miMatrix[j][data.classIndex()] - (1/((double)round)) * miSum;
                if (rr > ret[round][1]){
                    ret[round][1] = rr;
                    ret[round][0] = j;
                }
            }
            usedAttrib.set((int) ret[round][0]);
        }

        // return the result
        return ret;
    }


    @Override
    public void buildEvaluator(Instances data) throws Exception {

        this.data = data;
        this.numAttrib = data.numAttributes() - 1;

        if (data.classIndex() == -1){
            throw new Exception("Class attribute must be set.");
        }

        // initialize the matrix for mutual information
        this.miMatrix = new double [data.numAttributes()] [];

        for (int i = 0; i < this.miMatrix.length; ++i){
            this.miMatrix[i] = new double [data.numAttributes()];
            Arrays.fill(this.miMatrix[i], Double.NaN);
        }
    }

    /**
     * This
     * @return
     * @throws Exception
     */
    public double[][] rankedAttributes() throws Exception {
        return this.computemRMR();
    }

    /**
     * Not used.
     * @param threshold
     */
    public void setThreshold(double threshold) {        
    }

    /**
     * Not used. Always returns negative infinity.
     * @return
     */
    public double getThreshold() {
        return Double.NEGATIVE_INFINITY;
    }

    public void setNumToSelect(int numToSelect) {
        this.numAttrib = numToSelect;
    }

    /**
     * Returns the number of attributes the user selected, or the number of attributes in the data.
     * @return the number of attributes to be selected
     */
    public int getNumToSelect() {
        return this.numAttrib;
    }

    /**
     * Not used. Same as {@link #getNumToSelect() }
     * @return
     */
    public int getCalculatedNumToSelect() {
        return this.numAttrib;
    }

    public void setGenerateRanking(boolean doRanking) {
        this.ranking = doRanking;
    }

    public boolean getGenerateRanking() {
        return this.ranking;
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

    /**
     * Compute the mRMR value of an attribute subset (independent on the incremental algorithm). The value
     * is the difference b/t relevance and redundancy of the set.
     *
     * @param bitset the attribute subset to be used
     * @return the relevance-redundancy difference of the given set
     * @throws Exception
     */
    public double evaluateSubset(BitSet bitset) throws Exception {

        double relevance = 0.0;
        double redundancy = 0.0;
        double setSize = 0.0;

        for (int i = 0; i < bitset.length(); ++i){

            if (!bitset.get(i) || i == data.classIndex()){
                continue;
            }
            setSize++;

            // relevance of this attribute in the set
            double mi = miMatrix[i][data.classIndex()];

            if (Double.isNaN(mi)){
                mi = MutualInformation.mutualInformation(data, i, data.classIndex());
                miMatrix[i][data.classIndex()] = miMatrix[data.classIndex()][i] = mi;
            }
            relevance += mi;

            // redundancy with respect to all other set members
            for (int j = 0; j < i; ++j){

                if (!bitset.get(j) || j == data.classIndex()){
                    continue;
                }
                mi = miMatrix[i][j];
                if (Double.isNaN(mi)){
                    mi = MutualInformation.mutualInformation(data, i, j);
                    miMatrix[i][j] = miMatrix[j][i] = mi;
                }
                redundancy += mi;
            }
        }
        return (1/setSize) * relevance - (1/(setSize*setSize)) * redundancy;
    }
}
