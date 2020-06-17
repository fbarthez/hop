/*! ******************************************************************************
 *
 * Hop : The Hop Orchestration Platform
 *
 * http://www.project-hop.org
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.workflow.actions.xml.xmlwellformed;

import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSelectInfo;
import org.apache.commons.vfs2.FileType;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Result;
import org.apache.hop.core.ResultFile;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopXmlException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlCheck;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.action.validator.AbstractFileValidator;
import org.apache.hop.workflow.action.validator.ActionValidatorUtils;
import org.apache.hop.workflow.action.validator.AndValidator;
import org.apache.hop.workflow.action.validator.ValidatorContext;
import org.apache.hop.workflow.engine.IWorkflowEngine;
import org.w3c.dom.Node;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This defines a 'xml well formed' workflow action.
 * 
 * @author Samatar Hassan
 * @since 26-03-2008
 */
@Action(
        id = "XML_WELL_FORMED",
        i18nPackageName = "org.apache.hop.workflow.actions.xml.xmlwellformed",
        name = "XML_WELL_FORMED.Name",
        description = "XML_WELL_FORMED.Description",
        categoryDescription = "XML_WELL_FORMED.Category",
        documentationUrl = ""
)
public class XmlWellFormed extends ActionBase implements Cloneable, IAction {
  private static Class<?> PKG = XmlWellFormed.class; // for i18n purposes, needed by Translator2!!

  public static String SUCCESS_IF_AT_LEAST_X_FILES_WELL_FORMED = "success_when_at_least";
  public static String SUCCESS_IF_BAD_FORMED_FILES_LESS = "success_if_bad_formed_files_less";
  public static String SUCCESS_IF_NO_ERRORS = "success_if_no_errors";

  public static String ADD_ALL_FILENAMES = "all_filenames";
  public static String ADD_WELL_FORMED_FILES_ONLY = "only_well_formed_filenames";
  public static String ADD_BAD_FORMED_FILES_ONLY = "only_bad_formed_filenames";

  @Deprecated
  public boolean arg_from_previous;
  @Deprecated
  public boolean include_subfolders;

  @Deprecated
  public String[] source_filefolder;
  @Deprecated
  public String[] wildcard;
  private String nr_errors_less_than;
  private String success_condition;
  private String resultfilenames;

  int NrAllErrors = 0;
  int NrBadFormed = 0;
  int NrWellFormed = 0;
  int limitFiles = 0;
  int NrErrors = 0;

  boolean successConditionBroken = false;
  boolean successConditionBrokenExit = false;

  public XmlWellFormed(String n ) {
    super( n, "" );
    resultfilenames = ADD_ALL_FILENAMES;
    arg_from_previous = false;
    source_filefolder = null;
    wildcard = null;
    include_subfolders = false;
    nr_errors_less_than = "10";
    success_condition = SUCCESS_IF_NO_ERRORS;
  }

  public XmlWellFormed() {
    this( "" );
  }

  public Object clone() {
    XmlWellFormed je = (XmlWellFormed) super.clone();
    return je;
  }

  public String getXML() {
    StringBuilder retval = new StringBuilder( 300 );

    retval.append( super.getXml() );
    retval.append( "      " ).append( XmlHandler.addTagValue( "arg_from_previous", arg_from_previous ) );
    retval.append( "      " ).append( XmlHandler.addTagValue( "include_subfolders", include_subfolders ) );
    retval.append( "      " ).append( XmlHandler.addTagValue( "nr_errors_less_than", nr_errors_less_than ) );
    retval.append( "      " ).append( XmlHandler.addTagValue( "success_condition", success_condition ) );
    retval.append( "      " ).append( XmlHandler.addTagValue( "resultfilenames", resultfilenames ) );
    retval.append( "      " ).append( XmlHandler.openTag( "fields" ) ).append( Const.CR );
    if ( source_filefolder != null ) {
      for ( int i = 0; i < source_filefolder.length; i++ ) {
        retval.append( "        " ).append( XmlHandler.openTag( "field" ) ).append( Const.CR );
        retval.append( "          " ).append( XmlHandler.addTagValue( "source_filefolder", source_filefolder[i] ) );
        retval.append( "          " ).append( XmlHandler.addTagValue( "wildcard", wildcard[i] ) );
        retval.append( "        " ).append( XmlHandler.closeTag( "field" ) ).append( Const.CR );
      }
    }
    retval.append( "      " ).append( XmlHandler.closeTag( "fields" ) ).append( Const.CR );

    return retval.toString();
  }

