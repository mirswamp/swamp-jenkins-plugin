
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

import hudson.plugins.analysis.core.HealthDescriptor;
import hudson.plugins.analysis.core.PluginDescriptor;
import hudson.plugins.analysis.core.AbstractResultAction;
import org.continuousassurance.swamp.Messages;

/**
 * Controls the live cycle of the SWAMP results. This action persists the
 * results of the SWAMP analysis of a build and displays the results on the
 * build page. The actual visualization of the results is defined in the
 * matching <code>summary.jelly</code> file.
 * <p>
 * Moreover, this class renders the SWAMP result trend.
 * </p>
 *
 * @author Ulli Hafner
 */
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
        return DescriptorImpl.DISPLAY_NAME + " Action";
    }

    @Override
    protected PluginDescriptor getDescriptor() {
        return new DescriptorImpl();
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return asSet(new SwampProjectAction(getJob()));
    }
}
