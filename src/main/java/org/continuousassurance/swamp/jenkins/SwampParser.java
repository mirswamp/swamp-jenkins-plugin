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

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

/*
import edu.umd.cs.findbugs.BugAnnotation;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.Project;
import edu.umd.cs.findbugs.SortedBugCollection;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import edu.umd.cs.findbugs.ba.SourceFile;
import edu.umd.cs.findbugs.ba.SourceFinder;
import edu.umd.cs.findbugs.cloud.Cloud;
*/
import hudson.plugins.analysis.core.AnnotationParser;
import hudson.plugins.analysis.util.TreeStringBuilder;
import hudson.plugins.analysis.util.model.FileAnnotation;
import hudson.plugins.analysis.util.model.LineRange;
import hudson.plugins.analysis.util.model.Priority;
//import hudson.plugins.findbugs.FindBugsMessages;

/**
 * A parser for the native SWAMP XML SCARF files.
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
    private final List<String> mavenSources = new ArrayList<String>();

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
        this(new ArrayList<String>(), isRankActivated, excludePattern, includePattern);
    }

    /**
     * Creates a new instance of {@link SwampParser}.
     *
     * @param sourceFolders
     *            a collection of folders to scan for source files. If empty, the source folders are guessed.
     * @param isRankActivated
     *            determines whether to use the rank when evaluation the priority
     * @param excludePattern
     *            RegEx patterns of files to exclude from the report
     * @param includePattern
     *            RegEx patterns of files to include in the report
     */
    public SwampParser(final Collection<String> sourceFolders, final boolean isRankActivated,
            final String excludePattern, final String includePattern) {
        mavenSources.addAll(sourceFolders);
        this.isRankActivated = isRankActivated;
        addPatterns(includePatterns, includePattern);
        addPatterns(excludePatterns, excludePattern);
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

    
    public Collection<FileAnnotation> parse(final File file, final String moduleName){
    	Collection<FileAnnotation> bugList = new ArrayList<FileAnnotation>();
    	//TODO parse XML file and add bugs to list
    	FileAnnotation nextBug = new Bug(null);
    	bugList.add(nextBug);
    	return bugList;
    }

    /**
     * Returns the parsed FindBugs analysis file. This scanner accepts files in the native FindBugs format.
     *
     * @param file
     *            the FindBugs analysis file
     * @param sources
     *            a collection of folders to scan for source files
     * @param moduleName
     *            name of maven module
     * @param hashToMessageMapping
     *            mapping of hash codes to messages
     * @param categories
     *            mapping from bug types to their categories
     * @return the parsed result (stored in the module instance)
     * @throws IOException
     *             if the file could not be parsed
     * @throws DocumentException
     *             in case of a parser exception
     */
    
    private Collection<FileAnnotation> parse(/*final InputStream file,*/final String file, final Collection<String> sources,
            final String moduleName, final Map<String, String> hashToMessageMapping,
            final Map<String, String> categories) throws IOException, DocumentException {
        //SortedBugCollection collection = readXml(file);
    	
    	 try {

    			File fXmlFile = new File(file);
    			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    			Document doc = dBuilder.parse(fXmlFile);
    			NodeList bugList = doc.getElementsByTagName("Bug");
   	         	List<FileAnnotation> annotations = new ArrayList<FileAnnotation>();
    			for (int i = 0; i < bugList.getLength(); i++){
    				Bug nextBug = new Bug(bugList.item(i));
    				//TODO
    				annotations.add(nextBug);
    			}
    	        return applyFilters(annotations);
    			
    	 } catch (Exception e) {
    			e.printStackTrace();
    	 }
    	 throw new DocumentException();
    	
        /*Project project = collection.getProject();
        for (String sourceFolder : sources) {
            project.addSourceDir(sourceFolder);
        }

        SourceFinder sourceFinder = new SourceFinder(project);
        String actualName = extractModuleName(moduleName, project);

        TreeStringBuilder stringPool = new TreeStringBuilder();
        List<FileAnnotation> annotations = new ArrayList<FileAnnotation>();
        Collection<BugInstance> bugs = collection.getCollection();

        for (BugInstance warning : bugs) {

            SourceLineAnnotation sourceLine = warning.getPrimarySourceLineAnnotation();

            String message = warning.getMessage();
            String type = warning.getType();
            if (message.contains("TEST: Unknown")) {
                message = FindBugsMessages.getInstance().getShortMessage(type, LocaleProvider.getLocale());
            }
            String category = categories.get(type);
            if (category == null) { // alternately, only if warning.getBugPattern().getType().equals("UNKNOWN")
                category = warning.getBugPattern().getCategory();
            }
            Bug bug = new Bug(getPriority(warning), StringUtils.defaultIfEmpty(
                    hashToMessageMapping.get(warning.getInstanceHash()), message), category, type,
                    sourceLine.getStartLine(), sourceLine.getEndLine());
            bug.setInstanceHash(warning.getInstanceHash());
            bug.setRank(warning.getBugRank());

            boolean ignore = setCloudInformation(collection, warning, bug);
            if (!ignore) {
                bug.setNotAProblem(false);
                bug.setFileName(findSourceFile(project, sourceFinder, sourceLine));
                bug.setPackageName(warning.getPrimaryClass().getPackageName());
                bug.setModuleName(actualName);
                setAffectedLines(warning, bug);

                annotations.add(bug);
                bug.intern(stringPool);
            }

        }

        return applyFilters(annotations);*/
    }


    /**
     * Applies the exclude and include filters to the found annotations.
     *
     * @param allAnnotations
     *            all annotations
     * @return the filtered annotations if there is a filter defined
     */
    private List<FileAnnotation> applyFilters(final List<FileAnnotation> allAnnotations) {
        List<FileAnnotation> includedAnnotations;
        if (includePatterns.isEmpty()) {
            includedAnnotations = allAnnotations;
        }
        else {
            includedAnnotations = new ArrayList<FileAnnotation>();
            for (FileAnnotation annotation : allAnnotations) {
                for (Pattern include : includePatterns) {
                    if (include.matcher(annotation.getFileName()).matches()) {
                        includedAnnotations.add(annotation);
                    }
                }
            }
        }
        if (excludePatterns.isEmpty()) {
            return includedAnnotations;
        }
        else {
            List<FileAnnotation> excludedAnnotations = new ArrayList<FileAnnotation>(includedAnnotations);
            for (FileAnnotation annotation : includedAnnotations) {
                for (Pattern exclude : excludePatterns) {
                    if (exclude.matcher(annotation.getFileName()).matches()) {
                        excludedAnnotations.remove(annotation);
                    }
                }
            }
            return excludedAnnotations;
        }
    }

    /*private Priority getPriority(final BugInstance warning) {
        if (isRankActivated) {
            return getPriorityByRank(warning);
        }
        else {
            return getPriorityByPriority(warning);
        }
    }*/

    /*private SortedBugCollection readXml(final InputStream file) throws IOException, DocumentException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(SwampParser.class.getClassLoader());
            SortedBugCollection collection = new SortedBugCollection();
            collection.readXML(file);
            return collection;
        }
        finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }*/

    /**
     * Sets the cloud information.
     *
     * @param collection
     *            the warnings collection
     * @param warning
     *            the warning
     * @param bug
     *            the bug
     * @return true, if this warning is not a bug and should be ignored
     */
    /*@SuppressFBWarnings("NP")
    private boolean setCloudInformation(final SortedBugCollection collection, final BugInstance warning, final Bug bug) {
        Cloud cloud = collection.getCloud();
        cloud.waitUntilIssueDataDownloaded();

        bug.setShouldBeInCloud(cloud.isOnlineCloud());
        Map<String, String> cloudDetails = collection.getXmlCloudDetails();
        bug.setDetailsUrlTemplate(cloudDetails.get(CLOUD_DETAILS_URL_PROPERTY));

        long firstSeen = cloud.getFirstSeen(warning);
        bug.setInCloud(cloud.isInCloud(warning));
        bug.setFirstSeen(firstSeen);
        int ageInDays = (int)((collection.getAnalysisTimestamp() - firstSeen) / DAY_IN_MSEC);
        bug.setAgeInDays(ageInDays);
        bug.setReviewCount(cloud.getNumberReviewers(warning));

        return cloud.overallClassificationIsNotAProblem(warning);
    }*/

    /*private void setAffectedLines(final BugInstance warning, final Bug bug) {
        Iterator<BugAnnotation> annotationIterator = warning.annotationIterator();
        while (annotationIterator.hasNext()) {
            BugAnnotation bugAnnotation = annotationIterator.next();
            if (bugAnnotation instanceof SourceLineAnnotation) {
                SourceLineAnnotation annotation = (SourceLineAnnotation)bugAnnotation;
                bug.addLineRange(new LineRange(annotation.getStartLine(), annotation.getEndLine()));
            }
        }
    }*/

    /*private String findSourceFile(final Project project, final SourceFinder sourceFinder,
            final SourceLineAnnotation sourceLine) {
        try {
            SourceFile sourceFile = sourceFinder.findSourceFile(sourceLine);
            return sourceFile.getFullFileName();
        }
        catch (IOException exception) {
            StringBuilder sb = new StringBuilder("Can't resolve absolute file name for file ");
            sb.append(sourceLine.getSourceFile());
            if (isFirstError) {
                sb.append(", dir list = ");
                sb.append( project.getSourceDirList());
                isFirstError = false;
            }
            Logger.getLogger(getClass().getName()).log(Level.WARNING, sb.toString());
            return sourceLine.getPackageName().replace(DOT, SLASH) + SLASH + sourceLine.getSourceFile();
        }
    }*/

    /**
     * Maps the FindBugs library rank to plug-in priority enumeration.
     *
     * @param warning
     *            the FindBugs warning
     * @return mapped priority enumeration
     */
    /*private Priority getPriorityByRank(final BugInstance warning) {
        int rank = warning.getBugRank();
        if (rank <= HIGH_PRIORITY_LOWEST_RANK) {
            return Priority.HIGH;
        }
        if (rank <= NORMAL_PRIORITY_LOWEST_RANK) {
            return Priority.NORMAL;
        }
        return Priority.LOW;
    }*/

    /**
     * Maps the FindBugs library priority to plug-in priority enumeration.
     *
     * @param warning
     *            the FindBugs warning
     * @return mapped priority enumeration
     */
    /*private Priority getPriorityByPriority(final BugInstance warning) {
        switch (warning.getPriority()) {
            case 1:
                return Priority.HIGH;
            case 2:
                return Priority.NORMAL;
            default:
                return Priority.LOW;
        }
    }*/

    /**
     * Extracts the module name from the specified project. If empty then the provided default name is used.
     *
     * @param defaultName
     *            the default module name to use
     * @param project
     *            the maven 2 project
     * @return the module name to use
     */
    /*private String extractModuleName(final String defaultName, final Project project) {
        if (StringUtils.isBlank(project.getProjectName())) {
            return defaultName;
        }
        else {
            return project.getProjectName();
        }
    }*/

    /**
     * Provides an input stream for the parser.
     */
    interface InputStreamProvider {
        InputStream getInputStream() throws IOException;
    }
}
