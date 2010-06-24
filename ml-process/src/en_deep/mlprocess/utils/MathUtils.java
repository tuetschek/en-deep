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

import java.util.Vector;

/**
 * This class contains some needed mathematical help functions.
 * @author Ondrej Dusek
 */
public class MathUtils {

    /**
     * This returns the list of all k-tuple combinations in the interval [base,n>.
     *
     * @param base the lower bound of the interval (included)
     * @param k how big should the combinations be
     * @param n the upper bound of the interval (excluded)
     * @return the list of all k-tuple combinations
     */
    private static Vector<String> combinations(int base, int k, int n){

        Vector<String> ret = new Vector<String>();

        if (k == 0){
            ret.add("");
            return ret;
        }

        // if base > n (combinations that can't be finished), this returns empty list to
        // which nothing will be added
        for (int i = base; i < n; ++i){
            Vector<String> subCombs = combinations(i+1, k-1, n);
            for (String subComb : subCombs){
                ret.add(i + " " + subComb);
            }
        }

        return ret;
    }

    /**
     * This returns the list of all k-tuple combinations out of n.
     *
     * @param base the lower bound of the interval (included)
     * @param k how big should the combinations be
     * @param n the upper bound of the interval (excluded)
     * @return the list of all k-tuple combinations
     */
    public static Vector<String> combinations(int k, int n){
        return combinations(0, k, n);
    }

    /**
     * This sorts an array descending and returns its order. Uses the shakesort algorithm.
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
}
