//  SwampBuildResultAction.java
//
//  Copyright 2016 Jared Sweetland, Vamshi Basupalli, James A. Kupsch
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
package org.continuousassurance.swamp.jenkins;

import java.util.Collection;

import hudson.model.Action;
import hudson.model.Run;

import hudson.plugins.analysis.core.HealthDescriptor;
import hudson.plugins.analysis.core.PluginDescriptor;
import hudson.plugins.analysis.core.AbstractResultAction;

/**
 * Controls the live cycle of the FindBugs results. This action persists the
 * results of the FindBugs analysis of a build and displays the results on the
 * build page. The actual visualization of the results is defined in the
 * matching <code>summary.jelly</code> file.
 * <p>
 * Moreover, this class renders the FindBugs result trend.
 * </p>
 *
 * @author Ulli Hafner
 */
public class SwampBuildResultAction extends AbstractResultAction<SwampBuildResult> {
    /**
     * Creates a new instance of {@link SwampBuildResultAction}.
     *
     * @param owner
     *            the associated build of this action
     * @param healthDescriptor
     *            health descriptor to use
     * @param result
     *            the result in this build
     */
    public SwampBuildResultAction(final Run<?, ?> owner, final HealthDescriptor healthDescriptor, final SwampBuildResult result) {        
    	super(owner, null/*healthDescriptor*/, result);
    	System.out.println("Constructed");
    }

    @Override
    public String getDisplayName() {
        return this.getName();
    }

    @Override
    protected PluginDescriptor getDescriptor() {
        return null;
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return asSet(null/*new FindBugsProjectAction(getJob())*/);
    }
}