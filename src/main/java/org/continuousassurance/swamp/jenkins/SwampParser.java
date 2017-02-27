package org.continuousassurance.swamp.jenkins; // NOPMD

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.digester3.Digester;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.dom4j.DocumentException;
import org.jvnet.localizer.LocaleProvider;
import org.xml.sax.SAXException;

import com.google.common.collect.Sets;

import hudson.FilePath;
import hudson.plugins.analysis.core.AnnotationParser;
import hudson.plugins.analysis.util.TreeStringBuilder;
import hudson.plugins.analysis.util.model.FileAnnotation;
import hudson.plugins.analysis.util.model.LineRange;
import hudson.plugins.analysis.util.model.Priority;

import org.continousassurance.scarf.ScarfXmlReader;
import org.continousassurance.scarf.datastructures.BugInstance;

/**
 * A parser for the native FindBugs XML files (ant task, batch file or maven-findbugs-plugin greater than 1.2).
 *
 * @author Ulli Hafner
 */
// CHECKSTYLE:COUPLING-OFF
public class SwampParser implements AnnotationParser {
    /** Unique ID of this class. */
    private static final long serialVersionUID = 8306319007761954027L;

    private static final String DOT = ".";
    private static final String SLASH = "/";
    private static final String CLOUD_DETAILS_URL_PROPERTY = "detailsUrl";
    private static final String EMPTY_STRING = "";

    private static final int DAY_IN_MSEC = 1000 * 60 * 60 * 24;
    private static final int HIGH_PRIORITY_LOWEST_RANK = 4;
    private static final int NORMAL_PRIORITY_LOWEST_RANK = 9;

    /** Collection of source folders. */
    //@SuppressFBWarnings("SE")
    private final FilePath projectDir;

    /** Determines whether to use the rank when evaluation the priority. @since 4.26 */
    private final boolean isRankActivated;

    private final Set<Pattern> excludePatterns = Sets.newHashSet();
    private final Set<Pattern> includePatterns = Sets.newHashSet();

    private boolean isFirstError = true;

    /**
     * Creates a new instance of {@link SwampParser}.
     *
     * @param isRankActivated
     *            determines whether to use the rank when evaluation the priority
     */
    public SwampParser(final boolean isRankActivated) {
        this(isRankActivated, EMPTY_STRING, EMPTY_STRING);
    }

    /**
     * Creates a new instance of {@link SwampParser}.
     *
     * @param isRankActivated
     *            determines whether to use the rank when evaluation the priority
     * @param excludePattern
     *            RegEx patterns of files to exclude from the report
     * @param includePattern
     *            RegEx patterns of files to include in the report
     */
    public SwampParser(final boolean isRankActivated, final String excludePattern, final String includePattern) {
        this(null, isRankActivated, excludePattern, includePattern);
    }

    /**
     * Creates a new instance of {@link SwampParser}.
     *
     * @param projectDir
     *            a collection of folders to scan for source files. If empty, the source folders are guessed.
     * @param isRankActivated
     *            determines whether to use the rank when evaluation the priority
     * @param excludePattern
     *            RegEx patterns of files to exclude from the report
     * @param includePattern
     *            RegEx patterns of files to include in the report
     */
    public SwampParser(FilePath projectDir, final boolean isRankActivated,
            final String excludePattern, final String includePattern) {
        this.projectDir = projectDir;
        this.isRankActivated = isRankActivated;
        addPatterns(includePatterns, includePattern);
        addPatterns(excludePatterns, excludePattern);
    }

    public FilePath getProjectDir() {
		return projectDir;
	}

	/**
     * Add RegEx patterns to include/exclude in the report.
     *
     * @param patterns
     *            RegEx patterns
     * @param pattern
     *            String of RegEx patterns
     */
    private void addPatterns(final Set<Pattern> patterns, final String pattern) {
        if (StringUtils.isNotBlank(pattern)) {
            String[] split = StringUtils.split(pattern, ',');
            for (String singlePattern : split) {
                String trimmed = StringUtils.trim(singlePattern);
                String directoriesReplaced = StringUtils.replace(trimmed, "**", "*"); // NOCHECKSTYLE
                patterns.add(Pattern.compile(StringUtils.replace(directoriesReplaced, "*", ".*"))); // NOCHECKSTYLE
            }
        }
    }


    @Override
    public Collection<FileAnnotation> parse(final File file, final String moduleName) throws InvocationTargetException {
    	ScarfXmlReader r = new ScarfXmlReader();
		r.parseFromFile(file);
		
		Collection<FileAnnotation> bug_collection = new ArrayList<FileAnnotation>();
		
		for (BugInstance  bug_inst: r.getAllBugInstances()){
			Priority bug_priority;
			try {
				bug_priority = getPriority(Integer.parseInt(bug_inst.getBugSeverity()));
			}catch (NumberFormatException e){
				bug_priority = Priority.LOW;
			}
			int bug_start_line;
			int bug_end_line;
			String source_file;
			if (!bug_inst.getLocations().isEmpty()){
				bug_start_line = bug_inst.getLocations().get(0).getStartLine();
				bug_end_line = bug_inst.getLocations().get(0).getEndLine();
				source_file = bug_inst.getLocations().get(0).getSourceFile();
			}else{
				bug_start_line = 0;
				bug_end_line = 0;
				source_file = "";//TODO find any source file available
			}
			Bug bug = new Bug(bug_priority,					
					bug_inst.getBugMessage(), 
					bug_inst.getBugGroup(), 
					bug_inst.getBugCode(),
					file.getName(),
					bug_start_line, 
		            bug_end_line);
			
			//System.out.println(getProjectDir().toString() + '/' + source_file.substring(source_file.indexOf('/') + 1));
			//bug.setFileName(getProjectDir().toString() + '/' + source_file.substring(source_file.indexOf('/') + 1));
			bug.setFileName(getProjectDir().toString() + '/' + source_file);
			bug.setInstanceHash(String.valueOf(bug_inst.getBugId()));
			try {
				bug.setRank(Integer.parseInt(bug_inst.getBugRank()));
			}catch (NumberFormatException e){
				bug.setRank(-1);
			}
			bug_collection.add(bug);
		}
		return bug_collection;
    }
    
    /**
     * Maps the FindBugs library priority to plug-in priority enumeration.
     *
     * @param warning
     *            the FindBugs warning
     * @return mapped priority enumeration
     */
    private Priority getPriority(final int bug_severity) {
        switch (bug_severity) {
            case 1:
                return Priority.HIGH;
            case 2:
                return Priority.NORMAL;
            default:
                return Priority.LOW;
        }
    }
}
