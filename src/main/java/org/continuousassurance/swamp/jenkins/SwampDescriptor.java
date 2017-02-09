
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

import hudson.Extension;
import hudson.plugins.analysis.core.PluginDescriptor;

@Extension(ordinal = 100)
public class SwampDescriptor  extends PluginDescriptor {
	/** The ID of this plug-in is used as URL. */
    static final String PLUGIN_ID = "SWAMP";
    /** The URL of the result action. */
    static final String RESULT_URL = PluginDescriptor.createResultUrlName(PLUGIN_ID);
    /** Icons prefix. */
    static final String ICON_URL_PREFIX = "/plugin/SWAMP/icons/";
    /** Icon to use for the result and project action. */
    static final String ICON_URL = ICON_URL_PREFIX + "SWAMP-logo.png";

    /**
     * Creates a new instance of {@link SwampDescriptor}.
     */
    public SwampDescriptor() {
        super(SwampPublisher.class);
    }

    @Override
    public String getDisplayName() {
        return "Messages.Swamp_Publisher_Name()";
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
        return ICON_URL_PREFIX + "swamp-logo-small.png";
    }
}
