
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
import java.net.URLDecoder;
import java.util.Iterator;

import jenkins.model.Jenkins;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Descriptor.FormException;
import hudson.plugins.analysis.core.PluginDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;


//import org.continuousassurance.swamp.Messages;
import org.continuousassurance.swamp.api.Project;
import org.continuousassurance.swamp.cli.SwampApiWrapper;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Descriptor for the class {@link SwampPostBuild}. Used as a singleton. The
 * class is marked as public so that it can be accessed from views.
 *
 * @author Ulli Hafner
 */
@Extension(ordinal = 100)
public final class DescriptorImpl extends PluginDescriptor {
    /** The ID of this plug-in is used as URL. */
    static final String PLUGIN_ID = "swamp";
    
    static final String DISPLAY_NAME = "SWAMP Assessment";
    /** The URL of the result action. */
    static final String RESULT_URL = PluginDescriptor.createResultUrlName(PLUGIN_ID);
    /** Icons prefix. */
    static final String ICON_URL_PREFIX = "/plugin/swamp/icons/";
    /** Icon to use for the result and project action. */
    static final String ICON_URL = ICON_URL_PREFIX + "swamp-24x24.png";

    private String username;
	private String password;
	private String hostUrl;
	private String defaultProject;
	private SwampApiWrapper api;
	private boolean loginFail = false;
	private boolean verbose;
	private boolean runOnFail;
	private boolean backgroundAssess;
    /**
     * Creates a new instance of {@link DescriptorImpl}.
     * In order to load the persisted global configuration, you have to 
     * call load() in the constructor.
     */
    public DescriptorImpl() {
        super(SwampPostBuild.class);
        load();
	    try {
	    	api = login(username, password, hostUrl);
			//api = new SwampApiWrapper(HostType.DEVELOPMENT);
        	//api.login(username, password);
		    AssessmentInfo.setApi(api);
			loginFail = false;
		} catch (Exception e) {
			System.out.println("\n[ERROR]: Login to SWAMP failed! " + e.getMessage() + "\nCheck your credentials in the Global Configurations page.\n");
			loginFail = true;
		}
    	/*AssessmentInfo.setUsername(username);
	    AssessmentInfo.setPassword(password);*/
    }
    /**
     * Performs on-the-fly validation of the form field 'username'.
     * @param value This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the browser.
     */
    public FormValidation doCheckUsername(@QueryParameter String value){
        if (value.length() == 0){
            return FormValidation.error("Please enter your username.");
        }
        return FormValidation.ok();
    }

    /**
     * Performs on-the-fly validation of the form field 'password'.
     * @param value This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the browser.
     */
    public FormValidation doCheckPassword(@QueryParameter String value){
        if (value.length() == 0){
            return FormValidation.error("Please enter your password.");
        }
        return FormValidation.ok();
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
    	//SwampApiWrapper api;
		try {
			//api = new SwampApiWrapper(HostType.DEVELOPMENT);
        	//api.login(username, password);
		} catch (Exception e) {
			return FormValidation.error("Could not log in: "+e.getMessage() + ". Check your credentials in the global configuration.");
		}
    	String convertedLang = api.getPkgTypeString(value, "", "", null);
    	if (convertedLang == null){
    		return FormValidation.error("Language not supported");
    	}
    	return FormValidation.ok();
    }

    /** TODO Language fix when it comes out
     * Fills the languages list
     * @return a ListBoxModel containing the languages as strings
     */
    public ListBoxModel doFillPackageLanguageItems() {
    	ListBoxModel items = new ListBoxModel();
        //items.add(detectLanguage());
		/*
    	SwampApiWrapper api;
		try {
			api = new SwampApiWrapper(HostType.DEVELOPMENT);
		} catch (Exception e) {
			ListBoxModel error = new ListBoxModel();
    		error.add("ERROR: Could not load languages: " + e.getMessage() + " Please verify your username and password, delete this field, and retry.","null");
    		return error;
		}
		api.login(username, password);
        Iterator<String> validLanguages = api.getPackageTypes().keySet().iterator();
        while (validLanguages.hasNext()){
        	items.add(validLanguages.next());
        }
        */
    	for (int i = 0; i < SwampPostBuild.VALID_LANGUAGES.length; i++){
    		items.add(SwampPostBuild.VALID_LANGUAGES[i]);
    	}
        //items.sort(null);
        return items;
    }
    
    public String detectLanguage() {
    	//TODO - detect language of package
    	return "Java";
    }
    
    public String detectBuildSystem() {
    	//TODO - detect build system based on language or something
    	return "ant";
    }
    