  public void loadXml(Node entrynode, IHopMetadataProvider metadataProvider ) throws HopXmlException {
    try {
      super.loadXml( entrynode);

      arg_from_previous = "Y".equalsIgnoreCase( XmlHandler.getTagValue( entrynode, "arg_from_previous" ) );
      include_subfolders = "Y".equalsIgnoreCase( XmlHandler.getTagValue( entrynode, "include_subfolders" ) );

      nr_errors_less_than = XmlHandler.getTagValue( entrynode, "nr_errors_less_than" );
      success_condition = XmlHandler.getTagValue( entrynode, "success_condition" );
      resultfilenames = XmlHandler.getTagValue( entrynode, "resultfilenames" );

      Node fields = XmlHandler.getSubNode( entrynode, "fields" );

      // How many field arguments?
      int nrFields = XmlHandler.countNodes( fields, "field" );
      source_filefolder = new String[nrFields];
      wildcard = new String[nrFields];

      // Read them all...
      for ( int i = 0; i < nrFields; i++ ) {
        Node fnode = XmlHandler.getSubNodeByNr( fields, "field", i );

        source_filefolder[i] = XmlHandler.getTagValue( fnode, "source_filefolder" );
        wildcard[i] = XmlHandler.getTagValue( fnode, "wildcard" );
      }
    } catch ( HopXmlException xe ) {

      throw new HopXmlException( BaseMessages.getString( PKG, "JobXMLWellFormed.Error.Exception.UnableLoadXML" ), xe );
    }
  }


  public Result execute(Result previousResult, int nr ) throws HopException {
    Result result = previousResult;
    result.setNrErrors( 1 );
    result.setResult( false );

    List<RowMetaAndData> rows = result.getRows();
    RowMetaAndData resultRow = null;

    NrErrors = 0;
    NrWellFormed = 0;
    NrBadFormed = 0;
    limitFiles = Const.toInt( environmentSubstitute( getNrErrorsLessThan() ), 10 );
    successConditionBroken = false;
    successConditionBrokenExit = false;

    // Get source and destination files, also wildcard
    String[] vsourcefilefolder = source_filefolder;
    String[] vwildcard = wildcard;

    if ( arg_from_previous ) {
      if ( log.isDetailed() ) {
        logDetailed( BaseMessages.getString( PKG, "JobXMLWellFormed.Log.ArgFromPrevious.Found", ( rows != null ? rows
            .size() : 0 )
            + "" ) );
      }

    }
    if ( arg_from_previous && rows != null ) {
      // Copy the input row to the (command line) arguments
      for ( int iteration = 0; iteration < rows.size() && !parentWorkflow.isStopped(); iteration++ ) {
        if ( successConditionBroken ) {
          if ( !successConditionBrokenExit ) {
            logError( BaseMessages.getString( PKG, "JobXMLWellFormed.Error.SuccessConditionbroken", "" + NrAllErrors ) );
            successConditionBrokenExit = true;
          }
          result.setEntryNr( NrAllErrors );
          result.setNrLinesRejected( NrBadFormed );
          result.setNrLinesWritten( NrWellFormed );
          return result;
        }

        resultRow = rows.get( iteration );

        // Get source and destination file names, also wildcard
        String vsourcefilefolder_previous = resultRow.getString( 0, null );
        String vwildcard_previous = resultRow.getString( 1, null );

        if ( log.isDetailed() ) {
          logDetailed( BaseMessages.getString( PKG, "JobXMLWellFormed.Log.ProcessingRow", vsourcefilefolder_previous,
              vwildcard_previous ) );
        }

        processFileFolder( vsourcefilefolder_previous, vwildcard_previous, parentWorkflow, result );
      }
    } else if ( vsourcefilefolder != null ) {
      for ( int i = 0; i < vsourcefilefolder.length && !parentWorkflow.isStopped(); i++ ) {
        if ( successConditionBroken ) {
          if ( !successConditionBrokenExit ) {
            logError( BaseMessages.getString( PKG, "JobXMLWellFormed.Error.SuccessConditionbroken", "" + NrAllErrors ) );
            successConditionBrokenExit = true;
          }
          result.setEntryNr( NrAllErrors );
          result.setNrLinesRejected( NrBadFormed );
          result.setNrLinesWritten( NrWellFormed );
          return result;
        }

        if ( log.isDetailed() ) {
          logDetailed( BaseMessages.getString( PKG, "JobXMLWellFormed.Log.ProcessingRow", vsourcefilefolder[i],
              vwildcard[i] ) );
        }

        processFileFolder( vsourcefilefolder[i], vwildcard[i], parentWorkflow, result );

      }
    }

    // Success Condition
    result.setNrErrors( NrAllErrors );
    result.setNrLinesRejected( NrBadFormed );
    result.setNrLinesWritten( NrWellFormed );
    if ( getSuccessStatus() ) {
      result.setNrErrors( 0 );
      result.setResult( true );
    }

    displayResults();

    return result;
  }

