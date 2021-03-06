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

package en_deep.mlprocess.utils;

import java.util.BitSet;
import java.util.Vector;

/**
 * This class contains some needed mathematical help functions.
 * @author Ondrej Dusek
 */
public class MathUtils {

    /**
     * This returns the list of all k-tuple combinations in the sub-array, starting from base.
     *
     * @param base the lower bound of the interval (included)
     * @param k how big should the combinations be
     * @param values the list of possible values (from which the combinations are taken)
     * @return the list of all k-tuple combinations
     */
    private static Vector<String> combinations(int base, int k, int [] values){

        Vector<String> ret = new Vector<String>();

        if (k == 0){
            ret.add("");
            return ret;
        }

        // if base > n (combinations that can't be finished), this returns empty list to
        // which nothing will be added
        for (int i = base; i < values.length; ++i){
            Vector<String> subCombs = combinations(i+1, k-1, values);

            for (String subComb : subCombs){
                ret.add(values[i] + (subComb.length() == 0 ? "" : " " + subComb));
            }
        }

        return ret;
    }

    /**
     * This returns the list of all k-tuple combinations out of the values in the
     * given array (they must not repeat).
     *
     * @param k how big should the combinations be
     * @param values the list of possible values (from which the combinations are taken)
     * @return the list of all k-tuple combinations
     */
    public static Vector<String> combinations(int k, int [] values){
        return combinations(0, k, values);
    }

    /**
     * This sorts an array descending and returns its order. Uses the shakesort algorithm. The order of same
     * values is random.
     * @param arr the array to be sorted
     * @return the descending order of the elements in the array
     */
    public static int[] getOrder(double[] arr) {

        boolean changes = true;
        int [] order = new int [arr.length];

        for (int i = 0; i < order.length; ++i){ // initialize the order field
            order[i] = i;
        }

        while (changes){ // shakesort
            changes = false;

            for (int i = 1; i < arr.length; ++i){ // forward pass
                if (arr[i] > arr[i-1]){
                    double temp = arr[i];
                    int orderTemp = order[i]; // change the order along with the main array

                    changes = true;
                    arr[i] = arr[i-1];
                    order[i] = order[i-1];
                    arr[i-1] = temp;
                    order[i-1] = orderTemp;
                }
            }
            for (int i = arr.length-1; i >= 1; --i){ // backward pass
                if (arr[i] > arr[i-1]){
                    double temp = arr[i];
                    int orderTemp = order[i];
                    
                    changes = true;
                    arr[i] = arr[i-1];
                    order[i] = order[i-1];
                    arr[i-1] = temp;
                    order[i-1] = orderTemp;
                }
            }
        }

        return order;
    }

    /**
     * This returns the base 2 logarithm of the given value.
     * @param value the value to be logarithmed
     * @return the base 2 logarithm of the given value
     */
    public static double log2(double value) {
        return Math.log(value) / Math.log(2);
    }

    /**
     * This returns the position of a given value in the given array, or -1 if not found.
     * @param arr the array that contains the value
     * @param val the value to search
     * @return the position of the value in the array, or -1
     */
    public static int find(int [] arr, int val){

        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == val){
                return i;
            }
        }
        return -1;
    }

    /**
     * This converts an array of doubles to integers (using type-casts, not rounding).
     * @param arr the array to be converted
     * @return an integer version of the given array
     */
    public static int [] toInts(double [] arr){

        int [] ret = new int [arr.length];
        for (int i = 0; i < arr.length; i++) {
            ret[i] = (int) arr[i];
        }
        return ret;
    }

    /**
     * This creates an array that contains the given sequence of integers.
     * @param base the starting number of the sequence
     * @param size the size of the sequence
     * @param step the sequence step
     * @return the array with the specified integer sequence
     */
    public static int[] sequence(int base, int size, int step) {

        int [] ret = new int [size];
        for (int i = 0; i < size; i++){
            ret [i] = base + i * step;
        }
        return ret;
    }

    /**
     * This changes all occurrences of the value v1 to v2 and vice versa. Other data in the array
     * are left unchanged.
     * @param arr the array to be processed
     * @param v1 the value to be changed to v2
     * @param v2 the value to be change to v1
     */
    public static void swapValues(double [] arr, double v1, double v2){

        for (int i = 0; i < arr.length; ++i){
            if (arr[i] == v1){
                arr[i] = v2;
            }
            else if (arr[i] == v2){
                arr[i] = v1;
            }
        }

    }

    /**
     * Returns an array containing all numbers of bits that are set to true.
     * @param set the set to be processed
     * @return a list of all true bits in this set
     */
    public static int[] findTrue(BitSet set) {
        int [] ret = new int [set.cardinality()];
        int offset = 0;

        for (int idx = set.nextSetBit(0); idx >= 0; idx = set.nextSetBit(idx+1)) {
            ret[offset++] = idx;
        }
        return ret;
    }

    /**
     * Return the index of the maximum value found in the array.
     * @param values the array of values from which the highest one is to be found
     * @return the index of the highest value
     */
    public static int findMax(double [] values){

        int maxIdx = 0;

        for (int i = 0; i < values.length; ++i){
            if (values[i] > values[maxIdx]){
                maxIdx = i;
            }
        }
        return maxIdx;
    }
}
