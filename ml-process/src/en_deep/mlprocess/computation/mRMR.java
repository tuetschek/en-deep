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
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;
import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.RankedOutputSearch;
import weka.attributeSelection.SubsetEvaluator;
import weka.core.Capabilities;
import weka.core.CapabilitiesHandler;
import weka.core.Instances;
import weka.core.Option;
import weka.core.OptionHandler;
import weka.core.Utils;

/**
 * This implements the min-Redundancy Max-Relevance attribute search.
 * @todo this should most probably be a subset evaluator, not ranked output search.
 * @author Ondrej Dusek
 */
public class mRMR extends ASEvaluation implements RankedOutputSearch, CapabilitiesHandler, OptionHandler, SubsetEvaluator {

    /* DATA */

    /** The number of attributes to select */
    private int numAttrib;
    /** The current data */
    private Instances data;
    /** Show ranking ? */
    private boolean ranking;
    /* MI's of all attributes */
    private double [][] miMatrix;
    /** Beam size if beam search variant is used, or -1 if all attributes are examined in each round. */
    private int beamSize = -1;

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

        if (ret.length == 0){ // singular case: 0 attributes !!!
            return ret;
        }

        Candidates candList = new Candidates(this.beamSize);

        // compute the mututal information for all attributes against the class attribute
        // and get the mRMR of the first best attribute
        for (int i = 0; i < data.numAttributes(); ++i){

            if (i == data.classIndex()){
                continue;
            }

            double mi = this.getMutualInformation(i, data.classIndex());

            candList.add(i, mi);

            if (mi > ret[0][1]){
                ret[0][1] = mi;
                ret[0][0] = i;
            }
        }
        
        // remove the first best attribute from the list of further candidates (since it's used already)
        candList.remove((int) ret[0][0]);

        // round by round, select the attribute with the best relevance-redundancy score
        for (int round = 1; round < this.numAttrib; ++round){

            for (int j = candList.getFirstCandidate(); j != -1; j = candList.getNextCandidate()){

                // compute the mutual information sum against all already selected
                double miSum = 0.0; 
                for (int i = 0; i < round; ++i){
                    int k = (int) ret[i][0];
                    miSum += this.getMutualInformation(k, j);
                }

                // use it for computing the relevance-redundancy score for the given attribute
                double rr = this.getMutualInformation(j, data.classIndex()) - (1/((double)round)) * miSum;

                if (rr > ret[round][1]){
                    ret[round][1] = rr;
                    ret[round][0] = j;
                }
            }
            candList.remove((int) ret[round][0]);
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

        // initialize the (upper diagonal) matrix for mutual information
        this.miMatrix = new double [data.numAttributes()] [];

        for (int i = 0; i < this.miMatrix.length; ++i){
            this.miMatrix[i] = new double [data.numAttributes()-i];
            Arrays.fill(this.miMatrix[i], Double.NaN);
        }
    }

    /**
     * This returns the attributes in the order of the incremental mRMR algorithm. However, the rankings
     * are not guaranteed to be sorted (which follows from the nature of the algorithm).
     * 
     * @return the list of attributes in their mRMR order, along with their merits (that are valid only for the \
     *  given iteration of the mRMR algorithm, and therefor not sorted descending
     */
    @Override
    public double[][] rankedAttributes() throws Exception {
        return this.computemRMR();
    }

    /**
     * Not used.
     * @param threshold
     */
    @Override
    public void setThreshold(double threshold) {        
    }

    /**
     * Not used. Always returns negative infinity.
     * @return always negative infinity
     */
    @Override
    public double getThreshold() {
        return -Double.MAX_VALUE;
    }

    @Override
    public void setNumToSelect(int numToSelect) {
        this.numAttrib = numToSelect;
    }

    /**
     * Returns the number of attributes the user selected, or the number of attributes in the data.
     * @return the number of attributes to be selected
     */
    @Override
    public int getNumToSelect() {
        return this.numAttrib;
    }

    /**
     * Not used. Same as {@link #getNumToSelect() }
     * @return always the number of attributes
     */
    @Override
    public int getCalculatedNumToSelect() {
        return this.numAttrib;
    }

    @Override
    public void setGenerateRanking(boolean doRanking) {
        this.ranking = doRanking;
    }

    @Override
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
    @Override
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
            double mi = this.getMutualInformation(i, data.classIndex());
            relevance += mi;