    /**
     * Tests the connection using the credentials provided
     * @return validation of the test along with a message
     */
    public FormValidation doTestConnection(@QueryParameter String username, @QueryParameter String password,  @QueryParameter String hostUrl)/* throws IOException, ServletException */{
    	try{
    		api = login(username, password, hostUrl);
    		//SwampApiWrapper api = new SwampApiWrapper(HostType.DEVELOPMENT);
    		//api.login(username, password);
    		return FormValidation.ok("Success");
    	}catch (Exception e){
    		return FormValidation.error("Client error: "+e.getMessage() + ". Check your credentials in the global configuration.");
    	}
    }
    /**
     * Fills the build system list
     * @return a ListBoxModel containing the build systems as strings
     */
    public ListBoxModel doFillBuildSystemItems(@QueryParameter String packageLanguage){
        ListBoxModel items = new ListBoxModel();
        SwampPostBuild.buildSystemsPerLanguage = SwampPostBuild.setupBuildSystemsPerLanguage();
        if (SwampPostBuild.buildSystemsPerLanguage.containsKey(packageLanguage)){
        	for (int i = 0; i < SwampPostBuild.buildSystemsPerLanguage.get(packageLanguage).length; i++){
        		items.add(SwampPostBuild.buildSystemsPerLanguage.get(packageLanguage)[i]);
        	}
        }else{
        	items.add("","null");
        }
        /*
        if (packageLanguage != null){
            items.add(detectBuildSystem());
    	}
        for (int i = 0; i < VALID_BUILD_SYSTEMS.length; i++){
        	items.add(VALID_BUILD_SYSTEMS[i]);
    	}
    	*/
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
    	SwampPostBuild.defaultBuildFiles = SwampPostBuild.setupDefaultBuildFiles();
    	if (!SwampPostBuild.defaultBuildFiles.containsKey(value)){
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
    		Iterator<Project> myProjects = api.getProjectsList().iterator();
    		while(myProjects.hasNext()){
    			Project nextProject = myProjects.next();
    			items.add(nextProject.getFullName(),nextProject.getUUIDString());
    		}
		} catch (Exception e) {
			ListBoxModel error = new ListBoxModel();
    		error.add("ERROR: could not log in. Check your credentials in the global configuration.","null");
    		return error;
		}
    	return items;
    }
    
    /**
     * Fills the project names list
     * @return a ListBoxModel containing the project names as strings
     */
    public ListBoxModel doFillDefaultProjectItems(@QueryParameter String username, @QueryParameter String password,  @QueryParameter String hostUrl) {
        ListBoxModel items = new ListBoxModel();
    	try {
    		api = login(username, password, hostUrl);
    		//SwampApiWrapper api = new SwampApiWrapper(HostType.DEVELOPMENT);
    		//api.login(username, password);
    		Iterator<Project> myProjects = api.getProjectsList().iterator();
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
    
    public String defaultPackageName(){
    	//TODO detect default package name
    	String jobName = Jenkins.getInstance().getDescriptor().getDescriptorFullUrl();
    	jobName = jobName.substring(jobName.indexOf("job/") + 4);
    	try {
			jobName = URLDecoder.decode(jobName.substring(0,jobName.indexOf('/')), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
    	return jobName;
    }
    
    public String defaultHostUrl(){
    	return SwampApiWrapper.SWAMP_HOST_NAMES_MAP.get(SwampApiWrapper.HostType.PRODUCTION);
    }
    
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
        // Indicates that this builder can be used with all kinds of project types 
        return true;
    }

    /**
     * This human readable name is used in the configuration screen.
     */
    public String getDisplayName() {
        return DISPLAY_NAME;
    }
    
    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        // To persist global configuration information,
        // set that to properties and call save().
    	username = formData.getString("username");
    	password = formData.getString("password");
    	hostUrl = formData.getString("hostUrl");
    	defaultProject = formData.getString("defaultProject");
    	verbose = formData.getBoolean("verbose");
    	runOnFail = formData.getBoolean("runOnFail");
    	backgroundAssess = formData.getBoolean("backgroundAssess");
    	api = null;
        //useFrench = formData.getBoolean("useFrench");
        // ^Can also use req.bindJSON(this, formData);
        //  (easier when there are many fields; need set* methods for this, like setUseFrench)
        try {
        	api = login(username, password, hostUrl);
			//api = new SwampApiWrapper(HostType.DEVELOPMENT);
		    AssessmentInfo.setApi(api);
    		//api.login(username, password);
			loginFail = false;
		} catch (Exception e) {
			System.out.println("[ERROR]: Login to SWAMP failed! " + e.getMessage());
			loginFail = true;
		}
        save();
    	/*AssessmentInfo.setUsername(username);
	    AssessmentInfo.setPassword(password);*/
        return super.configure(req,formData);
    }
    
    static SwampApiWrapper login (String username, String password, String hostUrl) throws Exception {
    	SwampApiWrapper api = new SwampApiWrapper(SwampApiWrapper.HostType.CUSTOM,hostUrl);
    	api.login(username, password);
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
    
    public String getPassword() {
		return password;
	}
    
    public String getUsername() {
		return username;
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
    
    public boolean getRunOnFail(){
    	return runOnFail;
    }
    
    public boolean getBackgroundAssess(){
    	return backgroundAssess;
    }
    
    public boolean getLoginFail(){
    	return loginFail;
    }
    
    
}