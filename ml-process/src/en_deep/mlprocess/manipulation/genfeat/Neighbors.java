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

import en_deep.mlprocess.manipulation.StToArff;
import en_deep.mlprocess.manipulation.StReader;
import en_deep.mlprocess.utils.StringUtils;

/**
 * This encapsulates several features containing the topological neighbors of the given word.
 * It creates a feature for three neighboring words to the left and to the right and the first neighboring
 * bigrams from both sides.
 * @author Ondrej Dusek
 */
public class Neighbors extends Feature {

    /* CONSTANTS */

    /** Word separator for bigrams */
    private static final String BIGRAM_SEPARATOR = "|";

    /* METHODS */

    public Neighbors(StReader reader){
        super(reader);
    }

    @Override
    public String getHeader() {
        return StToArff.ATTRIBUTE + " Left3 " + StToArff.STRING + LF
                + StToArff.ATTRIBUTE + " Left2 " + StToArff.STRING + LF
                + StToArff.ATTRIBUTE + " Left1 " + StToArff.STRING + LF
                + StToArff.ATTRIBUTE + " Left12 " + StToArff.STRING + LF
                + StToArff.ATTRIBUTE + " Right12 " + StToArff.STRING + LF
                + StToArff.ATTRIBUTE + " Right1 " + StToArff.STRING + LF
                + StToArff.ATTRIBUTE + " Right2 " + StToArff.STRING + LF
                + StToArff.ATTRIBUTE + " Right3 " + StToArff.STRING;
    }


    @Override
    public String generate(int wordNo, int predNo) {
        
        return this.getNeighborsField(wordNo, this.reader.IDXI_FORM);
    }

    /**
     * Returns all the neighbors' values for the given field.
     * 
     * @param wordNo The word to compute the relative distance from.
     * @param field The field to get.
     * @return All the neighbors' values for the given field.
     */
    private String getNeighborsField(int wordNo, int field){

        return "\"" + StringUtils.escape(this.reader.getWordInfo(wordNo - 3, field)) + "\",\""
                + StringUtils.escape(this.reader.getWordInfo(wordNo - 2, field))  + "\",\""
                + StringUtils.escape(this.reader.getWordInfo(wordNo - 1, field)) + "\",\""
                + StringUtils.escape(this.reader.getWordInfo(wordNo - 2, field)
                    + BIGRAM_SEPARATOR + this.reader.getWordInfo(wordNo - 1, field))  + "\",\""
                + StringUtils.escape(this.reader.getWordInfo(wordNo + 1, field)
                    + BIGRAM_SEPARATOR + this.reader.getWordInfo(wordNo + 2, field))  + "\",\""
                + StringUtils.escape(this.reader.getWordInfo(wordNo + 1, field))  + "\",\""
                + StringUtils.escape(this.reader.getWordInfo(wordNo + 2, field))  + "\",\""
                + StringUtils.escape(this.reader.getWordInfo(wordNo + 3, field))  + "\"";
    }

}
