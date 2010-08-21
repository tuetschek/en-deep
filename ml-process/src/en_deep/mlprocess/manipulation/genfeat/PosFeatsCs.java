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
import java.util.HashMap;

/**
 * This feature handles the FEAT and PFEAT fields for the Czech language.
 * @author Ondrej Dusek
 */
public class POSFeatsCs extends Feature {

    /* CONSTANTS */

    /**
     * List of possible features that may occur in the FEAT field for Czech (Sub-POS,
     * Genus, Number, Case, Possessor Genus, Possessor Number, Person, Tense, Grade, Negation, Voice, Variant,
     * Semantic feature).
     */
    private static final String [] FEATS_LIST = {"SubPOS", "Gen", "Num", "Cas", "PGe", "PNu", "Per", "Ten",
            "Gra", "Neg", "Voi", "Var", "Sem"};

    /** Feature name prefix for golden feature values */
    private static final String PREFIX_GOLD = "feat_";
    /** Feature name prefix for predicted feature values */
    private static final String PREFIX_PRED = "pfeat_";
    /** Empty feature value (any string that is never used in the features values themselves) */
    private static final String EMPTY = "-";

    /* DATA */

    /** Position of the golden FEAT values in the ST file */
    private final int FEAT_GOLD;
    /** Position of the predicted FEAT values in the ST file */
    private final int FEAT_PRED;

    /** This maps the names in the {@link #FEATS_LIST} variable into their positions in that field */
    private final HashMap<String, Integer> FEAT_POS;

    /* METHODS */

    /**
     * This just initializes the {@link #reader} and sets-up the predicted/non-predicted
     * FEAT field positions.
     * @param reader
     */
    public POSFeatsCs(StReader reader){

        super(reader);

        if (reader.usePredicted){
            FEAT_PRED = reader.IDXI_FEAT;
            FEAT_GOLD = reader.IDXI_FEAT + reader.predictedNon;
        }
        else {
            FEAT_GOLD = reader.IDXI_FEAT;
            FEAT_PRED = reader.IDXI_FEAT + reader.predictedNon;
        }
        FEAT_POS = new HashMap<String, Integer>();
        for (int i = 0; i < FEATS_LIST.length; ++i){
            FEAT_POS.put(FEATS_LIST[i], i);
        }
    }

    @Override
    public String getHeader() {
        return this.getHeader(PREFIX_GOLD) + LF + this.getHeader(PREFIX_PRED);
    }



    @Override
    public String generate(int wordNo, int predNo) {
        return this.listFeats(this.reader.getWordInfo(wordNo, FEAT_GOLD)) + ","
                + this.listFeats(this.reader.getWordInfo(wordNo, FEAT_PRED));
    }

    /**
     * Generates the header and gives each feature the desired prefix (used to separate golden
     * and predicted versions).
     * @param prefix the desired prefix to prepend each feature name with
     */
    private String getHeader(String prefix) {

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String featType : FEATS_LIST){
            if (first){
                first = false;
            }
            else {
                sb.append(LF);
            }
            sb.append(StToArff.ATTRIBUTE + " ").append(prefix).append(featType).append(" " + StToArff.STRING);
        }
        return sb.toString();
    }

    /**
     * This lists the values of all possible morphological features, given their compact string representation.
     * @param featString the string representation of the features.
     * @return the array representation of the features.
     */
    private String listFeats(String featString) {

        String [] feats = featString.split("\\|");
        String [] featArr = new String [FEATS_LIST.length];
        
        for (String feat : feats){  // split into individual features listed
            String [] nameVal = feat.split("=", 2); // extract the name and value
            int pos = FEAT_POS.get(nameVal[0]); // find the position of each feature in the array

            featArr[pos] = nameVal[1]; // set it at the right position in the array
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < featArr.length; ++i){
            if (i > 0){
                sb.append(",");
            }
            sb.append("\"").append(featArr[i] != null ? featArr[i] : EMPTY).append("\"");
        }
        return sb.toString();
    }


}
