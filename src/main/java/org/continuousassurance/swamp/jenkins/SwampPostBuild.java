
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
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.PluginWrapper;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.Action;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.analysis.core.FilesParser;
import hudson.plugins.analysis.core.ParserResult;
import hudson.plugins.analysis.core.PluginDescriptor;
import hudson.plugins.analysis.views.DetailFactory;
import hudson.plugins.tasks.MavenInitialization;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

import org.apache.maven.plugin.MojoExecution;

import net.sf.json.JSONObject;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import org.continuousassurance.swamp.api.AssessmentRecord;
import org.continuousassurance.swamp.api.Platform;
import org.continuousassurance.swamp.api.Project;
import org.continuousassurance.swamp.api.Tool;
import org.continuousassurance.swamp.cli.SwampApiWrapper;
import org.continuousassurance.swamp.cli.SwampApiWrapper.HostType;
import org.continuousassurance.swamp.cli.exceptions.InvalidIdentifierException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class SwampPostBuild extends Recorder implements SimpleBuildStep {

	
	private static final String[] VALID_LANGUAGES = {/*"ActionScript","Ada","AppleScript","Assembly",
		"Bash",*/"C",/*"C#",*/"C++",/*"Cobol","ColdFusion",*/"CSS",/*"D","Datalog","Erlang",
		"Forth","Fortran","Haskell",*/"HTML","Java","JavaScript",/*"LISP","Lua","ML",
		"OCaml","Objective-C",*/"PHP",/*"Pascal",*/"Perl",/*"Prolog",*/"Python","Python-2","Python-3",
		/*"Rexx",*/"Ruby",/*"sh","SQL","Scala","Scheme","SmallTalk","Swift","Tcl","tcsh","Visual-Basic"*/};
		
	private static final String[] VALID_BUILD_SYSTEMS = {"android+ant","android+ant+ivy","android+gradle","android+maven",
		"ant","ant+ivy","cmake+make","configure+make","gradle","java-bytecode","make","maven",
		"no-build","none","other","python-distutils"};
	
	private static HashMap<String,String> defaultBuildFiles;
	
	private static HashMap<String,String> setupDefaultBuildFiles () {
		HashMap<String,String> defaults = new HashMap<String,String>();
		defaults.put("ant", "build.xml");
		defaults.put("maven", "pom.xml");
		defaults.put("make", "GNUmakefile,makefile,Makefile");
		defaults.put("gradle", "build.gradle");
		defaults.put("ivy", "build.xml");
		defaults.put("rake", "Rakefile,rakefile");
		defaults.put("bundler", "Gemfile,gemfile");
		return defaults;
	}
	
	private static HashMap<String,String[]> buildSystemsPerLanguage;
	
	private static final String[] C_BUILD_SYSTEMS = {"cmake+make","configure+make","make","no-build","other"};
	private static final String[] JAVA_BUILD_SYSTEMS = {"ant","ant+ivy","gradle","maven","no-build","android+ant","android+gradle","android+maven"};
	private static final String[] PYTHON_BUILD_SYSTEMS = {"python-distutils","no-build","other"};
	private static final String[] RUBY_BUILD_SYSTEMS = {"bundler","bundler+rake","bundler+other","rake","no-build","other","rubygem"};
	
	private static HashMap<String,String[]> setupBuildSystemsPerLanguage () {
		HashMap<String,String[]> defaults = new HashMap<String,String[]>();
		defaults.put("C", C_BUILD_SYSTEMS);
		defaults.put("C++", C_BUILD_SYSTEMS);
		defaults.put("Java", JAVA_BUILD_SYSTEMS);
		defaults.put("Python-2", PYTHON_BUILD_SYSTEMS);
		defaults.put("Python-3", PYTHON_BUILD_SYSTEMS);
		defaults.put("Ruby", RUBY_BUILD_SYSTEMS);
		return defaults;
	}
	
	private final String username;
	private final String password;
	private final String hostUrl;
	private final String projectUUID;
	private final String packageName;
	private final List<AssessmentInfo> assessmentInfo;
	private final String packageVersion;
	private final String packageDir;
	private final String packageLanguage;
	private final String buildSystem;
	private final String buildDirectory;
	private final String buildFile;
	private final String buildTarget;
	private final String buildCommand;
	private final String buildOptions;
	private final String cleanCommand;
	private final boolean outputToFile;
	private final String outputDir;
	private final boolean sendEmail;
	private final String email;
	
	private String projectName;
	private String archiveName;
	
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public SwampPostBuild(String projectUUID, List<AssessmentInfo> assessmentInfo, String packageName, String packageVersion, String packageDir, String packageLanguage, String buildSystem, String buildDirectory, String buildFile, String buildTarget, String buildCommand, String buildOptions, String outputDir, boolean sendEmail, boolean outputToFile, String email, String cleanCommand) {
        this.username = getDescriptor().getUsername();
        this.password = getDescriptor().getPassword();
        this.hostUrl = getDescriptor().getHostUrl();
        this.projectUUID = projectUUID;
        this.assessmentInfo = assessmentInfo;
        this.packageName = packageName;
        this.packageDir = packageDir;
        this.packageLanguage = packageLanguage;
        this.buildSystem = buildSystem;
        this.buildDirectory = buildDirectory;
        this.buildFile = buildFile;
        this.buildTarget = buildTarget;
        this.buildCommand = buildCommand;
        this.buildOptions = buildOptions;
    	this.sendEmail = sendEmail;
    	this.outputToFile = outputToFile;
    	this.email = email;
    	this.packageVersion = packageVersion;
        if (outputDir != null && outputDir.equals("")){
    		outputDir = "Assessment_Output";
    	}
        this.outputDir = outputDir;
    	if (cleanCommand != null && cleanCommand.equals("")){
    		if (buildSystem.equals("maven")){
    			cleanCommand = "mvn clean";
    		}else{
    			cleanCommand = buildSystem + " clean";
    		}
    	}
    	this.cleanCommand = cleanCommand;
    }
    
    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) {
    	//If the build failed, exit
    	if (!getDescriptor().getRunOnFail() && build.getResult().isWorseOrEqualTo(Result.FAILURE)){
    		if (getDescriptor().verbose){
    			listener.fatalError("Build failed: no point in sending to the SWAMP");
    		}
    		return;
    	}
    	//If the login failed, exit
    	if (getDescriptor().loginFail){
    		listener.fatalError("Login failed: check your credentials in the global configuration");
    		return;
    	}
    	//Error check build info
    	if (!checkBuild(workspace,listener)){
    		return;
    	}
    	
    	//Sets up some additional configuration options
    	DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss");
    	String uploadVersion = packageVersion;
    	if (uploadVersion.contains("$build")){
    		uploadVersion = uploadVersion.replace("$build", build.getId());
    	}
    	if (uploadVersion.contains("$date")){
    		uploadVersion = uploadVersion.replace("$date", dateFormat.format(new Date()));
    	}
		if (uploadVersion.contains("$git")){
			try {
				EnvVars buildVars = new EnvVars();
				buildVars = build.getEnvironment(listener);
				if (!buildVars.containsKey("GIT_COMMIT")){
					uploadVersion = uploadVersion.replace("$git", "");
					listener.error("Git commit not available. Replacing with blank string.");
				}else{
					uploadVersion = uploadVersion.replace("$git", buildVars.get("GIT_COMMIT"));
				}
			} catch (IOException | InterruptedException e) {
				uploadVersion = uploadVersion.replace("$git", "");
				listener.error("Error getting git commit: " + e.getMessage() + ". Replacing with blank string.");
			}
		}
		if (uploadVersion.contains("$svn")){
			try {
				EnvVars buildVars = new EnvVars();
				buildVars = build.getEnvironment(listener);
				if (!buildVars.containsKey("SVN_REVISION")){
					uploadVersion = uploadVersion.replace("$svn", "");
					listener.error("Subversion commit not available. Replacing with blank string.");
				}else{
					uploadVersion = uploadVersion.replace("$svn", buildVars.get("SVN_REVISION"));
				}
			} catch (IOException | InterruptedException e) {
				uploadVersion = uploadVersion.replace("$svn", "");
				listener.error("Error getting subversion commit: " + e.getMessage() + ". Replacing with blank string.");
			}
		}
		if (uploadVersion.contains("/")){
			uploadVersion.replaceAll("/", "-");
		}
		if (uploadVersion.contains("\\")){
			uploadVersion.replaceAll("\\", "-");
		}
    	
    	archiveName = packageName + "-" + uploadVersion + ".zip";
		//String jenkinsVersion = Jenkins.VERSION;
		//String swampPluginVersion = Jenkins.getInstance().pluginManager.getPlugin("Swamp").getVersion();
    	
    	//Login to the SWAMP
        SwampApiWrapper api;
    	try {
    		api = login(username, password, hostUrl);
			//api = new SwampApiWrapper(HostType.DEVELOPMENT);
    		//api.login(username, password);
		} catch (Exception e) {
			listener.fatalError("Error logging in: " + e.getMessage() + ". Check your credentials in the global configuration.");
			//build.setResult(Result.FAILURE);
			return;
		}
    	
    	//Get project, tool, and platform uuids given the names
    	try {
			getUUIDsFromNames(api,listener);
		} catch (Exception e) {
			//build.setResult(Result.FAILURE);
			return;
		}
    	
    	//Duplicate the workspace for cleaning
    	FilePath tempPath = new FilePath(workspace,(packageDir.equals("") ? ".TempPackage" : packageDir + ".TempPackage"));
    	try {
    		tempPath.mkdirs();
			workspace.copyRecursiveTo(tempPath);
		} catch (IOException | InterruptedException e) {
			listener.fatalError("Could not create temporary workspace: " + e.getMessage());
		}
    	
    	//Clean the package
    	try {
    		if (getDescriptor().getVerbose()){
    			listener.getLogger().println("Executing " + cleanCommand);
    		}
			ProcStarter starter = launcher.launch().cmdAsSingleString(cleanCommand).pwd(tempPath).envs(build.getEnvironment(listener)).stdout(listener);
			Proc proc = launcher.launch(starter);
			int retcode = proc.join();
			if (getDescriptor().getVerbose()){
    			listener.getLogger().println("Return code of " + cleanCommand + " is " + retcode);
			}
		} catch (IOException | InterruptedException e) {
			listener.error("Invalid clean command " + cleanCommand + ": " + e.getMessage());
		}
    	
    	String packageUUID;
		FilePath archivePath;
		// Zip the package
		try {
			archivePath = zipPackage(workspace, listener);
			tempPath.deleteRecursive();
		} catch (Exception e) {
			//build.setResult(Result.FAILURE);
			return;
		}
		
		// Create a config file for submission
		FilePath configPath;
		try {
			configPath = writeConfFile(workspace, listener, uploadVersion);
		} catch (Exception e) {
			//build.setResult(Result.FAILURE);
			return;
		}
		// Upload the package
		try {
			listener.getLogger().println(configPath.getRemote() + ", " + archivePath.getRemote() + ", " + projectUUID + ", "  + api.getConnectedHostName());
			packageUUID = api.uploadPackage(configPath.getRemote(),
					archivePath.getRemote(), projectUUID, false);
		} catch (InvalidIdentifierException e) {
			listener.fatalError("Could not upload Package: "
					+ e.getMessage());
			//build.setResult(Result.FAILURE);
			return;
		}
		if (getDescriptor().getVerbose()){
			listener.getLogger().println("Package Uploaded. UUID id " + packageUUID);
		}
		//*Delete the package archive and config file since they are no longer needed
		try {
			configPath.delete();
			archivePath.delete();
		} catch (IOException e) {
			listener.fatalError("Deletion of straggler files failed: " + e.getMessage());
			//build.setResult(Result.FAILURE);
			return;
		} catch (InterruptedException e) {
			listener.fatalError("Deletion of straggler files interrupted: " + e.getMessage());
			//build.setResult(Result.FAILURE);
			return;
		}
		
		//Deal with the "all" option
		List<AssessmentInfo> assessmentsToRun = new ArrayList<AssessmentInfo>();
		Iterator<AssessmentInfo> assessmentCheck = assessmentInfo.iterator();
		while (assessmentCheck.hasNext()){
			AssessmentInfo nextAssess = assessmentCheck.next();
			for (String nextTool : nextAssess.getToolUUID().split(",")){
				AssessmentInfo newAssess = new AssessmentInfo(nextTool,nextAssess.getPlatformUUID());
				assessmentsToRun.add(newAssess);
			}
		}
		
		String[] assessmentUUIDs;
		//Run the assessments
		assessmentUUIDs = new String[assessmentsToRun.size()];
		for (int i = 0; i < assessmentUUIDs.length;i++){
			try {
				if (getDescriptor().getVerbose()){
	    			listener.getLogger().println("Assessing with package " + packageName + ", project " + projectName + ", tool " + assessmentsToRun.get(i).getToolName(api,projectUUID) + ", and platform " + assessmentsToRun.get(i).getPlatformName(api));
				}
				assessmentUUIDs[i] = api.runAssessment(packageUUID, assessmentsToRun.get(i).getToolUUID(), projectUUID, assessmentsToRun.get(i).getPlatformUUID());
			} catch (Exception e) {
				listener.fatalError("Assessment failed: " + e.getMessage());
			}
		}
		
		if (sendEmail){
			//TODO Email results when complete
		}
		
		if (!getDescriptor().getBackgroundAssess() || outputToFile){
			FilePath outputPath = new FilePath(workspace, outputDir);
			try {
				outputPath.mkdirs();
			} catch (IOException | InterruptedException e) {
				listener.fatalError("Could not create output directory: " + e.getMessage());
				//build.setResult(Result.FAILURE);
			}
	    	//For each assessment
			Project project = api.getProject(projectUUID);
			for (int i = 0; i < assessmentUUIDs.length; i++){
				AssessmentRecord assessmentRecord = null;
				String assessmentResults = "null";
				String previousStatus = null;
				String assessmentName;
				try {
					assessmentName = "swampXml.xml";
					//assessmentName = ("Assessment-" + packageName + "-" + uploadVersion + "-" + assessmentsToRun.get(i).getToolName(api,projectUUID) + "-" + assessmentsToRun.get(i).getPlatformName(api)).replace(' ', '_');
				} catch (Exception e) {
					listener.fatalError("Tool / Platform missing unexpectedly: " + e.getMessage());
					return;
				}
				//Wait until the assessment is complete
				while (assessmentResults.equals("null")){
					assessmentRecord = null;
					for(AssessmentRecord executionRecord : api.getAllAssessmentRecords(project)) {
						if (executionRecord.getAssessmentRunUUID().equals(assessmentUUIDs[i])){
							assessmentRecord = executionRecord;
						}
					}
					if (assessmentRecord == null){
						listener.fatalError("AssessmentRun " + assessmentUUIDs[i] + " not found");
						//build.setResult(Result.FAILURE);
						return;
					}
					assessmentResults = assessmentRecord.getAssessmentResultUUID();
					if (!assessmentRecord.getStatus().equals(previousStatus)){
						listener.getLogger().println("Waiting on assessment " + assessmentName + ", Status is " + assessmentRecord.getStatus() + ", Results UUID is " + assessmentResults);
						previousStatus = assessmentRecord.getStatus();
					}
					if (assessmentResults.equals("null")){
						try {
							Thread.sleep(30000);
						} catch (InterruptedException e) {
							listener.fatalError("Waiting for status interrupted: " + e.getMessage());
							//build.setResult(Result.FAILURE);
							return;
						}
					}
				}
				if (outputToFile){
					//Save the assessment to the requested spot
					FilePath newFile = new FilePath(workspace,outputDir + "/" + assessmentName.replace("/", "-"));
					try {
						int fileNum = 0;
						while (newFile.exists()){
							fileNum++;
							newFile = new FilePath(workspace,outputDir + "/" + assessmentName.replace("/", "-") + "(" + fileNum + ")");
						}
					} catch (IOException | InterruptedException e) {
						listener.fatalError("Failed to check files: " + e.getMessage());
						//build.setResult(Result.FAILURE);
						return;
					}
					if (getDescriptor().getVerbose()){
		    			listener.getLogger().println("Assessment finished. Writing results to " + newFile.getRemote());
					}
					api.getAssessmentResults(projectUUID, assessmentResults, newFile.getRemote());
				}
			}
		}
		/*SwampDetailFactory detailBuilder = new SwampDetailFactory();
		DetailFactory.addDetailBuilder(SwampBuildResultAction.class, detailBuilder);
		if (PluginDescriptor.isMavenPluginInstalled()) {
            MavenInitialization.run(detailBuilder);
        }
		System.out.println(Jenkins.getInstance().getRawWorkspaceDir());
		System.out.println(Jenkins.getInstance().getRawBuildsDir());
		try {
			FilePath buildDir = new FilePath(workspace,"target/swampXml.xml");
			System.out.println(buildDir.getRemote());
			FilePath testXmlDir = new FilePath(new File("/p/swamp/home/sweetland/swampXml.xml"));
			buildDir.copyFrom(testXmlDir);
			System.out.println("Sucess!");
		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			System.out.println("Failure...");
			e.printStackTrace();
		}*/

		//Log out
		if (getDescriptor().getVerbose()){
			listener.getLogger().println("Logging out");
		}
		api.logout();
    	
    }

    private boolean checkBuild (FilePath workspace, TaskListener listener){
    	FilePath buildFile;
    	if (this.buildFile.equals("")){
        	defaultBuildFiles = setupDefaultBuildFiles();
    		if (!defaultBuildFiles.containsKey(buildSystem)){
    			listener.getLogger().println("[Warning] could not verify build file for " + buildSystem);
    			return true;
    		}
    		for (String nextPossibleName : defaultBuildFiles.get(buildSystem).split(",")){
    			buildFile = new FilePath(workspace,(buildDirectory.equals("") ? "" : buildDirectory + "/") + nextPossibleName);
        		try {
        			if (buildFile.exists()){
        				return true;
        			}
    			} catch (IOException | InterruptedException e) {
    				listener.fatalError("Could not verify build file's existance: " + e.getMessage());
    			}
    		}
			listener.fatalError("Build file " + defaultBuildFiles.get(buildSystem) + " not found at " + workspace.getRemote() + (buildDirectory.equals("") ? "" : buildDirectory + "/"));
			return false;
    	}else{
    		buildFile = new FilePath(workspace,(buildDirectory.equals("") ? "" : buildDirectory + "/") + this.buildFile);
    		try {
    			if (!buildFile.exists()){
    				listener.fatalError("Build file " + buildFile.getRemote() + " not found.");
    				return false;
    			}
			} catch (IOException | InterruptedException e) {
				listener.fatalError("Could not verify build file's existance: " + e.getMessage());
			}
    	}
    	return true;
    }
    
    /**
     * Gets the UUIDs from the names of the projects, tools, and platforms
     * @param api the api that we are logged into
     * @param listener the listener in Jenkins for any output of errors
     * @throws Exception if something goes wrong, details will be printed to the listener and a generic exception will be thrown
     */
    private void getUUIDsFromNames (SwampApiWrapper api, TaskListener listener) throws Exception{
    	//Get project UUID
    	if (getDescriptor().getVerbose()){
			listener.getLogger().println("Retrieving IDs from names");
    	}
    	boolean allGood = true;
    	try {
    		Project myProject = api.getProject(projectUUID);
    		if (myProject == null){
    			listener.fatalError("Project " + projectName + " does not exist.");
    			allGood = false;
    		}else{
    			projectName = myProject.getFullName();
    		}
    	}catch (Exception e) {
			listener.fatalError("Could not retrieve project list: " + e.getMessage());
			throw new Exception();
    	}
    	try {
    		Iterator<AssessmentInfo> myInfo = assessmentInfo.iterator();
    		while (myInfo.hasNext()){
    			AssessmentInfo nextAssess = myInfo.next();
    			if (getDescriptor().getVerbose()){
        			listener.getLogger().println("Verifying " + nextAssess.getAssessmentInfo(api, projectUUID));
    			}
    		}
    	}catch (Exception e){
    		listener.fatalError("Some tools or platforms are invalid: " + e.getMessage());
			allGood = false;
    	}
		if (!allGood){
			throw new Exception();
		}
		
    }
    /**
     * Zips the package for uploading
     * @param workspace the directory of the workspace
     * @param listener the listener in Jenkins for any output of errors
     * @return the file path to the output folder (archiveName must be added on)
     * @throws Exception if something goes wrong, details will be printed to the listener and a generic exception will be thrown
     */
    private FilePath zipPackage(FilePath workspace, TaskListener listener) throws Exception{
    	//Sets up the temporary directory to store the package
    	FilePath packagePath = new FilePath(workspace,(packageDir.equals("") ? ".TempPackage" : packageDir + ".TempPackage"));
		FilePath archivePath = new FilePath(workspace,archiveName);
    	//Zips the archive and moves it to the output directory
		try {
			OutputStream stream = archivePath.write();
			packagePath.zip(stream, "**");

			if (getDescriptor().getVerbose()){
    			listener.getLogger().println("Archive created at " + archivePath.getRemote());
			}
		} catch (IOException e) {
			listener.fatalError("Archive creation failed: " + e.getMessage());
			throw new Exception();
		} catch (InterruptedException e) {
			listener.fatalError("Archive creation interrupted: " + e.getMessage());
			throw new Exception();
		}

		return archivePath;
		
    }
    /**
     * Writes a configuration file for the package to be uploaded
     * @param workspace directory of the workspace of the package
     * @param listener the listener in Jenkins for any output of errors
     * @return the filepath to the config file
     * @throws Exception if something goes wrong, details will be printed to the listener and a generic exception will be thrown
     */
    private FilePath writeConfFile (FilePath workspace, TaskListener listener, String uploadVersion) throws Exception{
    	//Retrieves the MD5 and SHA-512 of the archive
    	FilePath archivePath = new FilePath(workspace,archiveName);
    	FilePath configPath = new FilePath(workspace,"package.conf");
    	String md5hash;
		String sha512hash;
		try {
			md5hash = archivePath.digest();
			if (getDescriptor().getVerbose()){
    			listener.getLogger().println("MD5: " + md5hash);
			}
			sha512hash = getSHA512(archivePath);
			if (getDescriptor().getVerbose()){
    			listener.getLogger().println("SHA-512: " + sha512hash);
			}
		} catch (IOException e) {
			listener.fatalError("Could not get MD5 or SHA-512: " + e.getMessage());
			throw new Exception();
		} catch (InterruptedException e) {
			listener.fatalError("MD5 or SHA-512 interrupted: " + e.getMessage());
			throw new Exception();
		}
		//Prepares the writer for the conf file
    	File pkgConf = new File(configPath.getRemote());
    	PrintWriter writer;
		try {
			writer = new PrintWriter(pkgConf.getAbsolutePath(), "UTF-8");
		} catch (FileNotFoundException e) {
			listener.fatalError("Could not create a package.conf file: " + e.getMessage());
			throw new Exception();
		} catch (UnsupportedEncodingException e) {
			listener.fatalError("Character Encoding not supported: " + e.getMessage());
			throw new Exception();
		}
		//Writes the details to the conf file
		writer.println("package-short-name=" + packageName);
		writer.println("package-version=" + uploadVersion);
		writer.println("package-archive=" + archivePath.getName());
		writer.println("package-archive-md5=" + md5hash);
		writer.println("package-archive-sha512=" + sha512hash);
		writer.println("package-language=" + packageLanguage);
		if (packageDir.equals("")){
			writer.println("package-dir=.");
		}else{
			writer.println("package-dir=" + packageDir);
		}
		writer.println("build-sys=" + buildSystem);
		if (!buildDirectory.equals("")){
			writer.println("build-dir=" + buildDirectory);
		}
		if (!buildFile.equals("")){
			writer.println("build-file=" + buildFile);
		}
		if (!buildTarget.equals("")){
			writer.println("build-target=" + buildTarget);
		}
		if (!buildOptions.equals("")){
			writer.println("build-opt=" + buildOptions);
		}
		if (!buildCommand.equals("")){
			writer.println("build-cmd=" + buildCommand);
		}
		writer.close();
		if (getDescriptor().getVerbose()){
			listener.getLogger().println("Config file written at " + configPath.getRemote());
		}
		return configPath;

    }
    /**
     * Retrieves the SHA-512 of an archive
     * @param archive the archive requested
     * @return the String containing the SHA-512
     * @throws IOException if the archive is not found
     */
    private String getSHA512 (FilePath archive) throws IOException{
    	
    	MessageDigest sha512;
    	try {
			sha512 = MessageDigest.getInstance("SHA-512");
			Path path = Paths.get(archive.getRemote());
			byte[] zipBytes = Files.readAllBytes(path);
	    	byte[] retArray = sha512.digest(zipBytes);
			return Hex.encodeHexString(retArray);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
	}
    
    private static SwampApiWrapper login (String username, String password, String hostUrl) throws Exception {
    	SwampApiWrapper api = new SwampApiWrapper(SwampApiWrapper.HostType.CUSTOM,hostUrl);
    	api.login(username, password);
    	return api;
    }

    //All getters used by Jenkins to save configurations
    public String getUsername() {
    	return username;
    }
    
    public String getPassword() {
		return password;
	}
    
    public String getHostUrl() {
    	return hostUrl;
    }
    
    public String getProjectUUID() {
		return projectUUID;
	}

    public List<AssessmentInfo> getAssessmentInfo() {
		return assessmentInfo;
	}
    
    public String getPackageDir() {
		return packageDir;
	}
    
    public String getPackageName() {
		return packageName;
	}
    
    public String getPackageVersion() {
		return packageVersion;
	}

    public String getPackageLanguage() {
		return packageLanguage;
	}
    
    public String getBuildSystem() {
		return buildSystem;
	}
    
    public String getBuildDirectory() {
		return buildDirectory;
	}
    
    public String getBuildFile() {
		return buildFile;
	}
    
    public String getBuildTarget() {
		return buildTarget;
	}
    
    public String getBuildCommand() {
    	return buildCommand;
    }
    
    public String getBuildOptions() {
    	return buildOptions;
    }
    
    public String getOutputDir() {
		return outputDir;
	}
    
    public boolean getSendEmail() {
		return sendEmail;
	}
    
    public String getEmail() {
		return email;
	}
    
    public boolean getOutputToFile() {
		return outputToFile;
	}
    
    public String getIconPath() {
    	PluginWrapper wrapper = Jenkins.getInstance().getPluginManager().getPlugin("SwampPreBuild");
    	return Jenkins.getInstance().getRootUrl() + "plugin/"+ wrapper.getShortName()+"/swamp-logo-large.png";
    }
    
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
    	
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
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
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
        	for (int i = 0; i < VALID_LANGUAGES.length; i++){
        		items.add(VALID_LANGUAGES[i]);
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
            buildSystemsPerLanguage = setupBuildSystemsPerLanguage();
            if (buildSystemsPerLanguage.containsKey(packageLanguage)){
            	for (int i = 0; i < buildSystemsPerLanguage.get(packageLanguage).length; i++){
            		items.add(buildSystemsPerLanguage.get(packageLanguage)[i]);
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
        	defaultBuildFiles = setupDefaultBuildFiles();
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
            return "SWAMP Assessment";
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
            save();
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
        	/*AssessmentInfo.setUsername(username);
		    AssessmentInfo.setPassword(password);*/
            return super.configure(req,formData);
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
    
	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}
	
	@Override
    public DescriptorImpl getDescriptor() {
        // see Descriptor javadoc for more about what a descriptor is.
        return (DescriptorImpl)super.getDescriptor();
    }
}
