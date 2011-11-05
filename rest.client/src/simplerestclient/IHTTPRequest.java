package simplerestclient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * General interface for HTTPRequest, shared with RateLimitedHTTPRequest.
 * @author kgilmer
 *
 */
public interface IHTTPRequest {

	/**
	 * @param url
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#get(java.lang.String)
	 */
	public abstract HTTPResponse get(String url) throws IOException;

	/**
	 * @param url
	 * @param headers
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#get(java.lang.String, java.util.Map)
	 */
	public abstract HTTPResponse get(String url, Map<String, String> headers) throws IOException;

	/**
	 * @param url
	 * @param data
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#post(java.lang.String, java.lang.String)
	 */
	public abstract HTTPResponse post(String url, String data) throws IOException;

	/**
	 * @param url
	 * @param data
	 * @param headers
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#post(java.lang.String, java.lang.String, java.util.Map)
	 */
	public abstract HTTPResponse post(String url, String data, Map headers) throws IOException;

	/**
	 * @param url
	 * @param stream
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#post(java.lang.String, java.io.InputStream)
	 */
	public abstract HTTPResponse post(String url, InputStream stream) throws IOException;

	/**
	 * @param url
	 * @param properties
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#post(java.lang.String, java.util.Map)
	 */
	public abstract HTTPResponse post(String url, Map properties) throws IOException;

	/**
	 * @param url
	 * @param data
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#post(java.lang.String, byte[])
	 */
	public abstract HTTPResponse post(String url, byte[] data) throws IOException;

	/**
	 * @param url
	 * @param parameters
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#postMultipart(java.lang.String, java.util.Map)
	 */
	public abstract HTTPResponse postMultipart(String url, Map parameters) throws IOException;

	/**
	 * @param url
	 * @param data
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#put(java.lang.String, java.lang.String)
	 */
	public abstract HTTPResponse put(String url, String data) throws IOException;

	/**
	 * @param url
	 * @param data
	 * @param headers
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#put(java.lang.String, java.lang.String, java.util.Map)
	 */
	public abstract HTTPResponse put(String url, String data, Map headers) throws IOException;

	/**
	 * @param url
	 * @param stream
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#put(java.lang.String, java.io.InputStream)
	 */
	public abstract HTTPResponse put(String url, InputStream stream) throws IOException;

	/**
	 * @param url
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#delete(java.lang.String)
	 */
	public abstract HTTPResponse delete(String url) throws IOException;

	/**
	 * @param url
	 * @param properties
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#put(java.lang.String, java.util.Map)
	 */
	public abstract HTTPResponse put(String url, Map properties) throws IOException;

	/**
	 * @param url
	 * @return
	 * @throws IOException
	 * @see simplerestclient.HTTPRequest#head(java.lang.String)
	 */
	public abstract HTTPResponse head(String url) throws IOException;

}