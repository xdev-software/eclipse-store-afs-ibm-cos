package software.xdev;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.cloud.objectstorage.ClientConfiguration;
import com.ibm.cloud.objectstorage.auth.AWSCredentials;
import com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder;
import com.ibm.cloud.objectstorage.oauth.BasicIBMOAuthCredentials;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;

import software.xdev.eclipse.store.afs.ibm.cos.types.CosConnector;


@SuppressWarnings("checkstyle:MagicNumber")
public final class Application
{
	private static final String COS_ENDPOINT = ""; // eg "https://s3.us.cloud-object-storage.appdomain.cloud"
	private static final String COS_API_KEY_ID = ""; // eg "0viPHOY7LbLNa9eLftrtHPpTjoGv6hbLD1QalRXikliJ"
	private static final String COS_SERVICE_CRN = "";
	// "crn:v1:bluemix:public:cloud-object-storage:global:a/<CREDENTIAL_ID_AS_GENERATED>:<SERVICE_ID_AS_GENERATED>::"
	private static final String COS_BUCKET_LOCATION = ""; // eg "us"
	private static final String BUCKET_NAME = "";
	
	private static final Logger LOG = LoggerFactory.getLogger(Application.class);
	
	/**
	 * This function connects to the IBM COS and writes one million String-Entries on it.
	 */
	public static void main(final String[] args)
	{
		final List<String> stringList = new ArrayList<>();
		LOG.info("List size before loading: {}", stringList.size());
		try(final EmbeddedStorageManager manager = getStorageManager(stringList))
		{
			LOG.info("List size after loading: {}", stringList.size());
			for(int i = 0; i < 1_000_000; i++)
			{
				stringList.add("Test" + i);
			}
			manager.store(stringList);
			LOG.info("List size after storing new entities: {}", stringList.size());
		}
	}
	
	public static EmbeddedStorageManager getStorageManager(final Object root)
	{
		final AmazonS3 client = createClient(COS_API_KEY_ID, COS_SERVICE_CRN, COS_ENDPOINT, COS_BUCKET_LOCATION);
		
		LOG.info("Start creating file system");
		final BlobStoreFileSystem cloudFileSystem = BlobStoreFileSystem.New(
			// use caching connector
			CosConnector.Caching(client)
		);
		LOG.info("Finished creating file system");
		
		LOG.info("Starting storage manager");
		final EmbeddedStorageManager storageManager = EmbeddedStorage.start(
			root,
			cloudFileSystem.ensureDirectoryPath(BUCKET_NAME));
		LOG.info("Finished storage manager");
		
		return storageManager;
	}
	
	public static AmazonS3 createClient(
		final String apiKey,
		final String serviceInstanceId,
		final String endpointUrl,
		final String location)
	{
		LOG.info("Start creating client");
		final AWSCredentials credentials = new BasicIBMOAuthCredentials(apiKey, serviceInstanceId);
		final ClientConfiguration clientConfig = new ClientConfiguration().withRequestTimeout(-1);
		clientConfig.setUseTcpKeepAlive(true);
		
		final AmazonS3 build = AmazonS3ClientBuilder.standard()
			.withCredentials(new AWSStaticCredentialsProvider(credentials))
			.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpointUrl, location))
			.withPathStyleAccessEnabled(true)
			.withClientConfiguration(clientConfig)
			.build();
		LOG.info("Finished creating client");
		return build;
	}
	
	private Application()
	{
	}
}
