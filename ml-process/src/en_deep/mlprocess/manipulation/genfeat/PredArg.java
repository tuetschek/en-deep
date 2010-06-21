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

import en_deep.mlprocess.manipulation.StReader;
import en_deep.mlprocess.manipulation.StToArff;
import en_deep.mlprocess.utils.StringUtils;

/**
 * This feature adds the predicate-argument candidate bigrams for POS, Coarse-POS, lemma and form.
 * @author Ondrej Dusek
 */
public class PredArg extends Feature {

    /** Separator of predicate and argument candidate fields */
    private static final String SEP = "|";

    /**
     * This just creates the new instance, setting the link to the reader. Otherwise it's empty.
     * @param reader the ST files reader.
     */
    public PredArg(StReader reader){
        super(reader);
    }

    @Override
    public String getHeader() {
        return StToArff.ATTRIBUTE + " PredArgPOS " + StToArff.STRING + LF
                + StToArff.ATTRIBUTE + " PredArgLemma " + StToArff.STRING + LF
                + StToArff.ATTRIBUTE + " PredArgForm " + StToArff.STRING + LF
                + StToArff.ATTRIBUTE + " PredArgCPOS " + StToArff.STRING;
    }

    @Override
    public String generate(int wordNo, int predNo) {

        String [] pos = new String [2];
        pos[0] = this.reader.getWordInfo(predNo, this.reader.IDXI_POS);
        pos[1] = this.reader.getWordInfo(wordNo, this.reader.IDXI_POS);
        String [] cpos = StringUtils.substrings(pos, 0, 1);
        String [] lemma = new String [2];
        lemma[0] = this.reader.getWordInfo(predNo, this.reader.IDXI_LEMMA);
        lemma[1] = this.reader.getWordInfo(wordNo, this.reader.IDXI_LEMMA);
        String [] form = new String [2];
        form[0] = this.reader.getWordInfo(predNo, this.reader.IDXI_FORM);
        form[1] = this.reader.getWordInfo(wordNo, this.reader.IDXI_FORM);

        return "\"" + StringUtils.escape(StringUtils.join(pos, SEP)) + "\",\""
                + StringUtils.escape(StringUtils.join(lemma, SEP)) + "\",\""
                + StringUtils.escape(StringUtils.join(form, SEP)) + "\",\""
                + StringUtils.escape(StringUtils.join(cpos, SEP)) + "\"";
    }

}
