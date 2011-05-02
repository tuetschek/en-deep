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

package en_deep.mlprocess.manipulation.posfeat;

import en_deep.mlprocess.manipulation.StReader;
import en_deep.mlprocess.utils.StringUtils;

/**
 * This handles Czech positional POS tags by just truncating them to the first
 * two positions (the actual POS, without any further information on flection etc.).
 * @author Ondrej Dusek
 */
public class POSFeatsCsTrunc extends POSFeatures {

    /* CONSTANTS */

    /** The ARFF attribute name suffix */
    private static final String ATTR_NAME = "POS";

    /* METHODS */

    /**
     * Empty constructor.
     */
    public POSFeatsCsTrunc(){

    }


    @Override
    public String getHeader(String prefix) {

        return StReader.ATTRIBUTE + " " + prefix + ATTR_NAME + " " + StReader.STRING;
    }

    @Override
    public String listFeats(String value) {

        String [] values = value.split(SEP); // allow multiple values
        String feats = null;

        for (String val : values){

            // standard Czech POS tag, ignore otherwise
            if (val.length() == 15){
                feats = (feats == null ? "" : feats + SEP) + val.substring(0, 2);
            }
        }
        return feats == null ? EMPTY : "\"" + StringUtils.escape(feats) + "\"";
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
