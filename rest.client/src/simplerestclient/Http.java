/*******************************************************************************
 * Copyright (c) 2008, 2009 Brian Ballantine and Bug Labs, Inc.
 * 
 * MIT License
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *  
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *  
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *******************************************************************************/
package simplerestclient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.IOUtils;

/**
 * A static class for dealing RESTfully with HTTP calls.
 * 
 * Successful calls return response payload as a String. Unsuccessful calls
 * throw IOExceptions.
 * 
 * Example Usage: String resp = Http.get("http://some.url")
 * System.out.println(resp.getString());
 * 
 * @author Ken Gilmer
 * @author Brian Ballantine
 * @author Alex Kravets
 */
public class Http {
	private static final String DEFAULT_ERROR_MESSAGE = "There was a connection error.  The server responded with status code ";
	// ////////////////////////////////////////////// HTTP REQUEST METHODS

	private static final String HEADER_TYPE = "Content-Type";
	private static final String HEADER_PARA = "Content-Disposition: form-data";
	private static final String CONTENT_TYPE = "multipart/form-data";
	private static final String LINE_ENDING = "\r\n";
	private static final String BOUNDARY = "boundary=";
	private static final String PARA_NAME = "name";
	private static final String FILE_NAME = "filename";
	private final Map<String, String> cache;
	private final int waitMillis;
	private final Lock lock;

	/**
	 * A Http instance with no cache and no rate limiting.
	 */
	public Http() {
		this.cache = null;
		this.lock = new BrokenLock();
		this.waitMillis = 0;
	}

	/**
	 * A Http instance with a predefined cache.
	 * 
	 * @param cache
	 */
	public Http(Map<String, String> cache) {
		this.cache = cache;
		this.lock = new BrokenLock();
		this.waitMillis = 0;
	}

	/**
	 * A Http instance with a new cache.
	 * 
	 * @param cache
	 */
	public Http(boolean cacheResults) {
		if (cacheResults)
			this.cache = new HashMap<String, String>();
		else
			this.cache = null;
		this.lock = new BrokenLock();
		this.waitMillis = 0;
	}

	/**
	 * A HTTP cache with rate limiting to specified wait time.
	 * 
	 * @param waitMillis
	 */
	public Http(int waitMillis) {
		this.cache = null;
		this.waitMillis = waitMillis;
		this.lock = new ReentrantLock(true);
	}

	/**
	 * A HTTP cache with rate limiting to specified wait time.
	 * 
	 * @param waitMillis
	 */
	public Http(int waitMillis, boolean cacheResults) {
		if (cacheResults)
			this.cache = new HashMap<String, String>();
		else
			this.cache = null;
		this.waitMillis = waitMillis;
		this.lock = new ReentrantLock(true);
	}

