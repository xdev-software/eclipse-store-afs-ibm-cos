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
package software.xdev.eclipse.store.afs.ibm.access;

import java.util.ArrayList;
import java.util.List;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.iterable.S3Objects;
import com.ibm.cloud.objectstorage.services.s3.model.AmazonS3Exception;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;


/**
 * Communicates with the IBM COS based on the AmazonS3-API.
 */
public interface CosAccessCommunicator
{
	/**
	 * @return all s3 objects that start with the configured prefix.
	 */
	List<S3ObjectSummary> getExistingFilesWithPrefix();
	
	/**
	 * @param fileName to check for.
	 * @return true if a s3 object with the key of the given fileName exists
	 */
	boolean checkIfFileExists(final String fileName);
	
	/**
	 * Creates an empty s3 object.
	 * @param fileName of the s3 object to create.
	 */
	void createEmptyFile(final String fileName);
	
	/**
	 * Deletes the s3 object with the given fileName as key.
	 */
	void deleteFile(final String fileName);
	
	class Default implements CosAccessCommunicator
	{
		
		public static final int STATUS_CODE_FORBIDDEN = 403;
		private final AmazonS3 client;
		private final AccessConfiguration configuration;
		
		public Default(final AccessConfiguration configuration, final AmazonS3 client)
		{
			this.client = client;
			this.configuration = configuration;
		}
		
		@Override
		public List<S3ObjectSummary> getExistingFilesWithPrefix()
		{
			final ArrayList<S3ObjectSummary> existingFilesWithPrefix = new ArrayList<>();
			S3Objects
				.withPrefix(this.client, this.configuration.getBucketName(), this.configuration.getAccessFilePrefix())
				.forEach(existingFilesWithPrefix::add);
			return existingFilesWithPrefix;
		}
		
		@Override
		public boolean checkIfFileExists(final String fileName)
		{
			try
			{
				return this.client.doesObjectExist(this.configuration.getBucketName(), fileName);
			}
			catch(final AmazonS3Exception e)
			{
				// This is a "feature": https://github.com/aws/aws-sdk-java/issues/974#issuecomment-272634444
				if(e.getStatusCode() == STATUS_CODE_FORBIDDEN)
				{
					return false;
				}
				throw e;
			}
		}
		
		@Override
		public void createEmptyFile(final String fileName)
		{
			this.client.putObject(
				this.configuration.getBucketName(),
				fileName,
				""
			);
		}
		
		@Override
		public void deleteFile(final String fileName)
		{
			this.client.deleteObject(
				this.configuration.getBucketName(),
				fileName
			);
		}
	}
}
