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

package en_deep.mlprocess.computation.wekaclassifier;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.utils.Pair;
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.manipulation.genfeat.Feature;
import en_deep.mlprocess.utils.StringUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import weka.core.Attribute;
import weka.core.Instances;

/**
 * A {@link Sequence} that browses through the ARFF data containing syntactic trees in a DFS order, tree-by-tree,
 * propagating the newly set class values to the syntactic neighborhood of the current node.
 * <p>
 * The class is able to work either with nominal or binary attributes. In nominal mode, the neighborhood class values must
 * be stored each in one attribute; in binary mode, a continuous field of binary attributes for all possible values is assumed.
 * </p>
 *
 * @author Ondrej Dusek
 */
public class TreeReader implements Sequence {

    /* CONSTANT */

    /** Binary mode name */
    private static final String BINARY = "bin";

    /** Possible fillers for missing nominal values */
    private static final String [] FILLERS = { "[OTHER]", "" };

    /** Multiple valued features: separator for the individual values */
    private static final String SEP = Feature.SEP;

    /* DATA */

    /** The current task id */
    private String taskId;

    /** The data set (original, with no filtered attributes) */
    private Instances data;

    /** Are we working with binary attributes ? */
    private boolean binMode;

    /** The current instance node */
    private Node curNode;
    /** The current root */
    private Node curRoot;

    /** Start of the current sentence in the data file */
    private int curSentBase;
    /** Length of the current sentence in the data file */
    private int curSentLen;

    /** Attribute(s) with the head of the current node's class value */
    private NeighborhoodAttribute headClass;
    /** Attribute(s) with the left sibling of the current node's class value */
    private NeighborhoodAttribute leftClass;
    /** Attribute(s) with class values of all the current node left siblings */
    private NeighborhoodAttribute leftClasses;
    /** In binary mode, attributes with class values of all the current node left siblings, set-aware mode */
    private NeighborhoodAttribute leftClassesSet;

    /** The word ID attribute index */
    private int wordIdOrd;
    /** The sentence ID attribute index */
    private int sentIdOrd;
    /** The syntactic attribute index */
    private int headOrd;


    /* METHODS */

    /**
     * Given the ARFF data file and a parameter string from the {@link Task} parameters,
     * this creates a new {@link TreeReader}.
     * <p>
     * The format of the class parameters is as follows:
     * </p>
     * <pre>
     * mode wordId sentId head headClass leftClass leftClasses
     * </pre>
     * Where:
     * <ul>
     * <li><tt>mode</tt> is <tt>bin</tt> or <tt>nom</tt>, i.e. working with binary or nominal attributes</li>
     * <li><tt>wordId</tt> is the word id attribute name</li>
     * <li><tt>sentId</tt> is the sentence id attribute name</li>
     * <li><tt>head</tt> is the attribute name for the word id of this node's head</li>
     * <li><tt>headClass</tt> is the attribute name for the class value of the head of the current node</li>
     * <li><tt>leftClass</tt> is the attribute name for the class value of the nearest left sibling of the current node</li>
     * <li><tt>leftClasses</tt> is the attribute name for the class values all left siblings of the current node</li>
     * </ul>
     *
     * @param taskId the current task id
     * @param data the input ARFF data file (with no filtered attributes, to be used as classification output)
     * @param params parameters: format -- mode wordId sentId head headClass leftClass leftClasses
     */
    public TreeReader(String taskId, Instances data, String params) throws TaskException, Exception {

        this.taskId = taskId;
        String [] paramArr = params.split("\\s+");

        if (paramArr.length != 7){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.taskId, "Invalid parameters for a TreeReader class!");
        }

        this.binMode = paramArr[0].equals(BINARY);

        this.data = data;
        this.findIdxAttributes(paramArr[1], paramArr[2], paramArr[3]);

