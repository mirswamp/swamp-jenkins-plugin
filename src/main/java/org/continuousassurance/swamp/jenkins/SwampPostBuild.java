
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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.codec.binary.Hex;
import org.continuousassurance.swamp.api.AssessmentRecord;
import org.continuousassurance.swamp.api.PackageThing;
import org.continuousassurance.swamp.api.Project;
import org.continuousassurance.swamp.api.Tool;
import org.continuousassurance.swamp.cli.SwampApiWrapper;
import org.continuousassurance.swamp.cli.exceptions.InvalidIdentifierException;
import org.continuousassurance.swamp.cli.util.AssessmentStatus;
import org.continuousassurance.swamp.session.HTTPException;
import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.PluginWrapper;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.plugins.analysis.core.BuildResult;
import hudson.plugins.analysis.core.FilesParser;
import hudson.plugins.analysis.core.HealthAwarePublisher;
import hudson.plugins.analysis.core.ParserResult;
import hudson.plugins.analysis.util.PluginLogger;
import hudson.tasks.BuildStepMonitor;
import hudson.util.Secret;
import jenkins.model.Jenkins;

public class SwampPostBuild extends HealthAwarePublisher {
	
    /*
	static final String[] VALID_LANGUAGES = {"ActionScript","Ada","AppleScript","Assembly",
		"Bash","C","C#","C++","Cobol","ColdFusion","CSS","D","Datalog","Erlang",
		"Forth","Fortran","Haskell","HTML","Java","JavaScript","LISP","Lua","ML",
		"OCaml","Objective-C","PHP","Pascal","Perl","Prolog","Python","Python-2","Python-3",
		"Rexx","Ruby","sh","SQL","Scala","Scheme","SmallTalk","Swift","Tcl","tcsh","Visual-Basic"};
	/*/
    static final String[] VALID_LANGUAGES = {
            "C", 
            "C++",
            "Java",
            "Python-2",
            "Python-3",
            "Ruby"
    };

    static final String[] VALID_BUILD_SYSTEMS = {
            "android+ant",
            "android+ant+ivy",
            "android+gradle",
            "android+maven",
            "ant",
            "ant+ivy",
            "cmake+make",
            "configure+make",
            "gradle",
            "java-bytecode",
            "make",
            "maven",
            "no-build",
            "none",
            "other",
            "python-distutils"
    };

