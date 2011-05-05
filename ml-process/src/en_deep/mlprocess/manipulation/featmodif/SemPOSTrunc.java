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

import en_deep.mlprocess.utils.StringUtils;

/**
 * This allows truncating semantic parts of speech to their first, or first and second field.
 * @author Ondrej Dusek
 */
public class SemPOSTrunc extends FeatureModifier {

    /* CONSTANTS */

    /** The ARFF attribute name suffix (second variant) */
    private static final String ATTR_NAME1 = "Trunc1";
    /** The ARFF attribute name suffix (second variant) */
    private static final String ATTR_NAME2 = "Trunc2";

    /* METHODS */

    /**
     * Empty constructor.
     */
    public SemPOSTrunc(){

    }


    @Override
    public String [] getOutputFeatsList(String prefix) {

        String [] ret = new String [2];
        ret[0] = prefix + "_" + ATTR_NAME1;
        ret[1] = prefix + "_" + ATTR_NAME2;
        return ret;
    }

    @Override
    public String [] getOutputValues(String value) {

        String [] values = value != null ? value.split(SEP) : new String[0]; // allow multiple values
        String [] trunc = new String [2];

        for (String val : values){

            for (int i = 0; i < 2; ++i){
                String [] fields = val.split("\\.");
                trunc[i] = (trunc[i] == null ? "" : trunc[i] + SEP) + StringUtils.join(fields, 0, i+1, ".");
            }
        }
        return trunc;
    }

}
