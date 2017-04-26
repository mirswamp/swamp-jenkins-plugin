
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

import java.util.List;

import com.google.common.collect.Lists;

import hudson.model.Job;
import hudson.plugins.analysis.core.AbstractProjectAction;
import hudson.plugins.analysis.core.ResultAction;
import hudson.plugins.analysis.graph.BuildResultGraph;
import org.continuousassurance.swamp.Messages;

/**
 * Entry point to visualize the SWAMP trend graph in the project screen.
 * Drawing of the graph is delegated to the associated {@link ResultAction}.
 *
 * @author Ulli Hafner
 */
public class SwampProjectAction extends AbstractProjectAction<ResultAction<SwampResult>> {
    /**
     * Instantiates a new {@link SwampProjectAction}.
     *
     * @param job
     *            the job that owns this action
     */
    public SwampProjectAction(final Job<?, ?> job) {
        this(job, SwampResultAction.class);
    }

    /**
     * Instantiates a new {@link SwampProjectAction}.
     *
     * @param job
     *            the job that owns this action
     * @param type
     *            the result action type
     */
    public SwampProjectAction(final Job<?, ?> job,
            final Class<? extends ResultAction<SwampResult>> type) {
        super(job, type, Messages._FindBugs_ProjectAction_Name(), Messages._FindBugs_Trend_Name(),
                DescriptorImpl.PLUGIN_ID, DescriptorImpl.ICON_URL, DescriptorImpl.RESULT_URL);
    }

    @Override
    protected List<BuildResultGraph> getAvailableGraphs() {
        List<BuildResultGraph> list = Lists.newArrayList();
        list.addAll(super.getAvailableGraphs());
        return list;
    }
}

