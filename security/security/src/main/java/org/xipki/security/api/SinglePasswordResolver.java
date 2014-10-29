/*
 * Copyright (c) 2014 Lijun Liao
 *
 * TO-BE-DEFINE
 *
 */

package org.xipki.security.api;

/**
 * @author Lijun Liao
 */

public interface SinglePasswordResolver
{
    boolean canResolveProtocol(String protocol);

    char[] resolvePassword(String passwordHint)
    throws PasswordResolverException;
}