  private void displayResults() {
    if ( log.isDetailed() ) {
      logDetailed( "=======================================" );
      logDetailed( BaseMessages.getString( PKG, "JobXMLWellFormed.Log.Info.FilesInError", "" + NrErrors ) );
      logDetailed( BaseMessages.getString( PKG, "JobXMLWellFormed.Log.Info.FilesInBadFormed", "" + NrBadFormed ) );
      logDetailed( BaseMessages.getString( PKG, "JobXMLWellFormed.Log.Info.FilesInWellFormed", "" + NrWellFormed ) );
      logDetailed( "=======================================" );
    }
  }

  private boolean checkIfSuccessConditionBroken() {
    boolean retval = false;
    if ( ( NrAllErrors > 0 && getSuccessCondition().equals( SUCCESS_IF_NO_ERRORS ) )
        || ( NrBadFormed >= limitFiles && getSuccessCondition().equals( SUCCESS_IF_BAD_FORMED_FILES_LESS ) ) ) {
      retval = true;
    }
    return retval;
  }

  private boolean getSuccessStatus() {
    boolean retval = false;

    if ( ( NrAllErrors == 0 && getSuccessCondition().equals( SUCCESS_IF_NO_ERRORS ) )
        || ( NrWellFormed >= limitFiles && getSuccessCondition().equals( SUCCESS_IF_AT_LEAST_X_FILES_WELL_FORMED ) )
        || ( NrBadFormed < limitFiles && getSuccessCondition().equals( SUCCESS_IF_BAD_FORMED_FILES_LESS ) ) ) {
      retval = true;
    }

    return retval;
  }

  private void updateErrors() {
    NrErrors++;
    updateAllErrors();
    if ( checkIfSuccessConditionBroken() ) {
      // Success condition was broken
      successConditionBroken = true;
    }
  }

  private void updateAllErrors() {
    NrAllErrors = NrErrors + NrBadFormed;
  }

  public static class XMLTreeHandler extends DefaultHandler {

  }

  private boolean CheckFile( FileObject file ) {
    boolean retval = false;
    try {
      retval = XmlCheck.isXmlFileWellFormed( file );
    } catch ( Exception e ) {
      logError( BaseMessages.getString( PKG, "JobXMLWellFormed.Log.ErrorCheckingFile", file.toString(), e.getMessage() ) );
    }

    return retval;
  }

