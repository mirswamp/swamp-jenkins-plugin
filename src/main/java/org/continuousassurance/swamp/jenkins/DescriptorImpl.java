
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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Descriptor.FormException;
import hudson.model.queue.Tasks;
import hudson.plugins.analysis.core.PluginDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

import hudson.security.ACL;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;

import org.apache.commons.lang3.StringUtils;
//import org.continuousassurance.swamp.Messages;
import org.continuousassurance.swamp.api.Project;
import org.continuousassurance.swamp.cli.SwampApiWrapper;
import org.continuousassurance.swamp.session.util.Proxy;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

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

    private String username;
    private String password;
    private String hostUrl;
    private String credentialId;
    private String defaultProject;
    private boolean loginFail = false;
    private boolean verbose;

    /**
     * Creates a new instance of {@link DescriptorImpl}.
     * In order to load the persisted global configuration, you have to 
     * call load() in the constructor.
     */
    public DescriptorImpl() {
        super(SwampPostBuild.class);
        load();
        try {
            SwampPostBuild.setSwampApi(login(username, password, hostUrl));
            AssessmentInfo.setSwampApi(SwampPostBuild.getSwampApi());
            loginFail = false;
        } catch (Exception e) {
            System.out.println("\n[ERROR]: Login to SWAMP failed! " + e.getMessage() + "\nCheck your credentials in the Global Configurations page.\n");
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

    /**
     * Tests the connection using the credentials provided
     * @return validation of the test along with a message
     */
    public FormValidation doTestConnection(@AncestorInPath Item item,
            @QueryParameter String hostUrl,
             @QueryParameter String credentialId)/* throws IOException, ServletException */{
        try{
            System.out.println("[INFO]: SWAMP credentialId " + credentialId);
            System.out.println("[INFO]: SWAMP item " + item);
            
            StandardUsernamePasswordCredentials credential = null;

            List<StandardUsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentials(
                    StandardUsernamePasswordCredentials.class, item, ACL.SYSTEM, Collections.<DomainRequirement> emptyList());

            IdMatcher matcher = new IdMatcher(credentialId);
            for (StandardUsernamePasswordCredentials c : credentials) {
                if (matcher.matches(c)) {
                    credential = c;
                }
            }
            
            System.out.println("[INFO]: SWAMP username " + credential.getUsername());
            System.out.println("[INFO]: SWAMP item " + credential.getPassword().getPlainText());
            
            SwampPostBuild.setSwampApi(login(credential.getUsername(), 
                    credential.getPassword().getPlainText(), 
                    hostUrl));
            return FormValidation.ok("Success");
        }catch (Exception e){
            return FormValidation.error("Client error: "+ e.getMessage() + ". Check your credentials in the global configuration.");
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
            ListBoxModel error = new ListBoxModel();
            error.add("ERROR: could not log in. Check your credentials in the global configuration.","null");
            return error;
        }
        return items;
    }

    public ListBoxModel doFillCredentialIdItems(@AncestorInPath Item item,
            @QueryParameter String credentialsId) {
        return new StandardListBoxModel()
                .withEmptySelection()
                .withMatching(
                        CredentialsMatchers.anyOf(
                                CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class)
                                ),
                        CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class,
                                item,
                                ACL.SYSTEM,
                                Collections.<DomainRequirement>emptyList())
                        );
    }

    /**
     * Performs on-the-fly validation of the form field 'username'.
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
            SwampPostBuild.setSwampApi(login(username, password, hostUrl));
            Iterator<Project> myProjects = SwampPostBuild.getSwampApi().getProjectsList().iterator();
            while(myProjects.hasNext()){
                Project nextProject = myProjects.next();
                items.add(nextProject.getFullName(), nextProject.getUUIDString());
            }
        } catch (Exception e) {
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
                e.printStackTrace();
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
        
        System.out.println("[INFO]: SWAMP url " + hostUrl);
        URL url = null;
        try {
            url = new URL(hostUrl);
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
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
        System.out.println(formData);
        username = formData.getString("username");
        password = formData.getString("password");
        hostUrl = formData.getString("hostUrl");
        defaultProject = formData.getString("defaultProject");
        verbose = formData.getBoolean("verbose");

        try {
            SwampPostBuild.setSwampApi(login(username, password, hostUrl));
            AssessmentInfo.setSwampApi(SwampPostBuild.getSwampApi());
            loginFail = false;
        } catch (Exception e) {
            System.out.println("[ERROR]: Login to SWAMP failed! " + e.getMessage());
            loginFail = true;
        }
        save();
        return super.configure(req,formData);
    }

    static SwampApiWrapper login (String username, String password, String hostUrl) throws Exception {
        SwampApiWrapper api = new SwampApiWrapper();
        Proxy proxy = getProxy(hostUrl);
        api.login(username, password, hostUrl, proxy);
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

    /*public boolean getBackgroundAssess(){
    	return backgroundAssess;
    }*/

    public String getUsername(){
        return username;
    }
    
    public String getPassword(){
        return password;
    }
    
    public boolean getLoginFail(){
        return loginFail;
    }


}