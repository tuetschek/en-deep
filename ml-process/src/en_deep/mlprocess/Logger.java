/*
 *  Copyright (c) 2009 Ondrej Dusek
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

package en_deep.mlprocess;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Aggregates all the informative / debug messages and prints only those that correspond
 * to the program verbosity setting.
 * Currently, the only output possible is the system error stream. The class is a singleton,
 * its only instance is created at process startup. For verbosity setting, see the
 * description of {@link Process}.
 * @author Ondrej Dusek
 */
public class Logger {

    /* CONSTANTS */

    /** Verbosity setting: no messages should be displayed. */
    public static final int V_NOTHING = 0;
    /** Verbosity setting: only important messages should be displayed. */
    public static final int V_IMPORTANT = 1;
    /** Verbosity setting: only important and warning messages should be displayed. */
    public static final int V_WARNING = 2;
    /** Verbosity setting: important, warning and informative messages should be displayed. */
    public static final int V_INFO = 3;
    /** Verbosity setting: all messages, including debug, shall be displayed */
    public static final int V_DEBUG = 4;

    /** Default verbosity setting */
    public static final int DEFAULT_VERBOSITY = V_WARNING;

    /* DATA */

    /** The verbosity setting, set to 1 at startup (for the startup errors to be seen). */
    private int verbosity = 1;

    /** The only {@link Logger} instance */
    private static final Logger instance = new Logger();

    /** This serves for formating dates */
    private SimpleDateFormat dateFormatter;

    /* METHODS */

    /**
     * Creates a new logger - this is called once at process startup. All classes should
     * access the {@link Logger} instance via the {@link getInstance} method. Verbosity is
     * set to 0 at startup.
     */
    private Logger(){
        this.dateFormatter = new SimpleDateFormat("MMMM dd, yyyy HH:mm:ss z --- ");
    }

    /**
     * Sets the verbosity for the logger.
     *
     * @param verbosity the new verbosity setting
     */
    public void setVerbosity(int verbosity){
        this.verbosity = verbosity;
    }

    /**
     * Returns the only instance of the {@link Logger} singleton.
     * @return the only {@link Logger} instance
     */
    public static Logger getInstance(){
        return Logger.instance;
    }

    /**
     * Outputs a message, if the current verbosity setting meets the message importance.
     *
     * @param importance the importance of the message (1 - important ... 4 - debug)
     * @param text the actual text of the message
     */
    public synchronized void message(String text, int importance){

        if (importance <= this.verbosity){
            System.err.print(this.dateFormatter.format(new Date()));
            System.err.println(text);
        }
    }

    /**
     * Outputs an exception stack trace, if the current verbosity setting meets the importance.
     *
     * @param ex the exception
     * @param importance the given importance
     */
    public synchronized void logStackTrace(Throwable ex, int importance) {

        if (importance <= this.verbosity){

            System.err.println(this.dateFormatter.format(new Date()) + ex.getClass().getName() + ":");

            StackTraceElement [] stackTrace = ex.getStackTrace();

            for (int i = 0; i < stackTrace.length; ++i){
                System.err.println("\tat " + stackTrace[i].toString());
            }
        }
    }

    /**
     * Return the current verbosity setting.
     * @return the current verbosity setting.
     */
    public int getVerbosity() {
        return this.verbosity;
    }
}
