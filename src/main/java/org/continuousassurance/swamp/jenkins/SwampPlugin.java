
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

import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.MojoExecution;

import hudson.Plugin;
import hudson.plugins.analysis.core.PluginDescriptor;
import hudson.plugins.analysis.views.DetailFactory;

public class SwampPlugin extends Plugin {
	 @Override
	    public void start() {
	        initializeDetails();
	    }

	    private void initializeDetails() {
	       /* SwampDetailFactory detailBuilder = new SwampDetailFactory();
	        DetailFactory.addDetailBuilder(FindBugsResultAction.class, detailBuilder);
	        if (PluginDescriptor.isMavenPluginInstalled()) {
	            MavenInitialization.run(detailBuilder);
	        }*/
	    }

	    /**
	     * Returns whether the specified maven swamp plug-in uses a Swamp
	     * release 2.0.0 or newer.
	     *
	     * @param mojoExecution
	     *            the maven version ID
	     * @return <code>true</code> if Swamp 2.0.0 or newer is used
	     */
	    /*public static boolean isFindbugs2x(final MojoExecution mojoExecution) {
	        try {
	            String[] versions = StringUtils.split(mojoExecution.getVersion(), ".");
	            if (versions.length > 1) {
	                int major = Integer.parseInt(versions[0]);
	                int minor = Integer.parseInt(versions[1]);
	                return major > 2 || (major == 2 && minor >= 4);
	            }
	        }
	        catch (Throwable exception) { // NOCHECKSTYLE NOPMD
	            // ignore and return false
	        }
	        return false;
	    }*/
}
