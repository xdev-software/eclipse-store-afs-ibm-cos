/*
 * Copyright © 2023 XDEV Software (https://xdev.software)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
