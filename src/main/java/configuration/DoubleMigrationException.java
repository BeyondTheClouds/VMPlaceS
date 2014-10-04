package configuration;

/* This exception is raised when looking for a non-existing host. */

/* Copyright (c) 2006-2014. The SimGrid Team.
 * All rights reserved.                                                     */

/* This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package. */


import org.simgrid.msg.MsgException;

/**
 * This exception is raised when a VM is migrated twice at the same time
 */

public class DoubleMigrationException extends MsgException {
    private static final long serialVersionUID = 1L;

    /** Constructs an <code>HostFailureException</code> without a detail message. */
    public DoubleMigrationException() {
        super();
    }
    /**
     * Constructs an <code>DoubleMigrationException</code> with a detail message.
     *
     * @param   s   the detail message.
     */
    public DoubleMigrationException(String s) {
        super(s);
    }
}