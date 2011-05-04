/*
 *  Copyright (c) 2011 Ondrej Dusek
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

/**
 * This feature handles Czech positional POS tags, splitting them into separate features for
 * the individual positions.
 *
 * @author Ondrej Dusek
 */
public class POSFeatsCsPositional extends FeatureModifier {

    /* CONSTANTS */

    /**
     * List of possible features that may occur in the FEAT field for Czech (Sub-POS,
     * Genus, Number, Case, Possessor Genus, Possessor Number, Person, Tense, Grade, Negation, Voice, Variant,
     * Semantic feature).
     */
    private static final String [] FEATS_LIST = {"MainPOS", "SubPOS", "Gen", "Num", "Cas", "PGe", "PNu", "Per", "Ten",
            "Gra", "Neg", "Voi", /* two empty positions here (omitted) */ "Var"};


    /* METHODS */

    /**
     * Empty constructor.
     */
    public POSFeatsCsPositional(){

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
        String [] feats = new String [13];

        for (String val : values){

            // standard Czech POS tag, ignore otherwise
            if (val.length() == 15){
                val = (val.substring(0, 12) + val.charAt(14)); // omit two empty (unused) positions
                
                for (int i = 0; i < feats.length; ++i){
                    feats[i] = (feats[i] == null ? "" : feats[i] + SEP) + val.charAt(i);
                }
            }
        }
        return feats;
    }


    /**
     * This returns just the POS to be used in the ARFF generated features (SubPOS not needed,
     * since SubPOS & POS works the same as SubPOS alone).
     */
    @Override
    public String getFullPOS(String posVal, String featVal) {

        return posVal;
    }

}