        this.headClass = new NeighborhoodAttribute(paramArr[4] + (this.binMode ? "=" : ""),
                this.data, this.binMode, this.taskId);
        this.leftClass = new NeighborhoodAttribute(paramArr[5] + (this.binMode ? "=" : ""),
                this.data, this.binMode, this.taskId);
        this.leftClasses = new NeighborhoodAttribute(paramArr[6] + (this.binMode ? "=" : ""),
                this.data, this.binMode, this.taskId);
        if (this.binMode){
            this.leftClassesSet = new NeighborhoodAttribute(paramArr[6] + ">", this.data, this.binMode, this.taskId);
        }
    }

    /**
     * Returns the instance number of the root node for the next tree. Creates a tree-like structure
     * in {@link #curRoot} to be browsed by all the further calls to {@link #getNextInstance()}.
     * 
     * @return the instance number of the root node for the next tree
     */
    private int getNextTree(){
        
        this.curSentBase += this.curSentLen; // first call: 0 + 0 = 0 and the further search begins

        if (this.curSentBase >= this.data.numInstances()){
            return -1;
        }

        this.curSentLen = 0;
        int curSentId = (int) this.data.get(this.curSentBase).value(this.sentIdOrd);
        int root = -1;

        while (this.curSentBase + this.curSentLen < this.data.numInstances()
                && (int) this.data.get(this.curSentBase + this.curSentLen).value(this.sentIdOrd) == curSentId){

            if (this.data.get(this.curSentBase + this.curSentLen).value(this.headOrd) == 0){
                root = this.curSentBase + this.curSentLen;
            }
            this.curSentLen++;
        }

        this.curRoot = this.exploreSubtree(root, null);
        this.curNode = null;

        return root;
    }

    /**
     * This creates a new node and explores its whole subtree recursively.
     * @param inst the input data instance whose subtree is to be explored
     * @param head the syntactic head of the current node
     * @return the whole subtree of the newly created node
     */
    private Node exploreSubtree(int inst, Node head){

        Node n = new Node(inst);

        n.head = head;
        n.subtreeLast = n;
        
        int [] childrenInst = this.getChildren(n.instance);
        if (childrenInst != null && childrenInst.length > 0){

            n.children = new Node [childrenInst.length];

            for (int i = 0; i < childrenInst.length; ++i){
                n.children[i] = exploreSubtree(childrenInst[i], n);
                if (i > 0){
                    n.children[i-1].subtreeLast.next = n.children[i];
                    n.children[i-1].rightSibling = n.children[i];
                    n.children[i].leftSibling = n.children[i-1];
                }
            }
            n.next = n.children[0];
            n.subtreeLast = n.children[childrenInst.length-1].subtreeLast;
        }

        return n;
    }

    /**
     * Returns the instance numbers of all syntactical children of the given node.
     * @param instance the instance number of the desired node
     * @return the instance numbers of all its children
     */
    private int[] getChildren(int instance) {
        
        ArrayList<Integer> children = new ArrayList<Integer>();
        int [] childrenArr;

        for (int i = 0; i < this.curSentLen; ++i){
            
            if (this.data.get(this.curSentBase + i).value(this.headOrd)
                    == this.data.get(instance).value(this.wordIdOrd)){

                children.add(this.curSentBase + i);
            }
        }
        childrenArr = new int[children.size()];
        for (int i = 0; i < children.size(); ++i){
            childrenArr[i] = children.get(i);
        }
        return childrenArr;
    }

    /**
     * Returns the instance number of the next node (in DFS).
     * @return the instance number of the next node (in DFS).
     */
    @Override
    public int getNextInstance(){

        // this holds always, except for the first call
        if (this.curNode != null){
            this.curNode = this.curNode.next; // this yields null at the end of the tree
        }

        // first call / end of a tree -- move to next tree if possible
        if (this.curNode == null){
            if (this.getNextTree() >= 0){
                this.curNode = this.curRoot;
            }
            else {
                return -1;
            }
        }
        return this.curNode.instance;
    }

    /**
     * Sets the class value for the current node and propagates it to all children and right siblings of the current node.
     * @param value the class value to be propagated
     */
    @Override
    public void setCurrentClass(double value){

        this.data.get(this.curNode.instance).setClassValue(value);
        
    }

    /**
     * This finds the necessary word ID, sentence ID and syntactic head attributes in the input data
     * and throws an exception if they are not present.
     *
     * @param wordId name of the word id parameter
     * @param sentId name of the sentence id parameter
     * @param head name of the syntactic head parameter
     */
    private void findIdxAttributes(String wordId, String sentId, String head) throws TaskException {

        if (this.data.attribute(wordId) == null || this.data.attribute(sentId) == null
                || this.data.attribute(head) == null){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.taskId, "TreeReader:"
                    + "wordId, sentId or head attribute not found in the ARFF data");
        }

        this.wordIdOrd = this.data.attribute(wordId).index();
        this.sentIdOrd = this.data.attribute(sentId).index();
        this.headOrd = this.data.attribute(head).index();
    }

    @Override
    public List<Pair<Integer, double[]>> getCurNeighborhood() {

        ArrayList ret = new ArrayList<Pair<Integer, double[]>>(4);

        // head class
        if (this.curNode.head != null){
            ret.add(this.headClass.getValues(this.data.get(this.curNode.head.instance).stringValue(this.data.classIndex())));
        }
        else {
            ret.add(this.headClass.getValues("")); // root node - no head formeme
        }

        // left siblings classes
        if (this.curNode.leftSibling != null){

            Node cur = this.curNode.leftSibling;
            ArrayDeque<String> vals = new ArrayDeque<String>();

            // the first left sibling
            vals.push(this.data.get(cur.instance).stringValue(this.data.classIndex()));
            ret.add(this.leftClass.getValues(vals.peek()));

            // all left siblings
            cur = cur.leftSibling;
            while (cur != null){
                vals.push(this.data.get(cur.instance).stringValue(this.data.classIndex()));
                cur = cur.leftSibling;
            }

            ret.add(this.leftClasses.getValues(StringUtils.join(vals, SEP))); // not set-aware
            if (this.binMode){
                ret.add(this.leftClassesSet.getValuesSet(vals.toArray(new String[0]))); // set-aware
            }
        }
        return ret;
    }


    /**
     * Tree representation of one sentence, used in DFS searches.
     */
    private class Node {

        /** Syntactic head of the current node */
        Node head;
        /** Syntactic children of the current node */
        Node [] children;
        /** The last node in DFS ordering in the subtree of the current node */
        Node subtreeLast;
        /** The next node in DFS ordering for the current tree */
        Node next;
        /** The nearest left sibling of the current node */
        Node leftSibling;
        /** The nearest right sibling of the current node */
        Node rightSibling;

        /** Instance number corresponding to the current node */
        int instance;

        /**
         * Create a node for the given instance
         * @param instance the instance number for the current node
         */
        private Node(int instance) {
            this.instance = instance;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(instance);
            if (this.children != null){
                sb.append("(");
                for (Node child : this.children){
                    sb.append(" ");
                    sb.append(child);
                }
                sb.append(" )");
            }
            return sb.toString();
        }
    }


    private class NeighborhoodAttribute {

        /** The index of the first attribute in the data set */
        int index;
        /** Length of this group of attributes, if binary mode is used, 0 otherwise */
        int binLength;
        /** The one attribute for nominal mode */
        Attribute nomAttrib;
        /** All the attributes for binary mode */
        Hashtable<String,Attribute> binAttribs;

        /**
         * This sets the initial values -- finds the attribute in the data set.
         * @param name the name/prefix of the attribute(s)
         * @param data the data set where the attribute is to be found
         * @param binMode use one nominal attribute or a set of binary ones?
         * @param taskId the current task id, just for error messages
         */
        NeighborhoodAttribute(String name, Instances data, boolean binMode, String taskId) throws TaskException{

            if (binMode){
                Enumeration<Attribute> attribs = data.enumerateAttributes();
                Attribute attrib = attribs.nextElement();

                // find the first corresponding attribute
                while (attribs.hasMoreElements() && !attrib.name().startsWith(name)){
                    attrib = attribs.nextElement();
                }

                this.index = attrib.index();
                this.binAttribs = new Hashtable<String, Attribute>();

                // find all possible attributes relating to this value
                while (attribs.hasMoreElements() && attrib.name().startsWith(name)){
                    this.binLength++;
                    this.binAttribs.put(attrib.name().substring(name.length()), attrib);
                    attrib = attribs.nextElement();
                }
            }
            else {
                this.index = data.attribute(name).index();
                this.nomAttrib = data.attribute(name);
            }

            // throw an exception, if the attribute(s) has/ve not been found
            if (this.binLength == 0 && this.nomAttrib == null){
                throw new TaskException(TaskException.ERR_INVALID_DATA, taskId, "Cannot find attribute "
                        + name + " in the data set " + data.relationName() + ".");
            }
        }

        /**
         * Return an array of values given the string value for this attribute.
         * @param strVal the string value to be set
         * @return
         */
        Pair<Integer,double []> getValues(String strVal){

            double [] numVals = new double [this.binLength != 0 ? this.binLength : 1];

            // nominal mode - return just one value
            if (this.binLength == 0){
                numVals[0] = this.nomAttrib.indexOfValue(strVal);

                if (numVals[0] == -1){
                    int i = 0;

                    while (numVals[0] == -1 && i < FILLERS.length){
                        numVals[0] = this.nomAttrib.indexOfValue(FILLERS[i++]);
                    }
                }
            }
            // binary mode -- make a bunch of 0's with one 1
            else {
                Attribute attr;

                if ((attr = this.binAttribs.get(strVal)) != null){
                    numVals[attr.index()-this.index] = 1;
                }
            }

            return new Pair<Integer, double []>(this.index, numVals);
        }

        /**
         * Return an array of values given the string value set for this attribute (binary mode only).
         * @param value the string value to be set
         * @return
         */
        Pair<Integer, double []> getValuesSet(String [] strVals){

            double [] numVals = new double [this.binLength != 0 ? this.binLength : 1];

            for (String strVal : strVals){
                Attribute attr;

                if ((attr = this.binAttribs.get(strVal)) != null){
                    numVals[attr.index()-this.index] = 1;
                }
            }
            return new Pair<Integer, double []>(this.index, numVals);
        }
    }
}

