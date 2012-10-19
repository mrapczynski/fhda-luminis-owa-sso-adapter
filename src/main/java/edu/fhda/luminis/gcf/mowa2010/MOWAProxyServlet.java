package edu.fhda.luminis.gcf.mowa2010;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.*;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Map;

/**
 * HTTP servlet designed to act as a proxy between the Luminis GCF framework, and Microsoft Exchange OWA 2010.
 * <br/><br/>
 * MOWA performs user agent detection to decide whether or not a requesting client should be able access the OWA
 * premimum experience, or the more basic light experience. Based on testing, premimum browsers such as Firefox, IE, and
 * Safari are all supported for the preimum experience. Access MOWA with anything else and light mode is the forced default.
 * <br/><br/>
 * When using the Generic Connector Framework (GCF) to perform a single sign-on transaction from the Luminis
 * portal into MOWA, the GCF client does not identify itself with a user agent string considered acceptable by OWA
 * to enable premium mode. Also missing is the ability to insert a specific cookie crucial to enabling premium
 * mode, and emulation of a special JavaScript call if premimum mode is detected as supported when MOWA is loading.
 * <br/><br/>
 * The MOWAProxy servlet is the glue to fill in this missing functionality. Rather than script the GCF to contact
 * MOWA directly, the MOWAProxy servlet adds an additional layer where the HTTP communication is performed with
 * extra steps to authenticate and enable a premium mode session.  Using a simple configuration in the
 * web.xml file, the servlet contacts MOWA via HTTP (or HTTPS), sends a mock user agent string, and includes the correct
 * cookies during authentication. The response from MOWA after requesting authentication via a POST request is sent
 * back directly to the GCF along with any session cookies acquired along the way.
 * <br/><br/>
 * Specific behaviors have been implemented to improve the outcome when MOWA is deployed behind a Windows Network
 * Load Balancing system.
 * @author Matt Rapczynski, Foothill-De Anza Community College District, rapczynskimatthew@fhda.edu
 * @version 1.0
 */
public class MOWAProxyServlet extends HttpServlet {

