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
  private String googleClientSecretFilePath;
  private String googleCredentialsFolder;

  public GoogleDiskService(String googleClientSecretFilePath, String googleCredentialsFolder) {
    this.googleClientSecretFilePath = googleClientSecretFilePath;
    this.googleCredentialsFolder = googleCredentialsFolder;
  }

  public void showFiles() {
    Drive googleDiskAPI = this.getGoogleDiskAPI();

    // Print the names and IDs for up to 10 files.
    FileList result;
    try {
      result = googleDiskAPI
        .files()
        .list()
        .setFields("nextPageToken, files(id, name)")
        .execute();
    } catch (IOException e) {
      throw new IllegalArgumentException("Can't get list of Google files");
    }

    List<com.google.api.services.drive.model.File> files = result.getFiles();

    if (files == null || files.isEmpty()) {
      System.out.println("No files found.");
    } else {
      System.out.println("Files:");
      for (com.google.api.services.drive.model.File file : files) {
        System.out.printf("%s (%s)\n", file.getName(), file.getId());
      }
    }
  }

  private Drive getGoogleDiskAPI() {
    NetHttpTransport netHttpTransport;
    try {
      netHttpTransport = GoogleNetHttpTransport.newTrustedTransport();
    } catch (Exception e) {
      throw new IllegalArgumentException("Can't get GoogleNetHttpTransport");
    }

    Credential credential = getCredentials(netHttpTransport);

    return new Drive.Builder(netHttpTransport, JSON_FACTORY, credential)
      .setApplicationName(APPLICATION_NAME)
      .build();
  }

  private Credential getCredentials(NetHttpTransport netHttpTransport) {
    File clientSecretFile = new File(this.googleClientSecretFilePath);

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

    File clientCredentialsFolder = new File(this.googleCredentialsFolder);
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
