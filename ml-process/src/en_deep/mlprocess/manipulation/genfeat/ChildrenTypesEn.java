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
 * This provides several features for the different POS-types of children (nominal, verbal,
 * preposition, particle) + features for their total number.
 * @author Ondrej Dusek
 */
public class ChildrenTypesEn extends Feature {

    /* CONSTANTS */

    /** The separator for the individual children */
    private static final String SEP = "|";

    /** Children patterns */
    private static final String [] PATTERNS = {
        "^[NJPF].*", "^[VM].*", "^[MNJPFV].*", "^[IT].*", "^RP"
    };

    /* DATA */

    /** Names for all possible features (depend on {@link #PATTERNS}) */
    private final String [] names;
    
    /* METHODS */
    
    /**
     * This just initializes the feature, setting the names for all features.
     * @param reader
     */
    public ChildrenTypesEn(StReader reader){
        super(reader);

        names = new String [PATTERNS.length];
        for (int i = 0; i < PATTERNS.length; i++) {
            names[i] = PATTERNS[i].replaceAll("[^A-Z]", "");
        }
    }


    /**
     * This returns the header for one feature (and one count feature).
     * @param suffix
     */
    private String getHeaderText(String suffix){
        return StToArff.ATTRIBUTE + " ChildrenType_" + suffix + " " + StToArff.STRING + LF
                + StToArff.ATTRIBUTE + " ChildrenTypeNum_" + suffix + " " + StToArff.INTEGER;
    }

    @Override
    public String getHeader() {

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < this.names.length; i++) {
            sb.append(this.getHeaderText(names[i]));
            if (i < this.names.length-1){
                sb.append(LF);
            }
        }
        return sb.toString();
    }

    @Override
    public String generate(int wordNo, int predNo) {

        int [] children = this.reader.getChildrenPos(wordNo);
        String [] pos = this.reader.getWordsInfo(children, this.reader.IDXI_POS);
        String [] words = this.reader.getWordsInfo(children, this.reader.IDXI_FORM);
        StringBuilder out = new StringBuilder();

        for (int j = 0; j < PATTERNS.length; j++) {

            int num = 0;

            if (j > 0){
                out.append(",");
            }
            out.append("\"");
            for (int i = 0; i < pos.length; i++) {

                if (pos[i].matches(PATTERNS[j])){
                    if (num > 0){
                        out.append(SEP);
                    }
                    out.append(StringUtils.escape(words[i]));
                    num++;
                }
            }
            out.append("\",").append(num);
        }
        
        return out.toString();
    }
}
