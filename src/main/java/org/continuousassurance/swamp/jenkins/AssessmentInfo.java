
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

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.continuousassurance.swamp.api.Platform;
import org.continuousassurance.swamp.api.Tool;
import org.continuousassurance.swamp.cli.SwampApiWrapper;
import org.continuousassurance.swamp.cli.exceptions.InvalidIdentifierException;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;

public class AssessmentInfo  extends AbstractDescribableImpl<AssessmentInfo> implements Serializable{

	private final String toolUUID;
	private final String platformUUID;
	private static SwampApiWrapper api = null;
	private static boolean midAccess = false; 

	public static void setApi(SwampApiWrapper newApi){
		api = newApi;
	}

	@DataBoundConstructor
	public AssessmentInfo(String toolUUID, String platformUUID){
		this.toolUUID = toolUUID;
		this.platformUUID = platformUUID;
	}

	public String getAssessmentInfo(SwampApiWrapper api, String projectUUID) throws Exception{
		return "Assessment " + getToolName(api,projectUUID) + " on " + getPlatformName(api);
	}

	public String getToolName(SwampApiWrapper api, String projectUUID) throws Exception{
		StringBuffer returnName = new StringBuffer();
		for (String nextUUID : toolUUID.split(",")){
			returnName.append(", " + api.getTool(nextUUID,projectUUID).getName());
		}
		returnName = returnName.delete(0, 2);
		return returnName.toString();
		/*String returnName = "";
		for (String nextUUID : toolUUID.split(",")){
			returnName += ", " + api.getTool(nextUUID,projectUUID).getName();
		}
		returnName = returnName.substring(2);
		return returnName;*/
	}

	public String getToolUUID(){
		return toolUUID;
	}

	public String getPlatformName(SwampApiWrapper api){
		return api.getPlatform(platformUUID).getName();
	}

	public String getPlatformUUID(){
		return platformUUID;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		// see Descriptor javadoc for more about what a descriptor is.
		return (DescriptorImpl)super.getDescriptor();
	}

	@Extension 
	public static class DescriptorImpl extends Descriptor<AssessmentInfo>{ 

		private String errorMessageTool;
		private String errorMessagePlatform;

		/** 
		 * Fills the Tool list based on the language provided
		 * @param packageLanguage the language of the package
		 * @param projectUUID the name of the project
		 * @return a ListBoxModel containing the tools as strings
		 */
		public ListBoxModel doFillToolUUIDItems(@QueryParameter @RelativePath("..") String packageLanguage,
				@QueryParameter @RelativePath("..") String projectUUID) {
			if (projectUUID == null){
				ListBoxModel error = new ListBoxModel();
				errorMessageTool = "ERROR: Could not get project UUID after 100 retries.";
				System.out.println(errorMessageTool);
				error.add("","null");
				midAccess = false;
				return error;
			}
			if (packageLanguage == null){
				ListBoxModel error = new ListBoxModel();
				errorMessageTool = "ERROR: Language is set to null. Please verify your language and retry.";
				System.out.println(errorMessageTool);
				error.add("","null");
				midAccess = false;
				return error;
			}
			packageLanguage = api.getPkgTypeString(packageLanguage, "", "", null);
			if (packageLanguage == null){ListBoxModel error = new ListBoxModel();
			errorMessageTool = "ERROR: Invalid language. Please verify your language and retry.";
			System.out.println(errorMessageTool);
			error.add("","null");
			midAccess = false;
			return error;
			}
			ListBoxModel items = new ListBoxModel();
			List<Tool> toolList = null;
			for (int i = 0; i < 100 && (toolList == null || toolList.isEmpty()); i++){
				try {
					toolList = api.getTools(packageLanguage, projectUUID);
					Thread.sleep(50);
				} catch (InvalidIdentifierException | InterruptedException e){

				}
			}
			if (toolList == null || toolList.isEmpty()){
				ListBoxModel error = new ListBoxModel();
				errorMessageTool = "ERROR: Could not load tools after 100 retries.";
				System.out.println(errorMessageTool);
				error.add("","null");
				midAccess = false;
				return error;
			}
			try {
				Iterator<Tool> allTools = toolList.iterator();
				items.add("","null");
				Option all = new Option("all","");
				while (allTools.hasNext()){
					Tool nextTool = allTools.next();
					if (nextTool.getSupportedPkgTypes().contains(packageLanguage)){
						items.add(nextTool.getName(),nextTool.getUUIDString());
						all.value = all.value + "," + nextTool.getUUIDString();
					}
				}
				all.value = all.value.substring(1);
				items.add(1, all);
				if (items.size() == 1){
					ListBoxModel error = new ListBoxModel();
					errorMessageTool = "ERROR: Could not load tools for " + packageLanguage + ". Please verify your language and retry.";
					System.out.println(errorMessageTool);
					error.add("","null");
					midAccess = false;
					return error;
				}
			} catch (InvalidIdentifierException e) {
				ListBoxModel error = new ListBoxModel();
				errorMessageTool = "ERROR: Could not load tools: " + e.getMessage();
				System.out.println(errorMessageTool);
				error.add("","null");
				midAccess = false;
				return error;
			}
			midAccess = false;
			return items;
		}

