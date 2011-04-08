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
import en_deep.mlprocess.manipulation.DataReader.WordInfo;
import en_deep.mlprocess.utils.StringUtils;

/**
 * This feature yields some information about the predicate (bundled/not-bundled with the same information
 * about the argument candidate).
 * @author Ondrej Dusek
 */
public class PredArg extends Feature {

    /* CONSTANTS */

    /** Bigram separator */
    private static final String SEP = "|";

    /* METHODS */

    /**
     * The constructor just sets the {@link #reader} variable.
     * @param reader the St-file reader
     */
    public PredArg(DataReader reader) {
        super(reader);
    }


    @Override
    public String getHeader() {
        return this.getHeaderText("Pred") + LF + this.getHeaderText("PredArg");
    }

    @Override
    public String generate(int wordNo, int predNo) {

        String [] predInfo = this.getFields(predNo);
        String [] predWordInfo = StringUtils.bigrams(predInfo, this.getFields(wordNo), SEP);

        return "\"" + StringUtils.join(predInfo, "\",\"") + "\",\"" + StringUtils.join(predWordInfo, "\",\"")
                + "\"";
    }

    /**
     * This returns all the header lines for one variant (Pred / PredArg).
     * @param prefix the prefix of the given variant
     * @return the header for the given variant of the feature
     */
    private String getHeaderText(String prefix) {

        return DataReader.ATTRIBUTE + " " + prefix + "Form " + DataReader.STRING + LF
                + DataReader.ATTRIBUTE + " " + prefix + "Lemma " + DataReader.STRING + LF
                + DataReader.ATTRIBUTE + " " + prefix + "POS " + DataReader.STRING + LF
                + DataReader.ATTRIBUTE + " " + prefix + "CPOS " + DataReader.STRING + LF
                + DataReader.ATTRIBUTE + " " + prefix + "DepRel " + DataReader.STRING;
    }

    /**
     * This returns all the needed information about one word (form, lemma, POS, CPOS and deprel).
     * @param wordNo the number of the word
     * @return the information about the given word
     */
    private String[] getFields(int wordNo) {
        String [] info = new String [5];
        int i = 0;

        info[i++] = StringUtils.escape(this.reader.getWordInfo(wordNo, WordInfo.FORM));
        info[i++] = StringUtils.escape(this.reader.getWordInfo(wordNo, WordInfo.LEMMA));
        info[i++] = StringUtils.escape(this.reader.getWordInfo(wordNo, WordInfo.POS));
        info[i++] = StringUtils.escape(this.reader.getWordInfo(wordNo, WordInfo.POS).substring(0, 1));
        info[i++] = StringUtils.escape(this.reader.getWordInfo(wordNo, WordInfo.SYNT_REL));

        return info;
    }

}