            // redundancy with respect to all other set members
            for (int j = 0; j < i; ++j){

                if (!bitset.get(j) || j == data.classIndex()){
                    continue;
                }
                redundancy += this.getMutualInformation(i, j);
            }
        }
        return (1/setSize) * relevance - (1/(setSize*setSize)) * redundancy;
    }

    @Override
    public Enumeration listOptions() {
        Vector<Option> opts = new Vector<Option>();
        opts.add(new Option("\tThe size of the beam if beam search is used\n"
                + "\t(all attributes are examined in each iteration if the beam size is not set).",
                "B", 1, "-B <beam_size>"));
        return opts.elements();
    }

  /**
   * Parses a given list of options. <p/>
   *
   <!-- options-start -->
   * Valid options are: <p/>
   *
   * <pre> -B &lt;beam_size&gt;
   *  The search beam size.
   *  (default: -1, i.e. no beam constraints)</pre>
   *
   <!-- options-end -->
   *
   * @param options the list of options as an array of strings
   * @throws Exception if an option is not supported
   */
    @Override
    public void setOptions(String[] options) throws Exception {

        String beam = Utils.getOption('B', options);
        
        if (beam != null){
            this.setBeamSize(Integer.parseInt(beam));
        }
    }

    @Override
    public String[] getOptions() {

        String [] options = new String [2];
        int current = 0;

        if (this.getBeamSize() > 0){
            options[current++] = "-B";
            options[current++] = Integer.toString(this.getBeamSize());
        }

        while (current < options.length) {
          options[current++] = "";
        }
        return options;
    }

    /**
     * Returns the currently set beam size, or -1 if no beam size is specified and all attributes
     * are examined in each round.
     * @return the beam size
     */
    public int getBeamSize() {
        return this.beamSize;
    }

    /**
     * Set the beam size (-1 for no beam size constraints, the usual greedy algorithm shall be used).
     * @param beamSize the desired beam size
     */
    public void setBeamSize(int beamSize){
        this.beamSize = beamSize;
    }

    /**
     * Return the mutual information of the i-th and j-th attributes. This stores the
     * already computed results in the {@link #miMatrix} member.
     *
     * @param i the i-th attribute
     * @param j the j-th attribute
     * @return the mutual information of the i-th and j-th attribute in the {@link #data}
     */
    private double getMutualInformation(int i, int j) {

        if (i > j){ // swap values if i > j
            int tmp = i;
            i = j;
            j = tmp;
        }

        double mi = this.miMatrix[i][j-i]; // the matrix is upper diagonal only!

        if (Double.isNaN(mi)){
            mi = MutualInformation.mutualInformation(this.data, i, j);
        }
        this.miMatrix[i][j-i] = mi;
        return mi;
    }


    /**
     * Class used for storing and sorting the attributes along with their merit (mutual information
     * against the target class).
     */
    private static class AttributeValue {

        /** The attribute index */
        int index;
        /** The attribute value (mutual information with the target class */
        double value;

        /**
         * Create a new attribute, given its index and value.
         * @param index the index of the new attribute
         * @param value the value of the new attribute
         */
        private AttributeValue(int index, double value) {
            this.index = index;
            this.value = value;
        }

    }

    /**
     * A helper class used for comparing attributes according to their value.
     */
    private static class AttributeValueComparator implements Comparator<AttributeValue> {

        /**
         * Compares two attributes according to their value (aiming for descending order!).
         * @param other the other attribute to be compared with this one
         * @return 1 if this attribute is less valuable, -1 if this one is more valuable, 0 if both are equally valuable
         */
        @Override
        public int compare(AttributeValue o1, AttributeValue o2) {
            return o1.value < o2.value ? 1 : (o1.value == o2.value ? 0 : -1);
        }
    }


    /**
     * Class storing a list of candidates, able to retrieve them sequentially up to the search beam size.
     */
    private static class Candidates {

        /** The candidate storage -- sorted by attribute index */
        LinkedList<AttributeValue> cands;
        /** The current search beam size */
        int beamSize;
        /** The current element */
        private Iterator<AttributeValue> iterator;
        /** Number of retrieved elements since {@link #getFirstCandidate() } was last called */
        private int got;
        /** Are the candidates already sorted ? */
        private boolean sorted;

        /**
         * This creates a new empty list of candidates.
         * @param beamSize the search beam size to be used
         */
        Candidates(int beamSize){

            this.beamSize = beamSize;
            this.cands = new LinkedList<AttributeValue>();
            this.sorted = false;
        }

        /**
         * Add a new attribute to the list of possible candidates.
         * @param index the index of the new attribute
         * @param value the value of the new attribute
         */
        void add(int index, double value){
            AttributeValue a = new AttributeValue(index, value);
            this.cands.add(a);
        }

        /**
         * Returns the first candidate attribute -- the one with the highest value.
         * @return
         */
        int getFirstCandidate(){

            // sort the candidates if it has not been done already
            if (!this.sorted){
                AttributeValue [] vals = this.cands.toArray(new AttributeValue[0]);
                Arrays.sort(vals, new AttributeValueComparator());
                this.cands = new LinkedList<AttributeValue>(Arrays.asList(vals));
                this.sorted = true;
            }

            this.iterator = this.cands.iterator();
            this.got = 0;
            return this.getNextCandidate();
        }

        /**
         * Returns the index of the next attribute candidate for examination in the value ordering,
         * or -1 if all viable candidates are taken.
         * @return the index of the next-to-be-examined attribute
         */
        int getNextCandidate(){
            this.got++;
            if (this.iterator.hasNext() && (this.beamSize <= 0 || this.got <= this.beamSize)){
                return this.iterator.next().index;
            }
            return -1;
        }

        /**
         * Permanently removes the attribute with the given index from the list of candidates.
         * @param index the index of the attribute to be removed
         * @todo this is probably slow, make it faster using a custom collection
         */
        void remove(int index){

            Iterator<AttributeValue> it = this.cands.iterator();
            AttributeValue val = null;

            while (it.hasNext() && (val = it.next()).index != index){
                // move
            }
            if (val != null && val.index == index){
                it.remove();
            }
        }
    }
}
