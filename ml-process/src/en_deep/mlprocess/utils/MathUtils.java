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
}
