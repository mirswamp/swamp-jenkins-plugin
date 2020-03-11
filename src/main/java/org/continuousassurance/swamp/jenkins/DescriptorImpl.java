
/* 
  SWAMP Jenkins Plugin

  Copyright 2016 Jared Sweetland, Vamshi Basupalli, James A. Kupsch

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package org.continuousassurance.swamp.jenkins;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
//import org.continuousassurance.swamp.Messages;
import org.continuousassurance.swamp.api.Project;
import org.continuousassurance.swamp.cli.SwampApiWrapper;
import org.continuousassurance.swamp.session.util.Proxy;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;

import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.XmlFile;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.plugins.analysis.core.PluginDescriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import javax.net.ssl.SSLHandshakeException;


/**
 * Descriptor for the class {@link SwampPostBuild}. Used as a singleton. The
 * class is marked as public so that it can be accessed from views.
 *
 * Derived from Ulli Hafner's Script
 */
@Extension(ordinal = 100)
public final class DescriptorImpl extends PluginDescriptor{
    /** The ID of this plug-in is used as URL. */
    static final String PLUGIN_ID = "swamp";

    static final String PLUGIN_DISPLAY_NAME = "SWAMP Assessment";
    /** The URL of the result action. */
    static final String RESULT_URL = PluginDescriptor.createResultUrlName(PLUGIN_ID);
    /** Icons prefix. */
    static final String ICON_URL_PREFIX = "/plugin/swamp/icons/";
    /** Icon to use for the result and project action. */
    static final String ICON_URL = ICON_URL_PREFIX + "swamp-logo-small.png";

    private String hostUrl;
    private String credentialId;
    private String defaultProject;
    private boolean loginFail = false;
    private boolean verbose;

    protected static StandardUsernamePasswordCredentials getCredentials(@AncestorInPath Item item, 
            String credentialId) {

        StandardUsernamePasswordCredentials credential = null;

        if (credentialId != null) {
            List<StandardUsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentials(
                    StandardUsernamePasswordCredentials.class, 
                    item, 
                    ACL.SYSTEM, 
                    Collections.<DomainRequirement> emptyList());

            IdMatcher matcher = new IdMatcher(credentialId);
            for (StandardUsernamePasswordCredentials c : credentials) {
                if (matcher.matches(c)) {
                    credential = c;
                    break;
                }
            }
        }

        return credential;
    }

