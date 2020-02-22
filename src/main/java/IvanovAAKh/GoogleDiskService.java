package IvanovAAKh;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.*;
import java.util.Collections;
import java.util.List;

// example took from: https://o7planning.org/en/11889/manipulating-files-and-folders-on-google-drive-using-java
public class GoogleDiskService {
  private static final String APPLICATION_NAME = "Historical data collector";
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  // Global instance of the scopes required by this quickstart. If modifying these
  // scopes, delete your previously saved credentials/ folder.
  private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
  private Drive googleDiskAPI;

  GoogleDiskService(String googleClientSecretFilePath, String googleCredentialsFolder) {
    this.googleDiskAPI = this.getGoogleDiskAPI(googleClientSecretFilePath, googleCredentialsFolder);
  }

  // Remember that in Google Disk folder - is also file
  private String getFileId(String parentFolderId, String fileName, boolean isFolder) {
    StringBuilder fileSearchQueryBuilder = new StringBuilder();
    fileSearchQueryBuilder.append("name = '").append(fileName).append("'");
    fileSearchQueryBuilder.append(" and '").append(parentFolderId).append("' in parents");
    if (isFolder) {
      fileSearchQueryBuilder.append(" and mimeType = 'application/vnd.google-apps.folder'");
    } else {
      fileSearchQueryBuilder.append(" and mimeType != 'application/vnd.google-apps.folder'");
    }

    String pageToken = null;
    String foundFileId = null;
    try {
      do {
        FileList result = this.googleDiskAPI
          .files()
          .list()
          .setQ(fileSearchQueryBuilder.toString())
          .setSpaces("drive")
          .setFields("nextPageToken, files(id)")
          .setPageToken(pageToken)
          .execute();

        List<File> foundFiles = result.getFiles();
        if (foundFiles.size() > 1) {
          throw new IllegalArgumentException("Google Disk can't have duplicated name in folder");
        }

        if (foundFiles.size() > 0) {
          foundFileId = foundFiles.get(0).getId();
        }
        pageToken = result.getNextPageToken();
      } while (pageToken != null && foundFileId == null);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Error on fetch files from Google Disk", ex);
    }

    return foundFileId;
  }

  private boolean isFoldersPathExists(String path) {
    String[] pathFolders = path.split("/");

    String currentParentFolderId = "root";
    for(String pathFolderName : pathFolders) {
      String foundFolderId = getFileId(currentParentFolderId, pathFolderName, true);
      if (foundFolderId == null) {
        return false;
      }
      currentParentFolderId = foundFolderId;
    }

    return true;
  }

  private File createFolder(String parentFolderId, String folderName) {
    File fileMetadata = new File();

    fileMetadata.setName(folderName);
    fileMetadata.setMimeType("application/vnd.google-apps.folder");

    if (parentFolderId != null) {
      List<String> parents = Collections.singletonList(parentFolderId);
      fileMetadata.setParents(parents);
    }

    File createdFolder = null;
    try {
      createdFolder = this.googleDiskAPI
        .files()
        .create(fileMetadata)
        .setFields("id, name")
        .execute();
    } catch (IOException ex) {
      throw new IllegalArgumentException("Can't create folder '" + folderName + "'", ex);
    }

    return createdFolder;
  }

  private void createFoldersPath(String path) {
    String[] pathFolders = path.split("/");

    String currentParentFolderId = "root";
    for(String pathFolderName : pathFolders) {
      String foundFolderId = getFileId(currentParentFolderId, pathFolderName, true);

      if (foundFolderId == null) {
        File createdFolder = createFolder(currentParentFolderId, pathFolderName);
        currentParentFolderId = createdFolder.getId();
      } else {
        currentParentFolderId = foundFolderId;
      }
    }
  }

  private String getLastFolderId(String path) {
    String[] pathFolders = path.split("/");

    String currentParentFolderId = "root";
    for(String pathFolderName : pathFolders) {
      String foundFolderId = getFileId(currentParentFolderId, pathFolderName, true);

      if (foundFolderId == null) {
        return null;
      } else {
        currentParentFolderId = foundFolderId;
      }
    }

    return currentParentFolderId;
  }

