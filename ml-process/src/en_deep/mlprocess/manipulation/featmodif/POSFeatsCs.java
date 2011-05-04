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

package en_deep.mlprocess.manipulation.featmodif;

import en_deep.mlprocess.manipulation.StReader;
import java.util.HashMap;

/**
 * This feature handles the ST file FEAT and PFEAT fields for the Czech language.
 * @author Ondrej Dusek
 */
public class POSFeatsCs extends FeatureModifier {

    /* CONSTANTS */

    /**
     * List of possible features that may occur in the FEAT field for Czech (Sub-POS,
     * Genus, Number, Case, Possessor Genus, Possessor Number, Person, Tense, Grade, Negation, Voice, Variant,
     * Semantic feature).
     */
    private static final String [] FEATS_LIST = {"SubPOS", "Gen", "Num", "Cas", "PGe", "PNu", "Per", "Ten",
            "Gra", "Neg", "Voi", "Var", "Sem"};

    /* DATA */

    /** This maps the names in the {@link #FEATS_LIST} variable into their positions in that field */
    private final HashMap<String, Integer> FEAT_POS;

    /* METHODS */

    /**
     * This just initializes the table of possible feature values.
     */
    public POSFeatsCs(){

        FEAT_POS = new HashMap<String, Integer>();
        for (int i = 0; i < FEATS_LIST.length; ++i){
            FEAT_POS.put(FEATS_LIST[i], i);
        }
    }


    @Override
    public String [] getOutputFeatsList(String prefix) {

        String [] list = new String [FEATS_LIST.length];
        for (int i = 0; i < FEATS_LIST.length; ++i){
            list[i] = prefix + "_" + FEATS_LIST[i];
        }
        return list;
    }

    @Override
    public String [] getOutputValues(String value) {

        String [] values = value != null ? value.split(SEP) : new String[0]; // allow multiple values
        String [] feats = new String [FEATS_LIST.length];

        for (String val : values){

            if (!val.equals(StReader.EMPTY_VALUE)){

                String [] featInfos = val.split("\\|");

                for (String featInfo : featInfos){  // split into individual features listed

                    String [] nameVal = featInfo.split("=", 2); // extract the name and value
                    int pos = FEAT_POS.get(nameVal[0]); // find the position of each feature in the array

                    feats[pos] = (feats[pos] == null ? "" : feats[pos] + SEP) + nameVal[1]; // set it at the right position in the array
                }
            }
        }
        
        return feats;
    }

}
