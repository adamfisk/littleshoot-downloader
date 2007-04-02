package org.lastbamboo.download;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;

import javax.servlet.http.HttpServletResponse;

/**
 * Interface for classes that download files.
 */
public interface Downloader
    {

    /**
     * Initiates the download.
     * 
     * @param sources The sources to download from.
     * @throws IOException If there's a read/write error downloading the file.
     */
    void download(Collection<URI> sources) throws IOException;

    /**
     * Sets the status of the download.
     * 
     * @param status The status of the download.
     */
    void setStatus(final String status);
    
    /**
     * Accessor for the status of the download.
     * 
     * @return The status of the download.
     */
    String getStatus();

    /**
     * Writes the file to the specified HTTP response.  This is useful to 
     * stream the downloading file to a browser, for example.
     * 
     * @param response The HTTP response class.
     */
    void writeFile(final HttpServletResponse response);
    
    /**
     * Cancels the download.
     */
    void cancel();

    /**
     * Alternate download method specifying the number of connections to 
     * maintain for each single host.  Creating multiple connections to the
     * same host can speed up downloads by over 200%.
     * 
     * @param sources The sources to download from.
     * @param connectionsPerHost The number of connections to maintain to
     * each host.
     * @throws IOException If there's a read/write error downloading the file.
     */
    void download(Collection<URI> sources, int connectionsPerHost) 
        throws IOException;

    /**
     * Adds a listener for download events.
     * 
     * @param dl The listener.
     */
    void addListener(DownloadListener dl);

    /**
     * Accessor for the file path the download is downloading to.
     * 
     * @return The file path the download is downloading to.
     */
    File getFile();
    
    /**
     * Accessor for the session ID for this download.
     * 
     * @return The session ID for this download.
     */
    String getSessionId();
    
    /**
     * Accessor for the URI for this download.
     * 
     * @return The URI for this download.
     */
    URI getUri();

    /**
     * Returns whether or not this downloader has already started downloading.
     * 
     * @return <code>true</code> if this downloader has already started
     * downloading, otherwise <code>false</code>.
     */
    boolean isDownloading();

    /**
     * Sets whether or not this downloader should be considered downloading.
     * This is useful to get around race conditions with many threads, for
     * example.
     * 
     * @param downloading whether or not this downloader should be considered
     * downloading.
     */
    void setDownloading(boolean downloading);

    /**
     * Accessor for the SHA-1 URN for the downloaded file.
     * 
     * @return The SHA-1 URN for the downloaded file.
     */
    URI getSha1Urn();

    /**
     * Sets the SHA-1 URN for this file.
     * 
     * @param sha1 The SHA-1 URN for the file.
     * @param matchesExpected Whether or not the SHA-1 URN matches the URN
     * we expected.
     */
    void setSha1Urn(URI sha1, boolean matchesExpected);

    /**
     * Sets the location of the downloaded file.  Applications might move it
     * after the download, for example, and this method allows it to be 
     * updated.
     * 
     * @param file The file location.
     */
    void setFile(File file);
    
    /**
     * Accessor for the MIME type.  This allows the central server to set the
     * MIME type appropriately.
     * 
     * @return The MIME type for the downloading file.
     */
    String getMimeType();

    /**
     * Returns whether or not we downloaded the expected SHA-1 URN.
     * @return Whether or not we downloaded the expected SHA-1 URN.
     */
    boolean downloadedExpectedSha1();

    }