	static HashMap<String,String> setupDefaultBuildFiles () {
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
	
	private static final String[] C_BUILD_SYSTEMS = {"cmake+make","configure+make","make","no-build","other"};
	private static final String[] JAVA_BUILD_SYSTEMS = {"ant","ant+ivy","gradle","maven","no-build","android+ant","android+gradle","android+maven"};
	private static final String[] PYTHON_BUILD_SYSTEMS = {"python-distutils","no-build","other"};
	private static final String[] RUBY_BUILD_SYSTEMS = {"bundler","bundler+rake","bundler+other","rake","no-build","other","rubygem"};
	
	static HashMap<String,String[]> setupBuildSystemsPerLanguage () {
		HashMap<String,String[]> defaults = new HashMap<String,String[]>();
		defaults.put("C", C_BUILD_SYSTEMS);
		defaults.put("C++", C_BUILD_SYSTEMS);
		defaults.put("Java", JAVA_BUILD_SYSTEMS);
		defaults.put("Python-2", PYTHON_BUILD_SYSTEMS);
		defaults.put("Python-3", PYTHON_BUILD_SYSTEMS);
		defaults.put("Ruby", RUBY_BUILD_SYSTEMS);
		return defaults;
	}
	
	//private final String username;
	//private final String password;
	private final String hostUrl;
	private final String projectUUID;
	private final String packageName;
	private final List<AssessmentInfo> assessmentInfo;
	private final String packageVersion;
	private final String packageDir;
	private final String packageLanguage;
	private final String packageLanguageVersion;
	private final String buildSystem;
	private final String buildDirectory;
	private final String buildFile;
	private final String buildTarget;
	private final String buildCommand;
	private final String buildOptions;
	private final String configCommand;
	private final String configOptions;
	private final String configDirectory;
	private final String cleanCommand;
	private final String outputDir;
	//private final boolean sendEmail;
	//private final String emailAddr;
    private String defaultEncoding;
	
	private String projectName;
	private String archiveName;
	
	private static SwampApiWrapper api;
	
    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public SwampPostBuild(String projectUUID, List<AssessmentInfo> assessmentInfo, 
    		String packageName, String packageVersion, 
    		String packageDir, String packageLanguage, 
    		String packageLanguageVersion, String buildSystem, 
    		String buildDirectory, String buildFile, 
    		String buildTarget, String buildCommand, 
    		String buildOptions, String configCommand, 
    		String configOptions, String configDirectory, 
    		String outputDir, /*boolean sendEmail, String emailAddr,*/ String cleanCommand) {
        super("SWAMP");
        //this.username = getDescriptor().getUsername();
        //this.password = getDescriptor().getPassword();
        this.hostUrl = getDescriptor().getHostUrl();
        this.projectUUID = projectUUID;
        this.assessmentInfo = assessmentInfo;
        this.packageName = packageName;
        this.packageDir = packageDir;
        this.packageLanguage = packageLanguage;
        this.packageLanguageVersion = packageLanguageVersion;
        this.buildSystem = buildSystem;
        this.buildDirectory = buildDirectory;
        this.buildFile = buildFile;
        this.buildTarget = buildTarget;
        this.buildCommand = buildCommand;
        this.buildOptions = buildOptions;
        this.configCommand = configCommand;
        this.configOptions = configOptions;
        this.configDirectory = configDirectory;
    	//this.sendEmail = sendEmail;
    	//this.emailAddr = emailAddr;
    	this.packageVersion = packageVersion;
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
    
    public void log_error(final PluginLogger logger, String error_msg, String error_remedy) {
        logger.log("[ERROR] ------------------------------------------------------------------------");
        logger.log("[ERROR] " + error_msg);
        if (error_remedy != null) {
            logger.log("[ERROR] " + error_remedy);
        }
        logger.log("[ERROR] ------------------------------------------------------------------------");    
    }
    
    @Override
    public BuildResult perform(final Run<?, ?> build, final FilePath workspace, final PluginLogger logger) throws IOException, InterruptedException {
    	SwampResult emptyResult = null;
    	//If the build failed, exit
    	Result buildResult = build.getResult(); 
    	if (buildResult == null ||  buildResult.isWorseOrEqualTo(Result.FAILURE)){
    		if (getDescriptor().getVerbose()){
    		    log_error(logger, "Build failed: no point in sending to the SWAMP", null);
    		}
    		return emptyResult;
    	}
    	//If the login failed, exit
    	if (getDescriptor().getLoginFail()){
    	    
    	    if (getDescriptor().getHostUrl() == null || getDescriptor().getHostUrl().isEmpty()) {
                log_error(logger, 
                        "SWAMP URL missing",
                        "Check SWAMP configuration @ 'Manage Jenkins >> Configure System >> SWAMP'");

                return emptyResult;  
            }
    	    
    	    if (getDescriptor().getCredentialId() == null || getDescriptor().getCredentialId().isEmpty()) {
    	        log_error(logger, 
                        "SWAMP credentials missing",
    	                "Check SWAMP configuration @ 'Manage Jenkins >> Configure System >> SWAMP'");
                return emptyResult;  
            }
    	    
    	    log_error(logger, 
    	            "Login failed",
    	            "Check SWAMP configuration @ 'Manage Jenkins >> Configure System >> SWAMP'");
    		return emptyResult;
    	}
    	//Error check build info
    	if (!checkBuild(workspace,logger)){
    		return emptyResult;
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
    	
    	//BuildListener listener = null;//TODO bring back task listener for needed env vars
    	//Launcher launcher = null;//TODO bring back launcher for needed clean commands
    	
		if (uploadVersion.contains("$git")){
			EnvVars buildVars = new EnvVars();
			buildVars = build.getCharacteristicEnvVars();
			if (!buildVars.containsKey("GIT_COMMIT")){
				uploadVersion = uploadVersion.replace("$git", "");
				logger.log("[WARNING] Git commit not available. Replacing with blank string.");
			}else{
				uploadVersion = uploadVersion.replace("$git", buildVars.get("GIT_COMMIT"));
			}
		}
		if (uploadVersion.contains("$svn")){
			EnvVars buildVars = new EnvVars();
			buildVars = build.getCharacteristicEnvVars();
			if (!buildVars.containsKey("SVN_REVISION")){
				uploadVersion = uploadVersion.replace("$svn", "");
				logger.log("[WARNING] Subversion commit not available. Replacing with blank string.");
			}else{
				uploadVersion = uploadVersion.replace("$svn", buildVars.get("SVN_REVISION"));
			}
		}
		//uploadVersion = uploadVersion.replaceAll("/", "-");
		//uploadVersion = uploadVersion.replaceAll("\\", "-");
		uploadVersion = uploadVersion.replaceAll("/", "-").replaceAll(":", "-");

    	archiveName = packageName + "-" + uploadVersion + ".zip";
		//String jenkinsVersion = Jenkins.VERSION;
		//String swampPluginVersion = Jenkins.getInstance().pluginManager.getPlugin("Swamp").getVersion();
    	
    	//Login to the SWAMP if needed
        if (getSwampApi() == null){
            if (getDescriptor().getCredentialId() == null || getDescriptor().getCredentialId().isEmpty() ) {
                log_error(logger, 
                        "SWAMP credentials missing",
                        "Check SWAMP configuration @ 'Manage Jenkins >> Configure System >> SWAMP'");
                return emptyResult;  
            }
            
        	logger.log("Logging in...");
        	try {
        	    @SuppressWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
        	    StandardUsernamePasswordCredentials credential =  CredentialsProvider.findCredentialById(getDescriptor().getCredentialId(),
        	            StandardUsernamePasswordCredentials.class,
        	            build,
        	            Collections.<DomainRequirement> emptyList());
        	    
        	    if (credential != null) {
    				setSwampApi(DescriptorImpl.login(credential.getUsername(), 
    				        Secret.toString(credential.getPassword()), 
    				        this.hostUrl));
        	    }else {
        	        log_error(logger,
        	                "SWAMP credentials missing",
                            "Check SWAMP configuration @ 'Manage Jenkins >> Configure System >> SWAMP'");
                    return emptyResult;
        	    }
			} catch (Exception e) {
			    log_error(logger,
			            "Login failed during build",
                        "Check SWAMP configuration @ 'Manage Jenkins >> Configure System >> SWAMP'");
		    	return emptyResult;
			}
        }
    	
    	//Get project, tool, and platform uuids given the names
    	try {
			getUUIDsFromNames(api,logger);
		} catch (Exception e) {
	    	return emptyResult;
		}
    	
    	//Duplicate the workspace for cleaning
    	//FilePath tempPath = new FilePath(workspace,(packageDir.equals("") ? ".TempPackage" : packageDir + ".TempPackage"));
    	/*FilePath tempPath = new FilePath(workspace,("../.TempPackage"));
    	FilePath target = new FilePath(workspace,(packageDir.equals("") ? ".TempPackage" : packageDir + ".TempPackage"));
    	try {
    		tempPath.mkdirs();
			workspace.copyRecursiveTo(tempPath);
			target.mkdirs();
			logger.log("tempPath = " + tempPath.getRemote() + ", target = " + target.getRemote());
			tempPath.copyRecursiveTo(target);
			tempPath.deleteRecursive();
		} catch (IOException | InterruptedException e) {
			logger.log("[ERROR] Could not create temporary workspace: " + e.getMessage());
			return emptyResult;
		}*/
    	
    	//Clean the package
    	/*try {
    		if (getDescriptor().getVerbose()){
    			logger.log("Executing " + cleanCommand);
    		}
			ProcStarter starter = launcher.launch().cmdAsSingleString(cleanCommand).pwd(tempPath).envs(build.getEnvironment(listener)).stdout(listener);
			Proc proc = launcher.launch(starter);
			int retcode = proc.join();
			if (getDescriptor().getVerbose()){
    			logger.log("Return code of " + cleanCommand + " is " + retcode);
			}
		} catch (IOException | InterruptedException e) {
			logger.log("[ERROR] Invalid clean command " + cleanCommand + ": " + e.getMessage());
		}*/
    	
    	/*String packageUUID;
		FilePath archivePath;
		// Zip the package
		try {
			archivePath = zipPackage(workspace, logger);
			target.deleteRecursive();
		} catch (Exception e) {
			//build.setResult(Result.FAILURE);
			logger.log("[ERROR] Archiving the package failed: " + e.getMessage());
			return emptyResult;
		}*/
    	
    	FilePath archivePath = new FilePath (new FilePath(build.getRootDir()), archiveName.replace('/', '-'));
    	//FilePath archivePath = new FilePath (workspace, "../" + archiveName.replace('/', '-'));
    	FilePath packagePath = new FilePath(workspace,packageDir.equals("") ? "" : "/" + packageDir);
    	//logger.log(archivePath.getRemote() + ", " + packagePath.getRemote());
    	//Zips the archive and moves it to the output directory
		try {
			OutputStream stream = archivePath.write();
			packagePath.zip(stream, "**");

			if (getDescriptor().getVerbose()){
    			logger.log("Archive created at " + archivePath.getRemote());
			}
		} catch (IOException e) {
		    log_error(logger, "Archive creation failed: " + e.getMessage(), null);
			return emptyResult;
		} catch (InterruptedException e) {
		    log_error(logger, "Archive creation interrupted: " + e.getMessage(), null);
			return emptyResult;
		}
		
		// Create a config file for submission
		FilePath configPath;
		try {
			configPath = writeConfFile(workspace, archivePath, logger, uploadVersion);
		} catch (Exception e) {
			//build.setResult(Result.FAILURE);
			return emptyResult;
		}
		
		// Upload the package
		String packageUUID;
		try {
			logger.log(configPath.getRemote() + ", " + archivePath.getRemote() + ", " + projectUUID);
			packageUUID = api.uploadPackage(configPath.getRemote(),
					archivePath.getRemote(), projectUUID,
					null,
					isNewPackage(packageName));
			logger.log("Config exists - " + new File(configPath.getRemote()).exists());
			logger.log("Archive exists - " + new File(archivePath.getRemote()).exists());
		} catch (InvalidIdentifierException e) {
		    log_error(logger, "Could not upload Package: " + e.getMessage(), null);
			//build.setResult(Result.FAILURE);
			return emptyResult;
		} catch (HTTPException e){
		    log_error(logger, "Could not upload Package: " + e.getMessage(), null);
			//build.setResult(Result.FAILURE);
			return emptyResult;
		}
		if (getDescriptor().getVerbose()){
			logger.log("Package Uploaded. UUID id " + packageUUID);
		}
		/*Delete the package archive and config file since they are no longer needed
		try {
			configPath.delete();
			archivePath.delete();
		} catch (IOException e) {
			logger.log("[ERROR] Deletion of straggler files failed: " + e.getMessage());
			//build.setResult(Result.FAILURE);
			return emptyResult;
		} catch (InterruptedException e) {
			logger.log("[ERROR] Deletion of straggler files interrupted: " + e.getMessage());
			//build.setResult(Result.FAILURE);
			return emptyResult;
		}*/
		
		//Deal with the "all" option
		List<AssessmentInfo> assessmentsToRun = new ArrayList<AssessmentInfo>();
		Iterator<AssessmentInfo> assessmentCheck = assessmentInfo.iterator();
		while (assessmentCheck.hasNext()){
			AssessmentInfo nextAssess = assessmentCheck.next();
			for (String nextTool : nextAssess.getToolUUID().split(",")){
				AssessmentInfo newAssess = new AssessmentInfo(nextTool,nextAssess.getPlatformVersionUUID());
				assessmentsToRun.add(newAssess);
			}
		}
		
		ArrayList<String> tools_without_permissions = new ArrayList<String>();
		//checking for tool permissions
		for (int i = 0; i < assessmentsToRun.size(); i++) {
		    Tool tool = api.getTool(assessmentsToRun.get(i).getToolUUID(), projectUUID); 
            
		    if(!api.hasToolPermission(tool.getUUIDString(), 
		            projectUUID, 
		            api.getPackageVersion(packageUUID, projectUUID).getPackageThing().getUUIDString())) {
		        tools_without_permissions.add(tool.getName());
		    }
		}
		
		if (!tools_without_permissions.isEmpty()) {
		    log_error(logger, "No permissions to use the tools: " + tools_without_permissions + 
		            "\nUnselect these tools OR get permissons to use them", null);
		    return emptyResult;
		}
		
		ArrayList<String> assessmentUUIDs = new ArrayList<String>();
		//Run the assessments
		for (int i = 0; i < assessmentsToRun.size();i++){
			try {
				logger.log("Running Assessment (package: " + packageName + "-" + uploadVersion + 
						", tool: " + assessmentsToRun.get(i).getToolName(api,projectUUID) + 
						", platform: " + assessmentsToRun.get(i).getPlatformVersionName(api) + ")");		
				
				String arun_uuid = api.runAssessment(api.getPackageVersion(packageUUID, projectUUID), 
				        api.getTool(assessmentsToRun.get(i).getToolUUID(), projectUUID), 
				        api.getProject(projectUUID), 
				        api.getPlatformVersion(assessmentsToRun.get(i).getPlatformVersionUUID())).getUUIDString();
				
				assessmentUUIDs.add(arun_uuid);
				
			} catch (Exception e) {
			    log_error(logger, "Assessment failed: " + e.getMessage(), null);
			}
		}
		
		/*if (sendEmail){
		 	//var emailAddr contains string of email
			//TODO Email results when complete
		}*/
		
		//ArrayList<String> assessmentNames = new ArrayList<String>();
		//if (!getDescriptor().getBackgroundAssess()){
		FilePath outputPath = new FilePath(workspace, outputDir);
		try {
			outputPath.mkdirs();
		} catch (IOException | InterruptedException e) {
		    log_error(logger, "Could not create output directory: " + e.getMessage(), null);
			//build.setResult(Result.FAILURE);
		}
		
		boolean assessment_done[] = new boolean[assessmentsToRun.size()];
		for(int i = 0; i < assessment_done.length; ++i) {
			assessment_done[i] = false;
		}
		int all_finished = 0;
		while(all_finished < assessmentUUIDs.size()) {
			try {
				Thread.sleep(30000);
				for(AssessmentRecord executionRecord : api.getAllAssessmentRecords(projectUUID)) {
					for (int i = 0; i < assessmentUUIDs.size(); i++){
						if (executionRecord.getAssessmentRunUUID().equals(assessmentUUIDs.get(i)) 
								&& assessment_done[i] == false){
							if (executionRecord.getAssessmentResultUUID().equals("null")) {
								// Assessment still in progress
								String assessmentName = packageName + "-" + uploadVersion + ", " + 
										assessmentsToRun.get(i).getPlatformVersionName(api) + ", " + 
										assessmentsToRun.get(i).getToolName(api, projectUUID) + ")";
									logger.log("Assessment Status: (" + assessmentName + "): " +
											AssessmentStatus.translateAssessmentStatus(executionRecord.getStatus()));
							}else {
								// Assessment done in progress
								String results_filename = ("Assessment-" + packageName + "-" + uploadVersion + "-" + 
										assessmentsToRun.get(i).getPlatformVersionName(api) + "-" + 
										assessmentsToRun.get(i).getToolName(api,projectUUID).replace('-', '_')).replace(' ', '_') + ".xml";
								results_filename = results_filename.replace("/", "-");
								
								FilePath newFile = new FilePath(outputPath, results_filename);
								logger.log("Assessment finished. Writing results to " + newFile.getRemote());
								api.getAssessmentResults(projectUUID, 
										executionRecord.getAssessmentResultUUID(), 
										newFile.getRemote());
								// book keeping
								assessment_done[i] = true;
								++all_finished;
							}
						}
					}
				}
				
			} catch (InterruptedException e) {
			    log_error(logger, "Waiting for status interrupted: " + e.getMessage(), null);
				//build.setResult(Result.FAILURE);
				return emptyResult;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return emptyResult;
			}
		}
			
		/*
    	//For each assessment
		//Project projectAPI = api.getProject(projectUUID);
		for (int i = 0; i < assessmentUUIDs.length; i++){
			AssessmentRecord assessmentRecord = null;
			String assessmentResults = "null";
			String previousStatus = null;
			String assessmentName;
			try {
				//assessmentName = "swampXml.xml";
				assessmentName = ("Assessment-" + packageName + "-" + uploadVersion + "-" + assessmentsToRun.get(i).getPlatformVersionName(api) + "-" + assessmentsToRun.get(i).getToolName(api,projectUUID).replace('-', '_')).replace(' ', '_') + ".xml";
				assessmentName = assessmentName.replace("/", "-");
			} catch (Exception e) {
				logger.log("[ERROR] Tool / Platform missing unexpectedly: " + e.getMessage());
				return emptyResult;
			}
			//Wait until the assessment is complete
			while (assessmentResults.equals("null")){
				assessmentRecord = null;
				for(AssessmentRecord executionRecord : api.getAllAssessmentRecords(projectUUID)) {
					if (executionRecord.getAssessmentRunUUID().equals(assessmentUUIDs[i])){
						assessmentRecord = executionRecord;
					}
				}
				if (assessmentRecord == null){
					logger.log("[ERROR] AssessmentRun " + assessmentUUIDs[i] + " not found");
					//build.setResult(Result.FAILURE);
					return emptyResult;
				}
				assessmentResults = assessmentRecord.getAssessmentResultUUID();
				if (!assessmentRecord.getStatus().equals(previousStatus)){
					//logger.log("Waiting on assessment " + assessmentName + ", Status is " + assessmentRecord.getStatus() + ", Results UUID is " + assessmentResults);
					logger.log("Waiting on assessment " + assessmentName + ", "
							+ "Status is " + AssessmentStatus.translateAssessmentStatus(assessmentRecord.getStatus())
							+ ", Results UUID is " + assessmentResults);
					previousStatus = assessmentRecord.getStatus();
				}
				if (assessmentResults.equals("null")){
					try {
						Thread.sleep(30000);
					} catch (InterruptedException e) {
						logger.log("[ERROR] Waiting for status interrupted: " + e.getMessage());
						//build.setResult(Result.FAILURE);
						return emptyResult;
					}
				}
			}

			//Save the assessment to the requested spot
			FilePath newFile = new FilePath(outputPath, assessmentName);
			try {
				int fileNum = 0;
				while (newFile.exists()){
					fileNum++;
					newFile = new FilePath(outputPath,assessmentName.replace("/", "-") + "(" + fileNum + ")");
				}
			} catch (IOException | InterruptedException e) {
				logger.log("[ERROR] Failed to check files: " + e.getMessage());
				//build.setResult(Result.FAILURE);
				return emptyResult;
			}
			if (getDescriptor().getVerbose()){
	    		logger.log("Assessment finished. Writing results to " + newFile.getRemote());
			}
			api.getAssessmentResults(projectUUID, assessmentResults, newFile.getRemote());
			logger.log("Assessment " + newFile.getRemote() + " exists = " + newFile.exists());
		}*/

		//Log out
		if (getDescriptor().getVerbose()){
			logger.log("Logging out");
		}
		api.logout();
		api = null;
		
		logger.log("Collecting SWAMP results files...");

		SwampResult result = null;
		//Add every assessment matching this file pattern
		String assessmentPattern = "**/Assessment-" + packageName + "-" + uploadVersion + "*.xml";
		assessmentPattern = assessmentPattern.replace(' ', '_');
		//Prepare the parser with the given files
        FilesParser collector = new FilesParser("SWAMP",
                assessmentPattern,
                new SwampParser(workspace, false, null, null), 
                false,false);
        //Run the parser
        ParserResult project = workspace.act(collector);
        logger.log(project.getLogMessages());
        //Save the results
        result = new SwampResult(build, defaultEncoding, project, true, true);
        //Post-Process the results into data for the analysis-core plugin
        build.addAction(new SwampResultAction(build, this, result));
        //Return the results
        return result;
    	
    }

    /**
     * Attempts to verify the build without uploading or building
     * @param workspace the directory of the workspace
     * @param logger the logger in Jenkins for any output of errors
     * @return if the build file is validated
     */
    private boolean checkBuild (FilePath workspace, PluginLogger logger){
    	FilePath buildFile;
    	if (this.buildFile.equals("")){
    		HashMap<String,String> defaultBuildFiles = setupDefaultBuildFiles();
    		if (!defaultBuildFiles.containsKey(buildSystem)){
    			logger.log("[Warning] could not verify build file for " + buildSystem);
    			return true;
    		}
    		for (String nextPossibleName : defaultBuildFiles.get(buildSystem).split(",")){
    			buildFile = new FilePath(workspace,(buildDirectory.equals("") ? "" : buildDirectory + "/") + nextPossibleName);
        		try {
        			if (buildFile.exists()){
        				return true;
        			}
    			} catch (IOException | InterruptedException e) {
    			    log_error(logger, "Could not verify build file's existance: " + e.getMessage(), null);
    			}
    		}
    		log_error(logger, "Build file " + defaultBuildFiles.get(buildSystem) + " not found at " + 
    		workspace.getRemote() + (buildDirectory.equals("") ? "" : buildDirectory + "/"), null);
			return false;
    	}else{
    		buildFile = new FilePath(workspace,(buildDirectory.equals("") ? "" : buildDirectory + "/") + this.buildFile);
    		try {
    			if (!buildFile.exists()){
    			    log_error(logger, "Build file " + buildFile.getRemote() + " not found.", null);
    				return false;
    			}
			} catch (IOException | InterruptedException e) {
				logger.log("[ERROR] Could not verify build file's existance: " + e.getMessage());
			}
    	}
    	return true;
    }
    
    /**
     * Gets the UUIDs from the names of the projects, tools, and platforms
     * @param api the api that we are logged into
     * @param logger the logger in Jenkins for any output of errors
     * @throws Exception if something goes wrong, details will be printed to the logger and a generic exception will be thrown
     */
    private void getUUIDsFromNames (SwampApiWrapper api, PluginLogger logger) throws Exception{
    	//Get project UUID
    	if (getDescriptor().getVerbose()){
			logger.log("Retrieving IDs from names");
    	}
    	boolean allGood = true;
    	try {
    		Project myProject = api.getProject(projectUUID);
    		if (myProject == null){
    			logger.log("[ERROR] Project " + projectName + " does not exist.");
    			allGood = false;
    		}else{
    			projectName = myProject.getFullName();
    		}
    	}catch (Exception e) {
			logger.log("[ERROR] Could not retrieve project list: " + e.getMessage());
			throw new Exception();
    	}
    	try {
    		Iterator<AssessmentInfo> myInfo = assessmentInfo.iterator();
    		while (myInfo.hasNext()){
    			AssessmentInfo nextAssess = myInfo.next();
    			if (getDescriptor().getVerbose()){
        			logger.log("Verifying " + nextAssess.getAssessmentInfo(api, projectUUID));
    			}
    		}
    	}catch (Exception e){
    		logger.log("[ERROR] Some tools or platforms are invalid: " + e.getMessage());
			allGood = false;
    	}
		if (!allGood){
			throw new Exception();
		}
		
    }
    
    /**
     * Zips the package for uploading
     * @param workspace the directory of the workspace
     * @param logger the logger in Jenkins for any output of errors
     * @return the file path to the output folder (archiveName must be added on)
     * @throws Exception if something goes wrong, details will be printed to the logger and a generic exception will be thrown
     */
    private FilePath zipPackage(FilePath workspace, PluginLogger logger) throws Exception{
    	//Sets up the temporary directory to store the package
    	FilePath packagePath = new FilePath(workspace,(packageDir.equals("") ? ".TempPackage" : packageDir + ".TempPackage"));
		FilePath archivePath = new FilePath(workspace,archiveName);
    	//Zips the archive and moves it to the output directory
		try {
			OutputStream stream = archivePath.write();
			packagePath.zip(stream, "**");

			if (getDescriptor().getVerbose()){
    			logger.log("Archive created at " + archivePath.getRemote());
			}
		} catch (IOException e) {
			logger.log("[ERROR] Archive creation failed: " + e.getMessage());
			throw new Exception();
		} catch (InterruptedException e) {
			logger.log("[ERROR] Archive creation interrupted: " + e.getMessage());
			throw new Exception();
		}

		return archivePath;
		
    }
    
    /**
     * Writes a configuration file for the package to be uploaded
     * @param workspace directory of the workspace of the package
     * @param logger the logger in Jenkins for any output of errors
     * @return the filepath to the config file
     * @throws Exception if something goes wrong, details will be printed to the logger and a generic exception will be thrown
     */
    private FilePath writeConfFile (FilePath workspace, FilePath archivePath, PluginLogger logger, String uploadVersion) throws Exception{
    	//Retrieves the MD5 and SHA-512 of the archive
    	FilePath configPath = new FilePath(workspace,"package.conf");
    	String md5hash;
		String sha512hash;
		try {
			md5hash = archivePath.digest();
			if (getDescriptor().getVerbose()){
    			logger.log("MD5: " + md5hash);
			}
			sha512hash = getSHA512(archivePath);
			if (getDescriptor().getVerbose()){
    			logger.log("SHA-512: " + sha512hash);
			}
		} catch (IOException e) {
			logger.log("[ERROR] Could not get MD5 or SHA-512: " + e.getMessage());
			throw new Exception();
		} catch (InterruptedException e) {
			logger.log("[ERROR] MD5 or SHA-512 interrupted: " + e.getMessage());
			throw new Exception();
		}
		//Prepares the writer for the conf file
    	File pkgConf = new File(configPath.getRemote());
    	PrintWriter writer;
		try {
			writer = new PrintWriter(pkgConf.getAbsolutePath(), "UTF-8");
		} catch (FileNotFoundException e) {
			logger.log("[ERROR] Could not create a package.conf file: " + e.getMessage());
			throw new Exception();
		} catch (UnsupportedEncodingException e) {
			logger.log("[ERROR] Character Encoding not supported: " + e.getMessage());
			throw new Exception();
		}
		//Writes the details to the conf file
		writer.println("package-short-name=" + packageName);
		writer.println("package-version=" + uploadVersion);
		writer.println("package-archive=" + archivePath.getName());
		writer.println("package-archive-md5=" + md5hash);
		writer.println("package-archive-sha512=" + sha512hash);
		writer.println("package-language=" + packageLanguage);
		if (!packageLanguageVersion.equals("")){
			writer.println("package-language-version=" + packageLanguageVersion);
		}
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
		if (!configCommand.equals("")){
			writer.println("config-cmd=" + configCommand);
		}
		if (!configOptions.equals("")){
			writer.println("config-opt=" + configOptions);
		}
		if (!configDirectory.equals("")){
			writer.println("config-dir=" + configDirectory);
		}
		writer.close();
		if (getDescriptor().getVerbose()){
			logger.log("Config file written at " + configPath.getRemote());
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
    
    private boolean isNewPackage (String packageName) {
    	List<PackageThing> myPackages = api.getPackagesList(projectUUID);
    	Iterator<PackageThing> myPackageIterator = myPackages.iterator();
    	while (myPackageIterator.hasNext()){
    		if (myPackageIterator.next().getName().equalsIgnoreCase(packageName)){
    			return false;
    		}
    	}
    	return true;
    }

    public static SwampApiWrapper getSwampApi() {
    	return api;
    }

    public static void setSwampApi(SwampApiWrapper newApi) {
    	api = newApi;
    }
    
    //All getters used by Jenkins to save configurations
     
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

    public String getPackageLanguageVersion() {
		return packageLanguageVersion;
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
    
    public String getConfigCommand() {
    	return configCommand;
    }
    
    public String getConfigOptions() {
    	return configOptions;
    }
    
    public String getConfigDirectory() {
    	return configDirectory;
    }
    
    public String getOutputDir() {
		return outputDir;
	}
    
    /*public boolean getSendEmail() {
		return sendEmail;
	}
    
    public String getEmail() {
		return email;
	}*/
    
    public String getIconPath() {
    	Jenkins instance = Jenkins.getInstance(); 
    	if (instance != null){
	    	PluginWrapper wrapper = instance.getPluginManager().getPlugin("SwampPreBuild");
	    	return instance.getRootUrl() + "plugin/"+ wrapper.getShortName()+"/swamp-logo-large.png";
    	}
    	return null;
    }
    
	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

    @Override
    public DescriptorImpl getDescriptor() {
    	return (DescriptorImpl)super.getDescriptor();
    }
    
    @Override
    public MatrixAggregator createAggregator(final MatrixBuild build, final Launcher launcher,
            final BuildListener listener) {
        return new SwampAnnotationsAggregator(build, launcher, listener, this, getDefaultEncoding(),
                usePreviousBuildAsReference(), useOnlyStableBuildsAsReference());
    }
    
    public void setDefaultEncoding(final String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
    }
}
