package org.lastbamboo.common.download;

/**
 * Exception thrown when SHA-1s do not match.
 */
public class Sha1Exception extends RuntimeException
    {

    /**
     * The serialization ID.
     */
    private static final long serialVersionUID = 3336706452332034857L;

    /**
     * Creates the exception.
     * 
     * @param msg The exception details message.
     */
    public Sha1Exception(final String msg)
        {
        super(msg);
        }

    }
