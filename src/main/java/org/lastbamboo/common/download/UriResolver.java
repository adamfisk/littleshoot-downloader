package org.lastbamboo.common.download;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;

/**
 * Interface for classes that can resolve URIs to download sources for that
 * URI.
 */
public interface UriResolver
    {

    /**
     * Accesses the available sources for a single URI.  The returned 
     * collection contains resolvable URIs we can download different parts of
     * the file from.
     * 
     * @param uri The URI to resolve.
     * @return The collection of URI sources we can download the URI from.
     * @throws IOException If we could not access the resolution service.
     */
    Collection<URI> resolve(final URI uri) throws IOException;

    /**
     * Accesses the SHA-1 for the resource this resolved just looked up.
     * 
     * @return The SHA-1 for the resource.
     */
    URI getSha1();

    }