  private boolean processFileFolder(String sourcefilefoldername, String wildcard, IWorkflowEngine parentWorkflow, Result result ) {
    boolean entrystatus = false;
    FileObject sourcefilefolder = null;
    FileObject CurrentFile = null;

    // Get real source file and wilcard
    String realSourceFilefoldername = environmentSubstitute( sourcefilefoldername );
    if ( Utils.isEmpty( realSourceFilefoldername ) ) {
      logError( BaseMessages.getString( PKG, "JobXMLWellFormed.log.FileFolderEmpty", sourcefilefoldername ) );
      // Update Errors
      updateErrors();

      return entrystatus;
    }
    String realWildcard = environmentSubstitute( wildcard );

    try {
      sourcefilefolder = HopVfs.getFileObject( realSourceFilefoldername );

      if ( sourcefilefolder.exists() ) {
        if ( log.isDetailed() ) {
          logDetailed( BaseMessages.getString( PKG, "JobXMLWellFormed.Log.FileExists", sourcefilefolder.toString() ) );
        }
        if ( sourcefilefolder.getType() == FileType.FILE ) {
          entrystatus = checkOneFile( sourcefilefolder, result, parentWorkflow );

        } else if ( sourcefilefolder.getType() == FileType.FOLDER ) {
          FileObject[] fileObjects = sourcefilefolder.findFiles( new AllFileSelector() {
            public boolean traverseDescendents( FileSelectInfo info ) {
              return true;
            }

            public boolean includeFile( FileSelectInfo info ) {

              FileObject fileObject = info.getFile();
              try {
                if ( fileObject == null ) {
                  return false;
                }
                if ( fileObject.getType() != FileType.FILE ) {
                  return false;
                }
              } catch ( Exception ex ) {
                // Upon error don't process the file.
                return false;
              } finally {
                if ( fileObject != null ) {
                  try {
                    fileObject.close();
                  } catch ( IOException ex ) {
                    /* Ignore */
                  }
                }

              }
              return true;
            }
          } );

          if ( fileObjects != null ) {
            for ( int j = 0; j < fileObjects.length && !parentWorkflow.isStopped(); j++ ) {
              if ( successConditionBroken ) {
                if ( !successConditionBrokenExit ) {
                  logError( BaseMessages.getString( PKG, "JobXMLWellFormed.Error.SuccessConditionbroken", ""
                      + NrAllErrors ) );
                  successConditionBrokenExit = true;
                }
                return false;
              }
              // Fetch files in list one after one ...
              CurrentFile = fileObjects[j];

              if ( !CurrentFile.getParent().toString().equals( sourcefilefolder.toString() ) ) {
                // Not in the Base Folder..Only if include sub folders
                if ( include_subfolders ) {
                  if ( GetFileWildcard( CurrentFile.toString(), realWildcard ) ) {
                    checkOneFile( CurrentFile, result, parentWorkflow );
                  }
                }

              } else {
                // In the base folder
                if ( GetFileWildcard( CurrentFile.toString(), realWildcard ) ) {
                  checkOneFile( CurrentFile, result, parentWorkflow );
                }
              }
            }
          }
        } else {
          logError( BaseMessages
              .getString( PKG, "JobXMLWellFormed.Error.UnknowFileFormat", sourcefilefolder.toString() ) );
          // Update Errors
          updateErrors();
        }
      } else {
        logError( BaseMessages.getString( PKG, "JobXMLWellFormed.Error.SourceFileNotExists", realSourceFilefoldername ) );
        // Update Errors
        updateErrors();
      }
    } catch ( Exception e ) {
      logError( BaseMessages.getString( PKG, "JobXMLWellFormed.Error.Exception.Processing", realSourceFilefoldername
          .toString(), e ) );
      // Update Errors
      updateErrors();
    } finally {
      if ( sourcefilefolder != null ) {
        try {
          sourcefilefolder.close();
        } catch ( IOException ex ) {
          /* Ignore */
        }

      }
      if ( CurrentFile != null ) {
        try {
          CurrentFile.close();
        } catch ( IOException ex ) {
          /* Ignore */
        }
      }
    }
    return entrystatus;
  }

  private boolean checkOneFile( FileObject file, Result result, IWorkflowEngine parentWorkflow ) throws HopException {
    boolean retval = false;
    try {
      // We deal with a file..so let's check if it's well formed
      boolean retformed = CheckFile( file );
      if ( !retformed ) {
        logError( BaseMessages.getString( PKG, "JobXMLWellFormed.Error.FileBadFormed", file.toString() ) );
        // Update Bad formed files number
        updateBadFormed();
        if ( resultfilenames.equals( ADD_ALL_FILENAMES ) || resultfilenames.equals( ADD_BAD_FORMED_FILES_ONLY ) ) {
          addFileToResultFilenames( HopVfs.getFilename( file ), result, parentWorkflow );
        }
      } else {
        if ( log.isDetailed() ) {
          logDetailed( "---------------------------" );
          logDetailed( BaseMessages.getString( PKG, "JobXMLWellFormed.Error.FileWellFormed", file.toString() ) );
        }
        // Update Well formed files number
        updateWellFormed();
        if ( resultfilenames.equals( ADD_ALL_FILENAMES ) || resultfilenames.equals( ADD_WELL_FORMED_FILES_ONLY ) ) {
          addFileToResultFilenames( HopVfs.getFilename( file ), result, parentWorkflow );
        }
      }

    } catch ( Exception e ) {
      throw new HopException( "Unable to verify file '" + file + "'", e );
    }
    return retval;
  }

