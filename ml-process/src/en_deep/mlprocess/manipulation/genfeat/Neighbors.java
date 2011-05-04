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

package en_deep.mlprocess.manipulation.genfeat;

import en_deep.mlprocess.manipulation.DataReader;
import en_deep.mlprocess.manipulation.DataReader.FeatType;
import en_deep.mlprocess.manipulation.DataReader.WordInfo;
import en_deep.mlprocess.utils.StringUtils;

/**
 * This encapsulates several features containing the topological neighbors of the given word.
 * It creates a feature for three neighboring words to the left and to the right and the first neighboring
 * bigrams from both sides.
 * @author Ondrej Dusek
 */
public class Neighbors extends ParametrizedFeature {

    /* METHODS */

    public Neighbors(DataReader reader){
        super(reader, FeatType.MORPH);
    }

    @Override
    public String getHeader() {
        return this.getParametrizedHeader("Left3_", DataReader.STRING) + LF
                + this.getParametrizedHeader("Left2_", DataReader.STRING) + LF
                + this.getParametrizedHeader("Left1_", DataReader.STRING) + LF
                + this.getParametrizedHeader("Left12_", DataReader.STRING) + LF
                + this.getParametrizedHeader("Right12_", DataReader.STRING) + LF
                + this.getParametrizedHeader("Right1_", DataReader.STRING) + LF
                + this.getParametrizedHeader("Right2_", DataReader.STRING) + LF
                + this.getParametrizedHeader("Right3_", DataReader.STRING);
    }


    @Override
    public String generate(int wordNo, int predNo) {

        return this.getInfo(wordNo-3, false) + "," + this.getInfo(wordNo-2, false)
                + "," + this.getInfo(wordNo-1, false) + "," + this.getInfo(wordNo-2, true)
                + "," + this.getInfo(wordNo+1, true) + "," + this.getInfo(wordNo+1, false)
                + "," + this.getInfo(wordNo+2, false) + "," + this.getInfo(wordNo+3, false);
    }

    /**
     * Retrieves all the required information about one word or a word and its successor.
     * @param wordNo the number of the word in question
     * @param bindWithNext should also its successor's values be added ?
     * @return all the required information, as ARFF quoted list
     */
    private String getInfo(int wordNo, boolean bindWithNext){

        if (!bindWithNext){
            return "\"" + StringUtils.join(this.getFields(wordNo), "\",\"") + "\"";
        }
        return "\"" + StringUtils.join(StringUtils.bigrams(this.getFields(wordNo), this.getFields(wordNo+1), SEP), "\",\"")
                + "\"";
    }
}