  private File createFile(String fullPath, AbstractInputStreamContent uploadStreamContent) {
    String foldersPath = fullPath.substring(0, fullPath.lastIndexOf('/'));

    if (!isFoldersPathExists(foldersPath)) {
      createFoldersPath(foldersPath);
    }

    File fileMetadata = new File();

    String fileName = fullPath.substring(fullPath.lastIndexOf('/') + 1);
    fileMetadata.setName(fileName);

    String parentFolderId = getLastFolderId(foldersPath);
    List<String> parents = Collections.singletonList(parentFolderId);
    fileMetadata.setParents(parents);

    File createdFile = null;

    try {
      createdFile = this.googleDiskAPI
        .files()
        .create(fileMetadata, uploadStreamContent)
        .setFields("id, webContentLink, webViewLink, parents")
        .execute();
    } catch (IOException ex) {
      throw new IllegalArgumentException("Can't create folder '" + fileName + "'", ex);
    }

    return createdFile;
  }

  public String uploadFile(String dstFilePath, InputStream inputStream) {
    AbstractInputStreamContent uploadStreamContent = new InputStreamContent("text/csv", inputStream);
    File createdFile = createFile(dstFilePath, uploadStreamContent);

    return createdFile.getId();
  }

  private File findFileByPath(String path) {
    String[] filePathItems = path.split("/");
    String currentParentFolderId = "root";
    String foundFileId = null;

    for (int i = 0; i < filePathItems.length; i++) {
      boolean isFileName = i == filePathItems.length - 1;
      if (isFileName) {
        foundFileId = getFileId(currentParentFolderId, filePathItems[i], false);
        break;
      } else {
        currentParentFolderId = getFileId(currentParentFolderId, filePathItems[i], true);
      }
    }

    File foundFile = null;
    if (foundFileId != null) {
      try {
        foundFile = this.googleDiskAPI
          .files()
          .get(foundFileId)
          .execute();
      } catch (IOException ex) {
        throw new IllegalArgumentException("Can't get found file by its id", ex);
      }
    }

    return foundFile;
  }

  public ByteArrayOutputStream downloadFile(String path) {
    File foundFile = findFileByPath(path);

    ByteArrayOutputStream baos = null;
    if (foundFile != null) {
      baos = new ByteArrayOutputStream();
      try {
        googleDiskAPI
          .files()
          .get(foundFile.getId())
          .executeMediaAndDownloadTo(baos);
      } catch (Exception ex) {
        throw new IllegalArgumentException("Can't get file by its id '" + foundFile.getId() + "'", ex);
      }
    }

    return baos;
  }

  private Drive getGoogleDiskAPI(String googleClientSecretFilePath, String googleCredentialsFolder) {
    NetHttpTransport netHttpTransport;
    try {
      netHttpTransport = GoogleNetHttpTransport.newTrustedTransport();
    } catch (Exception ex) {
      throw new IllegalArgumentException("Can't get GoogleNetHttpTransport", ex);
    }

    Credential credential = getCredentials(netHttpTransport, googleClientSecretFilePath, googleCredentialsFolder);

    return new Drive.Builder(netHttpTransport, JSON_FACTORY, credential)
      .setApplicationName(APPLICATION_NAME)
      .build();
  }

  private Credential getCredentials(NetHttpTransport netHttpTransport, String googleClientSecretFilePath, String googleCredentialsFolder) {
    java.io.File clientSecretFile = new java.io.File(googleClientSecretFilePath);

    // Load client secrets.
    InputStream clientSecretFileInputStream;
    try {
      clientSecretFileInputStream = new FileInputStream(clientSecretFile);
    } catch (FileNotFoundException ex) {
      throw new IllegalArgumentException("Can't load clientSecretFile", ex);
    }

    GoogleClientSecrets clientSecrets;
    try {
      clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(clientSecretFileInputStream));
    } catch (IOException ex) {
      throw new IllegalArgumentException("Can't get clientSecrets", ex);
    }

    java.io.File clientCredentialsFolder = new java.io.File(googleCredentialsFolder);
    // Build flow and trigger user authorization request.
    GoogleAuthorizationCodeFlow googleAuthorizationCodeFlow;
    try {
      googleAuthorizationCodeFlow = new GoogleAuthorizationCodeFlow
        .Builder(netHttpTransport, JSON_FACTORY, clientSecrets, SCOPES)
        .setDataStoreFactory(new FileDataStoreFactory(clientCredentialsFolder))
        .setAccessType("offline")
        .build();
    } catch (IOException ex) {
      throw new IllegalArgumentException("Can't build GoogleAuthorizationCodeFlow", ex);
    }

    Credential credential;
    try {
      credential = new AuthorizationCodeInstalledApp(googleAuthorizationCodeFlow, new LocalServerReceiver())
        .authorize("user");
    } catch (IOException ex) {
      throw new IllegalArgumentException("Can't authorize", ex);
    }

    return credential;
  }
}
