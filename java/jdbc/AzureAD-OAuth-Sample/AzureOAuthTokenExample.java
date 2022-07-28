/* Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.*/
/*
   DESCRIPTION
   This code example shows how to use JDBC and UCP's programmatic APIs for
   database authentication, using a token issued by the Active Directory (AD)
   service of Microsoft Azure.

   To run this example, Oracle Database must be configured for Azure AD
   authentication, as described in the Security Guide:
   https://docs.oracle.com/en/database/oracle/oracle-database/19/dbseg/authenticating-and-authorizing-microsoft-azure-active-directory-users-oracle-autonomous-databas.html#GUID-2712902B-DD07-4A61-B336-31C504781D0F

   To run this example, the Azure SDK for Java must be configured to
   authenticate with Azure AD. The Developer Guide describes how to setup and
   configure the SDK:
   https://docs.microsoft.com/en-us/azure/developer/java/sdk/
   This example uses the SDK's {@code DefaultAzureCredentials}, which can
   authenticate in a variety of ways, depending on the environment settings. See
   the JavaDoc for more information:
   https://docs.microsoft.com/en-us/java/api/com.azure.identity.defaultazurecredential?view=azure-java-stable
   Any environement variables required for authentication must be set to run
   this example.

   To run this example, use JDK 11 or newer, and have the classpath include
   the latest builds of Oracle JDBC, Oracle UCP, Oracle PKI, and the Azure SDK
   for Java. These artifacts can be obtained from Maven Central by declaring
   these dependencies:
     <dependencyManagement>
      <dependencies>
          <dependency>
              <groupId>com.azure</groupId>
              <artifactId>azure-sdk-bom</artifactId>
              <version>${bom.version}</version>
              <type>pom</type>
              <scope>import</scope>
          </dependency>
      </dependencies>
    </dependencyManagement>
    <dependency>
      <groupId>com.oracle.database.jdbc</groupId>
      <artifactId>ojdbc11-production</artifactId>
      <version>21.6.0.0.1</version>
      <type>pom</type>
    </dependency>
    <dependency>
      <groupId>com.azure</groupId>
      <artifactId>azure-identity</artifactId>
    </dependency>

   To run this example, set the values of static final fields declared in
   this class:
   DATABASE_URL = URL of an Autonomous Database that JDBC connects to
   DATABASE_APP_ID_URI = URI of an Autonomous Database that is registered with Azure AD

   NOTES
    Use JDK 8 or above
   MODIFIED          (MM/DD/YY)
    Michael-A-McMahon 05/31/22 - Creation
 */

import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import oracle.jdbc.AccessToken;
import oracle.jdbc.OracleConnectionBuilder;
import oracle.jdbc.datasource.OracleDataSource;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * The following is a summary of methods that can be found in this class,
 * with a brief description of what task each method performs:
 *
 * requestToken(PublicKey) shows how to request a token from Azure.
 *
 * createAccessToken() shows how to create an instance of
 * oracle.jdbc.AccessToken using the token requested from Azure.
 *
 * connectJdbc() shows how to create a single JDBC connection using an
 * AccessToken.
 *
 * connectJdbcDataSource() shows how to create multiple JDBC connections
 * using a Supplier that outputs a cached AccessToken.
 *
 * connectUcpDataSource() shows how to create a pool of JDBC connections
 * using a Supplier that outputs a cached AccessToken.
 */
public class AzureTokenExample {

  /**
   * The URL that JDBC connects with. The default value is using an
   * alias from $TNS_ADMIN/tnsnames.ora
   */
  private static final String DATABASE_URL =
    /*TODO: Set this to your database url*/
    //"jdbc:oracle:thin:@your_db_name_tp?TNS_ADMIN=/path/to/your/wallet";
    "jdbc:oracle:thin:@test?TNS_ADMIN=/Users/michaelmcmahon/iam-auth/Wallet_oauth-db";

  /**
   * The Application ID URI of an Oracle Database. This value can be obtained
   * from the registered applications overview page in Azure.
   */
  private static final String DATABASE_APP_ID_URI =
    /*TODO: Set this to your database Application ID URI*/
    //"https://<tenant-host>/<app-id>";
    "https://oracledevelopment.onmicrosoft.com/3d3e9766-503d-4870-a821-b0544c4224e5";


  // Print the configured values in this static block
  static {
    System.out.println("DATABASE_URL is set to: " + DATABASE_URL);
    System.out.println("DATABASE_APP_ID_URI is set to: " + DATABASE_APP_ID_URI);
  }

  /**
   * This main method executes example code to connect with both JDBC and UCP
   */
  public static void main(String[] args) {
    connectJdbc();
    connectJdbcDataSource();
    connectUcpDataSource();
  }

  /**
   * <p>
   * Creates an {@link AccessToken} that JDBC or UCP can use to authenticate
   * with Oracle Database. The token is requested from Azure. The scope of the
   * request uses the "/.default" path.
   * </p><p>
   * The request is authenticated using the Azure SDK's
   * {@link com.azure.identity.DefaultAzureCredential}. This means that
   * environment variables can configure different ways to authenticate. See
   * https://docs.microsoft.com/en-us/java/api/com.azure.identity.defaultazurecredential?view=azure-java-stable
   * </p>
   * @return An AccessToken from Azure
   */
  private static AccessToken createAccessToken() {
    return new DefaultAzureCredentialBuilder().build()
      .getToken(new TokenRequestContext().addScopes(
        DATABASE_APP_ID_URI + "/.default"))
      .map(azureToken ->
        // Map the Azure SDK's AccessToken to Oracle JDBC's AccessToken
        AccessToken.createJsonWebToken(azureToken.getToken().toCharArray()))
      .block(Duration.ofMinutes(1));
  }