		private static void fillWithTools (SwampApiWrapper api, String packageLanguage, String projectUUID, ListBoxModel items){
			Iterator<Tool> allTools = api.getTools(packageLanguage, projectUUID).iterator();
			while (allTools.hasNext()){
				Tool nextTool = allTools.next();
				items.add(nextTool.getName(),nextTool.getUUIDString());
			}
		}

		/** 
		 * Fills the Tool list based on the language provided
		 * @param projectUUID the name of the project
		 * @param toolUUID the tool uuid to get all the platforms from
		 * @return a ListBoxModel containing the tools as strings
		 */
		/*
        public ListBoxModel doFillPlatformUUIDItems(@QueryParameter @RelativePath("..")
        			String projectUUID, @QueryParameter String toolUUID) {
			if (toolUUID == null || toolUUID.equals("null") || toolUUID.equals("")){
				ListBoxModel error = new ListBoxModel();
	        	errorMessagePlatform = "Please select a tool.";
	        	System.out.println(errorMessagePlatform);
				error.add("","null");
				midAccess = false;
	        	return error;
			}
			toolUUID = toolUUID.split(",")[0];
			if (projectUUID == null){
				ListBoxModel error = new ListBoxModel();
        		errorMessagePlatform = "ERROR: project UUID not available after 100 retries.";
        		System.out.println(errorMessagePlatform);
				error.add("","null");
				midAccess = false;
        		return error;
			}
			ListBoxModel items = new ListBoxModel();
        	Iterator<String> availablePlatforms = null;
        	for (int i = 0; i < 100 && (availablePlatforms == null || !availablePlatforms.hasNext()); i++){
        		Tool currentTool = api.getTool(toolUUID, projectUUID);
        		if (currentTool != null){
        			availablePlatforms = currentTool.getSupportedPlatforms().iterator();
	        		try {
						Thread.sleep(50);
					} catch (InterruptedException e) {

					}
        		}
        	}
        	if (availablePlatforms == null || !availablePlatforms.hasNext()){
        		ListBoxModel error = new ListBoxModel();
        		errorMessagePlatform = "ERROR: platforms for " + toolUUID + " not available after 100 retries.";
        		System.out.println(errorMessagePlatform);
				error.add("","null");
				midAccess = false;
        		return error;
        	}
        	while (availablePlatforms.hasNext()){
        		String nextUUID = availablePlatforms.next();
        		Platform nextPlatform = null;
        		while (nextPlatform == null){
                	try {
                		nextPlatform = api.getPlatformFromName(nextUUID);
                	} catch (Exception e) {
        			}
        		}
    			items.add(nextPlatform.getName(),nextPlatform.getUUIDString());
    		}
			if (items.isEmpty()){
				ListBoxModel error = new ListBoxModel();
				errorMessagePlatform = "ERROR: Could not load platforms for " + api.getTool(toolUUID, projectUUID).getName() + ". Please verify your language and retry.";
				System.out.println(errorMessagePlatform);
				error.add("","null");
				midAccess = false;
        		return error;
			}
			midAccess = false;
            return items;
        }*/
		 
