
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

import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.analysis.core.BuildResult;
import hudson.plugins.analysis.core.FilesParser;
import hudson.plugins.analysis.core.HealthAwarePublisher;
import hudson.plugins.analysis.core.ParserResult;
import hudson.plugins.analysis.util.PluginLogger;

public class SwampPublisher extends HealthAwarePublisher {
	private static final long serialVersionUID = -5748362182226609649L;

    private static final String PLUGIN_NAME = "SWAMP";

    private static final String ANT_DEFAULT_PATTERN = "**/swamp.xml";
    private static final String MAVEN_DEFAULT_PATTERN = "**/swampXml.xml";

    /** Ant file-set pattern of files to work with. */
    private String pattern;

    /** Determines whether to use the rank when evaluation the priority. @since 4.26 */
    private boolean isRankActivated;

    /** RegEx patterns of files to exclude from the report. */
    private String excludePattern;

    /** RegEx patterns of files to include in the report. */
    private String includePattern;

    /**
     * Default data bound constructor.
     * Use setters to initialize the object if needed.
     */
    @DataBoundConstructor
    public SwampPublisher() {
        super(PLUGIN_NAME);
    }

    /**
     * Returns whether to use the rank when evaluation the priority.
     *
     * @return <code>true</code> if the rank should uses when evaluation the
     *         priority, <code>false</code> if the Swamp priority should be
     *         used
     */
    public boolean isRankActivated() {
        return isRankActivated;
    }

    /**
     * Added to properly uncoercing.
     */
    public boolean isIsRankActivated() {
        return isRankActivated;
    }

    /**
     * see link #isRankActivated()
     */
    @DataBoundSetter
    public void setIsRankActivated(boolean isRankActivated) {
        this.isRankActivated = isRankActivated;
    }

    /**
     * Returns the Ant file-set pattern of files to work with.
     *
     * @return Ant file-set pattern of files to work with
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * see link #getPattern()
     */
    @DataBoundSetter
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    /**
     * RegEx patterns of files to exclude from the report.
     *
     * @return String of concatenated exclude patterns separated by a comma
     */
    public String getExcludePattern() {
        return excludePattern;
    }

    /**
     * see link #getExcludePattern()
     */
    @DataBoundSetter
    public void setExcludePattern(String excludePattern) {
        this.excludePattern = excludePattern;
    }

    /**
     * Returns the RegEx patterns to include in the report.
     *
     * @return String of concatenated include patterns separated by a comma
     */
    public String getIncludePattern() {
        return includePattern;
    }

    /**
     * see link #getIncludePattern()
     */
    @DataBoundSetter
    public void setIncludePattern(String includePattern) {
        this.includePattern = includePattern;
    }

    @Override
    public BuildResult perform(final Run<?, ?> build, final FilePath workspace, final PluginLogger logger) throws InterruptedException, IOException {
        logger.log("Collecting swamp analysis files...");

        boolean isMavenBuild = isMavenBuild(build);
        String defaultPattern = isMavenBuild ? MAVEN_DEFAULT_PATTERN : ANT_DEFAULT_PATTERN;
        SwampParser newParser = new SwampParser(isRankActivated, getExcludePattern(), getIncludePattern());
        FilesParser collector = new FilesParser(PLUGIN_NAME,
                StringUtils.defaultIfEmpty(expandFilePattern(getPattern(), build.getEnvironment(TaskListener.NULL)), defaultPattern),
                newParser, shouldDetectModules(), isMavenBuild);

        ParserResult project = workspace.act(collector);
        logger.logLines(project.getLogMessages());
        SwampResult result = new SwampResult(build, getDefaultEncoding(), project,
                usePreviousBuildAsReference(), useOnlyStableBuildsAsReference());

        build.addAction(new SwampResultAction(build, this, result));

        return result;
    }

    @Override
    public SwampDescriptor getDescriptor() {
        return (SwampDescriptor)super.getDescriptor();
    }

    @Override
    public MatrixAggregator createAggregator(final MatrixBuild build, final Launcher launcher,
            final BuildListener listener) {
        return null/*new SwampAnnotationsAggregator(build, launcher, listener, this, getDefaultEncoding(),
                usePreviousBuildAsReference(), useOnlyStableBuildsAsReference())*/;
    }
}
