package software.xdev;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.serializer.persistence.exceptions.PersistenceException;
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

import software.xdev.eclipse.store.afs.ibm.access.AccessConfiguration;
import software.xdev.eclipse.store.afs.ibm.access.SingleAccessManager;
import software.xdev.eclipse.store.afs.ibm.cos.types.CosConnector;


public final class ApplicationWithSingleAccess
{
	private static final String COS_ENDPOINT = ""; // eg "https://s3.us.cloud-object-storage.appdomain.cloud"
	private static final String COS_API_KEY_ID = ""; // eg "0viPHOY7LbLNa9eLftrtHPpTjoGv6hbLD1QalRXikliJ"
	private static final String COS_SERVICE_CRN = "";
	// "crn:v1:bluemix:public:cloud-object-storage:global:a/<CREDENTIAL_ID_AS_GENERATED>:<SERVICE_ID_AS_GENERATED>::"
	private static final String COS_BUCKET_LOCATION = ""; // eg "us"
	private static final String BUCKET_NAME = "";
	
	private static final Logger LOG = LoggerFactory.getLogger(ApplicationWithSingleAccess.class);
	
	public static void main(final String[] args)
	{
		final AmazonS3 client = createClient(COS_API_KEY_ID, COS_SERVICE_CRN, COS_ENDPOINT, COS_BUCKET_LOCATION);
		try(final SingleAccessManager accessManager = new SingleAccessManager(
			new AccessConfiguration(BUCKET_NAME),
			client))
		{
			accessManager.waitForAndReserveSingleAccess();
			final List<String> stringList = new ArrayList<>();
			final long pid = ProcessHandle.current().pid();
			LOG.info("Process ID: {}", pid);
			try(final EmbeddedStorageManager storageManager = getStorageManager(stringList, client))
			{
				LOG.info("List size after loading: {}", stringList.size());
				accessManager.shutdownStorageWhenAccessShouldTerminate(storageManager);
				int i = 0;
				while(true)
				{
					i++;
					final String newData = String.format("Number %d written by client with pid %d", i, pid);
					stringList.add(newData);
					storageManager.store(stringList);
					LOG.info("Wrote new Data: {}", newData);
				}
			}
			catch(final PersistenceException e)
			{
				LOG.warn("Storage was shutdown.", e);
			}
			LOG.info("Process terminated.");
		}
	}
	
	public static EmbeddedStorageManager getStorageManager(final Object root, final AmazonS3 client)
	{
		final BlobStoreFileSystem cloudFileSystem = BlobStoreFileSystem.New(CosConnector.Caching(client));
		return EmbeddedStorage.start(
			root,
			cloudFileSystem.ensureDirectoryPath(BUCKET_NAME));
	}
	
	public static AmazonS3 createClient(
		final String apiKey,
		final String serviceInstanceId,
		final String endpointUrl,
		final String location)
	{
		final AWSCredentials credentials = new BasicIBMOAuthCredentials(apiKey, serviceInstanceId);
		final ClientConfiguration clientConfig = new ClientConfiguration().withRequestTimeout(-1);
		clientConfig.setUseTcpKeepAlive(true);
		
		return AmazonS3ClientBuilder.standard()
			.withCredentials(new AWSStaticCredentialsProvider(credentials))
			.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpointUrl, location))
			.withPathStyleAccessEnabled(true)
			.withClientConfiguration(clientConfig)
			.build();
	}
	
	private ApplicationWithSingleAccess()
	{
	}
}
