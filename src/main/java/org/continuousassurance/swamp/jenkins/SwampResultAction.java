
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

import java.util.Collection;

import hudson.model.Action;
import hudson.model.Run;
import hudson.plugins.analysis.core.AbstractResultAction;
import hudson.plugins.analysis.core.HealthDescriptor;
import hudson.plugins.analysis.core.PluginDescriptor;

public class SwampResultAction extends AbstractResultAction<SwampResult> {
	/**
     * Creates a new instance of {@link SwampResultAction}.
     *
     * @param owner
     *            the associated build of this action
     * @param healthDescriptor
     *            health descriptor to use
     * @param result
     *            the result in this build
     */
    public SwampResultAction(final Run<?, ?> owner, final HealthDescriptor healthDescriptor, final SwampResult result) {
        super(owner, new SwampHealthDescriptor(healthDescriptor), result);
    }

    @Override
    public String getDisplayName() {
    	return "Swamp";
        //return Messages.Swamp_ProjectAction_Name();
    }

    @Override
    protected PluginDescriptor getDescriptor() {
        return new SwampDescriptor();
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return asSet(new SwampProjectAction(getJob()));
    }
}