    public static boolean hasRawCredentials(XmlFile config_file) {
        
        try {
            String config_date = config_file.asString();
            DocumentBuilder newDocumentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();        
            Document parse = newDocumentBuilder.parse(new ByteArrayInputStream(config_date.getBytes(Charset.defaultCharset())));
            NodeList node_list = parse.getFirstChild().getChildNodes();
            boolean has_username = false;
            boolean has_password = false;
            for (int i = 0; i < node_list.getLength(); ++i) {
                Node node = node_list.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    String node_name = node.getNodeName();
                    if (node_name.equalsIgnoreCase("username")) {
                        has_username = true;
                    }else if (node_name.equalsIgnoreCase("password")) {
                        has_password = true;
                    }
                }
            }
            
            if ( has_username && has_password) {
                return true;
            }
            
        } catch (ParserConfigurationException | SAXException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace(System.out);
            
        }
        return false;
    }
    
    /**
     * Creates a new instance of {@link DescriptorImpl}.
     * In order to load the persisted global configuration, you have to 
     * call load() in the constructor.
     */
    public DescriptorImpl() {
        super(SwampPostBuild.class);
        load();
        XmlFile config_file = getConfigFile();
        if (config_file.exists() && hasRawCredentials(config_file)) {
            //config_file.delete();
            save();
            System.out.println("\n[WARNING]: SWAMP plugin requires re-configuration."
                    + "\nConfigure the plugin @ 'Manage Jenkins >> Configure System >> SWAMP'\n");
         }
        
        try {
            StandardUsernamePasswordCredentials credentials = getCredentials(null, credentialId);

            if (credentials != null) {
                SwampPostBuild.setSwampApi(login(credentials.getUsername(), 
                        Secret.toString(credentials.getPassword()), 
                        hostUrl));

                AssessmentInfo.setSwampApi(SwampPostBuild.getSwampApi());
            }

            loginFail = false;
        } catch (Exception e) {
            System.out.println("\n[ERROR]: Login to SWAMP failed! " + e.getMessage() + "\nCheck your credentials in the Global Configurations page.\n");
            e.printStackTrace(System.out);
            loginFail = true;
        }
    }

    /**
     * Performs on-the-fly validation of the form field 'password'.
     * @param value This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the browser.
     */
    public FormValidation doCheckHostUrl(@QueryParameter String value){
        
        if (value.length() == 0){
            return FormValidation.error("Please enter a host url.");
        }
        return FormValidation.ok();
    }

    public ListBoxModel doFillCredentialIdItems(@AncestorInPath Item item,
            @QueryParameter String credentialsId) {
        
        StandardListBoxModel result = new StandardListBoxModel();
       
        if (item == null) {
            if (!Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER)) {
                return result.includeCurrentValue(credentialsId);
            }
        } else if (!item.hasPermission(Item.EXTENDED_READ)
                && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
            return result.includeCurrentValue(credentialsId);
        }
        return result
                .includeEmptyValue()
                .includeAs(ACL.SYSTEM, item, StandardUsernamePasswordCredentials.class);
    }

    /**
     * Performs on-the-fly validation of the form field 'credentialId'.
     * @param credentialId This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the browser.
     */
    public FormValidation doCheckCredentialId(@AncestorInPath Item item,
            @QueryParameter String hostUrl,
            @QueryParameter String credentialId) {
        
        if (item == null) {
            if (!Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER)) { 
                return FormValidation.ok();
            }
        } else {
            if (!item.hasPermission(Item.EXTENDED_READ)
                    && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                return FormValidation.ok(); 
            }
        }

        if (StringUtils.isBlank(credentialId)) {
            return FormValidation.ok(); 
        }

        if (credentialId.startsWith("${") && credentialId.endsWith("}")) {
            return FormValidation.warning("Cannot validate expression based credentials");
        }

        if (CredentialsProvider.listCredentials(
                StandardUsernamePasswordCredentials.class,
                item,
                item instanceof Queue.Task ? Tasks.getAuthenticationOf((Queue.Task)item) : ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList(),
                        CredentialsMatchers.withId(credentialId)).isEmpty()) {
            return FormValidation.error("Cannot find currently selected credentials");
        }
        return FormValidation.ok();   
    }

    /**
     * Fills the project names list
     * @return a ListBoxModel containing the project names as strings
     */
    public ListBoxModel doFillDefaultProjectItems(@QueryParameter String hostUrl,
            @QueryParameter String credentialId) {
        
        ListBoxModel items = new ListBoxModel();
        try {
            StandardUsernamePasswordCredentials credentials = getCredentials(null, credentialId);

            if (credentials != null) {
                if (SwampPostBuild.getSwampApi() == null) {
                    SwampPostBuild.setSwampApi(login(credentials.getUsername(), 
                            Secret.toString(credentials.getPassword()),
                            hostUrl));
                }
                for (Project project : SwampPostBuild.getSwampApi().getProjectsList()) {
                    items.add(project.getFullName(), project.getUUIDString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            ListBoxModel error = new ListBoxModel();
            error.add("","null");
            return error;
        }
        return items;
    }

    /**
     * Performs on-the-fly validation of the tool.
     * @param value This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the browser.
     */
    public FormValidation doCheckDefaultProject(@QueryParameter String value){

        if (value == null || value.equals("")){
            return FormValidation.error("Select a project to be your global default");
        }
        if (value.equals("null")){
            return FormValidation.error("ERROR: could not log in.");
        }
        return FormValidation.ok();
    }

    /* Tests the connection using the credentials provided
     * @return validation of the test along with a message
     */
    public FormValidation doTestConnection(@AncestorInPath Item item,
            @QueryParameter String hostUrl,
            @QueryParameter String credentialId)/* throws IOException, ServletException */{
        try{

            StandardUsernamePasswordCredentials credential = getCredentials(item, credentialId);

            if (credential != null) {
                SwampPostBuild.setSwampApi(login(credential.getUsername(), 
                        Secret.toString(credential.getPassword()), 
                        hostUrl));
                return FormValidation.ok("Success");
            }else {
                return FormValidation.error("No credentials to authenticate");
            }
        }catch (SSLHandshakeException e) {
            e.printStackTrace(System.out);
            return FormValidation.error("\n[ERROR]: Server has a self-signed certificate");
        }catch (Exception e){       
            e.printStackTrace(System.out);
            return FormValidation.error("\n[ERROR]: "+ e.getMessage() + 
                    ". Check your credentials in the global configuration.");
        }
    }

    /**
     * Performs on-the-fly validation of the form field 'packageVersion'.
     * @param value This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the browser.
     */
    public FormValidation doCheckPackageVersion(@QueryParameter String value){
        if (value.length() == 0){
            return FormValidation.error("Please enter the version of your package.");
        }
        return FormValidation.ok();
    }

    /**
     * Performs on-the-fly validation of the form field 'packageLanguage'.
     * @param value This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the browser.
     */
    public FormValidation doCheckPackageLanguage(@QueryParameter String value){
        try {
        } catch (Exception e) {
            return FormValidation.error("Could not log in: "+ e.getMessage() + ". Check your credentials in the global configuration.");
        }
        String convertedLang = SwampPostBuild.getSwampApi().getPkgTypeString(value, "", "", null);
        if (convertedLang == null){
            return FormValidation.error("Language not supported");
        }
        return FormValidation.ok();
    }

    /**
     * Fills the languages list
     * @return a ListBoxModel containing the languages as strings
     */
    public ListBoxModel doFillPackageLanguageItems() {
        //TODO Get the list of valid languages from the SWAMP
        ListBoxModel items = new ListBoxModel();
        for (int i = 0; i < SwampPostBuild.VALID_LANGUAGES.length; i++){
            items.add(SwampPostBuild.VALID_LANGUAGES[i]);
        }
        return items;
    }
    /**
     * Guesses the language of the package
     */
    public String detectLanguage() {
        //TODO - detect language of package
        return "Java";
    }

    /**
     * Guesses the build of the package
     */
    public String detectBuildSystem() {
        //TODO - detect build system based on language or something
        return "ant";
    }

    /**
     * Fills the build system list
     * @return a ListBoxModel containing the build systems as strings
     */
    public ListBoxModel doFillBuildSystemItems(@QueryParameter String packageLanguage){
        ListBoxModel items = new ListBoxModel();
        HashMap<String,String[]> buildSystemsPerLanguage = SwampPostBuild.setupBuildSystemsPerLanguage();
        if (buildSystemsPerLanguage.containsKey(packageLanguage)){
            for (int i = 0; i < buildSystemsPerLanguage.get(packageLanguage).length; i++){
                items.add(buildSystemsPerLanguage.get(packageLanguage)[i]);
            }
        }else{
            items.add("","null");
        }
        return items;
    }

    /**
     * Performs on-the-fly validation of the form field 'buildSystem'.
     * @param value This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the browser.
     */
    public FormValidation doCheckBuildSystem(@QueryParameter String value){
        if (value.equals("null")){
            return FormValidation.error("Language not supported: Please select a valid language");
        }
        HashMap<String,String> defaultBuildFiles = SwampPostBuild.setupDefaultBuildFiles();
        if (!defaultBuildFiles.containsKey(value)){
            return FormValidation.warning("Under advanced settings, please enter the command used to clean your project.");
        }
        return FormValidation.ok();
    }

    /**
     * Fills the project names list
     * @return a ListBoxModel containing the project names as strings
     */
    public ListBoxModel doFillProjectUUIDItems() {
        ListBoxModel items = new ListBoxModel();
        //items.add(defaultProject);
        try {
            //SwampApiWrapper api = new SwampApiWrapper(HostType.DEVELOPMENT);
            //api.login(username, password);
            for (Project project : SwampPostBuild.getSwampApi().getProjectsList()) {
                items.add(project.getFullName(), project.getUUIDString());
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            ListBoxModel error = new ListBoxModel();
            error.add("ERROR: could not log in. Check your credentials in the global configuration.","null");
            return error;
        }
        return items;
    }


    /**
     * Gets the default name of the package from Jenkins
     */
    public String defaultPackageName(){
        //TODO detect default package name
        Jenkins instance = Jenkins.getInstance(); 
        if (instance != null){
            String jobName = instance.getDescriptor().getDescriptorFullUrl();
            jobName = jobName.substring(jobName.indexOf("job/") + 4);
            try {
                jobName = URLDecoder.decode(jobName.substring(0,jobName.indexOf('/')), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace(System.out);
            }
            return jobName;
        }
        return null;
    }

    /**
     * Gets the default host URL from the SWAMP
     */
    public String defaultHostUrl(){
        return SwampApiWrapper.SWAMP_HOST_NAME;
    }

    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
        // Indicates that this builder can be used with all kinds of project types 
        return true;
    }

    /**
     * This human readable name is used in the configuration screen.
     */
    public String getDisplayName() {
        return PLUGIN_DISPLAY_NAME;
    }

    static Proxy getProxy(String hostUrl) {
        Proxy proxy = new Proxy();

        URL url = null;
        try {
            url = new URL(hostUrl);
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace(System.out);
            return proxy;
        }

        @SuppressWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
        final Jenkins jenkins = Jenkins.getActiveInstance();

        if (jenkins != null && jenkins.proxy != null) { 

            ProxyConfiguration proxyConfig = jenkins.proxy;
            for (Pattern pattern : proxyConfig.getNoProxyHostPatterns()) {
                if (pattern.matcher(url.getHost()).matches()) {
                    System.out.println("[INFO]: SWAMP skipping proxy usage " + hostUrl);
                    return proxy;
                }
            }

            if (proxyConfig.name != null && !proxyConfig.name.equalsIgnoreCase("") &&
                    proxyConfig.port != -1 ) {

                proxy.setHost(proxyConfig.name);
                proxy.setPort(proxyConfig.port);
                if (proxyConfig.getUserName() != null && proxyConfig.getPassword() != null) {
                    proxy.setUsername(proxyConfig.getUserName());
                    proxy.setPassword(proxyConfig.getPassword());
                }
                proxy.setScheme("http");
                proxy.setConfigured(true);

            }
        }
        return proxy;
    }

    /**
     * Saves the global configuration for use during the build
     * @param req
     * @param formData
     * @return whether the configuration was successful
     */
    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
 
        hostUrl = formData.getString("hostUrl");
        credentialId = formData.getString("credentialId");
        defaultProject = formData.getString("defaultProject");
        verbose = formData.getBoolean("verbose");

        try {
            StandardUsernamePasswordCredentials credential = getCredentials(null, credentialId);

            if (credential != null) {
                SwampPostBuild.setSwampApi(login(credential.getUsername(), 
                        Secret.toString(credential.getPassword()), 
                        hostUrl));

                AssessmentInfo.setSwampApi(SwampPostBuild.getSwampApi());
            }
            loginFail = false;
        } catch (Exception e) {
            System.out.println("[ERROR]: Login to SWAMP failed! " + e.getMessage());
            e.printStackTrace(System.out);
            loginFail = true;
        }
        save();
        return super.configure(req, formData);
    }

    static SwampApiWrapper login (String username, String password, String hostUrl) throws Exception {
        SwampApiWrapper api = new SwampApiWrapper();
        Proxy proxy = getProxy(hostUrl);
        api.login(username, password, hostUrl, proxy, null);
        return api;
    }

    @Override
    public String getPluginName() {
        return PLUGIN_ID;
    }

    @Override
    public String getIconUrl() {
        return ICON_URL;
    }

    @Override
    public String getSummaryIconUrl() {
        return ICON_URL_PREFIX + "swamp-48x48.png";
    }


    public String getCredentialId() {
        return credentialId;
    }

    public String getHostUrl() {
        return hostUrl;
    }

    public String getDefaultProject() {
        return defaultProject;
    }

    public boolean getVerbose() {
        return verbose;
    }

    public boolean getLoginFail(){
        return loginFail;
    }


}