    private final static String GCF_USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; rv:12.0) Gecko/20120403211507 Firefox/12.0";
    private final Log log = LogFactory.getLog(MOWAProxyServlet.class);

    // String fields to be re-used during each GCF authentication request
    private URL mowaSystemURL = null;
    private String mowaAuthURLStep2 = null;
    private String mowaAuthURLStep3 = null;
    private String mowaDomain;

    // SSL and network configuration
    private KeyStore mowaTrustStore = null;
    private final SchemeRegistry httpSchemeRegistry = new SchemeRegistry();

    // HTTP client
    private DefaultHttpClient httpClient;

    @Override
    public void init() throws ServletException {
        // Define local variables for servlet initialization
        String mowaEncodedSystemURL;
        SchemeSocketFactory httpSSLSocketFactory;
        PoolingClientConnectionManager httpPoolManager;

        // Get application context
        ServletContext appContext = this.getServletContext();

        // Validate context parameters
        try {
            mowaSystemURL = new URL(appContext.getInitParameter("mowa_base_url"));
        }
        catch (MalformedURLException mowaURLError) {
            log.error("Parsing MOWA system URL failed", mowaURLError);
        }

        if(mowaSystemURL == null) {
            throw new ServletException("Cannot initialize without \"mowa_base_url\" parameter");
        }
        mowaEncodedSystemURL = URLEncoder.encode(mowaSystemURL.toString());

        // Pre-allocate values that will be used over and over, and do not need to change
        mowaAuthURLStep2 = mowaSystemURL.toString() + "auth/logon.aspx?replaceCurrent=1&url=" + mowaEncodedSystemURL;
        mowaAuthURLStep3 = mowaSystemURL.toString() + "auth.owa";
        mowaDomain = appContext.getInitParameter("mowa_domain");

        // Do we need to check SSL?
        if(mowaSystemURL.getProtocol().equalsIgnoreCase("https")) {
            log.info("SSL support requested");

            // Get and validate keystore config parameters
            String sslTrustFilename = appContext.getInitParameter("ssl_trust_file");
            String keystoreFilename;
            String keystorePassword;
            if(sslTrustFilename == null) {
                log.error("Cannot initialize without \"ssl_trust_file\" parameter");
                throw new ServletException("Cannot initialize without \"ssl_trust_file\" parameter");
            }
            keystoreFilename = appContext.getRealPath("/WEB-INF/") + "/" + sslTrustFilename;

            keystorePassword = appContext.getInitParameter("ssl_trust_password");
            if(keystorePassword == null) {
                log.error("Cannot initialize without \"ssl_trust_password\" parameter");
                throw new ServletException("Cannot initialize without \"ssl_trust_password\" parameter");
            }

            File keystoreFile = new File(keystoreFilename);
            if(!(keystoreFile.exists())) {
                log.error("Cannot initialize without valid keystore file: " + keystoreFile.getPath());
                throw new ServletException("Cannot initialize without valid keystore file: " + keystoreFile.getPath());
            }

            // Create a keystore object and load the configured keystore
            FileInputStream keystoreFileStream = null;
            try {
                mowaTrustStore  = KeyStore.getInstance(KeyStore.getDefaultType());
                keystoreFileStream = new FileInputStream(keystoreFile);
                mowaTrustStore.load(keystoreFileStream, keystorePassword.toCharArray());
            }
            catch (Throwable keystoreError) {
                log.error("Failed to load SSL keystore file", keystoreError);
            }
            finally {
                try { keystoreFileStream.close(); }
                catch (NullPointerException closeStreamErrror) {
                    log.error("Failed to close keystore stream", closeStreamErrror);
                }
                catch (IOException closeStreamErrror) {
                    log.error("Failed to close keystore stream", closeStreamErrror);
                }
            }

            // Setup HTTP SSL socket factory
            try {
                httpSSLSocketFactory = new SSLSocketFactory(mowaTrustStore);
                httpSchemeRegistry.register(new Scheme("https", 443, httpSSLSocketFactory));
            }
            catch(Throwable httpConfigError) {
                log.error("Failed to setup HTTP components for SSL", httpConfigError);
            }
        }

        // Setup HTTP pooled connection manager
        httpSchemeRegistry.register(new Scheme("http", 80, new PlainSocketFactory()));
        httpPoolManager = new PoolingClientConnectionManager(httpSchemeRegistry);
        httpPoolManager.setMaxTotal(24);
        httpPoolManager.setDefaultMaxPerRoute(12);

        // Create and configure HTTP client
        httpClient = new DefaultHttpClient(httpPoolManager);
        httpClient.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
        httpClient.getParams().setBooleanParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true);
        httpClient.getParams().setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 10000);
        httpClient.setRedirectStrategy(new LaxRedirectStrategy());

        // Configure global request handling logic for the HTTP client
        httpClient.addRequestInterceptor(new HttpRequestInterceptor() {
            @Override
            public void process(HttpRequest httpRequest, HttpContext httpContext) throws HttpException, IOException {
                // Add a mock user-agent header to encourage MOWA premimum mode
                httpRequest.setHeader(HTTP.USER_AGENT, MOWAProxyServlet.GCF_USER_AGENT);
            }
        });

        log.info("MOWA GCF Adapter Loaded Successfully");
    }

    @Override
    public void destroy() {
        // Perform any clean up logic specified in the parent class
        super.destroy();

        // Clean up the HTTP components
        httpClient.getConnectionManager().shutdown();
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Delegate POST verb to proxyRequest(...)
        proxyRequest(request, response);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Delegate GET verb to proxyRequest(...)
        proxyRequest(request, response);
    }

    private void proxyRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Get and validate identity parameters
        Map requestParams = request.getParameterMap();

        // Portal ID
        if(!(requestParams.containsKey("mailboxuser"))) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid parameter: mailboxuser");
            return;
        }

        // Portal password
        if(!(requestParams.containsKey("mailboxpassword"))) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid parameter: mailboxpassword");
            return;
        }

        // Create an HTTP context and cookie store for this SSO session
        HttpContext owaContext = new BasicHttpContext();
        CookieStore owaCookies = new BasicCookieStore();
        owaContext.setAttribute(ClientContext.COOKIE_STORE, owaCookies);

        // Begin a SSO process
        // Step 1 - Open OWA login
        HttpGet mowaRequest = new HttpGet(mowaSystemURL.toString());
        HttpResponse mowaResponse = httpClient.execute(mowaRequest, owaContext);
        EntityUtils.consume(mowaResponse.getEntity());

        // Step 2 - Get OWA premium login (emulates JavaScript call in premium mode)
        mowaRequest = new HttpGet(mowaAuthURLStep2);
        mowaResponse = httpClient.execute(mowaRequest, owaContext);
        EntityUtils.toString(mowaResponse.getEntity());

        // Step 3A - Build a set of name/value pairs for authentication
        ArrayList<NameValuePair> postParams = new ArrayList<NameValuePair>();
        postParams.add(new BasicNameValuePair("destination", mowaSystemURL.toString()));
        postParams.add(new BasicNameValuePair("flags", "0"));
        postParams.add(new BasicNameValuePair("forcedownlevel", "0"));
        postParams.add(new BasicNameValuePair("trusted", "0"));
        postParams.add(new BasicNameValuePair("username", ((String[]) requestParams.get("mailboxuser"))[0]));
        postParams.add(new BasicNameValuePair("password", ((String[]) requestParams.get("mailboxpassword"))[0]));
        postParams.add(new BasicNameValuePair("isUtf8", "1"));

        // Step 3B - Encode name/value pairs into a String entity body
        String encodedParams = URLEncodedUtils.format(postParams, "UTF-8");
        log.debug(encodedParams);

        // Step 3C - Setup the "PBack" cookie - required for successful authentication
        BasicClientCookie cookiePback = new BasicClientCookie("PBack", "0");
        cookiePback.setDomain(mowaDomain);
        cookiePback.setPath("/");
        owaCookies.addCookie(cookiePback);

        // Step 3D - Do an authentication request
        HttpPost mowaAuthRequest = new HttpPost(mowaAuthURLStep3);
        mowaAuthRequest.setEntity(new StringEntity(encodedParams));
        mowaAuthRequest.setHeader("Content-Type", "application/x-www-form-urlencoded");
        mowaAuthRequest.setHeader("Referer", mowaAuthURLStep2);
        mowaResponse = httpClient.execute(mowaAuthRequest, owaContext);

        // Step 4 - Write the cookies back to GCF connector
        for(Cookie owaCookie : owaCookies.getCookies()) {
            // Ignore specific cookies
            // UserContext is a pain and is best left to the client to manage. It may appear when
            // WNLB (Windows Network Load Balancing) is used to deploy a set of Exchange CAS servers.
            // http://technet.microsoft.com/en-us/library/ff625247.aspx#affinity
            if(owaCookie.getName().equals("UserContext")) {
                continue;
            }

            // Create a cookie object to exchange data with GCF connector
            javax.servlet.http.Cookie javaxCookie = new javax.servlet.http.Cookie(owaCookie.getName(), owaCookie.getValue());
            javaxCookie.setPath("/");
            javaxCookie.setSecure(owaCookie.isSecure());

            log.debug("MOWA Cookie - " + javaxCookie.getName() + " : " + javaxCookie.getValue());

            // Add the cookie to be returned to the client
            response.addCookie(javaxCookie);
        }

        // Step 5 - Write MOWA response back to GCF connector
        mowaResponse.getEntity().writeTo(response.getOutputStream());
    }

}
