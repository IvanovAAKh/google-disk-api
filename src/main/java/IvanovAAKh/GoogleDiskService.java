package IvanovAAKh;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
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

  public static void main(String ...args) {
    GoogleDiskService googleDiskService = new GoogleDiskService(
      "D:/projects/My/google_client_secret.json",
      "D:/projects/My"
      );

    System.out.println(googleDiskService.getFileOutputStream("7 kurs/test_folder/test1.txt"));
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

  public OutputStream getFileOutputStream(String path) {
    String[] filePathItems = path.split("/");
    String currentParentFolderId = "root";
    String foundFileId = null;

    for (int i = 0; i < filePathItems.length; i++) {
      boolean isFileName = i == filePathItems.length - 1;
      if (isFileName) {
        foundFileId = getFileId(currentParentFolderId, filePathItems[i], false);
      } else {
        currentParentFolderId = getFileId(currentParentFolderId, filePathItems[i], true);
      }
    }

    OutputStream outputStream = new ByteArrayOutputStream();
    if (foundFileId != null) {
      try {
        googleDiskAPI.files().get(foundFileId).executeMediaAndDownloadTo(outputStream);
      } catch (Exception ex) {
        throw new IllegalArgumentException("Can't get file by its id '" + foundFileId + "'", ex);
      }
    }

    return outputStream;
  }

  private Drive getGoogleDiskAPI(String googleClientSecretFilePath, String googleCredentialsFolder) {
    NetHttpTransport netHttpTransport;
    try {
      netHttpTransport = GoogleNetHttpTransport.newTrustedTransport();
    } catch (Exception e) {
      throw new IllegalArgumentException("Can't get GoogleNetHttpTransport");
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
    } catch (FileNotFoundException e) {
      throw new IllegalArgumentException("Can't load clientSecretFile");
    }

    GoogleClientSecrets clientSecrets;
    try {
      clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(clientSecretFileInputStream));
    } catch (IOException e) {
      throw new IllegalArgumentException("Can't get clientSecrets");
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
    } catch (IOException e) {
      throw new IllegalArgumentException("Can't build GoogleAuthorizationCodeFlow");
    }

    Credential credential;
    try {
      credential = new AuthorizationCodeInstalledApp(googleAuthorizationCodeFlow, new LocalServerReceiver())
        .authorize("user");
    } catch (IOException e) {
      throw new IllegalArgumentException("Can't authorize");
    }

    return credential;
  }
}
