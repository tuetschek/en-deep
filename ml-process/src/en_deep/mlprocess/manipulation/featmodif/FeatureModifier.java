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

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.manipulation.StReader;
import en_deep.mlprocess.manipulation.genfeat.Feature;
import en_deep.mlprocess.utils.StringUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 *
 * @author Ondrej Dusek
 */
public abstract class FeatureModifier {

    /** Feature name prefix for golden feature values */
    public static final String PREFIX_GOLD = "feat_";
    /** Feature name prefix for predicted feature values */
    protected static final String PREFIX_PRED = "pfeat_";
    /** Empty feature value (any string that is never used in the features values themselves) */
    protected static final String EMPTY = "-";

    protected static final String LF = System.getProperty("line.separator");
    /** Separator for multiple values */
    protected static final String SEP = Feature.SEP;


    /**
     * This, given a name prefix, lists all the output feature names.
     *
     * @param prefix prefix for the output feature names
     * @return a list of all output feature names
     */
     public abstract String[] getOutputFeatsList(String prefix);

     /**
     * This returns the POS features ARFF headers (both golden and predicted).
     * @return POS features ARFF headers
     */
    public String getHeaders() {
        return this.getHeader(PREFIX_GOLD) + LF + this.getHeader(PREFIX_PRED);
    }

    /**
     * This, given a name prefix, lists all the POS features ARFF headers.
     *
     * @param prefix prefix for POS feature names in the headers
     * @return the ARFF header with all the available POS features
     */
     public String getHeader(String prefix){
         
         String [] attribs = this.getOutputFeatsList(prefix);
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < attribs.length; ++i){
             sb.append(StReader.ATTRIBUTE + " ").append(attribs[i]).append(" " + StReader.STRING);
             if (i < attribs.length-1){
                 sb.append(LF);
             }
         }
         return sb.toString();
     }


    /**
     * This returns the output feature values, given the input text value of the processed feature.
     * The text value may be null; if so, the returned value should be as much null values as the
     * usual number of returned values.
     *
     * @param value the input feature value
     * @return an array with output feature values
     */
    public abstract String [] getOutputValues(String value);

    /**
     * This lists the POS feature values for ARFF, given the input feature strings for
     * both predicted and golden feature values.
     * @param goldFeat ST text format -- golden POS features values
     * @param predictedFeat ST text format -- predicted POS features values
     * @return ARFF data -- values of all features
     */
    public String listFeats(String goldFeat, String predictedFeat) {
        return this.listFeats(goldFeat) + "," + this.listFeats(predictedFeat);
    }

    /**
     * This lists the ARFF values of all possible morphological features, given their compact string representation.
     *
     * @param value a string representation of the input feature
     * @return the ARFF array representation of the features
     */
    public String listFeats(String value){

        String [] feats = this.getOutputValues(value);

        for (int i = 0; i < feats.length; ++i){
            if (feats[i] == null){
                feats[i] = EMPTY;
            }
        }

        return StringUtils.join(feats, ",", true);
    }
    
   
    /**
     * This tries to find the feature handling class with the given name and initialize it.
     * @param className the feature handling class name (within the {@link en_deep.mlprocess.manipulation.posfeat} package
     * @return the desired feature handler, or null if not successful
     */
    public static FeatureModifier createHandler(String className) {

        FeatureModifier res = null;
        Class featureClass = null;
        Constructor featureConstructor = null;

        // retrieve the feature handler class
        try {
            if (!className.contains(".")){
                className = FeatureModifier.class.getPackage().getName() + "." + className;
            }
            featureClass = Class.forName(className);
        }
        catch (ClassNotFoundException ex) {
            return null;
        }

        // try to call a constructor with no parameters
        try {
            featureConstructor = featureClass.getConstructor();
            res = (FeatureModifier) featureConstructor.newInstance();
        }
        catch (InvocationTargetException e){
            Logger.getInstance().logStackTrace(e.getCause(), Logger.V_DEBUG);
            return null;
        }
        catch (Exception ex){
            Logger.getInstance().logStackTrace(ex, Logger.V_DEBUG);
            return null;
        }

        return res;
    }


}