  private void updateWellFormed() {
    NrWellFormed++;
  }

  private void updateBadFormed() {
    NrBadFormed++;
    updateAllErrors();
  }

  private void addFileToResultFilenames( String fileaddentry, Result result, IWorkflowEngine parentWorkflow ) {
    try {
      ResultFile resultFile =
          new ResultFile( ResultFile.FILE_TYPE_GENERAL, HopVfs.getFileObject( fileaddentry ), parentWorkflow
              .getWorkflowName(), toString() );
      result.getResultFiles().put( resultFile.getFile().toString(), resultFile );

      if ( log.isDetailed() ) {
        logDetailed( BaseMessages.getString( PKG, "JobXMLWellFormed.Log.FileAddedToResultFilesName", fileaddentry ) );
      }

    } catch ( Exception e ) {
      logError( BaseMessages.getString( PKG, "JobXMLWellFormed.Error.AddingToFilenameResult", fileaddentry, e
          .getMessage() ) );
    }

  }

  /**********************************************************
   * 
   * @param selectedfile
   * @param wildcard
   * @return True if the selectedfile matches the wildcard
   **********************************************************/
  private boolean GetFileWildcard( String selectedfile, String wildcard ) {
    Pattern pattern = null;
    boolean getIt = true;

    if ( !Utils.isEmpty( wildcard ) ) {
      pattern = Pattern.compile( wildcard );
      // First see if the file matches the regular expression!
      if ( pattern != null ) {
        Matcher matcher = pattern.matcher( selectedfile );
        getIt = matcher.matches();
      }
    }

    return getIt;
  }

  public boolean isIncludeSubfolders() {
    return include_subfolders;
  }

  public void setIncludeSubfolders( boolean include_subfoldersin ) {
    this.include_subfolders = include_subfoldersin;
  }

  public boolean isArgFromPrevious() {
    return arg_from_previous;
  }

  public void setArgFromPrevious( boolean argfrompreviousin ) {
    this.arg_from_previous = argfrompreviousin;
  }

  public void setNrErrorsLessThan( String nr_errors_less_than ) {
    this.nr_errors_less_than = nr_errors_less_than;
  }

  public String[] getSourceFileFolders() {
    return source_filefolder;
  }

  public void setSourceFileFolders( String[] filefolders ) {
    this.source_filefolder = filefolders;
  }

  public String[] getSourceWildcards() {
    return wildcard;
  }

  public void setSourceWildcards( String[] wildcards ) {
    this.wildcard = wildcards;
  }

  public String getNrErrorsLessThan() {
    return nr_errors_less_than;
  }

  public void setSuccessCondition( String success_condition ) {
    this.success_condition = success_condition;
  }

  public String getSuccessCondition() {
    return success_condition;
  }

  public void setResultFilenames( String resultfilenames ) {
    this.resultfilenames = resultfilenames;
  }

  public String getResultFilenames() {
    return resultfilenames;
  }

  public boolean evaluates() {
    return true;
  }

  public void check(List<ICheckResult> remarks, WorkflowMeta jobMeta, IVariables space, IHopMetadataProvider metadataProvider ) {
    boolean res = ActionValidatorUtils.andValidator().validate( this, "arguments", remarks, AndValidator.putValidators( ActionValidatorUtils.notNullValidator() ) );

    if ( res == false ) {
      return;
    }

    ValidatorContext ctx = new ValidatorContext();
    AbstractFileValidator.putVariableSpace( ctx, getVariables() );
    AndValidator.putValidators( ctx, ActionValidatorUtils.notNullValidator(), ActionValidatorUtils.fileExistsValidator() );

    for ( int i = 0; i < source_filefolder.length; i++ ) {
      ActionValidatorUtils.andValidator().validate( this, "arguments[" + i + "]", remarks, ctx );
    }
  }

}
