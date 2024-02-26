package software.xdev.eclipse.store.afs.ibm;

import java.util.Optional;

import org.eclipse.serializer.configuration.exceptions.ConfigurationException;
import org.eclipse.serializer.configuration.types.Configuration;

import com.ibm.cloud.objectstorage.auth.BasicAWSCredentials;
import com.ibm.cloud.objectstorage.auth.DefaultAWSCredentialsProviderChain;
import com.ibm.cloud.objectstorage.auth.EnvironmentVariableCredentialsProvider;
import com.ibm.cloud.objectstorage.auth.SystemPropertiesCredentialsProvider;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3Client;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;


public final class CosClientCreator
{
	public static AmazonS3 createClient(
		final Configuration configuration
	)
	{
		final Configuration s3Configuration = configuration.child("ibm.cos");
		if(s3Configuration == null)
		{
			return null;
		}
		
		final AwsClientBuilder<AmazonS3ClientBuilder, AmazonS3> clientBuilder = AmazonS3Client.builder();
		configureClient(clientBuilder, s3Configuration);
		
		return clientBuilder.build();
	}
	
	private static void configureClient(
		final AwsClientBuilder<?, ?> clientBuilder,
		final Configuration configuration
	)
	{
		configuration.opt("endpoint-override").ifPresent(endpointOverride ->
		{
			final Optional<String> region = configuration.opt("region");
			if(region.isPresent())
			{
				clientBuilder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
					endpointOverride,
					region.get()));
			}
			else
			{
				throw new ConfigurationException(configuration);
			}
		});
		configuration.opt("region").ifPresent(
			region -> clientBuilder.setRegion(region)
		);
		configuration.opt("credentials.type").ifPresent(credentialsType ->
		{
			switch(credentialsType)
			{
				case "environment-variables":
				{
					clientBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
				}
				break;
				
				case "system-properties":
				{
					clientBuilder.setCredentials(new SystemPropertiesCredentialsProvider());
				}
				break;
				
				case "static":
				{
					clientBuilder.setCredentials(
						new com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider(
							new BasicAWSCredentials(
								configuration.get("credentials.access-key-id"),
								configuration.get("credentials.secret-access-key")
							)
						)
					);
				}
				break;
				
				case "default":
				{
					clientBuilder.setCredentials(new DefaultAWSCredentialsProviderChain());
				}
				break;
				
				default:
					// no credentials provider is used if not explicitly set
			}
		});
	}
}