  /**
   * Creates a single connection using Oracle JDBC. A call to
   * {@link oracle.jdbc.OracleConnectionBuilder#accessToken(AccessToken)}
   * configures JDBC to authenticate with a token requested from Azure
   * Active Directory.
   */
  private static void connectJdbc() {
    try {
      // Create a single AccessToken
      AccessToken accessToken = createAccessToken();

      // Configure an OracleConnectionBuilder to authenticate with the
      // AccessToken
      OracleDataSource dataSource = new oracle.jdbc.pool.OracleDataSource();
      dataSource.setURL(DATABASE_URL);
      OracleConnectionBuilder connectionBuilder =
        dataSource.createConnectionBuilder()
          .accessToken(accessToken);

      // Connect and print the database user name
      try (Connection connection = connectionBuilder.build()) {
        System.out.println(
          "Authenticated with JDBC as: " + queryUser(connection));
      }
    }
    catch (SQLException sqlException) {
      // Not recovering if the connection fails
      throw new RuntimeException(sqlException);
    }
  }

  /**
   * Creates multiple connections with Oracle JDBC. A call
   * to {@link OracleDataSource#setTokenSupplier(Supplier)} configures JDBC to
   * authenticate with tokens output by the {@link Supplier}. The
   * {@code Supplier} requests tokens from Azure Active Directory.
   */
  private static void connectJdbcDataSource() {
    try {

      // Define a Supplier that outputs a cached AccessToken. Caching the
      // token will minimize the number of Azure Active Directory requests. New
      // tokens will only be requested after a previously cached token has
      // expired.
      Supplier<? extends AccessToken> tokenCache =
        AccessToken.createJsonWebTokenCache(() -> createAccessToken());

      // Configure an OracleConnectionBuilder to authenticate with the
      // AccessToken
      OracleDataSource dataSource = new oracle.jdbc.pool.OracleDataSource();
      dataSource.setURL(DATABASE_URL);
      dataSource.setTokenSupplier(tokenCache);

      // Create multiple connections and print the database user name
      for (int i = 0; i < 3; i++) {
        try (Connection connection = dataSource.getConnection()) {
          System.out.println(
            "Authenticated with JDBC as: " + queryUser(connection));
        }
      }
    }
    catch (SQLException sqlException) {
      // Not recovering if the connection fails
      throw new RuntimeException(sqlException);
    }
  }

  /**
   * Creates multiple connections with Universal Connection Pool (UCP). A call
   * to {@link PoolDataSource#setTokenSupplier(Supplier)} configures UCP to
   * authenticate with tokens output by the {@link Supplier}. The
   * {@code Supplier} requests tokens from Azure Active Directory.
   */
  private static void connectUcpDataSource() {

    // Define a Supplier that outputs a cached AccessToken. Caching the
    // token will minimize the number of Azure Active Directory requests. New
    // tokens will only be requested after a previously cached token has
    // expired.
    Supplier<? extends AccessToken> tokenCache =
      AccessToken.createJsonWebTokenCache(() -> createAccessToken());

    // Configure UCP to use the cached token supplier when creating
    // Oracle JDBC connections
    final PoolDataSource poolDataSource;
    try {
      poolDataSource = PoolDataSourceFactory.getPoolDataSource();
      poolDataSource.setConnectionFactoryClassName(
        oracle.jdbc.pool.OracleDataSource.class.getName());
      poolDataSource.setURL(DATABASE_URL);
      poolDataSource.setMaxPoolSize(2);
      poolDataSource.setTokenSupplier(tokenCache);
    }
    catch (SQLException sqlException) {
      // Not recovering if UCP configuration fails
      throw new RuntimeException(sqlException);
    }

    // Execute multiple threads that share the pool of connections
    ExecutorService executorService =
      Executors.newFixedThreadPool(poolDataSource.getMaxPoolSize());
    try {
      for (int i = 0; i < poolDataSource.getMaxPoolSize() * 2; i++) {
        executorService.execute(() -> {
          try (Connection connection = poolDataSource.getConnection()) {
            System.out.println(
              "Authenticated with UCP as: " + queryUser(connection));
          }
          catch (SQLException sqlException) {
            sqlException.printStackTrace();
          }
        });
      }
    }
    finally {
      executorService.shutdown();
      try {
        executorService.awaitTermination(60, SECONDS);
      }
      catch (InterruptedException interruptedException) {
        // Print the error if interrupted
        interruptedException.printStackTrace();
      }
    }
  }

  /**
   * Queries the database to return the user that a {@code connection} has
   * authenticated as.
   * @param connection Connection to a database
   * @return Database user of the connection
   * @throws SQLException If the database query fails
   */
  private static String queryUser(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      ResultSet resultSet =
        statement.executeQuery("SELECT USER FROM sys.dual");
      resultSet.next();
      return resultSet.getString(1);
    }
  }
}