	/**
	 * HTTP GET
	 * 
	 * @param url
	 *            String URL to connect to
	 * @return Response as String
	 */
	public String get(String url) throws IOException {
		if (cache != null)
			if (cache.containsKey(url))
				return cache.get(url);

		try {
			if (!lockAndWait()) 
				return null;
			
			if (cache != null)
				if (cache.containsKey(url))
					return cache.get(url);

			// Create connection
			HttpURLConnection connection = (HttpURLConnection) (new URL(url)).openConnection();
			connection.setReadTimeout(16834);
			// Configure connection
			connection.setDoInput(true);
			connection.setDoOutput(false);

			// Check for error
			checkStatus(connection);

			// Retrieve response
			StringWriter writer = new StringWriter();
			IOUtils.copy(connection.getInputStream(), writer);

			String response = writer.toString();
			if (cache != null)
				cache.put(url, response);

			return response;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {		
			lock.unlock();
		}
	}

	/**
	 * HTTP GET
	 * 
	 * @param url
	 *            String URL to connect to
	 * @return Response as String
	 */
	public String get(String url, Map<String, String> headers) throws IOException {
		String cacheKey = null;
		if (cache != null) {
			cacheKey = url + propertyString(headers);
			if (cache.containsKey(cacheKey))
				return cache.get(cacheKey);
		}

		if (!lockAndWait())
			return null;

		try {
			// Create connection
			HttpURLConnection connection = (HttpURLConnection) (new URL(url)).openConnection();

			// Configure connection
			connection.setDoInput(true);
			connection.setDoOutput(false);
			for (Entry<String, String> e : headers.entrySet()) {
				connection.addRequestProperty(e.getKey(), e.getValue());
			}

			// Check for error
			checkStatus(connection);

			// Retrieve response
			StringWriter writer = new StringWriter();
			IOUtils.copy(connection.getInputStream(), writer);

			String response = writer.toString();
			if (cache != null)
				cache.put(cacheKey, response);

			return response;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * HTTP POST to url
	 * 
	 * @param url
	 *            String URL to connect to
	 * @param data
	 *            String data to post
	 * @return Response as String
	 */
	public String post(String url, String data) throws IOException {
		return post(url, data, null);
	}

	/**
	 * HTTP POST to url w/ extra http headers
	 * 
	 * @param url
	 * @param data
	 * @param headers
	 * @return Response as String
	 * @throws IOException
	 */
	public String post(String url, String data, Map<String, String> headers) throws IOException {
		if (!lockAndWait())
			return null;

		try {
			// Create connection
			HttpURLConnection connection = (HttpURLConnection) (new URL(url)).openConnection();

			// Configure connection
			if (headers != null)
				for (Map.Entry<String, String> e : headers.entrySet())
					connection.setRequestProperty(e.getKey(), e.getValue());

			connection.setDoOutput(true);
			OutputStreamWriter osr = new OutputStreamWriter(connection.getOutputStream());
			osr.write(data);
			osr.flush();
			osr.close();

			// Check for error
			checkStatus(connection);

			// Retrieve response
			StringWriter writer = new StringWriter();
			IOUtils.copy(connection.getInputStream(), writer);

			return writer.toString();
		} finally {
			if (!lockAndWait())
				return null;
		}
	}

	/**
	 * HTTP POST to url
	 * 
	 * @param url
	 *            String URL to connect to
	 * @param stream
	 *            InputStream data to post
	 * @return Response as String
	 */
	public String post(String url, InputStream stream) throws IOException {
		return post(url, Base64.encodeBytes(IOUtils.toByteArray(stream)));
	}

	/**
	 * POST a Map of key, value pair properties, like a web form
	 * 
	 * @param url
	 * @param properties
	 * @return Response as String
	 * @throws IOException
	 */
	public String post(String url, Map<String, String> properties) throws IOException {
		String data = propertyString(properties);
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("Content-Type", "application/x-www-form-urlencoded");
		return post(url, data, headers);
	}

	/**
	 * HTTP POST byte data to a url
	 * 
	 * @param url
	 * @param data
	 * @return Response as String
	 * @throws IOException
	 */
	public String post(String url, byte[] data) throws IOException {
		if (!lockAndWait())
			return null;

		try {
			// Create connection
			HttpURLConnection connection = (HttpURLConnection) (new URL(url)).openConnection();

			// Configure connection
			connection.setRequestProperty("Content-Length", String.valueOf(data.length));
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			IOUtils.copy(new ByteArrayInputStream(data), connection.getOutputStream());

			// Check for error
			checkStatus(connection);

			// Retrieve response
			StringWriter writer = new StringWriter();
			IOUtils.copy(connection.getInputStream(), writer);

			return writer.toString();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * HTTP multipart POST which is different than a regular post mostly use
	 * this one if you're posting files
	 * 
	 * @param url
	 * @param parameters
	 *            Key-Value pairs in map. Keys are always string. Values can be
	 *            string or IFormFile
	 * @param properties
	 * @return Response as String
	 */
	public String postMultipart(String url, Map<String, Object> parameters) throws IOException {
		if (!lockAndWait())
			return null;

		try {
			// Create connection
			HttpURLConnection connection = (HttpURLConnection) (new URL(url)).openConnection();

			// Configure connection
			connection.setRequestMethod("POST");
			String boundary = createMultipartBoundary();
			connection.setRequestProperty(HEADER_TYPE, CONTENT_TYPE + "; " + BOUNDARY + boundary);
			connection.setDoOutput(true);

			// write things out to connection
			OutputStream os = connection.getOutputStream();
			// add parameters
			for (String key : parameters.keySet()) {
				Object obj = parameters.get(key);

				StringBuffer buf = new StringBuffer();
				if (obj instanceof IFormFile) {
					IFormFile file = (IFormFile) obj;
					buf.append("--" + boundary + LINE_ENDING);
					buf.append(HEADER_PARA);
					buf.append("; " + PARA_NAME + "=\"" + key + "\"");
					buf.append("; " + FILE_NAME + "=\"" + file.getFilename() + "\"" + LINE_ENDING);
					buf.append(HEADER_TYPE + ": " + file.getContentType() + ";");
					buf.append(LINE_ENDING);
					buf.append(LINE_ENDING);
					os.write(buf.toString().getBytes());
					os.write(file.getBytes());
				} else if (obj != null) {
					buf.append("--" + boundary + LINE_ENDING);
					buf.append(HEADER_PARA);
					buf.append("; " + PARA_NAME + "=\"" + key + "\"");
					buf.append(LINE_ENDING);
					buf.append(LINE_ENDING);
					buf.append(obj.toString());
					os.write(buf.toString().getBytes());
				}
				os.write(LINE_ENDING.getBytes());
			}
			os.write(("--" + boundary + "--" + LINE_ENDING).getBytes());

			// Check for error
			checkStatus(connection);

			// Retrieve response
			StringWriter writer = new StringWriter();
			IOUtils.copy(connection.getInputStream(), writer);

			return writer.toString();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * HTTP PUT to url
	 * 
	 * @param url
	 *            String URL to connect to
	 * @param data
	 *            String data to post
	 * @return Response as String
	 */
	public String put(String url, String data) throws IOException {
		return put(url, data, null);
	}

	/**
	 * HTTP PUT to url with extra headers
	 * 
	 * @param url
	 * @param data
	 * @param headers
	 * @return Response as String
	 * @throws IOException
	 */
	public String put(String url, String data, Map<String, String> headers) throws IOException {
		if (!lockAndWait()) 
			return null;
		
		try {
			// Create connection
			HttpURLConnection connection = (HttpURLConnection) (new URL(url)).openConnection();
	
			// Configure connection
			for (Entry<String, String> e : headers.entrySet()) {
				connection.addRequestProperty(e.getKey(), e.getValue());
			}
			connection.setDoOutput(true);
			connection.setRequestMethod("PUT");
			OutputStreamWriter osr = new OutputStreamWriter(connection.getOutputStream());
			osr.write(data);
			osr.flush();
			osr.close();
	
			// Check for error
			checkStatus(connection);
	
			// Retrieve response
			StringWriter writer = new StringWriter();
			IOUtils.copy(connection.getInputStream(), writer);
	
			return writer.toString();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * HTTP PUT to url
	 * 
	 * @param url
	 *            String URL to connect to
	 * @param stream
	 *            InputStream data to put
	 * @return Response as String
	 */
	public String put(String url, InputStream stream) throws IOException {
		return put(url, Base64.encodeBytes(IOUtils.toByteArray(stream)));
	}

	/**
	 * HTTP DELETE to url
	 * 
	 * @param url
	 * @return Response as String
	 * @throws IOException
	 */
	public String delete(String url) throws IOException {
		if (!lockAndWait()) 
			return null;
		
		try {
			// Create connection
			HttpURLConnection connection = (HttpURLConnection) (new URL(url)).openConnection();
	
			// Configure connection
			connection.setDoInput(true);
			connection.setRequestMethod("DELETE");
	
			// Check for error
			checkStatus(connection);
	
			// Retrieve response
			StringWriter writer = new StringWriter();
			IOUtils.copy(connection.getInputStream(), writer);
	
			return writer.toString();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * PUT a Map of key, value pair properties, like a web form
	 * 
	 * @param url
	 * @param properties
	 * @return Response as String
	 * @throws IOException
	 */
	public String put(String url, Map<String, String> properties) throws IOException {
		String data = propertyString(properties);
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Content-Type", "application/x-www-form-urlencoded");
		return put(url, data, headers);
	}

	/**
	 * HTTP HEAD to url
	 * 
	 * @param url
	 *            String URL to connect to
	 * @return HttpURLConnection ready with response data
	 */
	public String head(String url) throws IOException {
		if (!lockAndWait()) 
			return null;
		
		try {
		// Create connection
		HttpURLConnection connection = (HttpURLConnection) (new URL(url)).openConnection();

		// Configure connection
		connection.setDoInput(true);
		connection.setRequestMethod("HEAD");

		// Check for error
		checkStatus(connection);

		// Retrieve response
		StringWriter writer = new StringWriter();
		IOUtils.copy(connection.getInputStream(), writer);

		return writer.toString();
		} finally {
			lock.unlock();
		}
	}
	
	/**
	 * Clear the contents of cache. Does nothing if no cache enabled.
	 */
	public void clearCache() {
		if (this.cache != null)
			cache.clear();
	}

	/**
	 * turns a map into a key=value property string for sending to bugnet
	 */
	private String propertyString(Map<String, String> props) throws IOException {
		StringBuffer sb = new StringBuffer();

		for (Entry<String, String> e : props.entrySet()) {
			sb.append(URLEncoder.encode(e.getKey(), "UTF-8"));
			sb.append('=');
			sb.append(URLEncoder.encode(e.getValue(), "UTF-8"));
			sb.append('&');
		}

		String s = sb.toString();
		return s.substring(1, s.length() - 1);
	}

	/**
	 * helper to create multipart form boundary
	 * 
	 * @return
	 */
	private String createMultipartBoundary() {
		StringBuffer buf = new StringBuffer();
		buf.append("---------------------------");

		for (int i = 0; i < 15; i++) {
			double rand = Math.random() * 35;
			if (rand < 10) {
				buf.append((int) rand);
			} else {
				int ascii = 87 + (int) rand;
				char symbol = (char) ascii;
				buf.append(symbol);
			}
		}
		return buf.toString();
	}

	/**
	 * Check the status of the current connection and throw an error if we find
	 * an http error code.
	 * 
	 */
	private static void checkStatus(HttpURLConnection connection) throws IOException, HTTPException {
		// Get Response code out
		int response = 0;

		String statusStr = connection.getHeaderField("Status");
		if (statusStr != null)
			response = Integer.parseInt(statusStr.substring(0, 3));

		// only set message from response body if it's an HTTP error
		if (response >= 400) {
			// gobble up the error so as not to hold us back getting an error
			// message set
			String errorMessage = null;
			try {
				errorMessage = getErrorMessage(connection);
			} catch (IOException e) {
				if (errorMessage == null)
					errorMessage = DEFAULT_ERROR_MESSAGE + response + ".";
			}
			throw new HTTPException(response, errorMessage);
		}
	}

	/**
	 * Get error message out of connection
	 * 
	 * @param connection
	 * @return
	 * @throws IOException
	 */
	private static String getErrorMessage(HttpURLConnection connection) throws IOException {
		InputStream is = connection.getErrorStream();

		if (is != null) {
			StringWriter errorStr = new StringWriter();
			IOUtils.copy(is, errorStr);
			return errorStr.toString();
		}

		return null;
	}

	/**
	 * Get the lock and sleep for predefined interval.
	 * 
	 * @return
	 */
	private boolean lockAndWait() {
		try {
			lock.lock();
			Thread.sleep(waitMillis);
		} catch (InterruptedException e) {
			try {
				lock.unlock();
			} catch (IllegalMonitorStateException e2) {
				// Ignore
			}

			return false;
		}

		return true;
	}

	/**
	 * A lock that does nothing to simplify optional locking code.
	 * 
	 * @author kgilmer
	 * 
	 */
	private class BrokenLock implements Lock {

		@Override
		public void lock() {
		}

		@Override
		public void lockInterruptibly() throws InterruptedException {
		}

		@Override
		public boolean tryLock() {
			return true;
		}

		@Override
		public boolean tryLock(long l, TimeUnit timeunit) throws InterruptedException {
			return true;
		}

		@Override
		public void unlock() {
		}

		@Override
		public Condition newCondition() {
			return null;
		}
	}
}