		public ListBoxModel doFillPlatformUUIDItems(@QueryParameter @RelativePath("..") String packageLanguage,
				@QueryParameter @RelativePath("..") String buildSystem,
				@QueryParameter @RelativePath("..") String projectUUID, 
				@QueryParameter String toolUUID) {
			if (toolUUID == null || toolUUID.equals("null") || toolUUID.equals("")){
				ListBoxModel error = new ListBoxModel();
				errorMessagePlatform = "Please select a tool.";
				System.out.println(errorMessagePlatform);
				error.add("","null");
				midAccess = false;
				return error;
			}
			toolUUID = toolUUID.split(",")[0];
			if (projectUUID == null){
				ListBoxModel error = new ListBoxModel();
				errorMessagePlatform = "ERROR: project UUID not available after 100 retries.";
				System.out.println(errorMessagePlatform);
				error.add("","null");
				midAccess = false;
				return error;
			}
			ListBoxModel items = new ListBoxModel();
			if (packageLanguage.equalsIgnoreCase("C") || packageLanguage.equalsIgnoreCase("C++")) {
				for (Platform platform : api.getSupportedPlatforms(toolUUID, projectUUID)) {
					items.add(platform.getName(), platform.getUUIDString());
				}
			}else {
				Platform platform = api.getDefaultPlatform(api.getPkgTypeString(packageLanguage, "", buildSystem, null));
				items.add(platform.getName(), platform.getUUIDString());
			}
			
			if (items.isEmpty()){
				ListBoxModel error = new ListBoxModel();
				errorMessagePlatform = "ERROR: Could not load platforms for " + api.getTool(toolUUID, projectUUID).getName() + ". Please verify your language and retry.";
				System.out.println(errorMessagePlatform);
				error.add("","null");
				midAccess = false;
				return error;
			}
			midAccess = false;
			return items;
		}

		private static void fillWithPlatforms(SwampApiWrapper api, ListBoxModel items){
			Iterator<Platform> allPlatforms = api.getPlatformsList().iterator();
			while (allPlatforms.hasNext()){
				Platform nextPlatform = allPlatforms.next();
				items.add(nextPlatform.getName(),nextPlatform.getUUIDString());
			}
		}

		public boolean multiplePlatforms(@QueryParameter @RelativePath("..")
		String projectName, @QueryParameter String toolUUID){
			try {
				String myProject = api.getProjectFromName(projectName).getUUIDString();
				return (api.getTool(toolUUID, myProject).getSupportedPlatforms().size() >= 1);
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}

		/**
		 * Performs on-the-fly validation of the tool.
		 * @param value This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the browser.
		 */
		public FormValidation doCheckToolUUID(@QueryParameter String value, @QueryParameter String toolUUID){
			if (errorMessageTool != null){
				String msg = errorMessageTool;
				errorMessageTool = null;
				return FormValidation.error(msg);
			}
			if (value == null || value.equals("")){
				return FormValidation.error("Please select a tool");
			}
			return FormValidation.ok();
		}

		/**
		 * Performs on-the-fly validation of the platform.
		 * @param value This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the browser.
		 */
		public FormValidation doCheckPlatformUUID(@QueryParameter String value, @QueryParameter String platformUUID){
			if (errorMessagePlatform != null){
				String msg = errorMessagePlatform;
				errorMessagePlatform = null;
				return FormValidation.error(msg);
			}
			if (value == null || value.equals("")){
				return FormValidation.ok();
			}
			return FormValidation.ok();
		}

		public String getDisplayName() {
			return org.continuousassurance.swamp.jenkins.DescriptorImpl.PLUGIN_DISPLAY_NAME;
		} 
	}
}
